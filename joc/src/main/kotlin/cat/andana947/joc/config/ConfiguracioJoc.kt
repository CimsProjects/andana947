package cat.andana947.joc.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Totes les regles i els valors ajustables del joc. Es llegeixen de `dades/configuracio_joc.json`, que és
 * l'únic lloc on es defineixen (CLAUDE.md). Els tests de `ConfiguracioJocTest` en comproven la coherència.
 *
 * Cada bloc porta un mapa `origen` que diu d'on surt cada valor, amb les etiquetes del GDD (secció 0):
 * `real`, `estimat` o `joc`. La clau `defecte` val per a tots els camps que no hi surten.
 */
@Serializable
data class ConfiguracioJoc(
    val versio: Int,
    val economia: Economia,
    val calendari: Calendari,
    val concessions: Concessions,
    val accionariat: Accionariat,
    val intermodalitat: Intermodalitat,
    val cohesio: Cohesio,
    val checkin: Checkin,
    val prestigi: Prestigi,
    val flota: Flota,
    val trams: Trams,
    val modelAfluencia: ModelAfluencia,
) {
    /** Un bloc de la configuració, amb l'origen de cada valor. */
    interface Bloc {
        val origen: Map<String, Origen>
        val fonts: Map<String, String>

        fun origenDe(camp: String): Origen = origen[camp] ?: origen.getValue(DEFECTE)
    }

    @Serializable
    enum class Origen {
        /** Dada publicada per una font oberta o oficial. */
        @SerialName("real")
        REAL,

        /** Surt d'un model d'estimació documentat al GDD. */
        @SerialName("estimat")
        ESTIMAT,

        /** Paràmetre de disseny, que s'ajusta a la fase 8. */
        @SerialName("joc")
        JOC,
    }

    /** GDD 4.1 i 4.2. */
    @Serializable
    data class Economia(
        val capitalInicial: Double,
        /** Ingrés per viatge, en catalins (1 € real = 1 catalí). */
        val tarifaBase: Double,
        val quotaConcessio: Double,
        val afluenciaReferencia: Double,
        val exponentAfluencia: Double,
        val diesRetorn: Int,
        val preuMinimConcessio: Double,
        val preuMinimAccio: Double,
        val horaTic: String,
        val zonaHoraria: String,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.4. */
    @Serializable
    data class Calendari(
        val any: Int,
        val diesFeiners: Int,
        val diesNoFeiners: Int,
        /** Viatgers d'un dissabte, diumenge o festiu respecte d'un feiner. */
        val factorNoFeiner: Double,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.2. */
    @Serializable
    data class Concessions(
        val duradaDies: Int,
        /** Un check-in quan falten menys d'aquests dies per caducar renova la concessió. */
        val finestraRenovacioDies: Int,
        val maximBase: Int,
        val maximPerNivell: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 3.1 i 4.3. */
    @Serializable
    data class Accionariat(
        val llindarViatgersFeiner: Double,
        val accionsPerEstacio: Int,
        val maximPerJugador: Int,
        val maximPerCheckin: Int,
        val diesSenseVisitaSenseDividend: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.5. */
    @Serializable
    data class Intermodalitat(
        val radiMetres: Double,
        val multiplicador: Double,
        val perModeAddicional: Double,
        val maxim: Double,
        /** Accions que cal tenir d'una gran estació perquè compti com a pròpia. */
        val minimAccions: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.6. */
    @Serializable
    data class Cohesio(
        /** Habitants per km². */
        val densitatMaxima: Double,
        val ingresMinimDiari: Double,
        /** Codis de comarca de l'ICGC. */
        val comarquesAltPirineuIAran: List<String>,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 7.3. */
    @Serializable
    data class Checkin(
        val radiParadaMetres: Double,
        val radiEstacioMetres: Double,
        val precisioMaximaMetres: Double,
        val velocitatMaximaKmh: Double,
        val velocitatEntreCheckinsKmh: Double,
        val accionsPerNodeIDia: Int,
        val rebutjosPerBloqueig: Int,
        val finestraRebutjosDies: Int,
        val horesBloqueig: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 5.1. */
    @Serializable
    data class Prestigi(
        val baseNivell: Double,
        val exponentNivell: Double,
        val primerCheckinNode: Int,
        val conquesta: Int,
        val renovacio: Int,
        val perAccio: Int,
        val municipiNou: Int,
        val comarcaNova: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.7. */
    @Serializable
    data class Flota(
        val diesFianca: Int,
        val vidaUtilBusAnys: Int,
        val vidaUtilTrenAnys: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** GDD 4.0: trams i monedes (MVP). */
    @Serializable
    data class Trams(
        /** Els trams amb menys parades no es juguen. */
        val minimParades: Int,
        /** Monedes per arribada a andana d'un tram petit (enter a l'atzar entre els dos, inclosos). */
        val recompensaPetitMinima: Int,
        val recompensaPetitMaxima: Int,
        /** El mateix per a un tram mitjà. */
        val recompensaMitjaMinima: Int,
        val recompensaMitjaMaxima: Int,
        /** Un tram costa les monedes que dona en aquests dies. */
        val diesRetorn: Int,
        val preuMinim: Int,
        val arrodonimentPreu: Int,
        val capitalInicial: Int,
        /** Les arribades del dia es reparteixen entre aquestes hores (local). */
        val horaIniciServei: Int,
        val horaFiServei: Int,
        val duradaTemporadaDies: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /** Paràmetres dels models d'estimació d'afluència (GDD 2.2 i 2.3), que fan servir els scripts. */
    @Serializable
    data class ModelAfluencia(
        val versio: Int,
        val radiFusioParadesMetres: Double,
        val radiPoblacioParadaMetres: Double,
        val divisorPoblacioParada: Double,
        val exponentPoblacioRegionals: Double,
        val factorLiniaMinim: Double,
        val factorLiniaMaxim: Double,
        val anyBaseRodalies: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    val blocs: Map<String, Bloc>
        get() = mapOf(
            "economia" to economia,
            "calendari" to calendari,
            "concessions" to concessions,
            "accionariat" to accionariat,
            "intermodalitat" to intermodalitat,
            "cohesio" to cohesio,
            "checkin" to checkin,
            "prestigi" to prestigi,
            "flota" to flota,
            "trams" to trams,
            "modelAfluencia" to modelAfluencia,
        )

    companion object {
        const val DEFECTE = "defecte"

        private val json = Json { ignoreUnknownKeys = false }

        fun llegeix(text: String): ConfiguracioJoc = json.decodeFromString(serializer(), text)
    }
}
