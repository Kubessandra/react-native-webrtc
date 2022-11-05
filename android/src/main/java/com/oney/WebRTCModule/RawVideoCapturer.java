package com.oney.WebRTCModule;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
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
import java.util.concurrent.TimeUnit;

public class RawVideoCapturer implements VideoCapturer {
    private final static String TAG = "RawVideoCapturer";
    private CapturerObserver capturerObserver;
    private MediaCodec m_codec;
    private DecodeFramesTask m_frame_task;
    private int current_width = 0;
    private int current_height = 0;
    private byte[] sps;
    private boolean processing_sps;
    private final ByteArrayOutputStream tmpBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream toBeProcessed = new ByteArrayOutputStream();

    public RawVideoCapturer() {}

    public void sendFrame(byte[] videoBuffer) {
        try {
            if (isNewNal(videoBuffer)) {
                processNewNal(videoBuffer[4], tmpBuffer.toByteArray());
                tmpBuffer.reset();
            }
            tmpBuffer.write(videoBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processNewNal(byte header, byte[] oldBuffer) throws IOException {
        // New NAL UNIT
        int val_unit_type = header & 0x1F; // 00011111
        // UNIT_TYPE for SPS
        if (val_unit_type == 7 && sps == null) {
            // SPS
            Log.d(TAG, "SPS Frame");
            processing_sps = true;
        } else {
            if (processing_sps) {
                processing_sps = false;
                sps = oldBuffer;
            } else {
                if (m_codec != null) {
                    toBeProcessed.write(oldBuffer);
                }
            }
        }
    }

    private boolean isNewNal(byte[] videoBuffer) {
        return videoBuffer.length >= 4 && videoBuffer[0] == 0 && videoBuffer[1] == 0 && videoBuffer[2] == 0 && videoBuffer[3] == 1;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        Log.d(TAG, "Start raw video capture w:" + width + " h:" + height + " framerate:" + framerate);
        current_width = width;
        current_height = height;
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
                if (m_codec == null && sps != null) {
                    setupMediaCodec(sps);
                } else if (m_codec != null) {
                    byte[] frameData = toBeProcessed.toByteArray();
                    toBeProcessed.reset();
                    fillInputBufferToBeProcessed(frameData);
                    processOutputBufferResult();
                }
            }
            return "";
        }

        private void processOutputBufferResult() {
            // Retrieve result of the codec
            MediaCodec.BufferInfo buffInfo = new MediaCodec.BufferInfo();
            int outIndex = m_codec.dequeueOutputBuffer(buffInfo, 10000);
            if (outIndex >= 0) {
                ByteBuffer buffer = m_codec.getOutputBuffer(outIndex);
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                sendNV12ToObserver(bytes);
                m_codec.releaseOutputBuffer(outIndex, false);
            }
        }

        private void fillInputBufferToBeProcessed(byte[] input) {
            // Fill buffer to be processed
            int inIndex = m_codec.dequeueInputBuffer(10000);
            if (inIndex >= 0) {
                ByteBuffer inputBuffer = m_codec.getInputBuffer(inIndex);
                inputBuffer.clear();
                inputBuffer.put(input);
                m_codec.queueInputBuffer(inIndex, 0, input.length, 16, 0);
            }
        }

        private void sendNV12ToObserver(byte[] bytes) {
            long timestampNS = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            NV12toNV21(bytes);
            NV21Buffer nv21Buffer = new NV21Buffer(bytes, current_width, current_height, null);

            VideoFrame videoFrame = new VideoFrame(nv21Buffer, 0, timestampNS);
            capturerObserver.onFrameCaptured(videoFrame);

            videoFrame.release();
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                if (m_codec != null) {
                    m_codec.stop();
                    m_codec.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupMediaCodec(byte[] spsUnit) {
        Log.d(TAG, "Setup media codec");
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", current_width, current_height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(spsUnit));
        try {
            String decoderName = new MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format);

            // Patch, hardware decoder for Pixel 7 not working, switch to software decoder.
            if (decoderName.contains("c2.exynos.h264.decoder")) {
                decoderName = "OMX.google.h264.decoder";
            }

            m_codec = MediaCodec.createByCodecName(decoderName);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        m_codec.configure(format, null, null, 0);
        m_codec.start();
    }

    // http://androidspanner.blogspot.com/2016/06/convert-nv12-to-nv21-in-android-with.html
    private void NV12toNV21(byte[] bytes) {
        final int length = bytes.length;
        for (int i1 = 0; i1 < length; i1 += 2) {
            if (i1 >= current_width * current_height) {
                byte tmp = bytes[i1];
                bytes[i1] = bytes[i1+1];
                bytes[i1+1] = tmp;
            }
        }
    }
}
