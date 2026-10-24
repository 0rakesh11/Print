# HP Ink Tank 319 USB OTG Printer App (Full Project)

Yeh project aapke screenshots (PrinterShare Dark UI) aur HP Ink Tank 319 USB OTG requirements ke hisaab se poora configure kiya gaya hai.

## Is Project se `.apk` File Kaise Nikalein (2 Aasan Tarike):

### Tarika 1: Android Studio (PC/Laptop par - 2 Minute me)
1. Is ZIP file ko extract karein.
2. **Android Studio** open karein -> **Open** par click karke is folder ko select karein.
3. Upar menu me **Build > Build Bundle(s) / APK(s) > Build APK(s)** par click karein.
4. `app/build/outputs/apk/debug/app-debug.apk` ko apne Vivo phone me install karein.

### Tarika 2: Bina PC ke (Sirf Mobile se GitHub Actions ke zariye)
Is project me `.github/workflows/build_apk.yml` pehle se maujood hai:
1. GitHub.com par ek naya repository banayein aur is ZIP ki sabhi files upload kar dein.
2. Repository ke **Actions** tab me jayein — wahan automatically **HP319-USB-Printer-APK** build hokar download ke liye mil jayega!
