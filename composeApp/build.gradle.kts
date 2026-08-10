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
            implementation(compose.desktop.common)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.foundation)
            implementation(compose.components.resources)

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            implementation(libs.kotlinx.serialization.json)

            implementation("io.coil-kt.coil3:coil-compose:3.4.0")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
            implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.144.6")

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
            packageVersion = "1.0.0"
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
