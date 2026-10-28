// Les versions són les del catàleg compartit amb l'app (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

val arrelRepositori = rootDir.parentFile.absolutePath

tasks.test {
    // Els tests llegeixen la configuració i les dades reals del repositori.
    systemProperty("arrel.repositori", arrelRepositori)
}

// Simulació del primer mes (docs/gdd.md, secció 8): ./gradlew -p joc simulacio
tasks.register<JavaExec>("simulacio") {
    group = "application"
    description = "Simula el primer mes d'un jugador amb les dades reals de dades/generades."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("cat.andana947.joc.simulacio.MainKt")
    args(arrelRepositori)
}
