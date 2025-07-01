# -*- coding: utf-8 -*-
# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
## Setup

To install the dependencies for this script, run:

``` 
pip install google-genai opencv-python pyaudio pillow mss
```

Before running this script, ensure the `GOOGLE_API_KEY` environment
variable is set to the api-key you obtained from Google AI Studio.

Important: **Use headphones**. This script uses the system default audio
input and output, which often won't include echo cancellation. So to prevent
the model from interrupting itself it is important that you use headphones. 

## Run

To run the script:

```
python Get_started_LiveAPI.py
```

The script takes a video-mode flag `--mode`, this can be "camera", "screen", or "none".
The default is "camera". To share your screen run:

```
python Get_started_LiveAPI.py --mode screen
```
"""

import asyncio
import base64
import io
import os
import sys
import traceback

import cv2
import pyaudio
import PIL.Image
import mss

import argparse
import logging

from google import genai

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('audio_streaming.log')
    ]
)

if sys.version_info < (3, 11, 0):
    import taskgroup, exceptiongroup

    asyncio.TaskGroup = taskgroup.TaskGroup
    asyncio.ExceptionGroup = exceptiongroup.ExceptionGroup

FORMAT = pyaudio.paInt16
CHANNELS = 1
SEND_SAMPLE_RATE = 16000
RECEIVE_SAMPLE_RATE = 24000
CHUNK_SIZE = 1024

MODEL = "models/gemini-2.0-flash-live-001"

DEFAULT_MODE = "camera"

client = genai.Client(http_options={"api_version": "v1beta"})

CONFIG = {"response_modalities": ["AUDIO"]}

pya = pyaudio.PyAudio()

class AudioLoop:
    def __init__(self, video_mode=DEFAULT_MODE, system_instruction=None):
        self.video_mode = video_mode
        self.system_instruction = system_instruction
        logging.info(f"Initializing AudioLoop with video mode: {video_mode}")

        self.audio_in_queue = None
        self.out_queue = None

        self.session = None

        self.send_text_task = None
        self.receive_audio_task = None
        self.play_audio_task = None
        
        # Audio listening control
        self.listening_enabled = True
        self.listening_event = asyncio.Event()
        self.listening_event.set()  # Start enabled

        logging.info("AudioLoop initialization completed")

    async def enable_listening(self):
        """Enable audio input capture"""
        if not self.listening_enabled:
            logging.info("Enabling audio input capture")
            self.listening_enabled = True
            self.listening_event.set()

    async def disable_listening(self):
        """Disable audio input capture"""
        if self.listening_enabled:
            logging.info("Disabling audio input capture")
            self.listening_enabled = False
            self.listening_event.clear()

    async def send_text(self):
        while True:
            text = await asyncio.to_thread(
                input,
                "message > ",
            )
            if text.lower() == "q":
                break
            
            await self.session.send(input=text or ".", end_of_turn=True)

    def _get_frame(self, cap):
        # Read the frameq
        ret, frame = cap.read()
        # Check if the frame was read successfully
        if not ret:
            return None
        # Fix: Convert BGR to RGB color space
        # OpenCV captures in BGR but PIL expects RGB format
        # This prevents the blue tint in the video feed
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = PIL.Image.fromarray(frame_rgb)  # Now using RGB frame
        img.thumbnail([1024, 1024])

        image_io = io.BytesIO()
        img.save(image_io, format="jpeg")
        image_io.seek(0)

        mime_type = "image/jpeg"
        image_bytes = image_io.read()
        return {"mime_type": mime_type, "data": base64.b64encode(image_bytes).decode()}

    async def get_frames(self):
        # This takes about a second, and will block the whole program
        # causing the audio pipeline to overflow if you don't to_thread it.
        cap = await asyncio.to_thread(
            cv2.VideoCapture, 0
        )  # 0 represents the default camera

        while True:
            frame = await asyncio.to_thread(self._get_frame, cap)
            if frame is None:
                break

            await asyncio.sleep(1.0)

            await self.out_queue.put(frame)

        # Release the VideoCapture object
        cap.release()

    def _get_screen(self):
        sct = mss.mss()
        monitor = sct.monitors[0]

        i = sct.grab(monitor)

        mime_type = "image/jpeg"
        image_bytes = mss.tools.to_png(i.rgb, i.size)
        img = PIL.Image.open(io.BytesIO(image_bytes))

        image_io = io.BytesIO()
        img.save(image_io, format="jpeg")
        image_io.seek(0)

        image_bytes = image_io.read()
        return {"mime_type": mime_type, "data": base64.b64encode(image_bytes).decode()}

    async def get_screen(self):

        while True:
            frame = await asyncio.to_thread(self._get_screen)
            if frame is None:
                break

            await asyncio.sleep(1.0)

            await self.out_queue.put(frame)

    async def send_realtime(self):
        while True:
            msg = await self.out_queue.get()
            await self.session.send(input=msg)

    async def listen_audio(self):
        input_device_index = 3
        try:
            device_info = pya.get_device_info_by_index(input_device_index)
            logging.info(f"Initializing audio input stream with device: {device_info['name']}")
        except:
            logging.warning(f"Could not get info for input device index {input_device_index}")
            
        self.audio_stream = await asyncio.to_thread(
            pya.open,
            format=FORMAT,
            channels=CHANNELS,
            rate=SEND_SAMPLE_RATE,
            input=True,
            input_device_index=input_device_index,
            frames_per_buffer=CHUNK_SIZE,
        )
        logging.info(f"Audio input stream initialized (Rate: {SEND_SAMPLE_RATE}Hz, Channels: {CHANNELS})")
        
        if __debug__:
            kwargs = {"exception_on_overflow": False}
        else:
            kwargs = {}
        
        while True:
            try:
                # Wait for listening to be enabled
                await self.listening_event.wait()
                
                data = await asyncio.to_thread(self.audio_stream.read, CHUNK_SIZE, **kwargs)
                await self.out_queue.put({"data": data, "mime_type": "audio/pcm"})
            except Exception as e:
                logging.error(f"Error during audio capture: {str(e)}")
                continue

    async def receive_audio(self):
        "Background task to reads from the websocket and write pcm chunks to the output queue"
        while True:
            try:
                turn = self.session.receive()
                async for response in turn:
                    if data := response.data:
                        logging.debug("Received audio data from websocket")
                        self.audio_in_queue.put_nowait(data)
                        continue
                    if text := response.text:
                        print(text, end="")

                # If you interrupt the model, it sends a turn_complete.
                # For interruptions to work, we need to stop playback.
                # So empty out the audio queue because it may have loaded
                # much more audio than has played yet.
                logging.info("Clearing audio queue for interruption")
                await self.enable_listening()  # Enable listening when clearing queue
                while not self.audio_in_queue.empty():
                    self.audio_in_queue.get_nowait()
            except Exception as e:
                logging.error(f"Error in receive_audio: {str(e)}")

    async def play_audio(self):
        output_device_index = 5
        try:
            device_info = pya.get_device_info_by_index(output_device_index)
            logging.info(f"Initializing audio output stream with device: {device_info['name']}")
        except:
            logging.warning(f"Could not get info for output device index {output_device_index}")
        
        stream = await asyncio.to_thread(
            pya.open,
            format=FORMAT,
            channels=CHANNELS,
            rate=RECEIVE_SAMPLE_RATE,
            output=True,
            output_device_index=output_device_index,
        )
        logging.info(f"Audio output stream initialized (Rate: {RECEIVE_SAMPLE_RATE}Hz, Channels: {CHANNELS})")
        
        while True:
            try:
                # Log start of waiting for audio data
                logging.debug("Waiting for audio data...")
                
                # Get audio data from queue
                bytestream = await self.audio_in_queue.get()
                
                # Disable listening before playback
                await self.disable_listening()
                
                # Log start of audio playback
                logging.info("Starting audio playback")
                
                # Write audio data to stream
                await asyncio.to_thread(stream.write, bytestream)
                
                # Log completion of audio playback
                logging.info("Audio playback completed")
                
            except Exception as e:
                logging.error(f"Error during audio playback: {str(e)}")
                continue

    async def run(self):
        logging.info("Starting AudioLoop run")
        try:
            logging.info(f"Connecting to model {MODEL}")
            
            async with (
                client.aio.live.connect(model=MODEL, config=CONFIG) as session,
                asyncio.TaskGroup() as tg,
            ):
                self.session = session

                self.audio_in_queue = asyncio.Queue()
                self.out_queue = asyncio.Queue(maxsize=5)

                if self.system_instruction:
                    await self.session.send(input=self.system_instruction, end_of_turn=True)

                send_text_task = tg.create_task(self.send_text())
                tg.create_task(self.send_realtime())
                tg.create_task(self.listen_audio())
                if self.video_mode == "camera":
                    tg.create_task(self.get_frames())
                elif self.video_mode == "screen":
                    tg.create_task(self.get_screen())

                tg.create_task(self.receive_audio())
                tg.create_task(self.play_audio())

                await send_text_task
                raise asyncio.CancelledError("User requested exit")

        except asyncio.CancelledError:
            logging.info("AudioLoop cancelled by user")
        except ExceptionGroup as EG:
            logging.error("Exception in AudioLoop run", exc_info=EG)
            self.audio_stream.close()
            traceback.print_exception(EG)
        finally:
            logging.info("AudioLoop shutdown complete")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        type=str,
        default=DEFAULT_MODE,
        help="pixels to stream from",
        choices=["camera", "screen", "none"],
    )
    args = parser.parse_args()
    
    # Define the instruction directly in the code
    fixed_instruction = """You are Dharun's personal AI assistant who attended a call for Dharun. Start by introducing yourself and informing the caller that Dharun is currently busy. Ask the caller if you can take a message. For each response, communicate in both English and Tamil. Speak naturally and natively in both languages without explicitly translating.

Here are the guidelines:
1. Introduce yourself.
2. Inform the caller that Dharun is busy right now.
3. Ask them if you can take a message.

You should start speaking in English and then switch to Tamil naturally in every response."""

    main = AudioLoop(video_mode=args.mode, system_instruction=fixed_instruction)
    asyncio.run(main.run())

