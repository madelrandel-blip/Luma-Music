# Estado de la app de Android — interfaz propia (v1)

Esta carpeta tiene **dos árboles de código Kotlin conviviendo**:

- `app/src/main/kotlin/com/arturo254/opentune/` — el código original portado de
  [Erorr40/OpenTune](https://github.com/Erorr40/OpenTune). Ya **no se lanza**
  (no está referenciado en `AndroidManifest.xml`), pero sigue compilando: de
  ahí sacamos el motor de reproducción real.
- `app/src/main/kotlin/com/lumamusic/android/` — la app nueva, con interfaz
  propia de Luma Music. **Esto es lo que corre hoy.**

## Qué se reutilizó del proyecto original (el "motor")

- **`innertube/`** (módulo aparte, sin cambios): el cliente de YouTube Music —
  búsqueda, metadatos, resolución de streams.
- **`YTPlayerUtils.kt`** y **`StreamClientUtils.kt`**: copiados a
  `com.lumamusic.android.playback` / `.utils` — resuelven la URL de audio real
  de un video de YouTube probando varios "clientes" de la API hasta que uno
  funciona. Es la pieza más delicada del proyecto original y no tenía
  dependencia de base de datos, así que se copió casi textual.

## Qué es nuevo

- `App.kt`, `MainActivity.kt`, `PlayerViewModel.kt`: interfaz propia (Compose),
  con el look rosa de escritorio (`0xFFED5564`).
- `playback/PlaybackService.kt`: un `MediaSessionService` de Media3 minimalista
  (sin caché en disco, sin descargas, sin base de datos) que usa
  `YTPlayerUtils` para resolver el stream de cada canción al vuelo.
- `data/SearchRepository.kt`: wrapper fino sobre `YouTube.search(...)`.

## Alcance de esta v1

Buscar canciones → tocar una → se reproduce, con mini-reproductor abajo
(play/pause). **No hay todavía**: biblioteca, playlists, cola completa,
pantalla de reproductor grande, favoritos, ni ajustes — se van agregando de a
poco sobre esta misma base, ahora que el motor de reproducción está
validado con la interfaz nueva.

## Por qué no se borró el código viejo

El puente de archivos de esta sesión no tiene acceso a una terminal en tu PC,
así que no pude borrar carpetas. El árbol `com/arturo254/opentune/` sigue ahí,
sin usarse, pesando en el build pero sin afectar el funcionamiento de la app
nueva. Se puede limpiar más adelante (a mano, o cuando tengamos terminal
disponible) sin apuro.
