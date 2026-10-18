plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

apply(from = rootProject.file("gradle/windows-test-classpath.gradle.kts"))

val MARKETING_VERSION = "3.1.2"

val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull()
    ?: MARKETING_VERSION.replace(".", "").toIntOrNull()
    ?: 1

val ciVersionName = (project.findProperty("versionName") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: MARKETING_VERSION

android {
    namespace = "com.lucent.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jiaying.yuan.lucentapp"
        minSdk = 28
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 只带真机（骁龙 8 至尊版）和模拟器两种架构，不再为老 32 位机型准备二进制副本
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    sourceSets {
        getByName("androidTest") {
            assets.directories += "$projectDir/schemas"
        }
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseStorePath = System.getenv("LUCENT_KEYSTORE_FILE")
        if (releaseStorePath != null) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("LUCENT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUCENT_KEY_ALIAS") ?: "lucent"
                keyPassword = System.getenv("LUCENT_KEY_PASSWORD")
                    ?: System.getenv("LUCENT_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)

    implementation(libs.androidx.profileinstaller)

    implementation(libs.haze)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation(libs.org.json)

    debugImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)

}
