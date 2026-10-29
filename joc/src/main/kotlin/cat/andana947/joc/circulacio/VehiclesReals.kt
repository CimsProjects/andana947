package cat.andana947.joc.circulacio

import cat.andana947.joc.geo.distanciaMetres
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/** Una posició real d'un tren, tal com la dona l'operador (a través de la funció `vehicles` de Supabase). */
@Serializable
data class VehicleReal(
    val id: String,
    /** La font: `renfe` o `fgc` ([Flota.FONTS_REALS]). */
    val font: String,
    /** El codi de la línia (R2S, S1…), si l'operador el dona. */
    val linia: String? = null,
    val lat: Double,
    val lon: Double,
)

/**
 * Mou els trens reals entre una posició i la següent. Les posicions arriben cada mig minut, més o menys: en arribar-ne
 * una de nova, el tren hi va des d'on es veia, durant [duradaMs]. Si el tren és sobre la via de la seva línia, hi avança
 * per sobre; si no, en línia recta.
 */
class SeguimentVehiclesReals(
    private val circulacions: Circulacions,
    private val duradaMs: Long,
) {
    /**
     * Un tren que va d'[origen] a [desti]. Si avança per la via, [tracat] i les distàncies diuen per on; si va recte,
     * [tracatFinal] i [distanciaFinal] diuen on queda sobre la via quan arriba.
     */
    private class Estat(
        val linia: Int?,
        val origen: Punt,
        val desti: Punt,
        val inici: Long,
        val tracat: Int? = null,
        val distanciaOrigen: Double = 0.0,
        val distanciaDesti: Double = 0.0,
        val tracatFinal: Int? = null,
        val distanciaFinal: Double = 0.0,
    )

    private val estats = mutableMapOf<String, Estat>()

    fun actualitza(vehicles: List<VehicleReal>, araMs: Long) {
        val vistos = mutableSetOf<String>()
        for (v in vehicles) {
            val id = "${v.font}:${v.id}"
            vistos += id
            val actual = estats[id]?.let { posicio(it, araMs) }
            val (linia, tracat, distancia) = encaixa(v, actual?.tracat)
            val desti = if (tracat != null) circulacions.tracats[tracat].posicio(distancia) else {
                val rumb = actual?.posicio?.let { a ->
                    if (distanciaMetres(a.lat, a.lon, v.lat, v.lon) > MOVIMENT_MINIM_METRES) Tracat.rumb(a, Punt(v.lat, v.lon)) else a.rumb
                } ?: 0.0
                Punt(v.lat, v.lon, rumb)
            }
            estats[id] = when {
                // La primera vegada, el tren apareix on és.
                actual == null -> Estat(linia, desti, desti, araMs, tracat, distancia, distancia)
                // Pel mateix traçat i sense salts estranys, avança per la via.
                tracat != null && actual.tracat == tracat && actual.distancia != null &&
                    kotlin.math.abs(distancia - actual.distancia) <= SALT_MAXIM_METRES ->
                    Estat(linia, actual.posicio, desti, araMs, tracat, actual.distancia, distancia)
                // Si no, recte des d'on es veia.
                else -> Estat(linia, actual.posicio, desti, araMs, tracatFinal = tracat, distanciaFinal = distancia)
            }
        }
        estats.keys.retainAll(vistos)
    }

    /** Els trens reals a [araMs]. */
    fun posicions(araMs: Long): List<VehicleAlMapa> = estats.map { (id, e) -> posicio(e, araMs).copy(id = id) }

    private fun posicio(e: Estat, araMs: Long): VehicleAlMapa {
        val f = ((araMs - e.inici).toDouble() / duradaMs).coerceIn(0.0, 1.0)
        if (e.tracat != null) {
            val d = e.distanciaOrigen + (e.distanciaDesti - e.distanciaOrigen) * f
            return VehicleAlMapa("", e.linia, circulacions.tracats[e.tracat].posicio(d), e.tracat, d, real = true)
        }
        if (f >= 1.0) return VehicleAlMapa("", e.linia, e.desti, e.tracatFinal, e.distanciaFinal.takeIf { e.tracatFinal != null }, real = true)
        val p = Punt(e.origen.lat + (e.desti.lat - e.origen.lat) * f, e.origen.lon + (e.desti.lon - e.origen.lon) * f, e.desti.rumb)
        return VehicleAlMapa("", e.linia, p, null, null, real = true)
    }

    /** La línia, el traçat i la distància sobre el traçat més proper de la línia del tren, si n'és prou a prop. */
    private fun encaixa(v: VehicleReal, preferit: Int?): Triple<Int?, Int?, Double> {
        val prefix = Flota.FONTS_REALS[v.font] ?: return Triple(null, null, 0.0)
        val linies = v.linia?.let { circulacions.liniesAmbCodi(prefix, it) }.orEmpty()
        if (linies.isEmpty()) return Triple(null, null, 0.0)
        var millor: Triple<Int?, Int?, Double> = Triple(linies.first(), null, 0.0)
        var separacio = SEPARACIO_MAXIMA_METRES
        for (linia in linies) {
            for (t in circulacions.tracatsDe(listOf(linia))) {
                val p = circulacions.tracats[t].projecta(v.lat, v.lon)
                // El traçat que ja seguia té preferència, si és tan a prop com els altres.
                val s = if (t == preferit) p.separacio - PREFERENCIA_METRES else p.separacio
                if (s < separacio) {
                    separacio = s
                    millor = Triple(linia, t, p.distancia)
                }
            }
        }
        return millor
    }

    companion object {
        const val SEPARACIO_MAXIMA_METRES = 150.0
        const val SALT_MAXIM_METRES = 5_000.0
        const val MOVIMENT_MINIM_METRES = 5.0
        const val PREFERENCIA_METRES = 20.0
    }
}

/**
 * Tots els trens que es veuen al mapa: els reals de les fonts que funcionen i, per a la resta de línies, els estimats
 * segons l'horari. Si una font real falla, les seves línies tornen a l'horari.
 */
class Flota(val circulacions: Circulacions, duradaAnimacioMs: Long) {
    private val seguiment = SeguimentVehiclesReals(circulacions, duradaAnimacioMs)
    private var fontsActives: Set<String> = emptySet()

    /** Unes posicions reals noves. [fontsOk] són les fonts que han respost bé (encara que no tinguin cap tren). */
    fun actualitzaReals(vehicles: List<VehicleReal>, fontsOk: Set<String>, araMs: Long) {
        fontsActives = fontsOk.intersect(FONTS_REALS.keys)
        seguiment.actualitza(vehicles.filter { it.font in fontsActives }, araMs)
    }

    /** Sense resposta del servidor: tot segons l'horari. */
    fun senseReals(araMs: Long) = actualitzaReals(emptyList(), emptySet(), araMs)

    /** Els vehicles dels [tipus] que es veuen al mapa (el jugador pot amagar els trens o els busos). */
    fun vehicles(ara: LocalDateTime, araMs: Long, tipus: Set<TipusVehicle> = TipusVehicle.TOTS): List<VehicleAlMapa> {
        if (tipus.isEmpty()) return emptyList()
        val prefixosReals = fontsActives.mapNotNull { FONTS_REALS[it] }
        val estimats = circulacions.enCirculacio(ara, tipus).filter { v ->
            val id = v.linia?.let { circulacions.horaris.linies[it].id } ?: return@filter true
            prefixosReals.none { id.startsWith(it) }
        }
        val reals = if (TipusVehicle.TREN in tipus) seguiment.posicions(araMs) else emptyList()
        return estimats + reals
    }

    companion object {
        /**
         * Les fonts de posicions reals i les línies que cobreixen (prefix de l'identificador de línia de linies.csv):
         * el GTFS-RT de Renfe Cercanías cobreix Rodalies (també els regionals R11-R17, RG1, RT, RL del mateix GTFS),
         * però no els de mitjana distància (`renfe_md:`), que continuen segons l'horari.
         */
        val FONTS_REALS = mapOf("renfe" to "rodalies:", "fgc" to "fgc:")
    }
}
