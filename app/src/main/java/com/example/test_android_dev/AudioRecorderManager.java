package com.example.test_android_dev;

import android.Manifest;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages audio recording functionality for voice commands.
 * Records audio in PCM format and provides byte array output.
 */
public class AudioRecorderManager {
    private static final String TAG = "AudioRecorderManager";
    private static AudioRecorderManager instance;

    private static final int SAMPLE_RATE = 16000; // 16kHz as specified in interface.md
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_MULTIPLIER = 4; // Buffer size multiplier for smooth recording

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService recordingExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioRecorderManager() {
        recordingExecutor = Executors.newSingleThreadExecutor();
    }

    public static synchronized AudioRecorderManager getInstance() {
        if (instance == null) {
            instance = new AudioRecorderManager();
        }
        return instance;
    }

    /**
     * Callback interface for audio recording results.
     */
    public interface AudioRecordCallback {
        void onAudioRecorded(byte[] audioBytes, long durationMs);
        void onError(String error);
    }

    /**
     * Start recording audio when user holds the button.
     * Recording continues until stopRecording() is called.
     */
    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw new RuntimeException("Invalid buffer size");
            }
            bufferSize *= BUFFER_SIZE_MULTIPLIER;

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new RuntimeException("AudioRecord failed to initialize");
            }

            audioRecord.startRecording();
            isRecording = true;
            Log.i(TAG, "Audio recording started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio recording", e);
            isRecording = false;
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    /**
     * Stop recording and return the recorded audio data.
     */
    public void stopRecording(@NonNull AudioRecordCallback callback) {
        if (!isRecording || audioRecord == null) {
            mainHandler.post(() -> callback.onError("Not recording"));
            return;
        }

        recordingExecutor.execute(() -> {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)];
                long startTime = System.currentTimeMillis();

                // Read audio data until recording is stopped
                while (isRecording && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                long durationMs = System.currentTimeMillis() - startTime;
                byte[] audioBytes = outputStream.toByteArray();
                outputStream.close();

                // Clean up
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                isRecording = false;

                Log.i(TAG, "Audio recording stopped. Duration: " + durationMs + "ms, Size: " + audioBytes.length + " bytes");
                mainHandler.post(() -> callback.onAudioRecorded(audioBytes, durationMs));

            } catch (Exception e) {
                Log.e(TAG, "Error during audio recording", e);
                if (audioRecord != null) {
                    try {
                        audioRecord.stop();
                        audioRecord.release();
                    } catch (Exception releaseEx) {
                        Log.e(TAG, "Error releasing AudioRecord", releaseEx);
                    }
                }
                audioRecord = null;
                isRecording = false;
                mainHandler.post(() -> callback.onError("Recording error: " + e.getMessage()));
            }
        });
    }

    /**
     * Check if audio recording is currently in progress.
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Get the audio format metadata as specified in interface.md.
     */
    public AudioMeta getAudioMeta(long durationMs) {
        return new AudioMeta("pcm", SAMPLE_RATE, durationMs);
    }

    /**
     * Audio metadata class matching interface.md specification.
     */
    public static class AudioMeta {
        public final String format;
        public final int sampleRate;
        public final long durationMs;

        public AudioMeta(String format, int sampleRate, long durationMs) {
            this.format = format;
            this.sampleRate = sampleRate;
            this.durationMs = durationMs;
        }
    }

    /**
     * Release resources when no longer needed.
     */
    public void release() {
        if (isRecording && audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            isRecording = false;
        }
        if (recordingExecutor != null && !recordingExecutor.isShutdown()) {
            recordingExecutor.shutdown();
        }
    }
}