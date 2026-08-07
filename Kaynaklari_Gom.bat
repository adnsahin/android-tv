@echo off
setlocal
cd /d "%~dp0"
if not exist "embedded_sources.json" (
  echo [HATA] embedded_sources.json bulunamadi.
  pause
  exit /b 1
)
copy /Y "embedded_sources.json" "android\app\src\main\assets\embedded_sources.json" >nul
copy /Y "embedded_sources.json" "web\embedded_sources.json" >nul
echo [OK] Kaynaklar Android ve Web surumune gomuldu.
echo APK'yi yeniden derlerseniz bu kaynaklar ilk kurulumda hazir gelir.
pause
