# Telephony AI Project

This project includes tools for audio device management, audio routing, and a LiveAPI implementation for AI-powered telephony features.

## Project Structure

- `Get_started_LiveAPI.py` - Main implementation of the LiveAPI for AI telephony
- `list_audio_devices.py` - Utility to list available audio input/output devices
- `test_audio_routing.py` - Test script for audio routing between devices

## Setup Instructions

1. Create a virtual environment:
```bash
# Windows
python -m venv venv
.\venv\Scripts\activate

# Linux/Mac
python -m venv venv
source venv/bin/activate
```

2. Install the required packages:
```bash
pip install -r requirements.txt
```

3. Environment Setup:
   - For the LiveAPI functionality, set your `GOOGLE_API_KEY` environment variable
   - For audio routing, install VB-Audio Virtual Cable from https://vb-audio.com/Cable/

## Usage

1. List available audio devices:
```bash
python list_audio_devices.py
```

2. Test audio routing:
```bash
python test_audio_routing.py
```

3. Run the LiveAPI implementation:
```bash
# With camera mode (default)
python Get_started_LiveAPI.py

# With screen sharing
python Get_started_LiveAPI.py --mode screen

# Without video
python Get_started_LiveAPI.py --mode none
```

## Important Notes

- Use headphones to prevent audio feedback when using the LiveAPI
- The LiveAPI requires a valid Google API key
- Audio routing requires VB-Audio Virtual Cable or similar virtual audio device
- Make sure your system's audio devices are properly configured

## Dependencies

The project requires several Python packages that are listed in `requirements.txt`. Key dependencies include:
- google-genai - For Google AI integration
- opencv-python - For video capture
- pyaudio - For audio handling
- pillow - For image processing
- mss - For screen capture
- scipy - For audio processing
- numpy - For numerical operations
- pyaudiowpatch - For WASAPI loopback support
