package com.diafon.companion

import android.content.Context

data class AppConfig(
    val host: String,
    val port: Int,
    val https: Boolean,
    val webhookId: String,
    val targetPackage: String,
    val returnHome: Boolean,
    val autoStart: Boolean
) {
    val webhookUrl: String
        get() {
            val scheme = if (https) "https" else "http"
            return "$scheme://$host:$port/api/webhook/$webhookId"
        }
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("diafon_config", Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        host = prefs.getString("host", "192.168.1.10").orEmpty(),
        port = prefs.getInt("port", 8123),
        https = prefs.getBoolean("https", false),
        webhookId = prefs.getString("webhook_id", "reolink_kapi_ac").orEmpty(),
        targetPackage = prefs.getString("target_package", "com.mcu.reolink").orEmpty(),
        returnHome = prefs.getBoolean("return_home", true),
        autoStart = prefs.getBoolean("auto_start", true)
    )

    fun save(config: AppConfig) {
        prefs.edit()
            .putString("host", config.host.trim())
            .putInt("port", config.port)
            .putBoolean("https", config.https)
            .putString("webhook_id", config.webhookId.trim())
            .putString("target_package", config.targetPackage.trim())
            .putBoolean("return_home", config.returnHome)
            .putBoolean("auto_start", config.autoStart)
            .apply()
    }
}
