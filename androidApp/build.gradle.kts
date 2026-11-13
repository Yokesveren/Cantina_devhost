import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sim"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("com.google.android.gms:play-services-nearby:19.5.1")
    implementation(libs.kotlinx.serialization.json)

    debugImplementation("androidx.compose.ui:ui-tooling")
}

android {
    namespace = "com.example.deaddrop"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.deaddrop"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["appName"] = "DeadDrop"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    flavorDimensions += "node"

    productFlavors {
        create("deviceA") {
            dimension = "node"
            applicationIdSuffix = ".deviceA"
            manifestPlaceholders["appName"] = "DeadDrop - Arjun (A1)"
            buildConfigField("String", "NODE_DEVICE_ID", "\"A1\"")
            buildConfigField("String", "NODE_USER_NAME", "\"Arjun\"")
            buildConfigField("String", "NODE_ROLE", "\"Field Rescuer · North Street\"")
            buildConfigField("String", "NODE_REGION", "\"A\"")
        }
        create("deviceB") {
            dimension = "node"
            applicationIdSuffix = ".deviceB"
            manifestPlaceholders["appName"] = "DeadDrop - Bhavna (B1)"
            buildConfigField("String", "NODE_DEVICE_ID", "\"B1\"")
            buildConfigField("String", "NODE_USER_NAME", "\"Bhavna\"")
            buildConfigField("String", "NODE_ROLE", "\"Field Rescuer · South Street\"")
            buildConfigField("String", "NODE_REGION", "\"A\"")
        }
        create("deviceC") {
            dimension = "node"
            applicationIdSuffix = ".deviceC"
            manifestPlaceholders["appName"] = "DeadDrop - Charan (C1)"
            buildConfigField("String", "NODE_DEVICE_ID", "\"C1\"")
            buildConfigField("String", "NODE_USER_NAME", "\"Charan\"")
            buildConfigField("String", "NODE_ROLE", "\"Group Lead · Base Camp\"")
            buildConfigField("String", "NODE_REGION", "\"A\"")
        }
    }
}
