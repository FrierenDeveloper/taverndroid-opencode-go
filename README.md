# PortaAlerta

App Android para tiendas de telefonía: no perder ninguna portabilidad ni seguro de equipo.
Todo funciona **sin internet** y los datos quedan **cifrados en el teléfono** de cada ejecutivo.

## Qué hace

- **Alta de cliente en 3 pasos**: nombre y teléfono, compañía con un toque (Entel, Movistar, Claro, WOM, Virgin, Mundo, VTR, Simple u otra) y la fecha exacta en que puede portarse.
- **Agenda** por urgencia (atrasados, para contactar ahora, próximos) en lista o calendario, con contadores.
- **Alarmas** a la hora elegida, con aviso previo y el día exacto; botones para escribir por WhatsApp o llamar.
- **WhatsApp en un toque** con tu mensaje por defecto personalizable (`{nombre}`, `{vendedor}`, `{tienda}`, `{compania}`, `{detalle}`, `{fecha}`).
- **Seguros del equipo** con cuenta regresiva (1 mes por defecto, editable).
- **Lector de chips (ICCID)** en vivo con la cámara: código de barras o número impreso, validado, sin internet. También desde una foto de la galería.
- **Purga de fotos** cuando un cliente rechaza (alerta al marcar "No le interesó").
- **Resumen del mes**: meta de cierres, contactados, porcentaje de cierre y cierres por semana.
- **Importar contactos** del teléfono.
- **Respaldo cifrado con contraseña** para pasar los datos a otro teléfono, y CSV sin cifrar para Excel.

## Seguridad y privacidad

- **Sin permiso de internet**: el APK no puede enviar datos. El CI falla si el permiso reaparece.
- **Base de datos cifrada** con SQLCipher; **cada foto cifrada** con AES-256-GCM. Las claves viven en el Keystore de Android y no salen del teléfono.
- **Sin respaldo de Android** (`allowBackup=false`): nada va a Google Drive ni a Google Fotos. Las fotos nunca pasan por la galería.
- **Notificaciones discretas** con el teléfono bloqueado (sin nombres de clientes).
- **Respaldo cifrado** con una contraseña elegida por la persona (PBKDF2-HMAC-SHA256 + AES-256-GCM). Sin la contraseña no se puede abrir.
- Versión final no depurable; el CI lo verifica.

Límites: no hay bloqueo propio de la app (quien abra la app con el teléfono desbloqueado ve los datos), y los datos no se sincronizan entre teléfonos.

## Instalar

Requiere **Android 8.0 o superior**.

1. En GitHub: **Actions → Build APK →** última ejecución exitosa **→ artefacto `PortaAlerta-apk`**.
2. Descomprimir e instalar `app-release.apk` (Android pedirá permitir instalar apps de esta fuente).
3. Aceptar el permiso de notificaciones y, en Android 12 o superior, "Alarmas y recordatorios". En Xiaomi, Samsung, Huawei y similares, desactivar el ahorro de batería para la app.

## Compilar

JDK 17 y Android SDK 34: `./gradlew testDebugUnitTest assembleRelease`.
El APK queda en `app/build/outputs/apk/release/`.

## Firma (para poder actualizar sin desinstalar)

El CI firma con una llave fija si existen estos secretos del repositorio (**Settings → Secrets and variables → Actions**):
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Sin ellos firma con la llave de depuración de cada ejecución y Android exige desinstalar para actualizar.
**La llave nunca debe subirse al repositorio.**

## Documentación

- `docs/EQUIPO_FIREBASE.md` y `firebase/firestore.rules`: modo equipo con Firebase, **preparado pero no activo**. La app es 100 % local.
