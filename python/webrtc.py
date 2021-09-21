import argparse
import asyncio
import logging
import os
import random
import time 
import json
from queue import Queue
from aiortc.mediastreams import MediaStreamTrack

import cv2
from av import VideoFrame

from aiortc import (
    RTCIceCandidate,
    RTCPeerConnection,
    RTCSessionDescription,
    VideoStreamTrack,
)
from aiortc.contrib.media import MediaBlackhole, MediaPlayer, MediaRecorder
from aiortc.contrib.signaling import object_from_string, object_to_string, BYE

ROOT = os.path.dirname(__file__)
PHOTO_PATH = os.path.join(ROOT, "photo.jpg")

class VideoImageTrack(VideoStreamTrack):
    """
    A video stream track that returns a rotating image.
    """

    def __init__(self):
        super().__init__()  # don't forget this!
        self.img = cv2.imread(PHOTO_PATH, cv2.IMREAD_COLOR)

    async def recv(self):
        pts, time_base = await self.next_timestamp()

        # rotate image
        rows, cols, _ = self.img.shape
        M = cv2.getRotationMatrix2D((cols / 2, rows / 2), int(pts * time_base * 45), 1)
        img = cv2.warpAffine(self.img, M, (cols, rows))

        # create video frame
        frame = VideoFrame.from_ndarray(img, format="bgr24")
        frame.pts = pts
        frame.time_base = time_base
        return frame

async def run(pc, player, recorder, signaling):
    def add_tracks():
        # if player and player.audio:
        #     pc.addTrack(player.audio)

        # if player and player.video:
        #     pc.addTrack(player.video)
        # else:
        pc.addTrack(VideoImageTrack())

    @pc.on("track")
    async def on_track(track):
        print("Track %s received" % track.kind)
        recorder.addTrack(track)

    # connect to websocket and join
    params = await signaling.connect()
    async def send_candidates (desc):

        lines = desc.split("\r\n")
        for line in lines:
            if "a=candidate:" in line:
                line = line.replace("a=candidate:", "")
                parts = line.split(" ")
                candidate_obj = RTCIceCandidate(
                    component=int(parts[1]),
                    foundation=parts[0],
                    ip=parts[4],
                    port=int(parts[5]),
                    priority=int(parts[3]),
                    protocol=parts[2],
                    type=parts[7],
                )

                candidate_str = object_to_string(candidate_obj)
                await signaling.send_str(candidate_str)

    def removeCandidates (desc) -> str:
        without_candidates = ""

        lines = desc.split("\r\n")
        for line in lines:
            if "a=candidate:" in line or "a=end-of-candidates" in line:
                continue
            
            without_candidates += line + "\r\n"

        return without_candidates

    # consume signaling
    while True:
        obj = await signaling.receive()

        if isinstance(obj, RTCSessionDescription):
            await pc.setRemoteDescription(obj)
            await recorder.start()

            if obj.type == "offer":
                add_tracks()
                await pc.setLocalDescription(await pc.createAnswer())
                await signaling.send(pc.localDescription)
                
                # Do we need to send candidates separately, or are they sent with the 'answer' sdp
                # await send_candidates(pc.localDescription.sdp)

        elif isinstance(obj, RTCIceCandidate):
            await pc.addIceCandidate(obj)

        elif obj is BYE:
            print("Exiting")
            break
    
class QueueSignalling:
    def __init__(self):
        self.receiving_queue = Queue()
        self.sending_queue = Queue()

    async def connect(self):
        return json.loads('{"is_initiator": "false"}')

    async def close(self):
        pass

    async def receive(self):
        item = self.receiving_queue.get()
        return object_from_string(json.dumps(item))

    async def send(self, descr):
        self.sending_queue.put(object_to_string(descr))

    async def send_str(self, descr):
        self.sending_queue.put(descr)

    # added, not part of the interface.
    def put (self, data):
        self.receiving_queue.put(data)

    def get (self):
        return self.sending_queue.get()

class WebRTC:
    def __init__(self):
        self.pc = RTCPeerConnection()
        self.player = MediaPlayer('file.mp4')
        self.recorder = MediaRecorder('file_out.mp4')
        self.signaling = QueueSignalling()

    def start (self):
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

        try:
            loop.run_until_complete(
                run(pc=self.pc, player=self.player, recorder=self.recorder, signaling=self.signaling)
            )
        except KeyboardInterrupt:
            pass

        finally:
            # cleanup
            print(f'Cleanup...')
            loop.run_until_complete(self.recorder.stop())
            loop.run_until_complete(self.signaling.close())
            loop.run_until_complete(self.pc.close())


    def put(self, data):
        self.signaling.put(data)    

    def get(self):
        return self.signaling.get()    
