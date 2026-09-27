<p align="center">
  <img src="composeApp/src/desktopMain/resources/icon.png" width="64">
</p>

<h1 align="center">Luma Music</h1>

<p align="center">
  <a href="https://discord.gg/YymhhUy4fH">
    <img src="https://img.shields.io/discord/1419649386656563324?style=for-the-badge&logo=discord&label=Discord&color=5865F2" alt="Discord">
  </a>
</p>

Cliente de YouTube Music para escritorio (Windows/Linux/macOS) y Android, construido con **Kotlin**.

Luma Music nació como una versión de escritorio de [OpenTune](https://github.com/Arturo254/OpenTune) (de Arturo254), reutilizando su módulo `innertube` para comunicarse con YouTube Music. El repositorio original de OpenTune fue dado de baja de GitHub por un DMCA; la versión de Android de Luma Music (carpeta `android/`) está basada en el fork activo y mantenido [Erorr40/OpenTune](https://github.com/Erorr40/OpenTune), con el nombre, ícono y textos adaptados a Luma Music.

## 📸 Capturas

![Inicio](composeApp/src/desktopMain/resources/explore.png)

## Funciones

- Navega por el inicio, busca y explora contenido (estados de ánimo y géneros)
- Reproduce canciones y álbumes con cola completa (secuencial, aleatorio, bucle)
- Indicador de ecualizador animado en la canción en reproducción
- Descarga canciones para escucharlas sin conexión
- Biblioteca sin conexión: canciones favoritas, descargas y caché de audio
- 21 paletas de colores + tema AMOLED negro puro
- Modo de pantalla completa
- Configuración persistente, caché y biblioteca (guardadas en `~/.opentune/`)

## Requisitos

- JDK 21
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (para descargar audio)
- [ffmpeg](https://ffmpeg.org/) (para convertir audio)

## Compilar y ejecutar

```bash
./gradlew composeApp:run
```

En Windows, usa `gradlew.bat`:

```bat
gradlew.bat composeApp:run
```

Compila un paquete distribuible:

```bash
./gradlew composeApp:packageDistributionForCurrentOS
```

## Estructura del proyecto

- `composeApp/` — la UI de escritorio y el reproductor (Compose Desktop)
- `innertube/` — cliente de la API de YouTube Music para el escritorio (del proyecto original OpenTune)
- `android/` — la app de Android, un proyecto Gradle **independiente** del de escritorio (tiene su propio `settings.gradle.kts`, `gradlew` y catálogo de versiones), basado en [Erorr40/OpenTune](https://github.com/Erorr40/OpenTune). Se compila por separado:

  ```bash
  cd android
  ./gradlew :app:assembleUniversalDebug
  ```

  En Windows: `cd android && gradlew.bat :app:assembleUniversalDebug`. El APK queda en `android/app/build/outputs/apk/`.

## Datos y privacidad

Los ajustes, canciones favoritas, metadatos de descargas y caché de audio se guardan localmente en `~/.opentune/`.

## Firma de código

Los lanzamientos de Windows se firman con un certificado proporcionado por [Necessary Code Signing](https://sign.necessary.nu). Esto elimina los avisos de SmartScreen cuando los usuarios descargan Luma Music.

Firma de código gratuita proporcionada por Necessary Code Signing.

Ver [CODESIGN.md](CODESIGN.md) para más detalles.

## Licencia

GPL-3.0. El módulo `innertube` de escritorio proviene originalmente de [OpenTune](https://github.com/Arturo254/OpenTune) (GPL-3.0). La carpeta `android/` proviene de [Erorr40/OpenTune](https://github.com/Erorr40/OpenTune) (GPL-3.0), un fork activo del proyecto original.
