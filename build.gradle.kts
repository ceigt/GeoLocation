// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Kotlin 2.2 needs R8 8.10.21 or newer; the AGP 8.7 bundled compiler predates it.
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath("com.android.tools:r8:8.10.21") }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
