package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.CommandParsingResult
import com.example.data.api.NvidiaChatRequest
import com.example.data.api.NvidiaClient
import com.example.data.api.NvidiaMessage
import com.example.data.database.CommandDao
import com.example.data.database.CommandLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class AssistantRepository(private val commandDao: CommandDao) {
    
    // OkHttp for audio uploads
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeAudioFile(audioFile: File, userProvidedKey: String? = null): String = withContext(Dispatchers.IO) {
        val configKey = BuildConfig.NVIDIA_API_KEY
        val rawKey = if (configKey.isNotBlank() && configKey != "MY_NVIDIA_API_KEY") {
            configKey
        } else {
            userProvidedKey ?: ""
        }

        if (rawKey.isBlank()) return@withContext "Error: Missing API Key"

        // Default to Groq since it specifically supports openai/whisper-large-v3, but use NIM if preferred. 
        // We will hit NVIDIA NIM endpoint for OpenAI compatibility:
        val url = "https://integrate.api.nvidia.com/v1/audio/transcriptions"
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", "openai/whisper-large-v3") // NVIDIA hosted whisper model
            .addFormDataPart("language", "en")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $rawKey")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "Unknown error"
                    Log.e("AssistantRepo", "ASR Error: ${response.code} $err")
                    return@withContext "Error: Could not transcribe audio. Response code ${response.code}"
                }
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                return@withContext json.optString("text", "Error: No text found")
            }
        } catch (e: Exception) {
            Log.e("AssistantRepo", "ASR Exception", e)
            return@withContext "Error: Failed to reach transcription service."
        }
    }

    val allLogs: Flow<List<CommandLog>> = commandDao.getAllLogs()

    suspend fun insertLog(log: CommandLog): Long = withContext(Dispatchers.IO) {
        commandDao.insertLog(log)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        commandDao.clearHistory()
    }

    suspend fun deleteLog(id: Int) = withContext(Dispatchers.IO) {
        commandDao.deleteLogById(id)
    }

    suspend fun processCommand(inputText: String, userProvidedKey: String? = null): CommandParsingResult = withContext(Dispatchers.IO) {
        // Read key from BuildConfig or fall back to the user's provided key
        val configKey = BuildConfig.NVIDIA_API_KEY
        val rawKey = if (configKey.isNotBlank() && configKey != "MY_NVIDIA_API_KEY") {
            configKey
        } else {
            userProvidedKey ?: ""
        }

        if (rawKey.isBlank()) {
            return@withContext CommandParsingResult(
                action = "UNKNOWN",
                parameter = "",
                reply = "API Key is missing. Please set your NVIDIA_API_KEY securely in the Secrets panel in AI Studio."
            )
        }

        val authHeader = "Bearer $rawKey"
        val systemPrompt = """
            You are a precise, smart AI application companion that translates spoken or typed user commands into structured device control instructions.
            You must analyze the user's input text very carefully, detect the target controller action, and parse any parameters (like song queries, search terms, etc.).

            Your output must be a single, valid, raw JSON object ONLY, with no extra text, explanations, or markdown formatting blocks.
            Do NOT enclose your reply in markdown syntax like ```json. Just raw text.

            JSON Schema:
            {
              "action": "PLAY_YOUTUBE" | "OPEN_YOUTUBE" | "OPEN_GALLERY" | "OPEN_CAMERA" | "WEB_SEARCH" | "SET_ALARM" | "SET_TIMER" | "NAVIGATE_TO" | "DIAL_PHONE" | "SEND_EMAIL" | "OPEN_APP" | "UNKNOWN",
              "parameter": "string (the query or extra context, or empty string)",
              "reply": "string (a friendly, natural visual confirmation of the action)"
            }

            Action Rules:
            1. If the user wants to play a song/video or search for media on YouTube (e.g. "Open YouTube and play some music", "Play despacito"), set action to "PLAY_YOUTUBE", parameter to the name of the song/video/artist, and reply to a nice message like "Playing [song] on YouTube! Enjoy."
            2. If the user just wants to open/display YouTube itself (e.g. "Open YouTube", "Go to youtube"), set action to "OPEN_YOUTUBE", parameter to "", and reply to "Launching YouTube...".
            3. If the user wants to open the media gallery (e.g. "Send open gallery", "Open the gallery", "Show my pictures"), set action to "OPEN_GALLERY", parameter to "", and reply to "Opening your photo gallery now!".
            4. If the user wants to take a picture or open the camera (e.g. "Open camera", "Take a picture", "Record a video"), set action to "OPEN_CAMERA", parameter to "", and reply to "Opening system camera now!".
            5. If the user wants to search the web (e.g. "Search who is the president", "Find recipes for pizza", "Google the weather"), set action to "WEB_SEARCH", parameter to the search query, and reply to "Searching the web for '[query]'...".
            6. If the user wants to set an alarm (e.g. "Set an alarm for 7 AM", "Wake me up tomorrow"), set action to "SET_ALARM", parameter to "7:00", and reply to "Opening clock to set your alarm...".
            7. If the user wants to set a timer (e.g. "Set a timer for 5 minutes"), set action to "SET_TIMER", parameter to the number of SECONDS (e.g. "300" for 5 mins), and reply to "Setting a timer...".
            8. If the user wants navigation or directions (e.g. "Take me to Starbucks", "Navigate home"), set action to "NAVIGATE_TO", parameter to the destination (e.g. "Starbucks"), and reply to "Starting navigation...".
            9. If the user wants to call someone (e.g. "Call Mom", "Dial 123456"), set action to "DIAL_PHONE", parameter to the phone number or contact name, and reply to "Opening dialer...".
            10. If the user wants to send an email (e.g. "Send an email", "Email John"), set action to "SEND_EMAIL", parameter to the subject or contact, and reply to "Opening email app...".
            11. If the user asks to open or launch an application by name (e.g. "Open Spotify", "Launch Netflix", "Start Calculator"), set action to "OPEN_APP", parameter to the exact app name (e.g. "Spotify", "Netflix"), and reply to "Opening [app]...".
            12. In all other cases where you cannot confidently map to a specific action, set action to "UNKNOWN", parameter to "", and reply to a conversational response.
        """.trimIndent()

        val messages = listOf(
            NvidiaMessage(role = "system", content = systemPrompt),
            NvidiaMessage(role = "user", content = inputText)
        )

        val request = NvidiaChatRequest(
            model = "meta/llama-3.1-70b-instruct",
            messages = messages,
            temperature = 0.1
        )

        try {
            val response = NvidiaClient.service.getChatCompletion(authHeader, request)
            if (response.isSuccessful) {
                val body = response.body()
                val choice = body?.choices?.firstOrNull()
                val rawContent = choice?.message?.content ?: ""

                if (rawContent.isNotBlank()) {
                    val sanitized = sanitizeJson(rawContent)
                    Log.d("NvidiaRepository", "Sanitized LLM Response: $sanitized")
                    
                    val adapter = NvidiaClient.moshiParser.adapter(CommandParsingResult::class.java)
                    val parsedResult = adapter.fromJson(sanitized)
                    
                    if (parsedResult != null) {
                        return@withContext parsedResult
                    }
                }
            } else {
                Log.e("NvidiaRepository", "Response unsuccessful: ${response.code()} / ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("NvidiaRepository", "Error calling NVIDIA API", e)
        }

        // Return a fallback/local parsing result if the API call fails or times out
        return@withContext performLocalRuleFallback(inputText)
    }

    private fun sanitizeJson(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```")
        }
        return cleaned.trim()
    }

    private fun performLocalRuleFallback(text: String): CommandParsingResult {
        val normalized = text.lowercase().trim()
        return when {
            normalized.contains("youtube") && (normalized.contains("play") || normalized.contains("music") || normalized.contains("song")) -> {
                // Extract possible query
                val query = text.replace(Regex("(?i)open youtube and play|play|on youtube|open youtube"), "").trim()
                CommandParsingResult(
                    action = "PLAY_YOUTUBE",
                    parameter = query.ifBlank { "some music" },
                    reply = "Offline local mode: Opening YouTube to search for '$query'..."
                )
            }
            normalized.contains("youtube") -> {
                CommandParsingResult(
                    action = "OPEN_YOUTUBE",
                    parameter = "",
                    reply = "Offline local mode: Opening YouTube..."
                )
            }
            normalized.contains("gallery") || normalized.contains("photos") || normalized.contains("pictures") -> {
                CommandParsingResult(
                    action = "OPEN_GALLERY",
                    parameter = "",
                    reply = "Offline local mode: Opening Gallery..."
                )
            }
            normalized.contains("camera") || normalized.contains("photo") || normalized.contains("picture") -> {
                CommandParsingResult(
                    action = "OPEN_CAMERA",
                    parameter = "",
                    reply = "Offline local mode: Opening Camera..."
                )
            }
            normalized.contains("search") || normalized.contains("find") || normalized.contains("google") -> {
                val query = text.replace(Regex("(?i)search for|google|find"), "").trim()
                CommandParsingResult(
                    action = "WEB_SEARCH",
                    parameter = query,
                    reply = "Offline local mode: Searching Google for '$query'..."
                )
            }
            normalized.contains("timer") -> {
                CommandParsingResult(
                    action = "SET_TIMER",
                    parameter = "300", // Default to 5 minutes offline
                    reply = "Offline local mode: Opening timer for 5 minutes..."
                )
            }
            normalized.contains("alarm") || normalized.contains("wake") -> {
                CommandParsingResult(
                    action = "SET_ALARM",
                    parameter = "",
                    reply = "Offline local mode: Opening alarms..."
                )
            }
            normalized.contains("navigate") || normalized.contains("directions") -> {
                val destination = text.replace(Regex("(?i)navigate to|directions to|take me to"), "").trim()
                CommandParsingResult(
                    action = "NAVIGATE_TO",
                    parameter = destination,
                    reply = "Offline local mode: Navigating to '$destination'..."
                )
            }
            normalized.contains("call") || normalized.contains("dial") -> {
                CommandParsingResult(
                    action = "DIAL_PHONE",
                    parameter = "",
                    reply = "Offline local mode: Opening phone dialer..."
                )
            }
            normalized.contains("email") -> {
                CommandParsingResult(
                    action = "SEND_EMAIL",
                    parameter = "",
                    reply = "Offline local mode: Opening email client..."
                )
            }
            normalized.contains("open") || normalized.contains("launch") || normalized.contains("start") -> {
                val app = normalized.replace("open", "").replace("launch", "").replace("start", "").trim()
                CommandParsingResult(
                    action = "OPEN_APP",
                    parameter = app,
                    reply = "Offline local mode: Opening $app..."
                )
            }
            else -> {
                CommandParsingResult(
                    action = "UNKNOWN",
                    parameter = "",
                    reply = "Local fallback: I received your request ('$text'), but wasn't sure how to map it without the cloud model."
                )
            }
        }
    }
}
