package cat.andana947.joc.circulacio

import cat.andana947.joc.geo.distanciaMetres
import java.time.LocalDate
import java.time.LocalDateTime

/** Una andana del jugador: on és i quines línies hi paren (els codis de `nodes.linies`). */
data class AndanaPropia(
    val id: String,
    val lat: Double,
    val lon: Double,
    val linies: List<String>,
    /** El mode del node (`metro`, `rodalies`, `bus`…): els busos només arriben a parades de bus, i els trens a estacions. */
    val mode: String,
)

/** Un vehicle de la [linia] que arriba a l'andana [andana] a l'hora [hora], segons l'horari. */
data class ArribadaAndana(val andana: String, val linia: LiniaCirculacio, val hora: LocalDateTime)

/**
 * Quan arriben els trens i els busos a les andanes del jugador, segons l'horari (docs/vehicles.md). Una parada d'un
 * recorregut és una andana del jugador si hi para una línia amb el mateix codi i són prou a prop: a menys de
 * [radiTrenMetres] a les estacions, que agrupen les andanes de cada sentit, i de [radiBusMetres] a les parades de bus,
 * que són d'un sol sentit i en tenen una altra a l'altra banda del carrer.
 * Fa servir l'horari també a les línies amb posició real (Rodalies i FGC), que no en tenen d'arribades.
 */
class Arribades(
    private val circulacions: Circulacions,
    private val andanes: List<AndanaPropia>,
    radiTrenMetres: Double = RADI_TREN_METRES,
    radiBusMetres: Double = RADI_BUS_METRES,
) {
    /** Per a cada tipus de dia, les arribades ordenades: segons des de la mitjanit, andana i línia. */
    private val perDia: Map<TipusDia, List<Event>>

    private class Event(val segons: Int, val andana: Int, val linia: Int)

    init {
        val horaris = circulacions.horaris
        val perCodi = andanes.indices.flatMap { a -> andanes[a].linies.map { normalitza(it) to a } }
            .groupBy({ it.first }, { it.second })
        // Per a cada patró, les seves parades que són andanes del jugador.
        val parades = HashMap<Int, List<Pair<Int, Int>>>()
        horaris.patrons.forEachIndexed { p, patro ->
            val linia = horaris.linies.getOrNull(patro.linia) ?: return@forEachIndexed
            val candidates = perCodi[normalitza(linia.codi)]?.filter {
                TipusVehicle.deMode(andanes[it].mode) == linia.tipus
            }.orEmpty()
            if (candidates.isEmpty()) return@forEachIndexed
            val tracat = circulacions.tracats.getOrNull(patro.tracat) ?: return@forEachIndexed
            // Per a cada andana, només la parada més propera del recorregut: un bus pot passar per dues parades a menys
            // del radi, i seria una sola arribada.
            val millor = HashMap<Int, Pair<Int, Double>>()
            for (k in patro.distancies.indices) {
                // Les parades virtuals de la frontera no són andanes.
                if (patro.parades?.getOrNull(k)?.let { it < 0 } == true) continue
                val punt = tracat.posicio(patro.distancies[k].toDouble())
                for (a in candidates) {
                    val d = distanciaMetres(punt.lat, punt.lon, andanes[a].lat, andanes[a].lon)
                    val radi = if (linia.tipus == TipusVehicle.BUS) radiBusMetres else radiTrenMetres
                    if (d <= radi && d < (millor[a]?.second ?: Double.MAX_VALUE)) millor[a] = k to d
                }
            }
            if (millor.isNotEmpty()) parades[p] = millor.map { (a, kd) -> kd.first to a }
        }
        perDia = TipusDia.entries.associateWith { tipus ->
            val pla = horaris.expedicions[tipus.clau].orEmpty()
            val events = mutableListOf<Event>()
            for (e in 0 until pla.size / 2) {
                val p = pla[2 * e]
                val inici = pla[2 * e + 1]
                val patro = horaris.patrons[p]
                parades[p]?.forEach { (k, a) -> events += Event(inici + patro.arribades[k], a, patro.linia) }
            }
            events.sortedBy { it.segons }
        }
    }

    /** Si alguna andana del jugador té arribades a l'horari. */
    val teArribades: Boolean get() = perDia.values.any { it.isNotEmpty() }

    /** Les arribades a les andanes del jugador entre [desDe] (exclòs) i [fins] (inclòs), en hora local de Catalunya. */
    fun entre(desDe: LocalDateTime, fins: LocalDateTime): List<ArribadaAndana> {
        if (!fins.isAfter(desDe)) return emptyList()
        val resultat = mutableListOf<ArribadaAndana>()
        // Les expedicions d'ahir poden arribar després de mitjanit (hores de més de 24:00 del GTFS).
        var dia: LocalDate = desDe.toLocalDate().minusDays(1)
        while (!dia.isAfter(fins.toLocalDate())) {
            val mitjanit = dia.atStartOfDay()
            val events = perDia.getValue(TipusDia.de(dia))
            val de = segonsDes(mitjanit, desDe)
            val fi = segonsDes(mitjanit, fins)
            var i = primerDespres(events, de)
            while (i < events.size && events[i].segons <= fi) {
                val e = events[i]
                resultat += ArribadaAndana(
                    andana = andanes[e.andana].id,
                    linia = circulacions.horaris.linies[e.linia],
                    hora = mitjanit.plusSeconds(e.segons.toLong()),
                )
                i++
            }
            dia = dia.plusDays(1)
        }
        return resultat.sortedBy { it.hora }
    }

    companion object {
        /**
         * Una parada de l'horari a menys d'aquesta distància d'una andana del jugador hi és la mateixa. Provat amb les
         * dades reals: a les estacions de tren, les arribades coincideixen amb les expedicions de `nodes.csv`; a les
         * parades de bus, amb 25 m, 47 de 60 parades a l'atzar queden entre el 80 i el 125 % (amb 50 m, 40).
         */
        const val RADI_TREN_METRES = 200.0
        const val RADI_BUS_METRES = 25.0

        fun normalitza(codi: String): String = codi.uppercase().replace(" ", "")

        private fun segonsDes(mitjanit: LocalDateTime, moment: LocalDateTime): Long =
            java.time.Duration.between(mitjanit, moment).seconds

        /** El primer event amb més segons que [minim] (exclòs). */
        private fun primerDespres(events: List<Event>, minim: Long): Int {
            var baix = 0
            var alt = events.size
            while (baix < alt) {
                val mig = (baix + alt) / 2
                if (events[mig].segons <= minim) baix = mig + 1 else alt = mig
            }
            return baix
        }
    }
}
