package com.oney.WebRTCModule;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import org.webrtc.CapturerObserver;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class RawVideoCapturer implements VideoCapturer {
    private final static String TAG = "RawVideoCapturer";
    private CapturerObserver capturerObserver;
    private MediaCodec m_codec;
    private DecodeFramesTask m_frame_task;
    private ByteArrayOutputStream tmpBuffer = new ByteArrayOutputStream();
    private ByteArrayOutputStream toBeProcessed = new ByteArrayOutputStream();

    public RawVideoCapturer() {}

    public void sendFrame(byte[] videoBuffer, int size, int width, int height) {
        try {
            Log.d(TAG, "New Frame received: " + Arrays.toString(videoBuffer) + "size" + size);
            if (videoBuffer.length >= 4 && videoBuffer[0] == 0 && videoBuffer[1] == 0 && videoBuffer[2] == 0 && videoBuffer[3] == 1) {
                Log.d(TAG, "New Nal received: " + Arrays.toString(videoBuffer) + "size" + size);
                // New NAL
                toBeProcessed.write(tmpBuffer.toByteArray());
                tmpBuffer.reset();
            }
            tmpBuffer.write(videoBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        Log.d(TAG, "Start raw video capture");
        try {
            this.m_codec = MediaCodec.createDecoderByType("video/avc");
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_WIDTH, width);
        format.setInteger(MediaFormat.KEY_HEIGHT, height);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE,30);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
        this.m_codec.configure(format, null, null, 0);
        this.m_codec.start();
        m_frame_task = new DecodeFramesTask();
        m_frame_task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

    private class DecodeFramesTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... strings) {
            while (!isCancelled()) {
                byte[] frameData = toBeProcessed.toByteArray();
                toBeProcessed.reset();
                int inIndex = m_codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer inputBuffer = m_codec.getInputBuffer(inIndex);
                    inputBuffer.clear();
                    inputBuffer.put(frameData);
                    m_codec.queueInputBuffer(inIndex, 0, frameData.length, 16, 0);
                }

                MediaCodec.BufferInfo buffInfo = new MediaCodec.BufferInfo();
                int outIndex = m_codec.dequeueOutputBuffer(buffInfo, 10000);

                if (outIndex >= 0) {
                    ByteBuffer buffer = m_codec.getOutputBuffer(outIndex);
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    long timestampNS = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    NV21Buffer nv21Buffer = new NV21Buffer(bytes, 1280, 720, null);

                    VideoFrame videoFrame = new VideoFrame(nv21Buffer, 0, timestampNS);
                    capturerObserver.onFrameCaptured(videoFrame);

                    videoFrame.release();
                    m_codec.releaseOutputBuffer(outIndex, false);
                }
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                m_codec.stop();
                m_codec.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}