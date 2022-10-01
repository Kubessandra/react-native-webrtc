package com.oney.WebRTCModule;

import android.content.Context;
import android.os.SystemClock;

import org.webrtc.CapturerObserver;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.util.concurrent.TimeUnit;

public class RawVideoCapturer implements VideoCapturer {
    private final static String TAG = "RawVideoCapturer";
    private CapturerObserver capturerObserver;

    public RawVideoCapturer() {}

    public void sendFrame(byte[] videoBuffer, int size, int width, int height) {
        long timestampNS = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
        NV21Buffer buffer = new NV21Buffer(videoBuffer, width, height, null);

        VideoFrame videoFrame = new VideoFrame(buffer, 0, timestampNS);
        capturerObserver.onFrameCaptured(videoFrame);

        videoFrame.release();
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
    }

    @Override
    public void stopCapture() throws InterruptedException {
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        // Empty on purpose
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean isScreencast() {
        return false;
    }
}