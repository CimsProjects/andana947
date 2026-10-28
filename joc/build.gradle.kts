// Les versions són les de municipis (gradle/libs.versions.toml). Quan la fase 3 creï el catàleg de
// versions a l'arrel, aquest fitxer el pot fer servir com a municipis.
plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "cat.andana947"
version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}

val arrelRepositori = rootDir.parentFile.absolutePath

tasks.test {
    // Els tests llegeixen la configuració i les dades reals del repositori.
    systemProperty("arrel.repositori", arrelRepositori)
}

// Simulació del primer mes (docs/gdd.md, secció 8): gradle -p joc simulacio
tasks.register<JavaExec>("simulacio") {
    group = "application"
    description = "Simula el primer mes d'un jugador amb les dades reals de dades/generades."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cat.andana947.joc.simulacio.MainKt")
    args(arrelRepositori)
}
