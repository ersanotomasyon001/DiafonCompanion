package com.diafon.companion

import java.net.HttpURLConnection
import java.net.URL

object HomeAssistantClient {
    fun postWebhook(config: AppConfig): Result<Int> = runCatching {
        val connection = (URL(config.webhookUrl).openConnection() as HttpURLConnection).apply {
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
