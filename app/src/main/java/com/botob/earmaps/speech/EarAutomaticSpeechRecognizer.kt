package com.botob.earmaps.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.here.android.mpa.nlp.Nlp
import com.here.android.mpa.nlp.SpeechToTextProvider
import java.util.*
import java.util.logging.Logger

class EarAutomaticSpeechRecognizer : SpeechToTextProvider, RecognitionListener {
    companion object {
        private val LOG: Logger = Logger.getLogger(EarAutomaticSpeechRecognizer::class.java.name)
    }

    private var mContext: Context
    private var mNlp: Nlp
    @Volatile private var mSpeechRecognizer: SpeechRecognizer? = null
    private var mListening = false

    constructor(context: Context, nlp: Nlp) {
        mContext = context
        mNlp = nlp
        create()
    }

    /**
     * Creates the speech recognizer as needed.
     */
    @Synchronized private fun create() {
        if (mSpeechRecognizer == null) {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext)
            mSpeechRecognizer?.setRecognitionListener(this)
        }
    }

    /**
     * Starts listening.
     */
    @Synchronized override fun start() {
        if (!mListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toString())
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)

            try {
                mSpeechRecognizer?.startListening(intent)
                mListening = true
            } catch (e: Exception) {
                LOG.severe(e.toString())
                destroy()
            }
        }
    }

    override fun isListening(): Boolean {
        return mListening
    }

    override fun stop(): Boolean {
        mSpeechRecognizer?.stopListening()
        mListening = false
        return true
    }

    override fun destroy() {
        if (stop()) {
            mSpeechRecognizer?.destroy()
            mListening = false
        }
    }

    override fun pause() {
        destroy()
    }

    override fun resume(context: Context) {
        if (!mContext.equals(context)) {
            destroy()
            mContext = context
            create()
        }
    }

    override fun cancel() {
        mSpeechRecognizer?.cancel()
    }

    override fun onReadyForSpeech(p0: Bundle?) {
        LOG.info("Ready for the user to start speaking.")
    }

    override fun onRmsChanged(rmsdB: Float) {

    }

    override fun onBufferReceived(p0: ByteArray?) {
        LOG.info("Buffer has been received.")
    }

    override fun onPartialResults(p0: Bundle?) {
        LOG.info("Partial results have been received.")
    }

    override fun onEvent(p0: Int, p1: Bundle?) {
        LOG.info("onEvent called.")
    }

    override fun onBeginningOfSpeech() {
        LOG.info("Speech has begun.")
    }

    override fun onEndOfSpeech() {
        LOG.info("Speech has ended.")
    }

    override fun onError(errorCode: Int) {
        val errorText: String
        when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> errorText = "ERROR_AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> errorText = "ERROR_CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> errorText = "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_NETWORK -> errorText = "ERROR_NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> errorText = "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> errorText = "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> errorText = "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_SERVER -> errorText = "ERROR_SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> errorText = "ERROR_SPEECH_TIMEOUT"
            else -> errorText = "UNKNOWN"
        }
        val message = "Recognition listener caught $errorText"
        LOG.warning(message)
        mListening = false
    }

    override fun onResults(results: Bundle?) {
        synchronized(this@EarAutomaticSpeechRecognizer) {
            val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            LOG.info("Speech results: $data")
            var understood = mNlp.understand(data?.get(0))
            LOG.info("Understood: $understood")
            mListening = false
        }
    }

}