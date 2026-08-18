import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Bump both values for every GitHub Release.
// versionCode must increase so Android and Obtainium treat the APK as an update.
val appVersionCode = 1
val appVersionName = "1.0.0"

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val releaseStoreFile = if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    val storePath = keystoreProperties.getProperty("storeFile").orEmpty()
    if (storePath.isNotBlank()) rootProject.file(storePath) else null
} else {
    null
}
val releaseSigningReady = releaseStoreFile != null && releaseStoreFile.exists()

android {
    namespace = "com.sinura.personaltrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sinura.personaltrainer"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "PersonalTrainer-$appVersionName")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword").orEmpty()
                keyAlias = keystoreProperties.getProperty("keyAlias").orEmpty()
                keyPassword = keystoreProperties.getProperty("keyPassword").orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            // Debug signing stays on the default debug keystore.
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Copy the release APK to PersonalTrainer-<version>.apk for GitHub / Obtainium.
// No ABI or density splits — this stays a single standard APK.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val produced = apkDir.listFiles()
            ?.filter { it.isFile && it.extension == "apk" && "unsigned" !in it.name }
            ?.maxByOrNull { it.lastModified() }
            ?: return@doLast
        val named = apkDir.resolve("PersonalTrainer-$appVersionName.apk")
        if (produced.canonicalPath != named.canonicalPath) {
            produced.copyTo(named, overwrite = true)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.play.services.auth)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
