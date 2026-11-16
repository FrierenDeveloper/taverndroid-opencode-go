# Logos de las compañías

La app muestra el **logo oficial** de cada compañía cuando existe el archivo `logo_<clave>.png` en
`app/src/main/res/drawable-nodpi/`. Si no existe, muestra una insignia de color con el nombre.

| Compañía | Archivo | Estado |
|---|---|---|
| Entel | `logo_entel.png` | incluido |
| Movistar | `logo_movistar.png` | incluido |
| Claro | `logo_claro.png` | incluido |
| WOM | `logo_wom.png` | incluido |
| VTR | `logo_vtr.png` | incluido (solo el símbolo, sin el texto "vtr.com") |
| GTD | `logo_gtd.png` | incluido |
| Virgin | `logo_virgin.png` | pendiente (insignia de color) |
| Mundo | `logo_mundo.png` | pendiente (insignia de color) |
| Simple | `logo_simple.png` | pendiente (insignia de color) |

Cómo se prepararon: cada logo se centra en un lienzo cuadrado de 512×512 px, con fondo transparente y sin deformarlo.
La app lo muestra sobre un cuadrado blanco redondeado, con margen.

Para agregar o reemplazar uno:
- PNG (o WebP) cuadrado de 512×512 px, logo centrado, fondo transparente o blanco.
- Nombre en minúsculas, sin espacios ni tildes.
- Usa archivos oficiales de cada compañía (kit de marca o prensa). Los logos son marcas registradas y suelen cambiar.
- Para una compañía nueva, agrega su nombre y clave en `Carriers.all` (`ui/Carriers.kt`).
