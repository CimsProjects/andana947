import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// local.properties no es puja al repositori: hi ha la URL de Supabase, la clau pública i la clau de pujada a Google Play.
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun propietat(nom: String, entorn: String): String? = local.getProperty(nom) ?: System.getenv(entorn)

android {
    namespace = "cat.andana947"
    compileSdk = 36

    defaultConfig {
        applicationId = "cat.andana947"
        minSdk = 26
        targetSdk = 36
        // A la CI, el número de la compilació, que sempre creix. Per pujar a Google Play des del teu ordinador,
        // `versio.codi` a local.properties: ha de ser més gran que el de l'última pujada (docs/publicacio.md).
        val compilacio = propietat("versio.codi", "VERSIO_CODI")?.toIntOrNull()
        versionCode = compilacio ?: 1
        versionName = if (compilacio != null) "0.1.$compilacio" else "0.1"

        // Projecte de Supabase (docs/supabase.md). Només la URL i la clau pública, llegides de local.properties,
        // que no es puja al repositori. A la CI, surten dels secrets del repositori.
        val url = propietat("supabase.url", "SUPABASE_URL") ?: ""
        val anonKey = propietat("supabase.anonKey", "SUPABASE_ANON_KEY") ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$url\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$anonKey\"")
        // Sense servidor, l'app funciona amb un compte local per provar-la (app/LLEGEIX-ME.md).
        buildConfigField("boolean", "SENSE_SERVIDOR", (url.isBlank() || anonKey.isBlank()).toString())
    }

    // La clau de pujada a Google Play (docs/publicacio.md). El fitxer .jks i les contrasenyes mai no van al repositori.
    val magatzem = propietat("signatura.fitxer", "SIGNATURA_FITXER")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (magatzem != null) {
            create("pujada") {
                storeFile = magatzem
                storePassword = propietat("signatura.contrasenya", "SIGNATURA_CONTRASENYA")
                keyAlias = propietat("signatura.alies", "SIGNATURA_ALIES") ?: "pujada"
                keyPassword = propietat("signatura.contrasenyaClau", "SIGNATURA_CONTRASENYA_CLAU")
                    ?: propietat("signatura.contrasenya", "SIGNATURA_CONTRASENYA")
            }
        }
    }

    buildTypes {
        release {
            // Sense clau (a la CI, per exemple), el release surt sense signar: serveix per compilar, no per pujar.
            if (magatzem != null) signingConfig = signingConfigs.getByName("pujada")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Els horaris i els traçats dels trens (docs/vehicles.md) surten de dades/generades/app, sense copiar-los a mà.
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("dades/generades/app"))
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        textReport = true
        textOutput = file("build/reports/lint-results.txt")
        // Les versions de les dependències s'actualitzen a mà.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.maplibre.android)

    // Les regles i les matemàtiques del joc en Kotlin pur (joc/, build composta): de moment, els trens del mapa.
    implementation("cat.andana947:joc")

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation("cat.andana947:joc")
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
