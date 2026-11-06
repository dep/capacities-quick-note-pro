import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
}

data class OAuthBuildProperties(
    val clientId: String,
    val redirectScheme: String = "com.dnnypck.capacitiesquicknotepro",
    val redirectHost: String = "oauth",
    val redirectPath: String = "/callback"
) {
    val redirectUri: String = "$redirectScheme://$redirectHost$redirectPath"
}

/** Public Capacities OAuth client ID (PKCE, no secret). Overridable via local.properties. */
val defaultCapacitiesClientId = "capacities_api_quick_note_pro"

fun firstNonBlank(vararg values: String?): String? {
    return values.firstOrNull { !it.isNullOrBlank() }
}

fun loadOAuthProperties(): OAuthBuildProperties {
    val props = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { props.load(it) }
    }
    val clientId = firstNonBlank(
        props.getProperty("CAPACITIES_CLIENT_ID"),
        project.findProperty("CAPACITIES_CLIENT_ID") as String?,
        defaultCapacitiesClientId
    ) ?: defaultCapacitiesClientId
    val scheme = props.getProperty("CAPACITIES_REDIRECT_SCHEME")
        ?: (project.findProperty("CAPACITIES_REDIRECT_SCHEME") as String?)
        ?: "com.dnnypck.capacitiesquicknotepro"
    val host = props.getProperty("CAPACITIES_REDIRECT_HOST")
        ?: (project.findProperty("CAPACITIES_REDIRECT_HOST") as String?)
        ?: "oauth"
    val path = props.getProperty("CAPACITIES_REDIRECT_PATH")
        ?: (project.findProperty("CAPACITIES_REDIRECT_PATH") as String?)
        ?: "/callback"
    return OAuthBuildProperties(
        clientId = clientId.trim(),
        redirectScheme = scheme.trim(),
        redirectHost = host.trim(),
        redirectPath = if (path.startsWith("/")) path.trim() else "/${path.trim()}"
    )
}

android {
    namespace = "com.dnnypck.capacitiesquicknotepro"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.dnnypck.capacitiesquicknotepro"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val oauth = loadOAuthProperties()
        val clientId = oauth.clientId.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "CAPACITIES_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "CAPACITIES_REDIRECT_URI", "\"${oauth.redirectUri}\"")
        buildConfigField("String", "CAPACITIES_API_VERSION", "\"1.0.0\"")

        manifestPlaceholders["oauthRedirectScheme"] = oauth.redirectScheme
        manifestPlaceholders["oauthRedirectHost"] = oauth.redirectHost
        manifestPlaceholders["oauthRedirectPath"] = oauth.redirectPath
    }

    signingConfigs {
        create("release") {
            storeFile = file("../upload-keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "targets-upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // OAuth: Custom Tabs + encrypted token storage
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
