plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val sizeOptimization = providers.gradleProperty("sizeOptimization")
    .map(String::toBoolean)
    .orElse(true)

android {
    namespace = "vn.demo.liteload"
    compileSdk = 36
    dynamicFeatures += setOf(":feature:gallery", ":feature:audio_guide", ":feature:itinerary", ":feature:food")

    defaultConfig {
        applicationId = "vn.demo.liteload"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SIZE_OPTIMIZATION", "false")
        }
        release {
            isMinifyEnabled = sizeOptimization.get()
            isShrinkResources = sizeOptimization.get()
            buildConfigField("boolean", "SIZE_OPTIMIZATION", sizeOptimization.get().toString())
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:contracts"))
    implementation(libs.play.feature.delivery)
    implementation(libs.androidx.activity.ktx)
    constraints { implementation(libs.androidx.fragment) { because("Activity Result requires a compatible transitive Fragment") } }
}

tasks.register("prepareKotlinBuildScriptModel") {
}
