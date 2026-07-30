package com.diafon.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "diafon_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val HOME_ASSISTANT_PACKAGE = "io.homeassistant.companion.android"

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

    private var overlayCard: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var doorButton: TextView? = null

    private val monitor = object : Runnable {
        override fun run() {
            val config = ConfigStore(this@OverlayService).load()
            val shouldShow =
                Settings.canDrawOverlays(this@OverlayService) &&
                    detector.currentPackage() == config.targetPackage

            if (shouldShow) showOverlay() else hideOverlay()
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
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayCard != null) return

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(
                color = Color.argb(235, 28, 28, 32),
                radiusDp = 20f
            )
            elevation = dp(12).toFloat()
        }

        val openDoor = createActionButton(
            text = "🔓  KAPIYI AÇ",
            backgroundColor = Color.rgb(25, 135, 84),
            textColor = Color.WHITE
        ).apply {
            setOnClickListener { sendDoorCommand() }
        }

        val openHa = createActionButton(
            text = "🏠  HOME ASSISTANT",
            backgroundColor = Color.rgb(64, 68, 76),
            textColor = Color.WHITE
        ).apply {
            setOnClickListener { openHomeAssistant() }
        }

        card.addView(openDoor)
        card.addView(space(dp(6)))
        card.addView(openHa)

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(210),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(14)
            y = 0
        }

        enableDragging(card, params)

        try {
            windowManager.addView(card, params)
            overlayCard = card
            overlayParams = params
            doorButton = openDoor
        } catch (_: Exception) {
            overlayCard = null
            overlayParams = null
            doorButton = null
        }
    }

    private fun createActionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int
    ): TextView = TextView(this).apply {
        this.text = text
        this.textSize = 16f
        this.setTextColor(textColor)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = roundedBackground(backgroundColor, 15f)
        isClickable = true
        isFocusable = true
        elevation = dp(3).toFloat()
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun space(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }

    private fun enableDragging(
        view: View,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchX - event.rawX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                        moved = true
                        params.x = (startX + dx).coerceAtLeast(0)
                        params.y = startY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    moved
                }

                else -> false
            }
        }
    }

    private fun hideOverlay() {
        overlayCard?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayCard = null
        overlayParams = null
        doorButton = null
    }

    private fun sendDoorCommand() {
        val button = doorButton ?: return
        val config = ConfigStore(this).load()

        button.isEnabled = false
        button.text = "⏳  GÖNDERİLİYOR..."

        thread {
            val result = HomeAssistantClient.postWebhook(config)

            handler.post {
                result.onSuccess {
                    button.text = "✅  KOMUT GİTTİ"
                    Toast.makeText(
                        this,
                        "Kapı açma komutu Home Assistant'a gönderildi.",
                        Toast.LENGTH_SHORT
                    ).show()

                    handler.postDelayed({
                        button.text = "🔓  KAPIYI AÇ"
                        button.isEnabled = true
                    }, 1200)
                }.onFailure {
                    button.text = "⚠️  BAĞLANTI HATASI"
                    Toast.makeText(
                        this,
                        "Home Assistant bağlantı hatası: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    handler.postDelayed({
                        button.text = "🔓  KAPIYI AÇ"
                        button.isEnabled = true
                    }, 1800)
                }
            }
        }
    }

    private fun openHomeAssistant() {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(HOME_ASSISTANT_PACKAGE)

        if (launchIntent == null) {
            Toast.makeText(
                this,
                "Home Assistant uygulaması bulunamadı.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        startActivity(launchIntent)
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
            .setContentTitle("Diafon Companion V2")
            .setContentText("Reolink açıkken kapı kontrolü gösteriliyor.")
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
