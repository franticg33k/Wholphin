// Cable TV mode: the TV screen (guide, direct play at the scheduled offset, static overlay) for channels scheduled by
// the Cable TV Jellyfin plugin. The app wires it in through TvModeHost; everything else lives here so upstream
// rebases touch as little of the app as possible.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

private fun Provider<String>.getInt() = get().toInt()

android {
    namespace = "com.github.damontecres.wholphin.tvmode"
    compileSdk {
        version = release(libs.versions.compileSdk.getInt())
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.getInt()
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    api(project(":tvmode-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    // Screenshot renders of the TV mode screens (TvModeScreenshots), run on demand.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
