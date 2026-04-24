plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Declaramos a versão exata do KSP para o Kotlin 2.0.21 aqui no topo
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}