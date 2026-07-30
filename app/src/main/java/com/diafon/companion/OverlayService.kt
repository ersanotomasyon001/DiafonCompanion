package com.diafon.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "diafon_overlay"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var detector: ForegroundAppDetector
    private var overlayButton: Button? = null

    private val monitor = object : Runnable {
        override fun run() {
            val config = ConfigStore(this@OverlayService).load()
            val shouldShow =
                Settings.canDrawOverlays(this@OverlayService) &&
                detector.currentPackage() == config.targetPackage

            if (shouldShow) showButton() else hideButton()
            handler.postDelayed(this, 600)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        detector = ForegroundAppDetector(this)
        handler.post(monitor)
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        hideButton()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showButton() {
        if (overlayButton != null) return

        val button = Button(this).apply {
            text = "🔓 KAPIYI AÇ"
            textSize = 17f
            setPadding(28, 18, 28, 18)
            setOnClickListener { openDoor() }
        }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 24
            y = 0
        }

        try {
            windowManager.addView(button, params)
            overlayButton = button
        } catch (_: Exception) {
            overlayButton = null
        }
    }

    private fun hideButton() {
        overlayButton?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayButton = null
    }

    private fun openDoor() {
        val button = overlayButton ?: return
        button.isEnabled = false
        val config = ConfigStore(this).load()

        thread {
            val result = HomeAssistantClient.postWebhook(config)

            handler.post {
                button.isEnabled = true

                result.onSuccess {
                    Toast.makeText(this, "Kapı komutu gönderildi.", Toast.LENGTH_SHORT).show()
                    if (config.returnHome) returnToHome()
                }.onFailure {
                    Toast.makeText(
                        this,
                        "Bağlantı hatası: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun returnToHome() {
        // Android, normal bir uygulamanın başka bir uygulamayı zorla kapatmasına izin vermez.
        // Bu işlem Reolink'i arka plana alıp ana ekrana döner.
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Diafon Companion")
            .setContentText("Reolink düğmesi izleniyor.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Diafon arka plan servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
