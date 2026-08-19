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

    sourceSets {
        // Lets a future MigrationTestHelper read the exported schemas as test assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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

// Room exports a JSON schema per database version into app/schemas/.
// These files are committed: they are the substrate for hand-written migrations and
// for MigrationTestHelper, and the only record of what shipped on the owner's phone.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Copy the release APK to PersonalTrainer-<version>.apk for GitHub / Obtainium.
// No ABI or density splits — this stays a single standard APK.
//
// Configuration-cache safety: the doLast action must not capture the build script
// object or the Project. Everything it needs is hoisted into locals of this run{}
// scope first — a Provider<Directory> and a String, both serializable — so the
// action closes over values only. Referencing `layout` or a script-level `val`
// directly inside doLast fails the build with "cannot serialize Gradle script
// object references".
run {
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/release")
    val releaseApkFileName = "PersonalTrainer-$appVersionName.apk"
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        doLast {
            val apkDir = releaseApkDir.get().asFile
            val produced = apkDir.listFiles()
                ?.filter { it.isFile && it.extension == "apk" && "unsigned" !in it.name }
                ?.maxByOrNull { it.lastModified() }
                ?: return@doLast
            val named = apkDir.resolve(releaseApkFileName)
            if (produced.canonicalPath != named.canonicalPath) {
                produced.copyTo(named, overwrite = true)
            }
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
