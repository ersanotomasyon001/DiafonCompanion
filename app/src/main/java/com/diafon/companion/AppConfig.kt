package com.diafon.companion

import android.content.Context

data class AppConfig(
    val baseUrl: String,
    val doorWebhookId: String,
    val bellWebhookId: String,
    val targetPackage: String,
    val targetTitle: String,
    val autoStart: Boolean
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val doorWebhookUrl: String
        get() = "$normalizedBaseUrl/api/webhook/${doorWebhookId.trim()}"

    val bellWebhookUrl: String
        get() = "$normalizedBaseUrl/api/webhook/${bellWebhookId.trim()}"
}

class ConfigStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("diafon_config", Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        baseUrl = prefs.getString(
            "base_url",
            "https://ha.youtubetv.com.tr"
        ).orEmpty(),
        doorWebhookId = prefs.getString(
            "door_webhook_id",
            prefs.getString("webhook_id", "reolink_kapi_ac")
        ).orEmpty(),
        bellWebhookId = prefs.getString(
            "bell_webhook_id",
            "reolink_zil_bildirimi"
        ).orEmpty(),
        targetPackage = prefs.getString(
            "target_package",
            "com.mcu.reolink"
        ).orEmpty(),
        targetTitle = prefs.getString(
            "target_title",
            "KAPI ZİLİ ALGILANDI"
        ).orEmpty(),
        autoStart = prefs.getBoolean("auto_start", true)
    )

    fun save(config: AppConfig) {
        prefs.edit()
            .putString("base_url", config.normalizedBaseUrl)
            .putString("door_webhook_id", config.doorWebhookId.trim())
            .putString("bell_webhook_id", config.bellWebhookId.trim())
            .putString("target_package", config.targetPackage.trim())
            .putString("target_title", config.targetTitle.trim())
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
