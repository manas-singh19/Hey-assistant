package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class NvidiaMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class NvidiaChatRequest(
    val model: String,
    val messages: List<NvidiaMessage>,
    val temperature: Double = 0.1
)

@JsonClass(generateAdapter = true)
data class NvidiaChoice(
    val index: Int,
    val message: NvidiaMessage,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class NvidiaChatResponse(
    val id: String?,
    val model: String?,
    val choices: List<NvidiaChoice>?
)

@JsonClass(generateAdapter = true)
data class CommandParsingResult(
    val action: String, // "PLAY_YOUTUBE", "OPEN_YOUTUBE", "OPEN_GALLERY", "OPEN_CAMERA", "WEB_SEARCH", "UNKNOWN"
    val parameter: String,
    val reply: String
)

interface NvidiaApiService {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorizationHeader: String,
        @Body request: NvidiaChatRequest
    ): Response<NvidiaChatResponse>
}
