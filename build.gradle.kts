plugins {
    alias(libs.plugins.kotlin.multiplatform) apply (false)
    alias(libs.plugins.kotlin.jvm) apply (false)
    alias(libs.plugins.kotlin.serialization) apply (false)
    alias(libs.plugins.compose.multiplatform) apply (false)
    alias(libs.plugins.compose.compiler) apply (false)
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
