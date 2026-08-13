<p align="center">
  <img src="composeApp/src/desktopMain/resources/icon.png" width="64">
</p>

<h1 align="center">Luma Music</h1>

<p align="center">
  <a href="https://discord.gg/YymhhUy4fH">
    <img src="https://img.shields.io/discord/1419649386656563324?style=for-the-badge&logo=discord&label=Discord&color=5865F2" alt="Discord">
  </a>
</p>

Cliente de escritorio para YouTube Music, construido con **Kotlin** y **Compose Multiplatform (Compose Desktop)**.

Luma Music es una versión de escritorio de la app de Android [OpenTune](https://github.com/Arturo254/OpenTune) (de Arturo254), reutilizando su módulo `innertube` para comunicarse con YouTube Music.

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
