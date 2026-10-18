package com.example.ai.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller manager that connects the Compose UI with CiciVoiceService
 * and CiciVoiceBridge for background voice processing.
 */
class VoiceAssistantManager(
    private val context: Context,
    private val onCommandReceived: (String) -> Unit
) {

    companion object {
        private const val TAG = "VoiceAssistantMgr"
    }

    val isListening: StateFlow<Boolean> = CiciVoiceBridge.isListening
    val isSpeaking: StateFlow<Boolean> = CiciVoiceBridge.isSpeaking
    val isStandbyActive: StateFlow<Boolean> = CiciVoiceBridge.isStandbyActive
    val isServiceRunning: StateFlow<Boolean> = CiciVoiceBridge.isServiceRunning

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var eventJob: Job? = null

    init {
        eventJob = scope.launch {
            CiciVoiceBridge.commandEvents.collect { command ->
                _lastRecognizedText.value = command
                onCommandReceived(command)
            }
        }
    }

    /**
     * Starts continuous hands-free shopping standby mode.
     * Starts CiciVoiceService as a Foreground Service with microphone type.
     * Continues listening in background even if app is minimized!
     */
    fun startStandby() {
        Log.d(TAG, "Starting background standby voice service...")
        try {
            CiciVoiceService.startStandby(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start background standby service: ${e.message}", e)
            try {
                startSingleListen()
            } catch (t: Throwable) {
                Log.e(TAG, "In-app fallback listen error: ${t.message}")
            }
        }
    }

    /**
     * Stops continuous standby mode, stops Foreground Service, and immediately turns off mic.
     */
    fun stopStandby() {
        Log.d(TAG, "Stopping background voice service...")
        try {
            CiciVoiceService.stopService(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to stop standby service: ${e.message}", e)
        }
    }

    fun toggleStandby() {
        if (isStandbyActive.value) {
            stopStandby()
        } else {
            startStandby()
        }
    }

    /**
     * One-shot listen for single sentence input.
     */
    fun startSingleListen() {
        try {
            CiciVoiceService.startSingleListen(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start single listen: ${e.message}", e)
        }
    }

    /**
     * Speaks text using Text-to-Speech through the service.
     */
    fun speak(text: String) {
        try {
            CiciVoiceService.speak(context.applicationContext, text)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to trigger speak: ${e.message}", e)
        }
    }

    fun stopSpeaking() {
        try {
            CiciVoiceService.stopService(context.applicationContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to stop speaking: ${e.message}", e)
        }
    }

    fun destroy() {
        eventJob?.cancel()
        try {
            if (!isStandbyActive.value) {
                CiciVoiceService.stopService(context.applicationContext)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in destroy: ${e.message}")
        }
    }
}
