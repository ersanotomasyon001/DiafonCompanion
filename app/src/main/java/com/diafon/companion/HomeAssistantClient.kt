package com.diafon.companion

import java.net.HttpURLConnection
import java.net.URL

object HomeAssistantClient {
    fun postDoorWebhook(config: AppConfig): Result<Int> =
        post(config.doorWebhookUrl)

    fun postBellWebhook(config: AppConfig): Result<Int> =
        post(config.bellWebhookUrl)

    private fun post(url: String): Result<Int> = runCatching {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Geçerli bir Home Assistant adresi girilmedi."
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write("{}".toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        connection.disconnect()

        if (code !in 200..299) {
            error("Home Assistant HTTP $code döndürdü.")
        }
        code
    }
}
