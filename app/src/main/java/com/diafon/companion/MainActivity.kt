package com.diafon.companion

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var baseUrl: EditText
    private lateinit var doorWebhook: EditText
    private lateinit var bellWebhook: EditText
    private lateinit var targetPackage: EditText
    private lateinit var targetTitle: EditText
    private lateinit var autoStart: CheckBox
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Diafon Companion V4"
        setContentView(buildUi())
        loadConfig()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(20)
            setPadding(padding, padding, padding, padding)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Diafon Companion V4"
            textSize = 26f
        })

        root.addView(TextView(this).apply {
            text = "Reolink bildirimini seçtiğiniz Home Assistant sunucusuna gönderir ve Reolink açıkken küçük kontrol ikonları gösterir."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        })

        baseUrl = field(root, "Home Assistant adresi", "Örnek: https://ha.example.com veya http://192.168.1.112:8123")
        doorWebhook = field(root, "Kapı açma Webhook ID", "reolink_kapi_ac")
        bellWebhook = field(root, "Zil bildirimi Webhook ID", "reolink_zil_bildirimi")
        targetPackage = field(root, "Reolink paket adı", "com.mcu.reolink")
        targetTitle = field(root, "Dinlenecek bildirim başlığı", "KAPI ZİLİ ALGILANDI")
        autoStart = check(root, "Telefon açılınca overlay servisini başlat")

        button(root, "AYARLARI KAYDET") {
            saveConfig()
            Toast.makeText(this, "Ayarlar kaydedildi.", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        button(root, "BİLDİRİM ERİŞİMİ VER") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        button(root, "OVERLAY İZNİ VER") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        button(root, "KULLANIM ERİŞİMİ VER") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        button(root, "OVERLAY SERVİSİNİ BAŞLAT") {
            saveConfig()
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Önce overlay izni ver.", Toast.LENGTH_LONG).show()
                return@button
            }
            if (!hasUsageAccess()) {
                Toast.makeText(this, "Önce kullanım erişimi ver.", Toast.LENGTH_LONG).show()
                return@button
            }
            OverlayService.start(this)
            updateStatus()
        }

        button(root, "OVERLAY SERVİSİNİ DURDUR") {
            OverlayService.stop(this)
            updateStatus()
        }

        button(root, "KAPI WEBHOOK'UNU TEST ET") {
            saveConfig()
            val config = ConfigStore(this).load()
            status.text = "Test ediliyor:\n${config.doorWebhookUrl}"
            thread {
                val result = HomeAssistantClient.postDoorWebhook(config)
                runOnUiThread {
                    status.text = result.fold(
                        onSuccess = { "Kapı webhook'u başarılı. HTTP $it" },
                        onFailure = { "Hata: ${it.message}" }
                    )
                }
            }
        }

        button(root, "ZİL WEBHOOK'UNU TEST ET") {
            saveConfig()
            val config = ConfigStore(this).load()
            status.text = "Test ediliyor:\n${config.bellWebhookUrl}"
            thread {
                val result = HomeAssistantClient.postBellWebhook(config)
                runOnUiThread {
                    status.text = result.fold(
                        onSuccess = { "Zil webhook'u başarılı. HTTP $it" },
                        onFailure = { "Hata: ${it.message}" }
                    )
                }
            }
        }

        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(18), 0, dp(12))
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = """
                Önemli:
                • Zil bildirimi ve kapı açma için iki ayrı webhook kullanılır.
                • Zil bildirimi webhook'u yalnızca iPhone bildirim otomasyonunu çalıştırmalıdır; kapıyı otomatik açmamalıdır.
                • Bildirim dinleyicisi yalnızca seçilen paket ve başlık tam eşleştiğinde çalışır.
                • Aynı bildirimin tekrar gönderilmesini önlemek için 10 saniyelik koruma vardır.
                • Reolink etkinlik geçişlerinde overlay 3 saniye bekleyerek kaybolur.

                Xiaomi:
                • Pil tasarrufu → Kısıtlama yok
                • Otomatik başlatma → Açık
                • Bildirim erişimi → Diafon Companion açık
            """.trimIndent()
        })

        return scroll
    }

    private fun field(root: LinearLayout, label: String, hintText: String): EditText {
        root.addView(TextView(this).apply {
            text = label
            setPadding(0, dp(12), 0, dp(4))
        })
        return EditText(this).also {
            it.hint = hintText
            it.inputType = android.text.InputType.TYPE_CLASS_TEXT
            root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun check(root: LinearLayout, textValue: String): CheckBox = CheckBox(this).also {
        it.text = textValue
        root.addView(it)
    }

    private fun button(root: LinearLayout, textValue: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = textValue
            setOnClickListener { action() }
        })
    }

    private fun loadConfig() {
        val config = ConfigStore(this).load()
        baseUrl.setText(config.baseUrl)
        doorWebhook.setText(config.doorWebhookId)
        bellWebhook.setText(config.bellWebhookId)
        targetPackage.setText(config.targetPackage)
        targetTitle.setText(config.targetTitle)
        autoStart.isChecked = config.autoStart
    }

    private fun saveConfig() {
        val enteredUrl = baseUrl.text.toString().trim()
        if (enteredUrl.isNotBlank() && !enteredUrl.startsWith("http://") && !enteredUrl.startsWith("https://")) {
            baseUrl.setText("https://$enteredUrl")
        }
        ConfigStore(this).save(
            AppConfig(
                baseUrl = baseUrl.text.toString(),
                doorWebhookId = doorWebhook.text.toString(),
                bellWebhookId = bellWebhook.text.toString(),
                targetPackage = targetPackage.text.toString(),
                targetTitle = targetTitle.text.toString(),
                autoStart = autoStart.isChecked
            )
        )
    }

    private fun updateStatus() {
        val config = ConfigStore(this).load()
        status.text = buildString {
            appendLine("Bildirim erişimi: ${if (hasNotificationAccess()) "Var" else "Yok"}")
            appendLine("Overlay izni: ${if (Settings.canDrawOverlays(this@MainActivity)) "Var" else "Yok"}")
            appendLine("Kullanım erişimi: ${if (hasUsageAccess()) "Var" else "Yok"}")
            appendLine("HA adresi: ${config.normalizedBaseUrl}")
            appendLine("Kapı webhook: ${config.doorWebhookId}")
            appendLine("Zil webhook: ${config.bellWebhookId}")
            appendLine("Hedef paket: ${config.targetPackage}")
            append("Hedef başlık: ${config.targetTitle}")
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val component = ComponentName(this, ReolinkNotificationListener::class.java)
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.split(":")
            ?.any { ComponentName.unflattenFromString(it) == component } == true
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
