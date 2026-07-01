plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val configuredKeystorePath = System.getenv("KEYSTORE_FILE")
val releaseKeystoreFile = file(configuredKeystorePath ?: "release.keystore")
val hasExplicitKeystorePath = !configuredKeystorePath.isNullOrBlank()
val hasReleaseKeystore = releaseKeystoreFile.isFile

if (hasExplicitKeystorePath && !hasReleaseKeystore) {
    throw GradleException("Configured KEYSTORE_FILE '${releaseKeystoreFile.path}' does not exist")
}

android {
    namespace = "labs.newrapaw.dlna.probe"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "labs.newrapaw.dlna.probe"
        minSdk = 23
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "pawcast"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":core"))
    // 1.17+ requires newer AGP/SDK than this project currently targets.
    //noinspection GradleDependency
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
