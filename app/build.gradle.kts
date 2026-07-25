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

fun getVersionName(): String = try {
    val tag = providers.exec {
        commandLine("git", "describe", "--tags", "--exact-match")
    }.standardOutput.asText.get().trim().removePrefix("v")
    tag.ifEmpty { getVersionFromBranch() }
} catch (_: Exception) {
    // Not on a tag — derive from branch
    getVersionFromBranch()
}

fun getVersionFromBranch(): String = try {
    val branch = providers.exec {
        commandLine("git", "branch", "--show-current")
    }.standardOutput.asText.get().trim()
    // Extract version from branch name like "releasecandidate/v1.0.3"
    val versionMatch = Regex("""v?(\d+\.\d+\.\d+)""").find(branch)
    if (versionMatch != null) {
        val ver = versionMatch.groupValues[1]
        val prefix = branch.substringBefore("/").take(3).lowercase() // e.g. "rel" -> "rc", "fea" -> "dev"
        val suffix = when {
            branch.startsWith("releasecandidate") -> "rc"
            branch.startsWith("release") -> "rc"
            branch.startsWith("hotfix") -> "fix"
            else -> "dev"
        }
        "$ver-$suffix"
    } else {
        // Fallback: latest tag + branch hint
        val lastTag = providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim().removePrefix("v").ifEmpty { "0.1.0" }
        "$lastTag-dev"
    }
} catch (_: Exception) { "0.1.0-dev" }

fun getVersionCode(): Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toIntOrNull() ?: 1
} catch (_: Exception) { 1 }
