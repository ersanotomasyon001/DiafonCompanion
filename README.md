# Diafon Companion V4

## V4 yenilikleri

- Reolink Android bildirimlerini doğrudan dinler.
- Yalnızca ayarlardaki paket adı ve başlık eşleşirse işlem yapar.
- Varsayılan paket: `com.mcu.reolink`
- Varsayılan başlık: `KAPI ZİLİ ALGILANDI`
- Zil olayını, uygulamada seçtiğiniz Home Assistant sunucusuna gönderir.
- Sunucu adresi sabit değildir; ayarlardaki adres kullanılır.
- Kapı açma ve zil bildirimi için ayrı webhook kimlikleri vardır.
- Aynı bildirimin iki kez işlenmesine karşı 10 saniyelik koruma vardır.
- Reolink etkinlik geçişlerinde overlay kaybolmadan önce 3 saniye bekler.

## Kurulum

1. Uygulamada Home Assistant adresini girin.
2. Kapı açma webhook ID: `reolink_kapi_ac`
3. Zil bildirimi webhook ID: `reolink_zil_bildirimi`
4. `BİLDİRİM ERİŞİMİ VER` düğmesine basıp Diafon Companion'ı etkinleştirin.
5. Xiaomi'de pil kısıtlamasını kaldırın ve otomatik başlatmayı açın.

## Güvenlik notu

`reolink_zil_bildirimi` webhook'u kapıyı doğrudan açmamalıdır. Yalnızca telefonlara bildirim göndermelidir. Kapı ancak bildirimdeki `KAPI_AC` eylemine basıldığında `button.kapiac` üzerinden açılmalıdır.

## Home Assistant — zil bildirimi

```yaml
alias: Reolink Zil Bildirimini Telefona Gönder
triggers:
  - trigger: webhook
    webhook_id: reolink_zil_bildirimi
    allowed_methods:
      - POST
    local_only: false
conditions: []
actions:
  - action: notify.mobile_app_telfon
    data:
      title: "🔔 Kapı Zili"
      message: "Birisi zile bastı."
      data:
        actions:
          - action: KAPI_AC
            title: "🔓 Kapıyı Aç"
            authenticationRequired: true
mode: single
```

## Home Assistant — bildirim düğmesinden kapıyı aç

```yaml
alias: Telefon Bildiriminden Kapıyı Aç
triggers:
  - trigger: event
    event_type: mobile_app_notification_action
    event_data:
      action: KAPI_AC
conditions: []
actions:
  - action: button.press
    target:
      entity_id: button.kapiac
mode: single
```
