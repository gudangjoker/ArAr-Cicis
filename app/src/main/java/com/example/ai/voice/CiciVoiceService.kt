package com.example.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Singleton communication bridge between CiciVoiceService and Compose UI / ViewModel.
 */
object CiciVoiceBridge {
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isStandbyActive = MutableStateFlow(false)
    val isStandbyActive: StateFlow<Boolean> = _isStandbyActive.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _commandEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val commandEvents: SharedFlow<String> = _commandEvents.asSharedFlow()

    fun updateListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun updateSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun updateStandby(standby: Boolean) {
        _isStandbyActive.value = standby
    }

    fun updateServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun emitCommand(command: String) {
        _commandEvents.tryEmit(command)
    }

    fun reset() {
        _isListening.value = false
        _isSpeaking.value = false
        _isStandbyActive.value = false
        _isServiceRunning.value = false
    }
}

/**
 * Foreground Service for background hands-free voice interaction with Si Cici.
 * Features:
 * - Runs with foregroundServiceType="microphone" so speech recognition remains active
 *   even when the user minimizes the app, locks the screen, or uses another app.
 * - Displays a sticky foreground notification with a quick "Matikan Mic" action.
 * - CRITICAL PRIVACY & SECURITY: Automatically stops listening, releases the microphone,
 *   shuts down TTS, and kills the service whenever the user closes the app (onTaskRemoved / onDestroy).
 */
class CiciVoiceService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CiciVoiceService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "cici_voice_foreground_channel"
        private const val UTTERANCE_ID = "CICI_SERVICE_SPEECH"

        const val ACTION_START_STANDBY = "com.example.ai.voice.ACTION_START_STANDBY"
        const val ACTION_STOP_SERVICE = "com.example.ai.voice.ACTION_STOP_SERVICE"
        const val ACTION_START_SINGLE_LISTEN = "com.example.ai.voice.ACTION_START_SINGLE_LISTEN"
        const val ACTION_SPEAK = "com.example.ai.voice.ACTION_SPEAK"
        const val EXTRA_SPEAK_TEXT = "extra_speak_text"

        fun startStandby(context: Context) {
            val intent = Intent(context, CiciVoiceService::class.java).apply {
                action = ACTION_START_STANDBY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CiciVoiceService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun speak(context: Context, text: String) {
            val intent = Intent(context, CiciVoiceService::class.java).apply {
                action = ACTION_SPEAK
                putExtra(EXTRA_SPEAK_TEXT, text)
            }
            context.startService(intent)
        }

        fun startSingleListen(context: Context) {
            val intent = Intent(context, CiciVoiceService::class.java).apply {
                action = ACTION_START_SINGLE_LISTEN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CiciVoiceService created")
        createNotificationChannel()
        // Immediately start foreground to satisfy Android OS 5-second contract!
        startForegroundWithNotification("Si Cici Siap Mendengarkan... 🎙️", "Katakan belanjaan atau catatan kas Bunda")
        initTextToSpeech()
        initSpeechRecognizer()
        CiciVoiceBridge.updateServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        try {
            when (action) {
                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Received ACTION_STOP_SERVICE. Cleaning up and stopping self.")
                    stopVoiceAndCleanup()
                    stopSelf()
                    return START_NOT_STICKY
                }

                ACTION_START_STANDBY -> {
                    updateNotification("Si Cici Siap Mendengarkan... 🎙️", "Katakan belanjaan atau catatan kas Bunda")
                    CiciVoiceBridge.updateStandby(true)
                    speakInternal("Halo Bun! Cici siap bantu sambil Bunda belanja.")
                }

                ACTION_START_SINGLE_LISTEN -> {
                    updateNotification("Si Cici Mendengarkan...", "Sebutkan perintah atau belanjaan Anda")
                    startListeningInternal()
                }

                ACTION_SPEAK -> {
                    val textToSpeak = intent.getStringExtra(EXTRA_SPEAK_TEXT)
                    if (!textToSpeak.isNullOrBlank()) {
                        speakInternal(textToSpeak)
                    }
                }

                else -> {
                    updateNotification("Si Cici Siap Mendengarkan... 🎙️", "Katakan belanjaan atau catatan kas Bunda")
                    CiciVoiceBridge.updateStandby(true)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}", e)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asisten Suara Si Cici (Latar Belakang)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status saat asisten suara Si Cici mendengarkan perintah belanja Bunda di latar belakang"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(title: String, content: String) {
        try {
            val notification = buildForegroundNotification(title, content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            try {
                val fallbackNotification = buildForegroundNotification(title, content)
                startForeground(NOTIFICATION_ID, fallbackNotification)
            } catch (t: Throwable) {
                Log.e(TAG, "Fallback startForeground also failed: ${t.message}", t)
            }
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action to explicitly turn off microphone directly from notification tray
        val stopIntent = Intent(this, CiciVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIconRes = try {
            R.drawable.ic_stat_cici_mic
        } catch (t: Throwable) {
            android.R.drawable.stat_notify_chat
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan Mic", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildForegroundNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    // ==========================================
    // SPEECH RECOGNITION & TTS IMPLEMENTATION
    // ==========================================

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val localeId = Locale("id", "ID")
            val result = textToSpeech?.setLanguage(localeId)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.language = Locale.getDefault()
            }
            textToSpeech?.setPitch(1.05f)
            textToSpeech?.setSpeechRate(1.0f)

            // Setup echo-cancellation: mute mic while TTS is speaking to prevent hearing itself
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    CiciVoiceBridge.updateSpeaking(true)
                    stopListeningInternal()
                }

                override fun onDone(utteranceId: String?) {
                    CiciVoiceBridge.updateSpeaking(false)
                    // Resume listening if standby mode is active
                    if (CiciVoiceBridge.isStandbyActive.value) {
                        startListeningInternal()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    CiciVoiceBridge.updateSpeaking(false)
                    if (CiciVoiceBridge.isStandbyActive.value) {
                        startListeningInternal()
                    }
                }
            })
            isTtsReady = true
        }
    }

    private fun initSpeechRecognizer() {
        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    if (SpeechRecognizer.isRecognitionAvailable(this)) {
                        speechRecognizer?.destroy()
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                            setRecognitionListener(this@CiciVoiceService)
                        }
                        Log.d(TAG, "SpeechRecognizer initialized successfully")
                    } else {
                        Log.w(TAG, "SpeechRecognizer not available on this device")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to create SpeechRecognizer on main thread: ${e.message}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Speech recognition check error: ${t.message}")
        }
    }

    private fun startListeningInternal() {
        if (CiciVoiceBridge.isSpeaking.value) return // Don't record while speaking

        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    if (speechRecognizer == null) {
                        if (SpeechRecognizer.isRecognitionAvailable(this)) {
                            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                                setRecognitionListener(this@CiciVoiceService)
                            }
                        }
                    }

                    val recognizer = speechRecognizer
                    if (recognizer == null) {
                        Log.w(TAG, "SpeechRecognizer is null, cannot start listening")
                        CiciVoiceBridge.updateListening(false)
                        return@post
                    }

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "id-ID")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }

                    recognizer.startListening(intent)
                    CiciVoiceBridge.updateListening(true)
                    updateNotification("Si Cici Mendengarkan... 🎙️", "Bicara sekarang, Cici sedang mendengarkan...")
                } catch (e: Throwable) {
                    Log.e(TAG, "Error starting speech recognition: ${e.message}")
                    CiciVoiceBridge.updateListening(false)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error dispatching startListening: ${e.message}")
            CiciVoiceBridge.updateListening(false)
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition: ${e.message}")
        }
        CiciVoiceBridge.updateListening(false)
    }

    private fun speakInternal(text: String) {
        if (!isTtsReady) return
        val cleanText = text.replace(Regex("[*#_`~]"), "") // Strip markdown symbols
        try {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
            }
            updateNotification("Si Cici Berbicara... 🔊", cleanText.take(45))
            textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text: ${e.message}")
        }
    }

    // ==========================================
    // CRITICAL PRIVACY & TERMINATION LIFECYCLE
    // ==========================================

    /**
     * Called by Android OS when user swipes the app away from Recent Apps list (force close by user).
     * We MUST immediately kill the microphone and service to prevent mic lingering.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: Aplikasi ditutup oleh pengguna. Mematikan mikrofon dan menghentikan layanan!")
        stopVoiceAndCleanup()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy: Layanan dimatikan. Melepaskan seluruh resource mikrofon dan audio.")
        stopVoiceAndCleanup()
    }

    private fun stopVoiceAndCleanup() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        }

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS: ${e.message}")
        }

        CiciVoiceBridge.reset()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing foreground notification: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        CiciVoiceBridge.updateListening(true)
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        CiciVoiceBridge.updateListening(false)
    }

    override fun onError(error: Int) {
        CiciVoiceBridge.updateListening(false)
        Log.d(TAG, "SpeechRecognizer error code: $error")

        // Auto-restart if in standby mode and error was silence timeout or no match
        if (CiciVoiceBridge.isStandbyActive.value) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
            ) {
                serviceScope.launch {
                    kotlinx.coroutines.delay(400)
                    if (CiciVoiceBridge.isStandbyActive.value && !CiciVoiceBridge.isSpeaking.value) {
                        startListeningInternal()
                    }
                }
            }
        }
    }

    override fun onResults(results: Bundle?) {
        CiciVoiceBridge.updateListening(false)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val spokenText = matches?.firstOrNull() ?: ""

        if (spokenText.isNotBlank()) {
            Log.d(TAG, "Recognized spoken text: $spokenText")
            CiciVoiceBridge.emitCommand(spokenText)
        }

        // In standby mode, will resume after response TTS completes (via UtteranceProgressListener)
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
