// El mòdul joc és una build independent (Kotlin pur, sense Android) perquè les regles del joc
// es puguin compilar i testejar sense l'SDK d'Android: ./gradlew -p joc test
// L'app l'inclou com a build composta (vegeu ../settings.gradle.kts), com a municipis.

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "joc"
