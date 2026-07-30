# TAM GITHUB SÜRÜMÜ

Bu paket GitHub Actions üzerinde **Gradle Wrapper olmadan** APK derleyecek şekilde hazırlanmıştır.

# Diafon Companion

Reolink uygulaması öndeyken ekranda kayan **Kapıyı Aç** düğmesi gösterir.
Düğme, Home Assistant webhook'una yerel ağ üzerinden POST gönderir.

## Home Assistant otomasyonu

Aşağıdaki entity adını kendi Wemos rölenle değiştir:

```yaml
alias: Reolink Uygulaması - Kapıyı Aç
triggers:
  - trigger: webhook
    webhook_id: reolink_kapi_ac
    allowed_methods:
      - POST
    local_only: true
actions:
  - action: switch.turn_on
    target:
      entity_id: switch.wemos_kapi_rolesi
  - delay:
      milliseconds: 700
  - action: switch.turn_off
    target:
      entity_id: switch.wemos_kapi_rolesi
mode: single
```

## Uygulama ayarları

- Home Assistant IP: ör. `192.168.1.50`
- Port: `8123`
- HTTPS: yerel kurulumda çoğunlukla kapalı
- Webhook ID: `reolink_kapi_ac`
- Reolink paket adı: varsayılan `com.mcu.reolink`

Reolink paket adı cihazındaki sürümde farklıysa değiştirebilirsin.

## Gerekli izinler

1. Diğer uygulamaların üzerinde göster
2. Kullanım erişimi
3. Bildirim izni
4. Xiaomi'de pil kısıtlaması kapalı ve otomatik başlatma açık

## Derleme — GitHub üzerinden

1. Bu klasörü yeni bir GitHub deposuna yükle.
2. `Actions` sekmesini aç.
3. `Build APK` iş akışını çalıştır.
4. İş bitince `Artifacts` bölümünden `DiafonCompanion-debug` dosyasını indir.
5. ZIP içindeki `app-debug.apk` dosyasını telefona kur.

## Önemli Android sınırlaması

Normal bir Android uygulaması Reolink'i zorla kapatamaz.
`Komuttan sonra ana ekrana dön` seçeneği Reolink'i arka plana alır ve ana ekranı açar.
