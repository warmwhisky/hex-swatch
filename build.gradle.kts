// Build file for the Hex Swatch plugin.
//   gradlew buildPlugin   ->  build/distributions/hex-swatch-<version>.zip
//   gradlew verifyPlugin  ->  JetBrains' compatibility check against the IDE versions listed below
// Compiles against the oldest supported IDE (2023.3) so it can't use anything newer by accident.
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "org.gtdb"
version = "1.5.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.8")
    }
}

// 2023.3 runs on Java 17
tasks.withType<JavaCompile> { options.release.set(17) }

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.PhpStorm, "2023.3.8")
            create(IntelliJPlatformType.PhpStorm, "2025.1")
            // Also the PhpStorm installed on this Windows machine, if there is one (Toolbox-free install path).
            val installed = file("${System.getenv("LOCALAPPDATA") ?: ""}/Programs/PhpStorm")
            if (installed.exists()) local(installed)
        }
    }
}
