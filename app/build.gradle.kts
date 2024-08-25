plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blue.curator"  // Add this line
    buildFeatures {
        viewBinding = true
    }
    compileSdkVersion(34)
    defaultConfig {
        applicationId = "com.blue.curator"
        minSdkVersion(24)
        targetSdkVersion(34)
        versionCode = 1
        versionName = "1.0"
    }
    lintOptions {
        disable("DependencyViolation") // This will suppress warnings like this one
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.12.0") // Downgrade from 1.13.0
    implementation("androidx.core:core:1.12.0")    // Downgrade from 1.13.0
    implementation("androidx.activity:activity:1.7.0") // Downgrade from 1.8.0
    implementation("androidx.transition:transition:1.4.1") // Downgrade from 1.5.0
    implementation("androidx.annotation:annotation-experimental:1.3.0") // Downgrade from 1.4.0
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}