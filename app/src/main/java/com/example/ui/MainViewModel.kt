package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AssistantDatabase
import com.example.data.database.CommandLog
import com.example.data.repository.AssistantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AssistantDatabase.getDatabase(application)
    private val repository = AssistantRepository(database.commandDao())

    // UI state flows
    val commandLogs: StateFlow<List<CommandLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _alwaysListening = MutableStateFlow(false)
    val alwaysListening: StateFlow<Boolean> = _alwaysListening.asStateFlow()

    private val _assistantState = MutableStateFlow("IDLE") // IDLE, LISTENING, PROCESSING, EXECUTING
    val assistantState: StateFlow<String> = _assistantState.asStateFlow()

    private val _feedbackMessage = MutableStateFlow("Say 'Hey assistant' or tap the microphone.")
    val feedbackMessage: StateFlow<String> = _feedbackMessage.asStateFlow()

    private val _overrideApiKey = MutableStateFlow("")
    val overrideApiKey: StateFlow<String> = _overrideApiKey.asStateFlow()

    // Key provided by user in prompt as a fallback to make it work instantly!
    private val promptApiKey = "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"

    // Action execution hooks
    private val _executionTrigger = MutableStateFlow<Pair<String, String>?>(null)
    val executionTrigger: StateFlow<Pair<String, String>?> = _executionTrigger.asStateFlow()

    fun updateInputText(newText: String) {
        _inputText.value = newText
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (listening) {
            _assistantState.value = "LISTENING"
            _feedbackMessage.value = "Listening active... Speak your command."
        } else {
            if (_assistantState.value == "LISTENING") {
                _assistantState.value = "IDLE"
                _feedbackMessage.value = "Tap microphone to scan or type a command."
            }
        }
    }

    fun toggleAlwaysListening() {
        _alwaysListening.value = !_alwaysListening.value
        if (_alwaysListening.value) {
            _feedbackMessage.value = "Continuous wake-word listening enabled. Say 'Hey assistant'!"
        } else {
            _feedbackMessage.value = "Continuous monitoring disabled."
        }
    }

    fun updateApiKey(key: String) {
        _overrideApiKey.value = key
    }

    fun clearExecutionTrigger() {
        _executionTrigger.value = null
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    /**
     * Entry point for voice speech transcription or typed command
     */
    fun onCommandReceived(text: String) {
        if (text.isBlank()) return
        
        val normalized = text.lowercase().trim()
        Log.d("MainViewModel", "Command received: '$text'")

        // Check if wake word is mentioned: "Hey assistant ..."
        val wakeKeywords = listOf("hey assistant", "hay assistant", "okay assistant", "assistant", "ok assistant", "hi assistant")
        var matchedKeyword: String? = null
        for (kw in wakeKeywords) {
            if (normalized.startsWith(kw)) {
                matchedKeyword = kw
                break
            }
        }

        if (matchedKeyword != null) {
            // Wake phrase triggered
            val commandBody = text.substring(matchedKeyword.length).trim()
            if (commandBody.isBlank()) {
                // User only said "Hey assistant" without payload -> trigger high-priority listening
                _feedbackMessage.value = "I'm here! What can I control for you?"
                _assistantState.value = "LISTENING"
                _isListening.value = true
                // Play audio cue request
                triggerWakeBeep()
            } else {
                // User said "Hey assistant [command]" -> execute immediately
                processActualCommand(commandBody)
            }
        } else {
            // Normal button press voice input or typed command
            processActualCommand(text)
        }
    }

    private fun triggerWakeBeep() {
        // Can be handled in UI. We update feedback state
        _inputText.value = ""
    }

    private fun processActualCommand(queryText: String) {
        viewModelScope.launch {
            _assistantState.value = "PROCESSING"
            _feedbackMessage.value = "Thinking (using NVIDIA MiniMax)..."
            
            // Collect key: check override input first, else fallback
            val userApiKey = _overrideApiKey.value.trim().ifBlank { promptApiKey }

            // Log pending state in database
            val rawLogId = repository.insertLog(
                CommandLog(
                    inputText = queryText,
                    actionDetected = "PROCESSING",
                    parameterExtracted = "",
                    responseReply = "Analyzing...",
                    status = "PENDING"
                )
            ).toInt()

            val result = repository.processCommand(queryText, userApiKey)

            // Update state and UI
            _assistantState.value = "EXECUTING"
            _feedbackMessage.value = result.reply

            // Delete temporary pending log and insert complete final log
            repository.deleteLog(rawLogId)
            repository.insertLog(
                CommandLog(
                    inputText = queryText,
                    actionDetected = result.action,
                    parameterExtracted = result.parameter,
                    responseReply = result.reply,
                    status = if (result.action != "UNKNOWN") "SUCCESS" else "FAILED"
                )
            )

            // Trigger Intent execution in Activity/View context
            if (result.action != "UNKNOWN") {
                _executionTrigger.value = Pair(result.action, result.parameter)
            }

            _assistantState.value = "IDLE"
        }
    }
}
