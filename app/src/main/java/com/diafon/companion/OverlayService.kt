package com.diafon.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
        private const val HA_FULL_PACKAGE = "io.homeassistant.companion.android"
        private const val HA_MINIMAL_PACKAGE =
            "io.homeassistant.companion.android.minimal"
        private const val LONG_PRESS_MS = 420L

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
    private lateinit var store: ConfigStore

    private var overlayPanel: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var doorButton: TextView? = null

    private val monitor = object : Runnable {
        override fun run() {
            val config = store.load()
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
        store = ConfigStore(this)
        handler.post(monitor)
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (overlayPanel != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(
                Color.argb(150, 20, 20, 24),
                34f
            )
            elevation = dp(8).toFloat()
        }

        val openDoor = createCircleButton(
            icon = "🔓",
            contentDescriptionText = "Kapıyı Aç",
            backgroundColor = Color.argb(235, 20, 145, 82)
        )

        val openHa = createCircleButton(
            icon = "🏠",
            contentDescriptionText = "Home Assistant",
            backgroundColor = Color.argb(225, 62, 68, 78)
        )

        panel.addView(openDoor)
        panel.addView(space(dp(5)))
        panel.addView(openHa)

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            dp(66),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = store.loadOverlayX().coerceAtLeast(0)
            y = store.loadOverlayY()
        }

        installPressAndDrag(openDoor, panel, params) {
            sendDoorCommand()
        }

        installPressAndDrag(openHa, panel, params) {
            openHomeAssistant()
        }

        try {
            windowManager.addView(panel, params)
            overlayPanel = panel
            overlayParams = params
            doorButton = openDoor
        } catch (_: Exception) {
            overlayPanel = null
            overlayParams = null
            doorButton = null
        }
    }

    private fun createCircleButton(
        icon: String,
        contentDescriptionText: String,
        backgroundColor: Int
    ): TextView = TextView(this).apply {
        text = icon
        textSize = 24f
        gravity = Gravity.CENTER
        contentDescription = contentDescriptionText
        background = roundedBackground(backgroundColor, 30f)
        elevation = dp(4).toFloat()
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
    }

    private fun installPressAndDrag(
        button: View,
        panel: View,
        params: WindowManager.LayoutParams,
        onShortTap: () -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialRawX = 0f
        var initialRawY = 0f
        var dragMode = false
        var moved = false

        val enterDragMode = Runnable {
            dragMode = true
            button.performHapticFeedback(
                android.view.HapticFeedbackConstants.LONG_PRESS
            )
        }

        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialRawX = event.rawX
                    initialRawY = event.rawY
                    dragMode = false
                    moved = false
                    handler.postDelayed(enterDragMode, LONG_PRESS_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = initialRawX - event.rawX
                    val deltaY = event.rawY - initialRawY

                    if (
                        !dragMode &&
                        (abs(deltaX) > dp(10) || abs(deltaY) > dp(10))
                    ) {
                        handler.removeCallbacks(enterDragMode)
                    }

                    if (dragMode) {
                        moved = true
                        params.x = (initialX + deltaX.toInt()).coerceAtLeast(0)
                        params.y = initialY + deltaY.toInt()

                        try {
                            windowManager.updateViewLayout(panel, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(enterDragMode)

                    if (dragMode || moved) {
                        store.saveOverlayPosition(params.x, params.y)
                    } else {
                        onShortTap()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(enterDragMode)
                    true
                }

                else -> false
            }
        }
    }

    private fun hideOverlay() {
        overlayPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayPanel = null
        overlayParams = null
        doorButton = null
    }

    private fun sendDoorCommand() {
        val button = doorButton ?: return
        val config = store.load()

        button.isEnabled = false
        button.text = "⏳"

        thread {
            val result = HomeAssistantClient.postWebhook(config)

            handler.post {
                result.onSuccess {
                    button.text = "✅"
                    Toast.makeText(
                        this,
                        "Kapı komutu gönderildi.",
                        Toast.LENGTH_SHORT
                    ).show()

                    handler.postDelayed({
                        button.text = "🔓"
                        button.isEnabled = true
                    }, 1100)
                }.onFailure {
                    button.text = "⚠️"
                    Toast.makeText(
                        this,
                        "Bağlantı hatası: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    handler.postDelayed({
                        button.text = "🔓"
                        button.isEnabled = true
                    }, 1700)
                }
            }
        }
    }

    private fun openHomeAssistant() {
        val packages = listOf(
            HA_FULL_PACKAGE,
            HA_MINIMAL_PACKAGE
        )

        for (packageName in packages) {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                startActivity(launchIntent)
                return
            }
        }

        // Paket adı görülemese veya farklı olsa bile HA adresini açmayı dene.
        // Home Assistant bu bağlantıyla ilişkilendirilmişse uygulama açılır;
        // değilse varsayılan tarayıcı açılır.
        val url = store.load().normalizedBaseUrl
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Home Assistant uygulaması veya uygun tarayıcı bulunamadı.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Float
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun space(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Diafon Companion V3")
            .setContentText("Reolink kapı kontrolleri etkin.")
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
