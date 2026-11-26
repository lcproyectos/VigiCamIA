package com.vigicamia.app

import android.content.Context

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("vigicam_prefs", Context.MODE_PRIVATE)

    var cameraIp: String
        get() = prefs.getString("camera_ip", "10.126.211.147") ?: "10.126.211.147"
        set(value) {
            val clean = value.trim()
            prefs.edit().putString("camera_ip", clean).apply()
            addRecentIp(clean)
        }

    var cameraUser: String
        get() = prefs.getString("camera_user", "") ?: ""
        set(value) = prefs.edit().putString("camera_user", value.trim()).apply()

    var cameraPass: String
        get() = prefs.getString("camera_pass", "") ?: ""
        set(value) = prefs.edit().putString("camera_pass", value.trim()).apply()

    var streamPort: String
        get() = prefs.getString("stream_port", "554") ?: "554"
        set(value) = prefs.edit().putString("stream_port", value.trim()).apply()

    var streamPath: String
        get() = prefs.getString("stream_path", "/live/ch00_1") ?: "/live/ch00_1"
        set(value) = prefs.edit().putString("stream_path", value.trim()).apply()

    fun getRecentIps(): List<String> {
        val raw = prefs.getString("recent_ips", "10.126.211.147") ?: "10.126.211.147"
        return raw.split("|").filter { it.isNotBlank() }
    }

    private fun addRecentIp(ip: String) {
        if (ip.isBlank()) return
        val current = getRecentIps().toMutableList()
        current.remove(ip)
        current.add(0, ip)
        val trimmed = current.take(4)
        prefs.edit().putString("recent_ips", trimmed.joinToString("|")).apply()
    }

    fun buildStreamUrl(input: String = cameraIp): String {
        var cleanInput = input.trim()
        cleanInput = cleanInput.replace(
            Regex("""^rtsp://(\d+\.\d+\.\d+\.\d+)\.(\d+)(/.*)$""", RegexOption.IGNORE_CASE),
            "rtsp://$1:$2$3"
        )
        if (cleanInput.startsWith("rtsp://", ignoreCase = true) ||
            cleanInput.startsWith("http://", ignoreCase = true)
        ) {
            return cleanInput
        }
        val auth = if (cameraUser.isNotEmpty() && cameraPass.isNotEmpty()) {
            "$cameraUser:$cameraPass@"
        } else ""
        val path = if (streamPath.startsWith("/")) streamPath else "/$streamPath"
        return "rtsp://$auth$cleanInput:$streamPort$path"
    }
}
