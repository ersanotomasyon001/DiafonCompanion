package com.diafon.companion

import android.content.Context

data class AppConfig(
    val baseUrl: String,
    val webhookId: String,
    val targetPackage: String,
    val autoStart: Boolean
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val webhookUrl: String
        get() = "$normalizedBaseUrl/api/webhook/${webhookId.trim()}"
}

class ConfigStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("diafon_config", Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        baseUrl = prefs.getString(
            "base_url",
            "https://ha.youtubetv.com.tr"
        ).orEmpty(),
        webhookId = prefs.getString(
            "webhook_id",
            "reolink_kapi_ac"
        ).orEmpty(),
        targetPackage = prefs.getString(
            "target_package",
            "com.mcu.reolink"
        ).orEmpty(),
        autoStart = prefs.getBoolean("auto_start", true)
    )

    fun save(config: AppConfig) {
        prefs.edit()
            .putString("base_url", config.normalizedBaseUrl)
            .putString("webhook_id", config.webhookId.trim())
            .putString("target_package", config.targetPackage.trim())
            .putBoolean("auto_start", config.autoStart)
            .apply()
    }

    fun loadOverlayX(): Int = prefs.getInt("overlay_x", 18)
    fun loadOverlayY(): Int = prefs.getInt("overlay_y", 0)

    fun saveOverlayPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt("overlay_x", x)
            .putInt("overlay_y", y)
            .apply()
    }
}
