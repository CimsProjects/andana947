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
    val regal: Regal,
    val temporades: Temporades,
    val compra: Compra,
    val maestria: Maestria,
    val millores: Millores,
    val unir: Unir,
    val accionariat: Accionariat,
    val intermodalitat: Intermodalitat,
    val cohesio: Cohesio,
    val checkin: Checkin,
    val prestigi: Prestigi,
    val flota: Flota,
    val caos: Caos,
    val social: Social,
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
        /**
         * Es cobra contínuament per cada arribada (GDD 4.8): amb l'app oberta, l'app demana el cobrament cada
         * [segonsCobramentApp] segons; el servidor paga el que s'ha guanyat des de l'última vegada.
         */
        val segonsCobramentApp: Int,
        /** El dia de servei (hora de Catalunya): les arribades del dia es reparteixen dins d'aquestes hores (GDD 4.8). */
        val serveiIniciHora: Int,
        val serveiFiHora: Int,
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

    /**
     * Les temporades (docs/abast.md): de [duradaDies] dies des de [inici], amb rànquing i sense reinici. El *check-in*
     * dona la concessió fins al final de la temporada, que multiplica per [multiplicadorConcessio] cada arribada. El
     * rànquing compta el que es fa a la temporada; els premis són skins (or, plata i bronze fins a aquestes posicions).
     */
    @Serializable
    data class Temporades(
        val duradaDies: Int,
        val inici: String,
        val reinici: Boolean,
        val multiplicadorConcessio: Double,
        val puntsPerCatali: Int,
        val puntsPerAndana: Int,
        val puntsPerCheckin: Int,
        val premiOr: Int,
        val premiPlata: Int,
        val premiBronze: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /**
     * Comprar andanes des de qualsevol lloc: el preu puja un [creixementPreuPerAndana] per cada andana que ja es té, i
     * cada andana costa de mantenir una [quotaManteniment] del que dona, més un [creixementMantenimentPerAndana] per
     * cada andana: créixer molt costa (docs/abast.md).
     */
    @Serializable
    data class Compra(
        val creixementPreuPerAndana: Double,
        val quotaManteniment: Double,
        val creixementMantenimentPerAndana: Double,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /**
     * Maestria de les andanes i de les línies (estil Clash Royale): cada arribada a una andana pròpia suma
     * [xpPerArribada] a l'andana i a la línia, i cada check-in hi suma [xpPerCheckin]. [xpNivells] és l'experiència
     * acumulada per arribar a cada nivell (de l'1 al 10). En pujar de nivell es cobra [premiNivellAndana] (per
     * [multiplicadorPremiLinia] a les línies) i, al nivell màxim, es guanya una skin. No dona ingressos: és prestigi.
     */
    @Serializable
    data class Maestria(
        val xpNivells: List<Int>,
        val xpPerArribada: Int,
        val xpPerCheckin: Int,
        val premiNivellAndana: List<Int>,
        val multiplicadorPremiLinia: Int,
        val skinAndanaMestra: String,
        val skinLiniaMestra: String,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc {
        val nivellMaxim: Int get() = xpNivells.size

        /** El nivell (1–10) d'una experiència acumulada. */
        fun nivell(xp: Double): Int = xpNivells.count { it <= xp }.coerceAtLeast(1)
    }

    /**
     * Millores d'una andana (marquesina, ascensor…): cada una suma un [Millora.bonus] a l'ingrés de cada arribada i
     * costa un [Millora.costRelatiu] del preu de l'andana. Es desbloquegen amb la maestria de l'andana.
     */
    @Serializable
    data class Millores(
        val cataleg: List<Millora>,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    @Serializable
    data class Millora(
        val id: String,
        val bonus: Double,
        val costRelatiu: Double,
        val nivellMinim: Int,
        val modes: List<String>,
    )

    /** Objectius d'«unir»: dues andanes pròpies a menys de [radiTransbordMetres] estan unides (transbord a peu). */
    @Serializable
    data class Unir(
        val radiTransbordMetres: Double,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /**
     * Les andanes de regal del Director (docs/lore.md): estacions de tren, metro, FGC o tramvia (no de bus), amb
     * l'afluència entre els dos llindars. No caduquen i no compten per al límit de la llicència.
     */
    @Serializable
    data class Regal(
        val opcionsPerProvincia: Int,
        /** Quantes de les opcions són trams de dues andanes seguides d'una línia: 0, 1 o 2 (el servidor no en fa més). */
        val tramsPerProvincia: Int,
        val maximAndanesPerProvincia: Int,
        val viatgersMinims: Double,
        val viatgersMaxims: Double,
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

    /**
     * GDD 4.7. Els vehicles es lloguen per línia: la quota és el preu real (dades/flota_cataleg.json) repartit en la
     * vida útil; l'explotació, els km del vehicle per [costKmBus] o [costKmTren]; l'ingrés, el de la línia (amb la
     * [subvencioPerIngres]) repartit entre els vehicles en hora punta. Tot a l'escala de la quota de concessió.
     */
    @Serializable
    data class Flota(
        val diesFianca: Int,
        val vidaUtilBusAnys: Int,
        val vidaUtilTrenAnys: Int,
        val subvencioPerIngres: Double,
        val costKmBus: Double,
        val costKmTren: Double,
        val costKmTramvia: Double,
        /** En vendre un vehicle comprat, se'n cobra aquesta part del valor que li queda (preu menys l'amortització). */
        val margeVenda: Double,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /**
     * Fase 7. Mercat d'accions entre jugadors (preu dins de [preuMinimRelatiu]–[preuMaximRelatiu] del preu de
     * referència; el venedor paga la [comissioVenda]), juntes de les grans estacions (una proposta s'aprova quan els
     * vots a favor passen de la [majoria] de les accions venudes) i cooperatives d'una comarca.
     */
    @Serializable
    data class Social(
        val comissioVenda: Double,
        val preuMinimRelatiu: Double,
        val preuMaximRelatiu: Double,
        val diesOferta: Int,
        val horesProposta: Int,
        val majoria: Double,
        val maximMembresCooperativa: Int,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    /**
     * GDD 6. El motor de caos: cada dia, per a cada àmbit de cada esdeveniment, se sorteja si passa amb la seva
     * [Esdeveniment.probabilitat] (×[multiplicadorTardor] de setembre a novembre si és de [Esdeveniment.tardor]).
     * Mentre dura, multiplica l'ingrés de les andanes i dels vehicles de l'àmbit.
     */
    @Serializable
    data class Caos(
        val multiplicadorTardor: Double,
        val esdeveniments: List<Esdeveniment>,
        override val origen: Map<String, Origen>,
        override val fonts: Map<String, String> = emptyMap(),
    ) : Bloc

    @Serializable
    data class Esdeveniment(
        val id: String,
        val probabilitat: Double,
        val multiplicador: Double,
        val duradaDies: List<Int>,
        val nomesFeiner: Boolean,
        val tardor: Boolean,
        val ambits: List<Ambit>,
    )

    /** On passa: unes línies (mode:codi), cada línia d'uns modes, unes comarques, o uns modes a prop d'un node. */
    @Serializable
    data class Ambit(
        val linies: List<String> = emptyList(),
        val perLiniaDelsModes: List<String> = emptyList(),
        val comarques: List<String> = emptyList(),
        val modes: List<String> = emptyList(),
        val centreNode: String? = null,
        val radiMetres: Double? = null,
        /** El nom de la zona, per a l'avís (p. ex. «Maresme»). */
        val nom: String? = null,
    )

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
            "regal" to regal,
            "temporades" to temporades,
            "compra" to compra,
            "maestria" to maestria,
            "millores" to millores,
            "unir" to unir,
            "accionariat" to accionariat,
            "intermodalitat" to intermodalitat,
            "cohesio" to cohesio,
            "checkin" to checkin,
            "prestigi" to prestigi,
            "flota" to flota,
            "caos" to caos,
            "social" to social,
            "modelAfluencia" to modelAfluencia,
        )

    companion object {
        const val DEFECTE = "defecte"

        private val json = Json { ignoreUnknownKeys = false }

        fun llegeix(text: String): ConfiguracioJoc = json.decodeFromString(serializer(), text)
    }
}
