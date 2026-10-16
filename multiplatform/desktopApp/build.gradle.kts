plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
    jvmToolchain(17)

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.1")
            implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.material:material:1.12.1")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.1")
            implementation("org.jetbrains.compose.animation:animation:1.12.1")
            implementation("org.jetbrains.compose.runtime:runtime:1.12.1")
            implementation(project(":engine"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            val javafxOs = javafxClassifier()
            implementation("org.openjfx:javafx-base:21.0.5:$javafxOs")
            implementation("org.openjfx:javafx-graphics:21.0.5:$javafxOs")
            implementation("org.openjfx:javafx-media:21.0.5:$javafxOs")
            implementation("org.openjfx:javafx-swing:21.0.5:$javafxOs")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.miaadrajabi.span.MainKt"
        jvmArgs += listOf("-Dapple.awt.application.name=Fetch")
        nativeDistributions {
            appResourcesRootDir.set(layout.buildDirectory.dir("media-resources"))
            modules("java.net.http", "jdk.unsupported", "jdk.crypto.ec", "java.sql", "java.management", "java.naming")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "Fetch"
            packageVersion = "1.0.0"
            description = "Download files on macOS, Windows, and Linux."
            vendor = "Miaad Rajabi"
            macOS {
                bundleID = "com.miaadrajabi.fetch"
                iconFile.set(project.file("icons/Fetch.icns"))
            }
            windows {
                menuGroup = "Fetch"
                shortcut = true
                upgradeUuid = "c576a738-715f-45f9-914b-bf8ec55ca3e2"
                iconFile.set(project.file("icons/Fetch.ico"))
            }
            linux {
                iconFile.set(project.file("icons/Fetch.png"))
            }
        }
    }
}

fun javafxClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") && (arch.contains("aarch") || arch.contains("arm")) -> "mac-aarch64"
        os.contains("mac") -> "mac"
        os.contains("win") -> "win"
        arch.contains("aarch") || arch.contains("arm") -> "linux-aarch64"
        else -> "linux"
    }
}

// Packages are self-contained: do not silently ship a non-working media studio.
tasks.matching { it.name == "createDistributable" }.configureEach {
    doFirst {
        val extension = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        listOf("yt-dlp", "deno", "ffmpeg").forEach { name ->
            check(layout.buildDirectory.file("media-resources/common/media-tools/$name$extension").get().asFile.isFile) {
                "Missing media helper $name. Run python3 tools/media/prepare.py before packaging."
            }
        }
    }
}
