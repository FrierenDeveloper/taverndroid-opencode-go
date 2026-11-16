# Logos de las compañías

La app muestra una insignia de color para cada compañía. Para usar el **logo oficial**, copia la imagen
a `app/src/main/res/drawable-nodpi/` con este nombre y la app la usa sola (no hay que tocar código):

| Compañía | Archivo |
|---|---|
| Entel | `logo_entel.png` |
| Movistar | `logo_movistar.png` |
| Claro | `logo_claro.png` |
| WOM | `logo_wom.png` |
| Virgin | `logo_virgin.png` |
| Mundo | `logo_mundo.png` |
| VTR | `logo_vtr.png` |
| Simple | `logo_simple.png` |

Recomendaciones:
- PNG (o WebP) **cuadrado, 512×512 px**, con el logo centrado y fondo transparente o blanco. La app lo muestra sobre un fondo blanco con margen y sin recortarlo.
- Nombres en minúsculas, sin espacios ni tildes (regla de Android para recursos).
- Usa los archivos oficiales de cada compañía (kit de marca o prensa). Los logos son marcas registradas y suelen cambiar; no los descargues de sitios no oficiales.
- Para una compañía nueva, agrega su nombre y clave en `Carriers.all` (`ui/Carriers.kt`) y su `logo_<clave>.png`.
