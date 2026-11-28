plugins {
    id("com.android.application") version "9.0.1"
    id("com.google.devtools.ksp") version "2.3.7"
}

android {
    namespace = "org.example.stashprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.example.stashprobe.instrumentation"
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].kotlin.srcDir("../shared/src")
    sourceSets["androidTest"].kotlin.srcDir("../shared/test")
}

dependencies {
    implementation("androidx.room3:room3-runtime:3.0.3")
    implementation("androidx.sqlite:sqlite-bundled:2.7.1")
    ksp("androidx.room3:room3-compiler:3.0.3")
    androidTestImplementation(kotlin("test"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xmulti-platform",
            "-Xcommon-sources=${file("../shared/src/ProbeDb.kt").absolutePath}",
        )
    }
}
