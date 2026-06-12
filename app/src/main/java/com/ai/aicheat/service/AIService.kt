package com.ai.aicheat.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.ai.aicheat.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AIService {
    private const val TAG = "AIService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun analyzeScreenshot(
        bitmap: Bitmap,
        prompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = ConfigManager.apiUrl
            val apiKey = ConfigManager.apiKey
            val model = ConfigManager.model
            val finalPrompt = prompt ?: ConfigManager.prompt

            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先配置API Key"))
            }

            val base64Image = try {
                bitmapToBase64(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "bitmapToBase64 failed", e)
                return@withContext Result.failure(Exception("图片转换失败"))
            }

            val messageContent = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", finalPrompt)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                        put("detail", "high")
                    })
                })
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", messageContent)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("max_tokens", 1000)
            }

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Sending request to AI API...")

            val response = try {
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Network request failed", e)
                return@withContext Result.failure(Exception("网络请求失败: ${e.message}"))
            }

            val responseBody = try {
                response.body?.string()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read response body", e)
                return@withContext Result.failure(Exception("读取响应失败"))
            }

            if (response.isSuccessful && responseBody != null) {
                try {
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        val content = message?.optString("content", "") ?: ""
                        if (content.isNotBlank()) {
                            Log.d(TAG, "AI Response received: ${content.take(100)}...")
                            Result.success(content)
                        } else {
                            Result.failure(Exception("AI返回内容为空"))
                        }
                    } else {
                        Result.failure(Exception("AI返回格式异常"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse failed", e)
                    Result.failure(Exception("解析响应失败"))
                }
            } else {
                val error = "API Error: ${response.code}"
                Log.e(TAG, "$error - $responseBody")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI request failed", e)
            Result.failure(Exception("请求失败: ${e.message}"))
        }
    }

    data class AIConfig(
        val apiUrl: String,
        val apiKey: String,
        val model: String
    )

    private var currentConfig: AIConfig? = null

    fun updateConfig(config: AIConfig) {
        currentConfig = config
    }
}
