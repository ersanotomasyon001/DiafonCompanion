package com.diafon.companion

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.concurrent.thread

class ReolinkNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "DiafonReolink"
        private const val DUPLICATE_WINDOW_MS = 10_000L

        @Volatile
        private var lastSentAt = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val config = ConfigStore(this).load()

        if (sbn.packageName != config.targetPackage.trim()) return

        val title = notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (!title.equals(config.targetTitle.trim(), ignoreCase = true)) return

        val now = System.currentTimeMillis()
        if (now - lastSentAt < DUPLICATE_WINDOW_MS) return
        lastSentAt = now

        thread(name = "diafon-bell-webhook") {
            HomeAssistantClient.postBellWebhook(config)
                .onSuccess { code ->
                    Log.i(TAG, "Zil bildirimi webhook'a gönderildi. HTTP $code")
                }
                .onFailure { error ->
                    Log.e(TAG, "Zil bildirimi gönderilemedi.", error)
                    lastSentAt = 0L
                }
        }
    }
}
