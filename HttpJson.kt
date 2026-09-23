package com.example.virtualcompanion.ai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HttpJson {
    fun post(url: String, headers: Map<String,String>, json: JSONObject): Pair<Int,String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k,v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        return code to body
    }
}
