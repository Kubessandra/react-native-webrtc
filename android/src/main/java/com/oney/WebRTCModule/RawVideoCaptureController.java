package com.oney.WebRTCModule;

import org.webrtc.VideoCapturer;

public class RawVideoCaptureController extends AbstractVideoCaptureController {
    private static final int DEFAULT_FPS = 30;
    protected RawVideoCapturer videoCapturer;

    public RawVideoCaptureController(int width, int height) {
        super(width, height, DEFAULT_FPS);
    }

    public void sendFrame(byte[] videoBuffer) {
        if (this.videoCapturer == null) throw new Error("No video capturer available");
        this.videoCapturer.sendFrame(videoBuffer);
    }

    @Override
    protected VideoCapturer createVideoCapturer() {
        this.videoCapturer = new RawVideoCapturer();
        return this.videoCapturer;
    }
}
