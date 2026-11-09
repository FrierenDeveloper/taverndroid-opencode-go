# Modo equipo con Firebase (preparado, aún no activado)

Objetivo: cada ejecutivo con su cuenta; la líder ve a todo el equipo en una pestaña **Equipo** de su propia app.
Las fotos de chips **nunca** se suben. Plan gratuito (Spark), sin tarjeta, para equipos de hasta ~15 personas.

## Estado actual de la app
- 100 % sin conexión: el APK **no** declara el permiso `INTERNET` y el CI falla si aparece.
- Los datos viven solo en el teléfono (base Room). Fotos en carpeta privada, fuera de respaldos.
- Ya está listo para el modo equipo:
  - `app/build.gradle.kts` aplica el plugin `google-services` **solo si existe** `app/google-services.json`.
  - `firebase/firestore.rules` tiene las reglas de seguridad (sin probar).
  - El CI omite el control de "sin INTERNET" cuando existe `app/google-services.json`.

## Lo que hace la líder (una vez, ~20 min)
1. Entrar a https://console.firebase.google.com con una cuenta de Google (mejor la de la empresa).
2. **Crear proyecto** (sin Google Analytics).
3. **Build → Authentication → Comenzar → Correo electrónico/contraseña → Habilitar.**
4. **Build → Firestore Database → Crear base de datos** → modo **producción** → ubicación `southamerica-west1` (Santiago) o la más cercana.
5. Pestaña **Reglas** → pegar el contenido de `firebase/firestore.rules` → **Publicar**.
6. **Configuración del proyecto → Tus apps → Android** → nombre del paquete `cl.portaalerta` → descargar `google-services.json`.
7. Entregar ese archivo al desarrollador (no es una contraseña, pero no lo publiques): se guarda como `app/google-services.json`.
8. Recomendado: en Google Cloud → Credenciales, restringir la clave de API a la app Android `cl.portaalerta`.

## Lo que falta programar (por etapas, cada una con su build)
1. Quitar las dos líneas `tools:node="remove"` del manifiesto y agregar las dependencias de Firebase (Auth, Firestore).
2. **Migración de base 3 → 4**: columnas `uid` (texto estable) y `updatedAt` en clientes y recordatorios, para sincronizar sin duplicar.
   Se deja para esta etapa a propósito: una migración sin uso real solo agrega riesgo a los datos actuales.
3. Cuentas: registro con correo, creación de equipo (líder) y unión con código de invitación (ejecutivos).
4. Sincronización ejecutivo → nube (cola si no hay internet; la base local sigue siendo la fuente de verdad).
5. Pestaña **Equipo** de la líder: resumen por ejecutivo (atrasados, cierres, último movimiento) y detalle al tocar.
6. Avisos de la líder → ejecutivo: documento en `alerts`; el teléfono lo revisa cada ~15 min (WorkManager).

## Pruebas obligatorias de las reglas (simulador de Firebase)
Con tres usuarios: líder `L`, ejecutivos `A` y `B`, equipo `T`.
- `A` lee `T/members/A/clients/*` → **permitido**.
- `A` lee `T/members/B/clients/*` → **denegado**.
- `L` lee `T/members/A/clients/*` y `T/members/B/clients/*` → **permitido**.
- `L` escribe en `T/members/A/clients/*` → **denegado**.
- `A` lee `T/private/join` → **denegado**.
- `A` crea su miembro con código incorrecto → **denegado**; con el correcto y `role: 'exec'` → **permitido**.
- `A` crea su miembro con `role: 'leader'` → **denegado**.
- `A` cambia su propio `role` → **denegado**.
- `L` borra `T/members/A`; luego `A` lee y escribe lo suyo → **denegado**.
- Usuario sin sesión → todo **denegado**.

## Datos personales
Subir nombres y teléfonos de clientes a un servicio de Google necesita la autorización de la empresa.
Considera sincronizar solo lo necesario (por ejemplo, sin teléfono ni notas) si la empresa lo prefiere.
