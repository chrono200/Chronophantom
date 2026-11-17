package com.chrono.phantom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenRouterClient {
    suspend fun analyse(apiKey: String, question: String, snapshot: DeviceSnapshot): String = withContext(Dispatchers.IO) {
        val system = """
            You are PHANTOM, an Android device diagnostics analyst. Produce a concise, useful intelligence briefing, not a conversation.
            Explain what measurements suggest, distinguish evidence from hypotheses, and state what data is missing.
            For data-drain questions, compare current and previous 24-hour mobile totals. Do not claim an app caused data use based on foreground time; foreground time is not app data usage.
            Give: 1) likely explanation, 2) evidence, 3) next checks in Android Settings, 4) confidence (low/medium/high).
            Never invent measurements or claim to inspect packet contents.
            User's issue: $question
            ${snapshot.toPromptContext()}
        """.trimIndent()
        val payload = JSONObject().put("model", "openrouter/free")
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", "Generate the diagnostic summary now.")))
            .put("temperature", 0.2).put("max_tokens", 900)
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 45000; doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://github.com/chrono200/Chronophantom")
            setRequestProperty("X-Title", "ChronoPhantom")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(body).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException("OpenRouter error $code: ${message ?: body.take(240)}")
            }
            JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("The model returned an empty summary.")
        } finally { connection.disconnect() }
    }
}
