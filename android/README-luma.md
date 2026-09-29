# Estado de la app de Android — v3: se usa OpenTune completo

Desde este cambio, la app de Android que se lanza es la **app completa
original de OpenTune** (`app/src/main/kotlin/com/arturo254/opentune/`),
la misma que ya vivía en este repo sin usarse desde el v1. Es el mismo
motor/UI que el fork [Arturo254/OpenTune](https://github.com/Arturo254/OpenTune)
del que salió también la versión de PC — por eso ahora el look de Android
coincide con el de escritorio (mismo `Theme.kt`, mismos componentes de
reproductor, letras, cola, etc.).

## Qué cambió

- `AndroidManifest.xml`: la `<application>` y la `MainActivity` vuelven a
  apuntar a `com.arturo254.opentune.App` / `.MainActivity` (antes apuntaban
  a la mini app `com.lumamusic.android`). Se restauraron también los
  componentes que la app completa necesita para andar:
  - `DebugActivity` (pantalla de crash, corre en el proceso `:crash`).
  - `MusicService` (el `MediaLibraryService` real de Media3, con cola,
    ecualizador, crossfade, Discord RPC, etc. — reemplaza al
    `PlaybackService` minimalista del v1/v2).
  - `ExoDownloadService` (descargas para escuchar offline).
  - El receiver del widget de pantalla de inicio (`OpenTunePlayerWidgetReceiver`,
    un solo Glance widget responsive: compacto/grande/vinilo/reproductor
    según el tamaño).
  - El `FileProvider` (para compartir imágenes de letras, etc.).
  - Permisos: se agregaron los que la app completa necesita y el v1/v2 no
    usaba — lectura de música local (`READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`),
    `BLUETOOTH_CONNECT` (auto-inicio por Bluetooth), `VIBRATE` (haptics),
    `FOREGROUND_SERVICE_DATA_SYNC` (descargas).
- `app_name` ya decía "Luma Music" (`res/values/app_name.xml`), así que el
  nombre de la app no cambia.

## Qué queda sin usar (pero sigue compilando)

`app/src/main/kotlin/com/lumamusic/android/` — la interfaz mini que se
armó a mano en el v1/v2 (Home/Buscar/Favoritos/cola con reproductor
propio). Ya no se lanza. Se puede borrar más adelante si no hace falta
como referencia; el puente de archivos de esta sesión no tiene terminal
en tu PC para borrarla directamente.

## Qué falta verificar

Esto se armó reconstruyendo el `AndroidManifest.xml` a partir del código
fuente (GitHub estaba bloqueado para bajar el manifest original tal
cual), así que aunque el módulo ya compilaba antes de este cambio, puede
que falte algún permiso o declaración puntual que solo se note al
compilar/correr de verdad. Si compila o corre y tira algún error
puntual (falta un permiso, un componente no declarado, etc.), pasámelo
tal cual sale y lo ajustamos — es la misma dinámica que veníamos usando
con los otros errores de CI.
