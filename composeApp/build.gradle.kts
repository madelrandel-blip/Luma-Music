import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            // compose.desktop.common ships the Compose Desktop API classes but NO native Skiko
            // runtime at all - on any OS. That's fine for a library module, but for the actual
            // application (this module, run via `composeApp:run` and packaged for distribution)
            // it caused "Cannot find libskiko-<os>-<arch>.so.sha256, proper native dependency
            // missing" on every OS that didn't also get an explicit runtime dependency (Windows
            // worked here only because of a separate hardcoded skiko-awt-runtime-windows-x64
            // dependency below; Linux/macOS got nothing and crashed at startup).
            // compose.desktop.currentOs pulls in compose.desktop.common's API PLUS the correct
            // native Skiko runtime for whichever OS is currently running Gradle, at a version
            // that matches this project's Compose Multiplatform version - this is the officially
            // recommended way to depend on Compose Desktop for a runnable/packaged application,
            // and it works out of the box on Windows, Linux and macOS without any OS-conditional
            // logic (see https://github.com/JetBrains/compose-multiplatform, "desktop" docs).
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.foundation)
            implementation(compose.components.resources)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            implementation(libs.kotlinx.serialization.json)

            implementation("io.coil-kt.coil3:coil-compose:3.4.0")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
            implementation("org.xerial:sqlite-jdbc:3.47.1.0")
            implementation("net.java.dev.jna:jna:5.13.0")
            implementation("net.java.dev.jna:jna-platform:5.13.0")
            implementation("me.friwi:jcefmaven:146.0.10")
            implementation("com.github.MinnDevelopment:java-discord-rpc:2.0.2") {
                exclude(group = "club.minnced", module = "discord-rpc-release")
            }

            implementation(project(":innertube"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.arturo254.opentune.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LumaMusic"
            packageVersion = "4.0.0"
            description = "Luma Music - YouTube Music desktop client"
            vendor = "Luma Music"

            appResourcesRootDir.set(project.file("src/desktopMain/extraResources"))

            windows {
                menuGroup = "Luma Music"
                perUserInstall = true
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }

            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }

        jvmArgs += listOf(
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200",
            "--add-exports=java.base/java.lang=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(true)
    }
}
