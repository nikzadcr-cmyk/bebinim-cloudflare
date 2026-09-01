import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.app.hamfilm"
version = "1.5.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.json:json:20240303")

    // Video playback — requires libvlc at runtime (Fedora: sudo dnf install vlc)
    implementation("uk.co.caprica:vlcj:4.8.3")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
    }
}

compose.desktop {
    application {
        mainClass = "com.app.hamfilm.desktop.MainKt"

        // GPU rendering for the run task AND the packaged launchers (RPM/AppImage/portable)
        jvmArgs("-Dskiko.renderApi=OPENGL", "-Dskiko.fallback.renderApi=SOFTWARE")

        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "HamFilm"
            packageVersion = "1.5.0"
            description = "HamFilm - Watch Together"
            vendor = "HamFilm"
            licenseFile.set(project.file("../README.md"))

            linux {
                iconFile.set(project.file("src/main/resources/hamfilm/icon.png"))
                menuGroup = "hamfilm"
                rpmLicenseType = "GPL-3.0"
                shortcut = true
            }

            // bundled JRE — users do NOT need Java installed
            includeAllModules = true
        }
    }
}
