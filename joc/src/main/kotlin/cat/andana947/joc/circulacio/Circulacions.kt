package cat.andana947.joc.circulacio

import java.time.LocalDateTime

/**
 * Un tren al mapa. [tracat] i [distancia] diuen on és sobre la via (per dibuixar-lo seguint-la); sense traçat, el tren
 * es dibuixa recte, orientat segons el [Punt.rumb]. [real] diu si la posició és de l'operador o *estimada* segons l'horari.
 */
data class VehicleAlMapa(
    val id: String,
    val linia: Int?,
    val posicio: Punt,
    val tracat: Int?,
    val distancia: Double?,
    val real: Boolean,
    val aturat: Boolean = false,
)

/**
 * On és cada tren segons l'horari: entre dues parades avança per la via accelerant i frenant, i a cada parada s'hi
 * espera fins a l'hora de sortida.
 */
class Circulacions(val horaris: HorarisTrens) {
    val tracats: List<Tracat> = horaris.tracats.map { Tracat(Polilinia.descodifica(it)) }

    private class Dia(val patrons: IntArray, val inicis: IntArray, val maxDurada: Int)

    private val dies: Map<TipusDia, Dia> = TipusDia.entries.associateWith { tipus ->
        val pla = horaris.expedicions[tipus.clau].orEmpty()
        val n = pla.size / 2
        val ordre = (0 until n).sortedBy { pla[2 * it + 1] }
        val patrons = IntArray(n) { pla[2 * ordre[it]] }
        Dia(
            patrons = patrons,
            inicis = IntArray(n) { pla[2 * ordre[it] + 1] },
            maxDurada = patrons.maxOfOrNull { horaris.patrons[it].durada } ?: 0,
        )
    }

    private val tipusLinia: Array<TipusVehicle> = horaris.linies.map { it.tipus }.toTypedArray()

    /** El tipus (tren o bus) del vehicle; els reals sense línia coneguda són trens (Renfe i FGC). */
    fun tipusDe(vehicle: VehicleAlMapa): TipusVehicle = vehicle.linia?.let { tipusLinia[it] } ?: TipusVehicle.TREN

    /** Els vehicles dels [tipus] demanats en circulació a l'hora local [ara] (Europa/Madrid). */
    fun enCirculacio(ara: LocalDateTime, tipus: Set<TipusVehicle> = TipusVehicle.TOTS): List<VehicleAlMapa> {
        val segons = ara.toLocalTime().toSecondOfDay()
        val avui = TipusDia.de(ara.toLocalDate())
        val ahir = TipusDia.de(ara.toLocalDate().minusDays(1))
        // Les expedicions d'ahir que passen de mitjanit (les hores de més de 24:00 del GTFS).
        return enCirculacio(ahir, segons + SEGONS_DIA, "a", tipus) + enCirculacio(avui, segons, "d", tipus)
    }

    /** Els trens del dia [tipus] als [segons] des de la seva mitjanit. [prefix] fa únics els identificadors. */
    fun enCirculacio(
        tipus: TipusDia,
        segons: Int,
        prefix: String = "d",
        vehicles: Set<TipusVehicle> = TipusVehicle.TOTS,
    ): List<VehicleAlMapa> {
        val dia = dies.getValue(tipus)
        val resultat = mutableListOf<VehicleAlMapa>()
        var i = primerAmbInici(dia.inicis, segons - dia.maxDurada)
        while (i < dia.inicis.size && dia.inicis[i] <= segons) {
            val patro = horaris.patrons[dia.patrons[i]]
            val t = segons - dia.inicis[i]
            // Els vehicles amagats ni es calculen: amb els busos, són la gran majoria.
            if (t <= patro.durada && tipusLinia[patro.linia] in vehicles) {
                val (distancia, aturat) = distanciaRecorreguda(patro, t.toDouble())
                val tracat = tracats[patro.tracat]
                resultat += VehicleAlMapa(
                    id = "$prefix${tipus.ordinal}:$i",
                    linia = patro.linia,
                    posicio = tracat.posicio(distancia),
                    tracat = patro.tracat,
                    distancia = distancia,
                    real = false,
                    aturat = aturat,
                )
            }
            i++
        }
        return resultat
    }

    /** Les línies d'un prefix de feed (`rodalies:`, `fgc:`…) amb el [codi] donat, en majúscules i sense espais. */
    fun liniesAmbCodi(prefix: String, codi: String): List<Int> {
        val net = codi.uppercase().replace(" ", "")
        return horaris.linies.indices.filter {
            val l = horaris.linies[it]
            l.id.startsWith(prefix) && l.codi.uppercase().replace(" ", "") == net
        }
    }

    /** Els traçats que fan servir les [linies]. */
    fun tracatsDe(linies: Collection<Int>): Set<Int> {
        val conjunt = linies.toSet()
        return horaris.patrons.filter { it.linia in conjunt }.mapTo(mutableSetOf()) { it.tracat }
    }

    companion object {
        const val SEGONS_DIA = 86_400

        private fun primerAmbInici(inicis: IntArray, minim: Int): Int {
            var baix = 0
            var alt = inicis.size
            while (baix < alt) {
                val mig = (baix + alt) / 2
                if (inicis[mig] < minim) baix = mig + 1 else alt = mig
            }
            return baix
        }

        /**
         * Metres recorreguts [t] segons després de sortir, i si el tren és aturat a una parada. Entre parades, el tren
         * accelera i frena (corba *smoothstep*), en comptes d'anar sempre a la mateixa velocitat.
         */
        fun distanciaRecorreguda(patro: Patro, t: Double): Pair<Double, Boolean> {
            val n = patro.arribades.size
            for (i in 0 until n) {
                if (t < patro.arribades[i]) {
                    // Entre la parada i - 1 i la i.
                    val sortida = patro.sortida(i - 1).toDouble()
                    val arribada = patro.arribades[i].toDouble()
                    val d0 = patro.distancies[i - 1].toDouble()
                    val d1 = patro.distancies[i].toDouble()
                    val f = if (arribada <= sortida) 1.0 else ((t - sortida) / (arribada - sortida)).coerceIn(0.0, 1.0)
                    return d0 + (d1 - d0) * f * f * (3 - 2 * f) to false
                }
                if (t <= patro.sortida(i)) return patro.distancies[i].toDouble() to true
            }
            return patro.distancies.last().toDouble() to true
        }
    }
}
