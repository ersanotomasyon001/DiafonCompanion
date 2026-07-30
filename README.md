# Diafon Companion V2

Reolink uygulaması öndeyken iki düğmeli kayan bir kart gösterir:

- **Kapıyı Aç:** Home Assistant webhook'una POST gönderir.
- **Home Assistant:** Röleyi tetiklemeden HA uygulamasını açar.

## Home Assistant tarafı

`switch.wemos_kapi_rolesi` adını kendi gerçek Wemos röle entity'n ile değiştir:

```yaml
alias: Diafon Companion - Kapıyı Aç
description: Telefon uygulamasından gelen webhook ile Wemos rölesini kısa süre tetikler.
triggers:
  - trigger: webhook
    webhook_id: reolink_kapi_ac
    allowed_methods:
      - POST
    local_only: true
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

Uygulamadaki **Webhook ID** alanıyla otomasyondaki `webhook_id` birebir aynı olmalıdır:

```text
reolink_kapi_ac
```

Home Assistant uygulamasının açık olması gerekmez. Telefon, doğrudan HA sunucusuna şu adrese POST gönderir:

```text
http://HA_IP:8123/api/webhook/reolink_kapi_ac
```

## Kurulum

1. Home Assistant IP'sini gir.
2. Portu gir (`8123`).
3. Webhook ID'yi gir (`reolink_kapi_ac`).
4. Ayarları kaydet.
5. Overlay ve kullanım erişimi izinlerini ver.
6. Servisi başlat.
7. Reolink'i aç.

## Güvenlik

Webhook yalnızca yerel ağda çalışacak şekilde `local_only: true` ayarlanmıştır.
Röleyi fiziksel kilide bağlamadan önce otomasyonu boş bir test switch'iyle doğrula.
