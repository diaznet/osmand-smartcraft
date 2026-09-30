plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.diaznet.osmandsmartcraft"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.diaznet.osmandsmartcraft"
        minSdk = 26
        targetSdk = 34
        versionCode = getVersionCode()
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}

// Release version lives in gradle.properties (appVersionName/appVersionCode) so F-Droid's
// update checker can read it. Branch builds get a git-derived suffix; tags and detached checkouts don't.
fun getVersionName(): String {
    val base = providers.gradleProperty("appVersionName").get()
    fun git(vararg args: String) = try {
        providers.exec { commandLine("git", *args); isIgnoreExitValue = true }.standardOutput.asText.get().trim()
    } catch (_: Exception) { "" }
    if (git("describe", "--tags", "--exact-match") == "v$base") return base
    val branch = git("branch", "--show-current")
    val suffix = when {
        branch.isEmpty() -> return base
        branch.startsWith("release") -> "rc"
        branch.startsWith("hotfix") -> "fix"
        else -> "dev"
    }
    return "$base-$suffix"
}

fun getVersionCode(): Int = providers.gradleProperty("appVersionCode").get().toInt()
