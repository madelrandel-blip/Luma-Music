# Estado de la app de Android — interfaz propia (v2)

Esta carpeta tiene **dos árboles de código Kotlin conviviendo**:

- `app/src/main/kotlin/com/arturo254/opentune/` — el código original portado de
  [Erorr40/OpenTune](https://github.com/Erorr40/OpenTune). Ya **no se lanza**
  (no está referenciado en `AndroidManifest.xml`), pero sigue compilando: de
  ahí sacamos el motor de reproducción real.
- `app/src/main/kotlin/com/lumamusic/android/` — la app nueva, con interfaz
  propia de Luma Music. **Esto es lo que corre hoy.**

## Qué se reutilizó del proyecto original (el "motor")

- **`innertube/`** (módulo aparte, sin cambios): el cliente de YouTube Music —
  búsqueda, metadatos, home feed, resolución de streams.
- **`YTPlayerUtils.kt`** y **`StreamClientUtils.kt`**: copiados a
  `com.lumamusic.android.playback` / `.utils` — resuelven la URL de audio real
  de un video de YouTube probando varios "clientes" de la API hasta que uno
  funciona. Es la pieza más delicada del proyecto original y no tenía
  dependencia de base de datos, así que se copió casi textual.

## Qué es nuevo

- `App.kt`, `MainActivity.kt`, `PlayerViewModel.kt`: interfaz propia (Compose),
  con el look rosa de escritorio (`0xFFED5564`).
- `MainActivity.kt` arma un layout de 3 pestañas (Inicio / Buscar / Favoritos)
  con mini-reproductor persistente y una pantalla de reproductor completa.
- `playback/PlaybackService.kt`: un `MediaSessionService` de Media3 minimalista
  (sin caché en disco, sin descargas, sin base de datos) que usa
  `YTPlayerUtils` para resolver el stream de cada canción al vuelo.
- `PlayerViewModel.kt`: ahora maneja una **cola real** (no solo una canción
  suelta) — `playQueue(songs, startIndex)` carga toda una lista (resultados de
  búsqueda, sección de Inicio, o favoritos) como cola reproducible, con
  `next()`/`previous()` delegando en los comandos nativos de Media3
  (`seekToNextMediaItem`/`seekToPreviousMediaItem`), y hace polling de
  posición/duración cada 500ms para alimentar la barra de progreso del
  reproductor completo.
- `data/SearchRepository.kt`: wrapper fino sobre `YouTube.search(...)`.
- `data/HomeRepository.kt`: wrapper fino sobre `YouTube.home()` — feed de
  inicio de YouTube Music. La pantalla de Inicio (`ui/screens/HomeScreen.kt`)
  solo muestra las entradas de tipo canción (`SongItem`) de cada sección;
  álbumes/playlists/artistas del feed se descartan por ahora porque navegar a
  esas páginas necesita pantallas propias que quedan fuera de este alcance.
- `data/FavoritesStore.kt`: favoritos persistidos localmente con
  `DataStore Preferences` (una lista de `SongItem` codificada en JSON, ya que
  `SongItem` es `@Serializable`) — **a propósito sin Room**, siguiendo la
  decisión original de no reinstaurar la base de datos vieja.
- `ui/components/SongComponents.kt`: `SongRow` y `MiniPlayerBar` compartidos
  entre las tres pestañas.
- `ui/screens/`: `SearchScreen.kt`, `HomeScreen.kt`, `FavoritesScreen.kt` y
  `FullPlayerScreen.kt` (pantalla grande con carátula, barra de progreso
  arrastrable, play/pause/siguiente/anterior y botón de favorito).

## Alcance actual

Buscar / Inicio / Favoritos, los tres reproducen como **cola real** (con
siguiente/anterior), mini-reproductor persistente, y pantalla de reproductor
completa con seek bar y favoritos. **Todavía no hay**: pantallas de
álbum/playlist/artista (navegación dentro del feed de Inicio), reordenar la
cola a mano, ni ajustes.

## Por qué no se borró el código viejo

El puente de archivos de esta sesión no tiene acceso a una terminal en tu PC,
así que no pude borrar carpetas. El árbol `com/arturo254/opentune/` sigue ahí,
sin usarse, pesando en el build pero sin afectar el funcionamiento de la app
nueva. Se puede limpiar más adelante (a mano, o cuando tengamos terminal
disponible) sin apuro.
