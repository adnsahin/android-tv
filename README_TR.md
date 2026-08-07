# SeyirPlus M3U TV 1.0

Bu proje, SeyirTürk kodundan bağımsız çalışan yalnızca M3U/M3U8 kaynakları için hazırlanmış ayrı uygulamadır.

## İçerik

- `android/` — Android / Android TV uygulaması
- `web/` — bağımsız web sürümü
- `embedded_sources.json` — APK ve web sürümüne önceden gömülecek kaynaklar
- `Kaynaklari_Gom.bat` — üstteki JSON'u Android ve web varlıklarına kopyalar
- `.github/workflows/build-apk.yml` — GitHub Actions ile APK üretir

## Uygulama özellikleri

- Birden fazla M3U/M3U8 adresi ekleme, kaydetme, düzenleme, aç/kapat ve silme
- Kaynakları APK içine önceden gömme
- Canlı TV / Filmler / Diziler ayrı sekmeler
- `group-title` ve Xtream kategori bilgilerinden kategori filtreleri
- `tvg-logo`, `stream_icon`, `cover`, `poster` vb. görseller
- Xtream `player_api.php` üzerinden logo/poster/kategori zenginleştirmesi
- Aynı kanal veya film farklı listelerdeyse tek kart altında alternatif kaynak olarak birleştirme
- Dizileri sezon/bölüm olarak gruplama
- Canlı TV ve film kartında tek tık oynatma
- Dizi bölümünde tek tık oynatma
- Android TV D-pad / OK / Geri kumanda navigasyonu
- Native Media3/ExoPlayer oynatıcı
- HLS, DASH ve progressive/TS benzeri HTTP akışlarını ExoPlayer'a verme
- Kaynak hata verirse aynı içerikteki sonraki M3U kaynağını otomatik deneme
- Player'da CH+/CH- ile alternatif kaynak değiştirme
- M3U listelerini cihazda cache'leme; sonraki açılışta kartların daha hızlı gelmesi
- HTTP (cleartext) IPTV adreslerine izin

## Kaynak ekleme

Uygulama içinde `Kaynaklar` > `Kaynak Ekle` ile adres ekleyebilirsiniz. Adres ve ayarlar Android uygulamasının özel uygulama verisinde saklanır.

URL içine kullanıcı adı/parola gömülü olabileceğinden ortak veya güvenilmeyen cihazlarda kişisel IPTV hesabı bırakmayın.

## APK içine kaynak gömme

1. Bu klasördeki `embedded_sources.json` dosyasını düzenleyin.
2. Örnek yapı için `embedded_sources.example.json` dosyasına bakın.
3. `Kaynaklari_Gom.bat` çalıştırın.
4. APK'yı yeniden derleyin.

Örnek:

```json
[
  {
    "id": "ev_iptv",
    "name": "Ev IPTV",
    "url": "http://SUNUCU:PORT/get.php?username=KULLANICI&password=PAROLA&type=m3u_plus&output=ts",
    "type": "auto",
    "enabled": true,
    "referer": "",
    "userAgent": ""
  }
]
```

Not: Gömülü kaynaklar ilk kurulumda uygulamanın kayıtlı kaynaklarına aktarılır. Uygulama daha önce kurulmuşsa yeni gömülü listeyi görmek için uygulama verisini temizlemek veya kaynağı uygulama içinden eklemek gerekir.

## Android TV kumandası

Ana ekran:
- Yön tuşları: kartlar, sekmeler ve kategoriler arasında dolaşma
- OK/Enter: seçme / oynatma
- Geri: dizi detayından geri veya uygulamadan çıkış

Oynatıcı:
- OK: oynat / duraklat
- Sol / Sağ: 15 saniye geri / ileri (VOD içeriklerde)
- CH+ / Page Up / Media Next: sonraki M3U kaynağı
- CH- / Page Down / Media Previous: önceki M3U kaynağı
- Geri: oynatıcıyı kapat

## Web sürümü

`web/SeyirPlus_M3U_Web.bat` çalıştırın. Şunları başlatır:
- web arayüzü: `http://127.0.0.1:8085/`
- M3U yardımcı proxy: `8091`

Web oynatıcı HLS için hls.js, MPEG-TS/FLV için mpegts.js'i CDN üzerinden yükler. Android APK bunlara ihtiyaç duymaz.

## APK üretme

En kolay yol `APK_OLUSTUR_GITHUB.md` dosyasındaki GitHub Actions yöntemidir. Oluşan `app-debug.apk` Android TV'ye doğrudan yüklenebilir.
