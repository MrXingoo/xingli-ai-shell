plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.mgaoxin.xingli.shell"

    lint {
        disable += "MissingTranslation"
    }
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mgaoxin.xingli.shell"
        minSdk = 26
        targetSdk = 36
        // Version overrides passed from CLI via -PversionName / -PversionCode
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "1.0-dev"
    }

    signingConfigs {
        create("release") {
            val isReleaseBuild =
                gradle.startParameter.taskNames.any { name ->
                    name.contains("Release", ignoreCase = true) || name == "build"
                }

            if (isReleaseBuild) {
                val storePath = System.getenv("KEYSTORE_PATH")
                val storePass = System.getenv("KEYSTORE_PASSWORD")
                val alias = System.getenv("KEY_ALIAS")
                val keyPass = System.getenv("KEY_PASSWORD")

                if (storePath != null && storePass != null && alias != null && keyPass != null) {
                    storeFile = file(storePath)
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                } else {
                    logger.warn("Release signing config missing env vars — signing deferred to release workflow")
                    storeFile = file("dummy.keystore")
                    storePassword = "dummy"
                    keyAlias = "dummy"
                    keyPassword = "dummy"
                }
            } else {
                storeFile = file("dummy.keystore")
                storePassword = "dummy"
                keyAlias = "dummy"
                keyPassword = "dummy"
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs["release"]
            buildConfigField("boolean", "ALLOW_CLEARTEXT", "false")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            pickFirsts += "META-INF/AL2.0"
            pickFirsts += "META-INF/LGPL2.1"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
