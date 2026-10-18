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
        CiciVoiceService.startStandby(context.applicationContext)
    }

    /**
     * Stops continuous standby mode, stops Foreground Service, and immediately turns off mic.
     */
    fun stopStandby() {
        Log.d(TAG, "Stopping background voice service...")
        CiciVoiceService.stopService(context.applicationContext)
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
        CiciVoiceService.startSingleListen(context.applicationContext)
    }

    /**
     * Speaks text using Text-to-Speech through the service.
     */
    fun speak(text: String) {
        CiciVoiceService.speak(context.applicationContext, text)
    }

    fun stopSpeaking() {
        // Turning off service stops speech immediately
        CiciVoiceService.stopService(context.applicationContext)
    }

    fun destroy() {
        eventJob?.cancel()
        // If standby is not explicitly active, stop service
        if (!isStandbyActive.value) {
            CiciVoiceService.stopService(context.applicationContext)
        }
    }
}
