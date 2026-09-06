import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("com.android.application") apply false
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)

    // Include the Test API
    testImplementation(compose.desktop.uiTestJUnit4)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ControlRemotoPC"
            packageVersion = "1.0.0"
            windows {
                menuGroup = "Control Remoto PC"
                upgradeUuid = "8f14e45f-ceea-467e-956e-d7b8d5b4b1e0"
            }
        }
    }
}