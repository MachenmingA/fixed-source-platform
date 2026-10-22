package com.fixedsource.mobile.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

class ApiException(val statusCode: Int, override val message: String) : IOException(message)
class SessionExpiredException(message: String = "登录状态已失效，请重新登录") : IOException(message)

class ApiClient(
    private val baseUrlProvider: () -> String,
    private val tokenProvider: () -> String?
) {
    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun health(baseUrlRaw: String): HealthResult = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrlRaw)
            ?: throw ApiException(400, "服务地址格式不正确")
        val request = Request.Builder().url("$base/api/health").get().build()
        gson.fromJson(execute(request), HealthResult::class.java)
    }

    suspend fun login(baseUrlRaw: String, username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val base = normalizeBaseUrl(baseUrlRaw)
                ?: throw ApiException(400, "服务地址格式不正确")
            val payload = mapOf("username" to username, "password" to password)
            val request = Request.Builder()
                .url("$base/api/mobile/auth/login")
                .post(gson.toJson(payload).toRequestBody(jsonMedia))
                .build()
            gson.fromJson(execute(request), LoginResult::class.java)
        }

    suspend fun currentUser(): UserInfo = withContext(Dispatchers.IO) {
        gson.fromJson(execute(authed("/api/auth/me").get().build()), UserInfo::class.java)
    }

    suspend fun tasks(): List<TaskSummary> = withContext(Dispatchers.IO) {
        val type = object : TypeToken<List<TaskSummary>>() {}.type
        gson.fromJson<List<TaskSummary>>(
            execute(authed("/api/mobile/tasks").get().build()),
            type
        )
    }

    suspend fun task(taskNo: String): TaskDetail = withContext(Dispatchers.IO) {
        gson.fromJson(
            execute(authed("/api/mobile/tasks/$taskNo").get().build()),
            TaskDetail::class.java
        )
    }

    suspend fun accept(taskNo: String): TaskDetail = withContext(Dispatchers.IO) {
        gson.fromJson(
            execute(authed("/api/mobile/tasks/$taskNo/accept").post(EMPTY_BODY).build()),
            TaskDetail::class.java
        )
    }

    suspend fun feedback(taskNo: String, body: FeedbackRequest): TaskDetail =
        withContext(Dispatchers.IO) {
            val request = authed("/api/mobile/tasks/$taskNo/feedback")
                .post(gson.toJson(body).toRequestBody(jsonMedia))
                .build()
            gson.fromJson(execute(request), TaskDetail::class.java)
        }

    suspend fun uploadEvidence(file: File): UploadResult = withContext(Dispatchers.IO) {
        val media = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(media))
            .build()
        val request = authed("/api/files").post(body).build()
        gson.fromJson(execute(request), UploadResult::class.java)
    }

    private fun authed(path: String): Request.Builder {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw SessionExpiredException()
        return Request.Builder()
            .url("${baseUrlProvider().trimEnd('/')}$path")
            .header("X-Token", token)
            .header("Accept", "application/json")
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = errorMessage(body).ifBlank { "请求失败（HTTP ${response.code}）" }
                if (response.code == 401) throw SessionExpiredException(message)
                if (response.code == 403) throw ApiException(403, message.ifBlank { "当前角色无权执行此操作" })
                throw ApiException(response.code, message)
            }
            return body
        }
    }

    private fun errorMessage(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            JsonParser.parseString(body).asJsonObject.get("message")?.asString.orEmpty()
        }.getOrDefault("")
    }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        fun normalizeBaseUrl(raw: String): String? {
            var value = raw.trim()
            if (value.isBlank()) return null
            if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
                value = "http://$value"
            }
            value = value.trimEnd('/')
            if (value.endsWith("/api", true)) {
                value = value.dropLast(4).trimEnd('/')
            }
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.host.isNullOrBlank()) return null
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
            return value
        }
    }
}

object Api {
    lateinit var client: ApiClient

    fun init() {
        client = ApiClient(
            baseUrlProvider = { SessionStore.baseUrl },
            tokenProvider = { SessionStore.token }
        )
    }
}
