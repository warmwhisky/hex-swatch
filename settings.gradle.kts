// foojay: lets Gradle download the Java 17 that compiling for IDE 2023.3 needs, if it isn't installed.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "hex-swatch"
