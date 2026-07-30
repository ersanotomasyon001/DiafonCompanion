# Diafon Companion V3

## Yenilikler

- Küçük, yuvarlak ikon butonları
- 🔓 kısa dokunma: kapı webhook'u
- 🏠 kısa dokunma: Home Assistant uygulaması
- İkona yaklaşık 420 ms basılı tutma: paneli sürükleme
- Panel konumunu kalıcı olarak kaydetme
- Tek Home Assistant adresi alanı:

- Resmî ve minimal Home Assistant Android paketlerini algılama
- Paket açılamazsa HA web adresine geri dönüş

## Home Assistant otomasyonu

Gerçek Wemos entity adını daha sonra değiştir:

```yaml
alias: Diafon Companion - Kapıyı Aç
triggers:
  - trigger: webhook
    webhook_id: reolink_kapi_ac
    allowed_methods:
      - POST
    local_only: false
conditions: []
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

## Yerel ve uzaktan erişim

Uygulamada tek adres kullanılır:

```text
```

Uygulama otomatik olarak şunu çağırır:

```text

```

Uzaktan erişim kullanılacaksa webhook otomasyonunda `local_only: false` gerekir.
Webhook kimliğini parola gibi gizli tut.
