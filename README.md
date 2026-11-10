# TavernDroid OpenCode Go

APK de prueba de TavernDroid 1.0.4 con configuración rápida para OpenCode Go.

Descarga el archivo `TavernDroid-Go-1.0.4.apk` desde la sección **Releases**. La app solicitará que configures tu propia API key; no se incluye ninguna clave en el APK.

---

# PortaAlerta (app Android para tienda de telefonía)

App nativa (Kotlin + Jetpack Compose + Room) para no perder ventas por portabilidad ni seguros.

## Qué hace

- **Clientes**: nombre, teléfono, compañía y plan actual, notas, búsqueda.
- **Portabilidad a WOM**: anotas una fecha base y la app te alarma **a los 4 meses** (plazo editable) y **N días antes** (por defecto 7). Suena a la hora que elijas, también tras reiniciar el teléfono.
- **Seguros**: pestaña propia con **cuenta regresiva de 1 mes** (plazo editable) con barra de progreso y alarma previa.
- **WhatsApp en un toque**: desde la notificación o la tarjeta del cliente se abre `wa.me` con tu **mensaje por defecto personalizable** (`{nombre}`, `{vendedor}`, `{tienda}`, `{compania}`, `{plan}`, `{detalle}`, `{fecha}`). También botón de llamada.
- **Agenda**: atrasados / para contactar ahora / próximos.
- **Respaldo**: exporta clientes y recordatorios a CSV y compártelo.

## Compilar

El APK se genera en GitHub Actions (pestaña **Actions → Build APK → artefacto `PortaAlerta-debug-apk`**).
En local: abre la carpeta en Android Studio, o `./gradlew assembleDebug` (JDK 17 + Android SDK 34).
Requiere Android 8.0+ (API 26). En Android 13+ acepta el permiso de notificaciones; en 12+ permite “Alarmas y recordatorios” desde Ajustes para que suenen a la hora exacta.

## Seguridad y privacidad (versión 1.1)
- **Sin internet**: el APK no declara el permiso `INTERNET` (el CI lo verifica en cada compilación).
- **Notificaciones discretas** con el teléfono bloqueado (sin nombres de clientes).
- **Fotos de chips** solo en la carpeta privada de la app; **ni fotos ni base de clientes** van al respaldo de Google.
- Versión final no depurable. Para poder **actualizar sin desinstalar**, configura la llave fija (ver abajo).
- Modo equipo con Firebase: preparado en `docs/EQUIPO_FIREBASE.md` y `firebase/firestore.rules`.

### Llave de firma fija (una vez)
En GitHub: Settings → Secrets and variables → Actions → New repository secret. Crear `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
Mientras no existan, el APK se firma con una llave de depuración distinta en cada compilación (hay que desinstalar para actualizar).
