plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.versions)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "cz.kotox.crypto.sdk"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    // Tell the plugin to only check for stable Gradle versions
    gradleReleaseChannel = "current"
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

// 1. Configure Spotless (The Formatter)
spotless {
    kotlin {
        target("**/*.kt")
        // Use the official Kotlin style guide
        ktlint()
        // Or strictly follow standard .editorconfig
        // ktlint().setEditorConfigPath("$projectDir/.editorconfig")

        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// 2. Configure Detekt (The Linter)
detekt {
    buildUponDefaultConfig = true // Use default rules + your overrides
    allRules = false // true if you want to be very strict
    // config.setFrom(files("$projectDir/config/detekt/detekt.yml")) // Your config file
}
