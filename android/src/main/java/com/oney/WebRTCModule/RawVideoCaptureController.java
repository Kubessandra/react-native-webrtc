package com.oney.WebRTCModule;

import org.webrtc.VideoCapturer;

public class RawVideoCaptureController extends AbstractVideoCaptureController {
    private static final int DEFAULT_FPS = 30;
    protected RawVideoCapturer videoCapturer;

    public RawVideoCaptureController(int width, int height) {
        super(width, height, DEFAULT_FPS);
    }

    public void sendFrame(byte[] videoBuffer, int size, int width, int height) {
        if (this.videoCapturer == null) throw new Error("No video capturer available");
        this.videoCapturer.sendFrame(videoBuffer, size, width, height);
    }

    @Override
    protected VideoCapturer createVideoCapturer() {
        this.videoCapturer = new RawVideoCapturer();
        return this.videoCapturer;
    }
}
