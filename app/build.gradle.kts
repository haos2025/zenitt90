import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply { if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile)) }
val gitCommitHashProvider: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short=7", "HEAD"); isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifBlank { "unknown" } }.orElse("unknown")

android {
    namespace = "com.platinum.ott"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.platinum.ott"
        minSdk = 26; targetSdk = 35
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "3.0.0-dev"
        buildConfigField("String", "GIT_COMMIT", "\"${gitCommitHashProvider.get()}\"")
        val tmdbKeyValue = providers.environmentVariable("TMDB_API_KEY").orNull ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKeyValue}\"")
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }
    signingConfigs {
        create("release") {
            val envKeystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
            val envKeyAlias = providers.environmentVariable("KEY_ALIAS").orNull
            val envKeyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            val envStorePassword = providers.environmentVariable("STORE_PASSWORD").orNull
            when {
                envKeystorePath != null && envKeyAlias != null -> {
                    storeFile = file(envKeystorePath); keyAlias = envKeyAlias
                    keyPassword = envKeyPassword; storePassword = envStorePassword
                }
                keystoreProps.getProperty("storeFile") != null -> {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                    storePassword = keystoreProps.getProperty("storePassword")
                }
            }
        }
        getByName("debug") {
            val committed = rootProject.file("debug.keystore")
            if (committed.exists()) { storeFile = committed; storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" }
        }
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); val rc = signingConfigs.findByName("release"); if (rc?.storeFile != null) signingConfig = rc }
        debug { applicationIdSuffix = ".debug" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}"; excludes += "/META-INF/*.kotlin_module" } }
    lint { abortOnError = false; checkReleaseBuilds = true; disable += setOf("MissingTranslation", "ExtraTranslation") }
}

detekt { config.setFrom(files("$rootDir/config/detekt/detekt.yml")); buildUponDefaultConfig = true }
ktlint { version.set("1.3.1"); android.set(true); ignoreFailures.set(false) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.quickjs.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)
    // П3: QR
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    // П4, П6, П7: WorkManager
    implementation(libs.work.runtime.ktx)
    // П11: LeakCanary
    debugImplementation(libs.leakcanary)
    // П11: Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
