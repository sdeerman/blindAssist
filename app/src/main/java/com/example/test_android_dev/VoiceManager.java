package com.example.test_android_dev;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封装语音输入/输出（ASR/TTS）
 */
public class VoiceManager {
    private static final String TAG = "VoiceManager";
    private static VoiceManager instance;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean isTtsReady = false;
    private boolean isTtsInitializing = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> utteranceCallbacks = new ConcurrentHashMap<>();
    private final ArrayList<PendingUtterance> pendingUtterances = new ArrayList<>();

    // A helper class to queue requests that arrive before TTS is ready
    private static class PendingUtterance {
        final String text;
        final boolean isImmediate;
        final Runnable onDoneCallback;

        PendingUtterance(String text, boolean isImmediate, Runnable onDoneCallback) {
            this.text = text;
            this.isImmediate = isImmediate;
            this.onDoneCallback = onDoneCallback;
        }
    }

    private VoiceManager() {}

    public static synchronized VoiceManager getInstance() {
        if (instance == null) {
            instance = new VoiceManager();
        }
        return instance;
    }

    public void init(Context context) {
        if (tts != null || isTtsInitializing) {
            return; // Avoid re-initialization
        }
        isTtsInitializing = true;

        tts = new TextToSpeech(context, status -> {
            isTtsInitializing = false;
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                isTtsReady = true;
                Log.d(TAG, "TTS initialized successfully.");
                processPendingUtterances();
            } else {
                Log.e(TAG, "TTS initialization failed.");
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) { }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId == null) return;
                Runnable callback = utteranceCallbacks.remove(utteranceId);
                if (callback != null) {
                    mainHandler.post(callback);
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS Error for utterance: " + utteranceId);
                if (utteranceId != null) {
                    utteranceCallbacks.remove(utteranceId);
                }
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } else {
            Log.e(TAG, "Speech recognition not available on this device.");
        }
    }

    private void processPendingUtterances() {
        synchronized (pendingUtterances) {
            if (pendingUtterances.isEmpty()) return;

            Log.d(TAG, "Processing " + pendingUtterances.size() + " pending utterances.");
            for (PendingUtterance utterance : pendingUtterances) {
                if (utterance.isImmediate) {
                    speakImmediateInternal(utterance.text, utterance.onDoneCallback);
                } else {
                    speakInternal(utterance.text, utterance.onDoneCallback);
                }
            }
            pendingUtterances.clear();
        }
    }

    public void speak(String text) {
        speak(text, null);
    }

    public void speak(String text, Runnable onDoneCallback) {
        if (isTtsReady && tts != null) {
            speakInternal(text, onDoneCallback);
        } else {
            Log.d(TAG, "TTS not ready, queuing utterance: " + text);
            synchronized (pendingUtterances) {
                pendingUtterances.add(new PendingUtterance(text, false, onDoneCallback));
            }
        }
    }
    
    public void speakImmediate(String text) {
        speakImmediate(text, null);
    }

    public void speakImmediate(String text, Runnable onDoneCallback) {
        if (isTtsReady && tts != null) {
            speakImmediateInternal(text, onDoneCallback);
        } else {
            Log.d(TAG, "TTS not ready, queuing immediate utterance: " + text);
            synchronized (pendingUtterances) {
                // When queuing an immediate utterance, it should clear previous pending ones.
                pendingUtterances.clear();
                pendingUtterances.add(new PendingUtterance(text, true, onDoneCallback));
            }
        }
    }

    private void speakInternal(String text, Runnable onDoneCallback) {
        final String utteranceId = UUID.randomUUID().toString();
        if (onDoneCallback != null) {
            utteranceCallbacks.put(utteranceId, onDoneCallback);
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
    }

    private void speakImmediateInternal(String text, Runnable onDoneCallback) {
        final String utteranceId = UUID.randomUUID().toString();
        if (onDoneCallback != null) {
            utteranceCallbacks.put(utteranceId, onDoneCallback);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    public interface VoiceCallback {
        void onResult(String text);
        void onError(String error);
    }

    public void startListening(final VoiceCallback callback) {
        if (speechRecognizer == null) {
            if (callback != null) callback.onError("Speech recognizer not initialized.");
            return;
        }

        mainHandler.post(() -> speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "onReadyForSpeech"); }
            @Override public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech"); }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorText(error);
                Log.e(TAG, "ASR Error: " + errorMessage);
                if (callback != null) callback.onError(errorMessage);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    Log.d(TAG, "ASR Result: " + text);
                    if (callback != null) callback.onResult(text);
                } else {
                    if (callback != null) callback.onError("No speech input");
                }
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        }));

        mainHandler.post(() -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toString());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speechRecognizer.startListening(intent);
        });
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            mainHandler.post(speechRecognizer::destroy);
        }
        isTtsReady = false;
        instance = null;
    }

    private String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: message = "Audio recording error"; break;
            case SpeechRecognizer.ERROR_CLIENT: message = "Client side error"; break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Insufficient permissions"; break;
            case SpeechRecognizer.ERROR_NETWORK: message = "Network error"; break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: message = "Network timeout"; break;
            case SpeechRecognizer.ERROR_NO_MATCH: message = "No match"; break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: message = "Recognizer busy"; break;
            case SpeechRecognizer.ERROR_SERVER: message = "Error from server"; break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: message = "No speech input"; break;
            default: message = "Didn't understand, please try again."; break;
        }
        return message;
    }
}