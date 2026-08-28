import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    jacoco
}

// Bump both values for every signed GitHub Release (gym-floor Temper).
// versionCode must increase so Android and Obtainium treat the APK as an update.
val appVersionCode = 1
val appVersionName = "1.0.0"
// Bump this for every Temper Debug Obtainium drop. Gym-floor stays on
// appVersionCode. The two apps are different ids, so they do not share
// Android's upgrade counter. Obtainium will not offer an update if this
// stays put — both previous debug-live APKs were versionCode 1.
val debugLiveCode = 4

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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sinura.personaltrainer"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "PersonalTrainer-$appVersionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // Lets MigrationTestHelper read the exported schemas as test assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").java.srcDir("$projectDir/src/sharedTest/java")
        // Same substrate for the JVM (Robolectric) migration lane.
        getByName("test").assets.srcDir("$projectDir/schemas")
        getByName("test").java.srcDir("$projectDir/src/sharedTest/java")
    }

    lint {
        // Existing findings live in lint-baseline.xml. A new warning is an
        // error. P4.6 still owns cleaning the baseline to zero.
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = false
        // AGP 8.9.2 is the official compileSdk-36 pair (P4.1). The "newer
        // stable is 9.x" nag is not a defect; check-sdk-target.py is the
        // ratchet. UseKtx is a style detector: the KTX edit() inline
        // inflates Robolectric-blind timer bytecode and would drop the
        // 18% floor. P4.6 owns zero-warning cleanup.
        disable += setOf("AndroidGradlePluginVersion", "UseKtx", "GradleDependency")
    }

    testOptions {
        unitTests {
            // Robolectric: serves the app's (and test source set's) assets —
            // MigrationTestHelper reads app/schemas from there.
            isIncludeAndroidResources = true
        }
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
            // A different id so Run ▶ cannot open the release history. The next
            // debug install is a new app; uninstall the old debug (same id as
            // release, debug-signed) when you see two Temper icons.
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(debugLiveCode)
            output.versionName.set("$appVersionName+debug.$debugLiveCode")
        }
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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.room.testing)
}

val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Impl.class",
    "**/*_Impl$*.class",
    "**/*_Factory.class",
    "**/*ComposableSingletons*.*",
    "**/databinding/**",
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Unit-test coverage XML/HTML for tools/check-coverage.py"
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    val javaTree = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(jacocoExcludes)
    }
    val kotlinTree = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(jacocoExcludes)
    }
    classDirectories.setFrom(javaTree, kotlinTree)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
        layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"),
    )
}
