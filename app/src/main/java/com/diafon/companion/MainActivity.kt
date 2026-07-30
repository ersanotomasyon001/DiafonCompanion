package com.diafon.companion

import android.Manifest
import android.app.AppOpsManager
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

    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var webhook: EditText
    private lateinit var targetPackage: EditText
    private lateinit var https: CheckBox
    private lateinit var returnHome: CheckBox
    private lateinit var autoStart: CheckBox
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Diafon Companion"
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
            val p = dp(20)
            setPadding(p, p, p, p)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Diafon Companion"
            textSize = 26f
        })

        root.addView(TextView(this).apply {
            text = "Reolink açıkken kayan Kapıyı Aç düğmesi gösterir."
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        })

        host = field(root, "Home Assistant IP / alan adı")
        port = field(root, "Port", inputType = android.text.InputType.TYPE_CLASS_NUMBER)
        webhook = field(root, "Webhook ID")
        targetPackage = field(root, "Reolink paket adı")
        https = check(root, "HTTPS kullan")
        returnHome = check(root, "Komuttan sonra ana ekrana dön")
        autoStart = check(root, "Telefon açılınca servisi başlat")

        button(root, "Ayarları Kaydet") {
            saveConfig()
            Toast.makeText(this, "Ayarlar kaydedildi.", Toast.LENGTH_SHORT).show()
        }

        button(root, "Overlay İzni Ver") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        button(root, "Kullanım Erişimi Ver") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        button(root, "Servisi Başlat") {
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

        button(root, "Servisi Durdur") {
            OverlayService.stop(this)
            updateStatus()
        }

        button(root, "Webhook'u Test Et") {
            saveConfig()
            val config = ConfigStore(this).load()
            status.text = "Test ediliyor: ${config.webhookUrl}"
            thread {
                val result = HomeAssistantClient.postWebhook(config)
                runOnUiThread {
                    status.text = result.fold(
                        onSuccess = { "Başarılı. HTTP $it" },
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
                Not: Android, bu uygulamanın Reolink'i zorla kapatmasına izin vermez.
                Seçenek açıksa başarılı komuttan sonra Reolink arka plana alınır ve ana ekrana dönülür.

                Xiaomi için ayrıca:
                • Ayarlar → Uygulamalar → Diafon Companion → Pil tasarrufu → Kısıtlama yok
                • Otomatik başlatmayı aç
            """.trimIndent()
        })

        return scroll
    }

    private fun field(
        root: LinearLayout,
        label: String,
        inputType: Int = android.text.InputType.TYPE_CLASS_TEXT
    ): EditText {
        root.addView(TextView(this).apply {
            text = label
            setPadding(0, dp(12), 0, dp(4))
        })
        return EditText(this).also {
            it.inputType = inputType
            root.addView(
                it,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun check(root: LinearLayout, textValue: String): CheckBox =
        CheckBox(this).also {
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
        host.setText(config.host)
        port.setText(config.port.toString())
        webhook.setText(config.webhookId)
        targetPackage.setText(config.targetPackage)
        https.isChecked = config.https
        returnHome.isChecked = config.returnHome
        autoStart.isChecked = config.autoStart
    }

    private fun saveConfig() {
        val parsedPort = port.text.toString().toIntOrNull() ?: 8123
        ConfigStore(this).save(
            AppConfig(
                host = host.text.toString(),
                port = parsedPort,
                https = https.isChecked,
                webhookId = webhook.text.toString(),
                targetPackage = targetPackage.text.toString(),
                returnHome = returnHome.isChecked,
                autoStart = autoStart.isChecked
            )
        )
    }

    private fun updateStatus() {
        status.text = buildString {
            appendLine("Overlay izni: ${if (Settings.canDrawOverlays(this@MainActivity)) "Var" else "Yok"}")
            appendLine("Kullanım erişimi: ${if (hasUsageAccess()) "Var" else "Yok"}")
            append("Hedef paket: ${ConfigStore(this@MainActivity).load().targetPackage}")
        }
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
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
