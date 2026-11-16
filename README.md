# smart28

Ovládanie Tuya zariadení (zoznam, rozvrhy, týždenný program termostatov) + Android APK s widgetom.

- Web: https://dvaosem.com/smart28/smart28.html (prihlásenie cez [dvaosem](https://dvaosem.com) hub)
- Backend: Firebase Cloud Functions (projekt `smart28-6dc0b`), Tuya kľúče sú len v Secret Manageri.
- APK: tenký WebView obal + widget (teploty, obľúbené ZAP/VYP) a aktualizácie z GitHub Releases.

## Inštalácia
1. Stiahni `https://github.com/dvaosem/smart28/releases/latest/download/smart28.apk`.
2. Povoľ inštaláciu z neznámych zdrojov (len prvý raz), nainštaluj.
3. Prihlás sa e-mailom. Widget: tlačidlo „Pridať widget" v appke alebo cez launcher.
4. ★ na karte zariadenia = zobrazí sa vo widgete (ukladá sa na zariadení).

## Build
Push do `main` (mimo `web/**`, `*.md`) spustí GitHub Actions → release `build-N` so `smart28.apk`.
Webové súbory v `web/` sa nahrávajú na hosting zvlášť.
