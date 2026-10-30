import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "cat.andana947"
    compileSdk = 36

    defaultConfig {
        // La branca main-marti s'instal·la com una app a part («Andana Martí»), perquè convisqui al mòbil amb la de
        // main. El codi (namespace) continua sent cat.andana947.
        applicationId = "cat.andana947.marti"
        minSdk = 26
        targetSdk = 36
        // A la CI, el número de la compilació, que sempre creix.
        val compilacio = System.getenv("VERSIO_CODI")?.toIntOrNull()
        versionCode = compilacio ?: 1
        versionName = if (compilacio != null) "0.1.$compilacio" else "0.1"

        // Projecte de Supabase (docs/supabase.md). Només la URL i la clau pública, llegides de local.properties,
        // que no es puja al repositori. A la CI, surten dels secrets del repositori.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val url = local.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL") ?: ""
        val anonKey = local.getProperty("supabase.anonKey") ?: System.getenv("SUPABASE_ANON_KEY") ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$url\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$anonKey\"")
        // Sense servidor, l'app funciona amb un compte local per provar-la (app/LLEGEIX-ME.md).
        buildConfigField("boolean", "SENSE_SERVIDOR", (url.isBlank() || anonKey.isBlank()).toString())
    }

    buildTypes {
        release {
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
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test-junit"))
}
