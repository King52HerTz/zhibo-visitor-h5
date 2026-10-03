plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "cn.xiangyu.museum"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.xiangyu.museum"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/puzzleAssets")))
}
val preparePuzzleProject by tasks.registering(Copy::class) {
    from("src/main/assets") { exclude(".godot/**", "**/*.uid") }
    from("src/main/res/drawable-nodpi/abo.png")
    into(layout.buildDirectory.dir("godotProject"))
}
val exportPuzzle by tasks.registering(Exec::class) {
    dependsOn(preparePuzzleProject)
    inputs.files(preparePuzzleProject)
    outputs.file(layout.buildDirectory.file("generated/puzzleAssets/game.pck"))
    doFirst { layout.buildDirectory.dir("generated/puzzleAssets").get().asFile.mkdirs() }
    doLast {
        check(layout.buildDirectory.file("generated/puzzleAssets/game.pck").get().asFile.length() > 0) {
            "Godot resource export did not produce a pack"
        }
    }
    commandLine(providers.environmentVariable("GODOT_BIN").getOrElse("godot"), "--headless",
        "--path", layout.buildDirectory.dir("godotProject").get().asFile.absolutePath,
        "--export-pack", "Android embedded", layout.buildDirectory.file("generated/puzzleAssets/game.pck").get().asFile.absolutePath)
}
tasks.named("preBuild") { dependsOn(exportPuzzle) }
dependencies {
    implementation("org.godotengine:godot:4.6.3.stable")
    implementation("androidx.fragment:fragment:1.8.6")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
