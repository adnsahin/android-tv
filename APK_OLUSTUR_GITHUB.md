# GitHub ile APK üretme

Projede APK üretme workflow'u hazırdır: `.github/workflows/build-apk.yml`.

1. GitHub'da boş bir repository oluşturun.
2. `SeyirPlus_M3U_TV` klasörünün **içindeki tüm dosya ve klasörleri** repository köküne yükleyin. `.github` klasörünü de yükleyin.
3. İsterseniz önce `embedded_sources.json` içine kendi M3U adreslerinizi yazıp `Kaynaklari_Gom.bat` çalıştırın ve güncellenen dosyaları da yükleyin.
4. Repository'de `Actions` sekmesine girin.
5. `SeyirPlus M3U TV APK` workflow'unu açın.
6. `Run workflow` seçin.
7. Build tamamlanınca sayfanın altındaki `Artifacts` bölümünden `SeyirPlus-M3U-TV-APK` dosyasını indirin.
8. ZIP içindeki `app-debug.apk` dosyasını Android TV'ye kurun.

Debug APK sideload için imzalıdır ve kurulabilir. Google Play yayınlaması için ayrı bir release keystore ve release imzalama ayarı gerekir.
