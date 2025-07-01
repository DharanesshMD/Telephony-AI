import pyaudio
import asyncio
import numpy as np
from scipy import signal
import array

# Audio settings
FORMAT = pyaudio.paInt16
CHUNK_SIZE = 1024

class AudioRouter:
    def __init__(self):
        try:
            import pyaudiowpatch as pyaudio_patch
            self.pya = pyaudio_patch.PyAudio()
            print("Using PyAudioWPatch for WASAPI loopback")
        except ImportError:
            self.pya = pyaudio.PyAudio()
            print("PyAudioWPatch not found, using standard PyAudio")
        
        self.system_capture_stream = None
        self.virtual_mic_stream = None
        
        # List all available audio devices
        # self.print_audio_devices()

    def print_audio_devices(self):
        """Print all available audio devices and their properties."""
        print("\nAvailable Audio Devices:")
        print("-" * 60)
        for i in range(self.pya.get_device_count()):
            try:
                device_info = self.pya.get_device_info_by_index(i)
                print(f"\nDevice {i}:")
                print(f"  Name: {device_info['name']}")
                print(f"  Max Input Channels: {device_info['maxInputChannels']}")
                print(f"  Max Output Channels: {device_info['maxOutputChannels']}")
                print(f"  Default Sample Rate: {int(device_info['defaultSampleRate'])}Hz")
                if device_info['maxOutputChannels'] > 0:
                    print("  Type: Output/Playback Device")
                if device_info['maxInputChannels'] > 0:
                    print("  Type: Input/Recording Device")
                if device_info.get('isLoopbackDevice', False):
                    print("  Type: Loopback Device")
            except Exception as e:
                print(f"  Error getting device {i} info: {e}")
        print("-" * 60 + "\n")

    def find_device_by_name(self, name_fragment, is_input=True, is_loopback=False):
        """Find a device by partial name match."""
        for i in range(self.pya.get_device_count()):
            try:
                info = self.pya.get_device_info_by_index(i)
                if name_fragment.lower() in info['name'].lower():
                    if is_input and info['maxInputChannels'] > 0:
                        if is_loopback == bool(info.get('isLoopbackDevice', False)):
                            return info
                    elif not is_input and info['maxOutputChannels'] > 0:
                        return info
            except Exception as e:
                continue
        return None

    def find_virtual_cable_output(self):
        """Find VB-Audio Virtual Cable or similar virtual audio device for output."""
        virtual_devices = [
            "CABLE Input",
            "VB-Audio Virtual Cable",
            "Virtual Audio Cable",
            "Stereo Mix"
        ]
        
        for device_name in virtual_devices:
            device_info = self.find_device_by_name(device_name, is_input=False)
            if device_info:
                return device_info
        
        print("Warning: No virtual audio cable found. You may need to install VB-Audio Virtual Cable.")
        print("Download from: https://vb-audio.com/Cable/")
        return None

    def resample_audio(self, data, original_rate, target_rate, original_channels, target_channels):
        """Resample audio data and convert channels."""
        try:
            # Convert bytes to numpy array
            audio_data = np.frombuffer(data, dtype=np.int16)
            
            # Handle stereo to mono conversion
            if original_channels == 2 and target_channels == 1:
                # Reshape to stereo and average channels
                audio_data = audio_data.reshape(-1, 2)
                audio_data = np.mean(audio_data, axis=1, dtype=np.int16)
            elif original_channels == 1 and target_channels == 2:
                # Duplicate mono to stereo
                audio_data = np.repeat(audio_data, 2)
            
            # Resample if rates are different
            if original_rate != target_rate:
                # Calculate the number of samples after resampling
                num_samples = int(len(audio_data) * target_rate / original_rate)
                audio_data = signal.resample(audio_data, num_samples).astype(np.int16)
            
            return audio_data.tobytes()
        except Exception as e:
            print(f"Error in resampling: {e}")
            return data

    def get_device_info(self, is_input=False, use_virtual=False):
        """Get device info with default settings."""
        try:
            if is_input and not use_virtual:
                # Find the system audio loopback
                device_info = self.find_device_by_name("Headset Earphone", is_input=True, is_loopback=True)
                if not device_info:
                    try:
                        device_info = self.pya.get_default_wasapi_loopback()
                        print("Using default WASAPI loopback device")
                    except:
                        device_info = self.pya.get_default_input_device_info()
                channels = 2  # Loopback is typically stereo
                rate = int(device_info.get('defaultSampleRate', 48000))
            else:
                # Find virtual audio cable for output
                device_info = self.find_virtual_cable_output()
                if not device_info:
                    # Fallback to default output device
                    device_info = self.pya.get_default_output_device_info()
                channels = 2  # Most output devices support stereo
                rate = int(device_info.get('defaultSampleRate', 48000))
            
            # Print device info for debugging
            print(f"\nSelected {'input' if is_input else 'output'} device:")
            print(f"  Name: {device_info['name']}")
            print(f"  Channels: {channels}")
            print(f"  Sample Rate: {rate}Hz")
            print(f"  Index: {device_info['index']}")
            
            return {
                'index': device_info['index'],
                'name': device_info['name'],
                'channels': channels,
                'rate': rate
            }
        except Exception as e:
            print(f"Error getting device info: {str(e)}")
            return None

    async def route_system_to_virtual_mic(self):
        """Capture system audio output and route it to virtual microphone."""
        try:
            # Get device info
            input_info = self.get_device_info(is_input=True)  # System audio loopback
            output_info = self.get_device_info(is_input=False, use_virtual=True)  # Virtual cable
            
            if not input_info or not output_info:
                raise OSError("Failed to get device information")
            
            print(f"Capturing system audio from: {input_info['name']}")
            print(f"Routing to virtual device: {output_info['name']}")
            print(f"Input: {input_info['channels']} channels at {input_info['rate']}Hz")
            print(f"Output: {output_info['channels']} channels at {output_info['rate']}Hz")
            
            # Open stream for capturing system audio
            try:
                self.system_capture_stream = await asyncio.to_thread(
                    self.pya.open,
                    format=FORMAT,
                    channels=input_info['channels'],
                    rate=input_info['rate'],
                    input=True,
                    input_device_index=input_info['index'],
                    frames_per_buffer=CHUNK_SIZE
                )
            except Exception as e:
                print(f"Error opening system capture stream: {str(e)}")
                raise
            
            # Open stream for virtual microphone output
            try:
                self.virtual_mic_stream = await asyncio.to_thread(
                    self.pya.open,
                    format=FORMAT,
                    channels=output_info['channels'],
                    rate=output_info['rate'],
                    output=True,
                    output_device_index=output_info['index'],
                    frames_per_buffer=CHUNK_SIZE
                )
            except Exception as e:
                print(f"Error opening virtual microphone stream: {str(e)}")
                raise
            
            print("Audio routing streams opened successfully")
            print("Starting audio routing... (Press Ctrl+C to stop)")
            print("Now set your virtual audio cable as the microphone input in your application.")
            
            while True:
                try:
                    # Read from system audio
                    data = await asyncio.to_thread(
                        self.system_capture_stream.read, 
                        CHUNK_SIZE, 
                        exception_on_overflow=False
                    )
                    
                    if data:
                        try:
                            # Resample and convert channels if needed
                            processed_data = self.resample_audio(
                                data,
                                input_info['rate'],
                                output_info['rate'],
                                input_info['channels'],
                                output_info['channels']
                            )
                            
                            # Write to virtual microphone
                            await asyncio.to_thread(self.virtual_mic_stream.write, processed_data)
                        except Exception as e:
                            print(f"Error processing audio data: {str(e)}")
                            continue
                            
                except Exception as e:
                    print(f"Error in audio routing: {str(e)}")
                    break
                    
        except OSError as e:
            print(f"Error setting up audio routing: {str(e)}")
            print("\nTroubleshooting tips:")
            print("1. Install VB-Audio Virtual Cable from https://vb-audio.com/Cable/")
            print("2. Make sure Windows audio devices are properly configured")
            print("3. Run the program as Administrator if needed")
            return
        finally:
            self.cleanup()

    def cleanup(self):
        """Clean up audio streams and PyAudio instance."""
        print("\nCleaning up audio streams...")
        try:
            if self.system_capture_stream:
                self.system_capture_stream.close()
                self.system_capture_stream = None
        except:
            pass
        try:
            if self.virtual_mic_stream:
                self.virtual_mic_stream.close()
                self.virtual_mic_stream = None
        except:
            pass
        # Don't terminate PyAudio instance to allow reuse
        print("Cleanup complete")

async def main():
    router = AudioRouter()
    try:
        await router.route_system_to_virtual_mic()
    except KeyboardInterrupt:
        print("\nStopping audio routing...")
    finally:
        router.cleanup()

if __name__ == "__main__":
    print("Audio Routing Program")
    print("This program routes system audio to a virtual microphone.")
    print("You'll need VB-Audio Virtual Cable or similar software installed.")
    print("Press Ctrl+C to stop the program.\n")
    
    # Check for required dependencies
    try:
        import scipy
        import numpy
        print("Required dependencies found.")
    except ImportError as e:
        print(f"Missing dependency: {e}")
        print("Install with: pip install scipy numpy")
        exit(1)
    
    asyncio.run(main())
