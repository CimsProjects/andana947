// El mòdul joc és una build independent (Kotlin pur, sense Android) perquè les regles del joc
// es puguin compilar i testejar sense l'SDK d'Android: gradle -p joc test (o ./gradlew -p joc test
// quan hi hagi l'arrel de Gradle de la fase 3, que l'inclou com a build composta).

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
}

rootProject.name = "joc"
