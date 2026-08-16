package com.herdr.remote.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class SpeechState {
    object Idle : SpeechState()
    object Initializing : SpeechState()
    data class Listening(val rmsDb: Float = 0f, val partialText: String = "") : SpeechState()
    data class Success(val finalText: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

class SpeechRecognizerHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private var isListeningActive = false
    private var currentLanguage = "en-US"
    private val accumulatedTextBuilder = StringBuilder()
    private var latestPartialChunk = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(languageCode: String = "en-US") {
        if (!isAvailable()) {
            _speechState.value = SpeechState.Error("Speech recognition is not available on this device.")
            return
        }

        cleanupRecognizer()

        isListeningActive = true
        currentLanguage = languageCode
        accumulatedTextBuilder.clear()
        latestPartialChunk = ""
        _speechState.value = SpeechState.Initializing

        startInternalRecognizer()
    }

    private fun startInternalRecognizer() {
        if (!isListeningActive) return

        mainHandler.post {
            try {
                cleanupRecognizer()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (isListeningActive) {
                                val currentText = getFullSpokenText()
                                _speechState.value = SpeechState.Listening(rmsDb = 0f, partialText = currentText)
                            }
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            if (isListeningActive) {
                                val currentText = getFullSpokenText()
                                _speechState.value = SpeechState.Listening(
                                    rmsDb = rmsdB.coerceIn(0f, 10f),
                                    partialText = currentText
                                )
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            if (!isListeningActive) return

                            // Non-fatal pause / timeout errors -> restart continuously
                            if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_AUDIO
                            ) {
                                mainHandler.postDelayed({
                                    if (isListeningActive) {
                                        startInternalRecognizer()
                                    }
                                }, 150)
                                return
                            }

                            // Fatal errors
                            if (accumulatedTextBuilder.isEmpty() && latestPartialChunk.isEmpty()) {
                                val errorMsg = when (error) {
                                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                                    SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                                    else -> "Recognition error (code $error)"
                                }
                                isListeningActive = false
                                _speechState.value = SpeechState.Error(errorMsg)
                            } else {
                                // Keep listening if user already spoke text
                                mainHandler.postDelayed({
                                    if (isListeningActive) {
                                        startInternalRecognizer()
                                    }
                                }, 200)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim() ?: ""

                            if (text.isNotBlank()) {
                                if (accumulatedTextBuilder.isNotEmpty()) {
                                    accumulatedTextBuilder.append(" ")
                                }
                                accumulatedTextBuilder.append(text)
                            }
                            latestPartialChunk = ""

                            if (isListeningActive) {
                                val fullText = accumulatedTextBuilder.toString().trim()
                                _speechState.value = SpeechState.Listening(rmsDb = 0f, partialText = fullText)
                                // Keep listening continuously until user taps stop!
                                mainHandler.postDelayed({
                                    if (isListeningActive) {
                                        startInternalRecognizer()
                                    }
                                }, 100)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim() ?: ""
                            latestPartialChunk = partial

                            if (isListeningActive) {
                                val fullText = getFullSpokenText()
                                val current = _speechState.value
                                val rms = if (current is SpeechState.Listening) current.rmsDb else 0f
                                _speechState.value = SpeechState.Listening(rmsDb = rms, partialText = fullText)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, currentLanguage)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                if (accumulatedTextBuilder.isEmpty()) {
                    isListeningActive = false
                    _speechState.value = SpeechState.Error("Speech recognizer error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun getFullSpokenText(): String {
        val accumulated = accumulatedTextBuilder.toString().trim()
        val partial = latestPartialChunk.trim()
        return when {
            accumulated.isNotBlank() && partial.isNotBlank() -> "$accumulated $partial"
            accumulated.isNotBlank() -> accumulated
            partial.isNotBlank() -> partial
            else -> ""
        }
    }

    fun stopListening(): String {
        isListeningActive = false
        val finalText = getFullSpokenText()
        cleanupRecognizer()

        if (finalText.isNotBlank()) {
            _speechState.value = SpeechState.Success(finalText)
        } else {
            _speechState.value = SpeechState.Idle
        }
        return finalText
    }

    fun reset() {
        isListeningActive = false
        cleanupRecognizer()
        accumulatedTextBuilder.clear()
        latestPartialChunk = ""
        _speechState.value = SpeechState.Idle
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored
        } finally {
            speechRecognizer = null
        }
    }
}
