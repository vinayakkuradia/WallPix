import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val osName: String = System.getProperty("os.name").lowercase()
val isMac = "mac" in osName
val isWindows = !isMac && "win" in osName

compose.desktop {
    application {
        mainClass = "app.wallpix.MainKt"

        nativeDistributions {
            // One installer format per host OS: jpackage cannot cross-compile, so CI builds on each OS.
            targetFormats(
                *when {
                    isMac -> arrayOf(TargetFormat.Dmg)
                    isWindows -> arrayOf(TargetFormat.Msi)
                    else -> arrayOf(TargetFormat.Deb)
                }
            )
            packageName = "WallPix"
            packageVersion = "1.0.0"
            description = "Daily Bing wallpapers from Peapix, with favourites and slideshow"
            vendor = "WallPix"
            licenseFile.set(rootProject.file("LICENSE"))
            // java.net.http (HTTP client) is not in the default jlink set; include everything for safety.
            includeAllModules = true

            macOS {
                bundleID = "app.wallpix"
                iconFile.set(project.file("packaging/icon.icns"))
            }
            windows {
                menuGroup = "WallPix"
                menu = true
                shortcut = true
                upgradeUuid = "6f1e3b0e-5a0b-4c1e-9a55-3f7f2a6d8c11"
                iconFile.set(project.file("packaging/icon.ico"))
            }
            linux {
                packageName = "wallpix"
                menuGroup = "Graphics"
                appCategory = "Graphics"
                shortcut = true
                iconFile.set(project.file("packaging/icon.png"))
            }
        }
    }
}
