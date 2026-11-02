plugins { id("com.android.application") }
android {
    namespace = "org.textsentry.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.textsentry.app"
        manifestPlaceholders["appAuthRedirectScheme"] = "textsentry"
        minSdk = 26
        targetSdk = 36
        // GitHub workflow run numbers are independent; switching between sandbox,
        // production-candidate and normal release must not downgrade an installed app.
        // GITHUB_RUN_ID is repository-wide, monotonically increasing and currently
        // safely fits in a 32-bit Android versionCode after dividing by 100.
        versionCode = System.getenv("GITHUB_RUN_ID")?.toLongOrNull()
            ?.div(100)?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
            ?: (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            // Explicit opt-in until sandbox checkout and paid entitlement enforcement pass.
            buildConfigField("boolean", "WEB_CHECKOUT_ENABLED",
                (System.getenv("TEXTSENTRY_ENABLE_WEB_CHECKOUT") == "true").toString())
            val checkoutUrl = System.getenv("TEXTSENTRY_WEB_CHECKOUT_URL")
                ?: "https://pay.rev.cat/mimrlvpobvqnjikw/"
            require(checkoutUrl.matches(Regex("https://pay[.]rev[.]cat/[A-Za-z0-9_/-]+"))) {
                "RevenueCat checkout must be an official pay.rev.cat purchase link"
            }
            buildConfigField("String", "WEB_CHECKOUT_URL",
                "\"" + checkoutUrl.trimEnd('/') + "/\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "WEB_CHECKOUT_ENABLED", "false")
            buildConfigField("String", "WEB_CHECKOUT_URL", "\"\"")
        }
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("ciRelease") {
            val path = System.getenv("APP_SIGNING_STORE_FILE")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("APP_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("APP_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("APP_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("APP_SIGNING_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("net.openid:appauth:0.11.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
