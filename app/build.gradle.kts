plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "de.rwth_aachen.phyphox"
    testNamespace = "de.rwth_aachen.phyphoxTest"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.rwth_aachen.phyphox"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionName = "1.2.0"
        //  format  WXXYYZZ, where WW is major, XX is minor, YY is patch, and ZZ is build
        versionCode = 1020009 //1.02.00-09

        val locales = listOf(
            "en",
            "cs",
            "de",
            "el",
            "es",
            "fr",
            "hi",
            "it",
            "ja",
            "ka",
            "nl",
            "pl",
            "pt",
            "ru",
            "sr",
            "b+sr+Latn",
            "tr",
            "vi",
            "zh-rCN",
            "zh-rTW",
        )
        buildConfigField(
            "String[]",
            "LOCALE_ARRAY",
            "new String[]{\"" + locales.joinToString("\",\"") + "\"}",
        )
        resourceConfigurations.addAll(locales)

        vectorDrawables {
            useSupportLibrary = true
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true
    }

    buildTypes {
        getByName("release") {
            lint {
                disable.add("MissingTranslation")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    flavorDimensions.add("permissions")
    productFlavors {
        create("screenshot") {
            dimension = "permissions"
        }
        create("regular") {
            dimension = "permissions"
        }
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_20
        targetCompatibility = JavaVersion.VERSION_20
    }
    kotlin {
        jvmToolchain(20)
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    ndkVersion = "28.0.13004108"

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        checkDependencies = true
//        baseline = file("lint-baseline.xml") no base line till satisfactory state is reached

        // Reports
//        textReport = true
        htmlReport = true

        htmlOutput = file("${layout.buildDirectory}/reports/code-quality/lint.html")
        textOutput = file("${layout.buildDirectory}/reports/code-quality/lint.txt")

        // Compose-specific checks
        enable += setOf(
            "ComposableNaming",
            "ComposableFunctionName",
            "ModifierOrder",
            "UnrememberedMutableState",
        )
    }
    ktlint {
        android.set(true)
        ignoreFailures.set(true)
        coloredOutput.set(true)
        this.outputToConsole.set(true)


        reporters {
//            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        }

        outputToConsole.set(true)
        outputColorName.set("RED")
    }
}
detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    allRules = false
//    ignoredFlavors = listOf("screenshot")
//    ignoredBuildTypes = listOf("release")

    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
    basePath = projectDir.path
    ignoreFailures = true
}

// Add this to handle the deprecated reports block issue
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        // Enable HTML report
        html.required.set(true)
        html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/detekt/detekt.html"))

//        // Enable XML report (often used for CI)
        xml.required.set(false)
//        xml.outputLocation.set(file("${layout.buildDirectory.get()}/reports/detekt/detekt.xml"))
//
//        // Enable SARIF report (best for GitHub Actions)
        sarif.required.set(false)
//        sarif.outputLocation.set(file("${layout.buildDirectory.get()}/reports/detekt/detekt.sarif"))

        // Disable TXT report if not needed
        txt.required.set(false)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.androidx.multidex)
    implementation(libs.google.material)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.recyclerview.selection)
    implementation(libs.androidx.recyclerview)
    implementation(libs.bundles.camerax)// CameraX Bundle
    implementation(libs.commons.io)
    implementation(libs.zxing.android.embedded)//https://github.com/journeyapps/zxing-android-embedded/blob/master/CHANGES.md
    implementation(libs.apache.poi)
    implementation(libs.jlhttp)
    implementation(libs.caverock.androidsvg)//https://bigbadaboom.github.io/androidsvg/release_notes.html
    implementation(libs.paho.mqtt.android)

    //hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    detektPlugins(libs.detekt.compose)

    implementation(libs.androidx.datastore.preferences)

    add("androidTestScreenshotImplementation", libs.junit)
    add("androidTestScreenshotImplementation", libs.fastlane.screengrab)
    add("androidTestScreenshotImplementation", libs.androidx.test.rules)
    add("androidTestScreenshotImplementation", libs.androidx.test.ext.junit)
    add("androidTestScreenshotImplementation", libs.androidx.test.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
}

tasks.register("lintCheck") {
    group = "verification"
    description = "Run ktlint, detekt, and Android Lint sequentially"

    dependsOn(
        ":app:detekt",
        ":app:ktlintCheck"
//        ":app:lintRegularDebug",
    )
}
