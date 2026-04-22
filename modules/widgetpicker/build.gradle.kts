/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.launcher3.widgetpicker"
    testNamespace = "com.android.launcher3.widgetpicker.tests"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.android.launcher3.widgetpicker.tests"
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        named("main") {
            java.directories.add("src")
            kotlin.directories.add("src")
            manifest.srcFile("AndroidManifest.xml")
            res.directories.add("res")
        }
        named("androidTest") {
            java.directories.addAll(
                listOf(
                    "tests/multivalentScreenshotTests/src",
                    "tests/multivalentTestsForDevice/src",
                )
            )
            kotlin.directories.addAll(
                listOf(
                    "tests/multivalentScreenshotTests/src",
                    "tests/multivalentTestsForDevice/src",
                )
            )
            manifest.srcFile("tests/AndroidManifest.xml")
        }
        named("test") {
            java.directories.add("tests/multivalentTests/src")
            kotlin.directories.add("tests/multivalentTests/src")
            resources.directories.add("tests/config")
            manifest.srcFile("tests/AndroidManifest.xml")
            res.directories.add("tests/multivalentScreenshotTests/res")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // Exclude META-INF for running test with android studio
    packagingOptions.resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)
    ksp(libs.dagger.android.processor)

    // Compose UI dependencies
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Other UI dependencies
    implementation(libs.compose.material3.windowSizeClass)
    implementation(libs.androidx.window)

    // Compose android studio preview support
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Testing
    // this needs to be modern to support JDK-17 + asm byte code.
    testImplementation(libs.mockito.robolectric.bytebuddy.agent)
    testImplementation(libs.mockito.robolectric.bytebuddy)
    testImplementation(libs.mockito.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Compose UI Tests
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(projects.concurrent)
    implementation(projects.dagger)
}
