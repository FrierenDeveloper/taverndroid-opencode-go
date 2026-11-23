# PortaAlerta · Handoff

Documento para retomar el proyecto sin releer todo el código. Léelo primero.

## Qué es
App Android nativa (Kotlin + Jetpack Compose) para ejecutivos de tienda de telefonía en Chile. Registra clientes, calcula cuándo pueden portarse, avisa con alarmas y permite contactar por WhatsApp con un mensaje listo. Incluye lector de chips (ICCID) con la cámara y fotos de chips cifradas.

- Paquete: `cl.portaalerta` · versión `1.2.<build>` · minSdk 26 (Android 8.0)
- Rama de trabajo: `ccr-22f0ce33-7bglze` (repo `FrierenDeveloper/taverndroid-opencode-go`)

## Decisiones de diseño (no cambiar sin hablarlo)
- **Datos solo en el teléfono.** Sin Firebase ni sincronización. Firebase quedó preparado pero inerte (`docs/EQUIPO_FIREBASE.md`, `firebase/firestore.rules`).
- **Sin permiso de internet.** `INTERNET` y `ACCESS_NETWORK_STATE` se eliminan en el manifest; la CI falla si vuelven.
- **Cifrado:** base de datos con SQLCipher (`portaalerta_secure.db`), fotos cifradas una a una con AES-256-GCM, claves envueltas con el Android Keystore.
- **Sin respaldo de Android** (`allowBackup=false`). El respaldo propio es un archivo cifrado con contraseña (PBKDF2 600k + AES-256-GCM).
- **Sin bloqueo de pantalla propio** (pedido explícito del usuario).
- **Fecha de portabilidad:** la fecha elegida es el día en que el cliente PUEDE portarse. "Desde contrato" suma 4 meses solo como cálculo secundario. La garantía (seguro) es fecha de compra + plazo.
- **"Sin respuesta" no cierra el recordatorio**: se reprograma. No cuenta como contactado.

## Mapa del código
- `app/src/main/java/cl/portaalerta/`
  - `MainViewModel.kt`: navegación por pila (`Screen` sellado) y estado de UI hoisted (pestaña, modo de agenda, calendario, borrador del alta).
  - `NewClientDraft.kt` / `ui/NewClientScreen.kt`: alta en 4 pasos (nombre+número, compañía, fecha, chip opcional). `NewClientHost` separa la pantalla del ViewModel.
  - `ui/Carriers.kt`: lista de compañías y `CarrierBadge` (usa `logo_<key>` si existe; si no, insignia de color).
  - `ui/ChipsScreens.kt`, `ui/ChipReader.kt`, `data/Iccid.kt`: lector de chips (ML Kit, en el dispositivo).
  - `data/PhotoStore.kt`, `data/PhotoCrypto.kt`, `data/SecureKeys.kt`: fotos y claves.
  - `data/BackupCrypto.kt`, `data/BackupCodec.kt`, `ui/BackupUi.kt`: respaldo cifrado.
  - `data/Data.kt`: entidades Room (Client, Followup, ChipScan), AppDatabase v3.
  - `Alarms.kt`: AlarmManager y notificaciones.
  - `ui/Summary.kt`, `ui/Calendar.kt`, `ui/Home.kt`, `ui/FollowupEdit.kt`, `ui/ClientScreens.kt`, `ui/ImportScreen.kt`: resto de pantallas.
- `app/src/main/res/drawable-nodpi/logo_*.png`: logos oficiales (Entel, Movistar, Claro, WOM, VTR recortado a símbolo, GTD). Faltan Virgin, Mundo y Simple (ver `docs/LOGOS.md`).
- `app/src/test/java/cl/portaalerta/`: `LogicTest`, `CryptoTest`, `BackupTest`; `screens/FlujoAltaScreens.kt` son capturas (Roborazzi), excluidas salvo con `-Pscreens`.
- `.github/workflows/android.yml`:
  - Job `build`: tests, APK release, guardas del manifest, lint, artefacto `PortaAlerta-apk`.
  - Job `screens`: corre solo con `[capturas]` en el mensaje del commit o disparo manual. Publica PNG en la rama `capturas`.

## Cómo verificar (sin Android SDK local)
No hay SDK en el contenedor. La compilación y las pruebas corren en GitHub Actions:
1. Commit y push a la rama de trabajo.
2. Revisar el run con `gh run list` / `gh run view --log-failed`.
3. Para capturas reales: incluir `[capturas]` en el mensaje del commit y bajar los PNG de la rama `capturas`.

Las capturas y la presentación del proyecto (PPTX, DOCX, videos) viven en el escritorio de trabajo, no en el repo.

## Pendientes
- **Del usuario:** dejar el repo privado; crear los secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; borrar el release de TavernDroid.
- **Sin firma propia:** el build usa la clave de depuración, que cambia por run. Hay que desinstalar antes de instalar una versión nueva hasta tener los secretos.
- **Logos:** Virgin, Mundo y Simple (archivos PNG 512×512 transparentes en `drawable-nodpi`, con el nombre `logo_<key>`).
- **Entel:** su logo se ve pequeño en insignias de 28 dp; se puede subir a 36 dp.
- **Nunca probado en un teléfono real.** Antes de usarlo con clientes: instalar el APK, probar alarmas y el lector de chips, y revisar el ahorro de batería de Xiaomi/Samsung.
- **Cumplimiento (Ley 21.719, vigente desde el 1 de dic. 2026 salvo postergación):** falta aviso de privacidad en la app, procedimiento para borrar o entregar datos de un cliente, plazo de retención y procedimiento de filtraciones. Ver la conversación con el usuario.

## Qué NO hacer
- No agregar permiso de internet, analytics ni SDKs que envíen datos.
- No commitear keystores, contraseñas ni `google-services.json`.
- No usar datos reales de clientes en capturas, presentaciones o videos.
- No hacer force-push a `main`.
