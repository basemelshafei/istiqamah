package com.basem.qiyam

import java.net.HttpURLConnection
import java.net.URL

/** Tiny blocking HTTP helper for the read-only APIs (GitHub, Trading 212, eToro). Call off the main thread. */
object Net {
    /** Off on the simulated phone used by tests and screenshots, so builds never hit real servers. */
    private val offline = android.os.Build.FINGERPRINT == "robolectric"

    fun get(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> = call("GET", url, headers, null)

    fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> = call("POST", url, headers, body)

    private fun call(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        if (offline) return 0 to ""
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Istiqamah-Android")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }
}
