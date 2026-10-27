import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Nucleus extends org.jetbrains.compose: nucleus.application { } replaces compose.desktop.application { }.
    alias(libs.plugins.nucleus)
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
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jna)

                // Nucleus: nucleusApplication { }, DecoratedWindow, and the Tao (Rust, AWT-free) window backend.
                implementation(libs.nucleus.application)
                implementation(libs.nucleus.decorated.window.tao)
                implementation(libs.nucleus.darkmode.detector)
                implementation(libs.nucleus.system.color)

                // Native notifications: the platform module must be on the runtime classpath.
                implementation(libs.nucleus.notification.common)
                runtimeOnly(libs.nucleus.notification.linux)
                runtimeOnly(libs.nucleus.notification.macos)
                runtimeOnly(libs.nucleus.notification.windows)

                // Native system tray and menu (Compose's own Tray is AWT-based and does not work on Tao).
                implementation(libs.nucleus.tray)

                // One native design system per OS. All three are on the classpath; the right one is picked at runtime.
                implementation(libs.macos.ui) // macOS 26 "Liquid Glass"
                implementation(libs.fluent) // Windows 11 Fluent, Mica and Acrylic
                implementation(libs.fluent.icons.extended)
                implementation(libs.fluent.decorated.window)
                implementation(libs.yaru) // Ubuntu / GNOME: Yaru, GTK and libadwaita flavours
                implementation(libs.yaru.decorated.window)
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

// Required for Linux .deb packaging. Set -Pwallpix.homepage=... and -Pwallpix.maintainer=... for a real release.
val appHomepage = providers.gradleProperty("wallpix.homepage").orElse("https://example.com/wallpix").get()
val appMaintainer = providers.gradleProperty("wallpix.maintainer").orElse("WallPix <wallpix@example.com>").get()

nucleus.application {
    mainClass = "app.wallpix.MainKt"

    nativeDistributions {
        // One installer set per host OS: packaging cannot cross-compile, so CI builds on each OS.
        targetFormats(
            *when {
                isMac -> arrayOf(TargetFormat.Dmg)
                isWindows -> arrayOf(TargetFormat.Nsis)
                else -> arrayOf(TargetFormat.Deb, TargetFormat.AppImage)
            },
        )
        packageName = "WallPix"
        packageVersion = "1.0.0"
        description = "Daily Bing wallpapers from Peapix, with favourites and slideshow"
        vendor = "WallPix"
        homepage = appHomepage
        licenseFile.set(rootProject.file("LICENSE"))

        // java.net.http, TLS and imaging all need modules the minimal jlink set may leave out.
        includeAllModules = true
        // Drop native libraries of the other operating systems from dependency JARs.
        cleanupNativeLibs = true

        macOS {
            bundleID = "app.wallpix"
            dockName = "WallPix"
            minimumSystemVersion = "12.0"
            // Link the launcher against the macOS 26 SDK so Liquid Glass renders.
            macOsSdkVersion = "26.0"
            iconFile.set(project.file("packaging/icon.icns"))
        }
        windows {
            menu = true
            perUserInstall = true
            upgradeUuid = "6f1e3b0e-5a0b-4c1e-9a55-3f7f2a6d8c11"
            iconFile.set(project.file("packaging/icon.ico"))
            nsis {
                oneClick = false
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
            }
        }
        linux {
            packageName = "wallpix"
            menuGroup = "Graphics"
            appCategory = "Graphics"
            debMaintainer = appMaintainer
            iconFile.set(project.file("packaging/icon.png"))
        }
    }
}
