# PortaAlerta · instrucciones para el asistente

Antes de tocar código lee `HANDOFF.md`: explica qué es la app, las decisiones que no se cambian y dónde está cada cosa.

## Reglas duras
- Los datos se quedan en el teléfono. No agregar `INTERNET`, analytics, Firebase activo ni llamadas de red.
- No commitear claves, keystores, contraseñas ni `google-services.json`.
- Datos de ejemplo únicamente en capturas, PNG y material de presentación.
- Respetar las guardas de la CI (sin INTERNET, `allowBackup=false`, no depurable).

## Flujo de trabajo
- Trabajar en la rama `ccr-22f0ce33-7bglze`. Push con `git push -u origin <rama>`.
- No hay Android SDK local: validar con la CI (`gh run list`, `gh run view --log-failed`).
- Para capturas reales, incluir `[capturas]` en el mensaje del commit.
- No crear PRs salvo que el usuario lo pida.

## Estilo
- Respuestas al usuario en español.
- Textos de la app en español de Chile, tuteo neutro (sin voseo).
- Al cambiar la UI, correr o actualizar las pruebas de capturas en `screens/`.
