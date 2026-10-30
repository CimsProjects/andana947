package cat.andana947.introduccio

import cat.andana947.data.joc.OfertesRegal

/**
 * Les opcions de regal triades. Només serveix per guiar el jugador: qui decideix si la tria és vàlida és el servidor
 * (`tria_regal`).
 */
data class SeleccioRegal(val ofertes: OfertesRegal, val triades: Set<String> = emptySet()) {
    private val provinciaDe: Map<String, String> =
        ofertes.provincies.flatMap { p -> p.opcions.map { it.id to p.provincia } }.toMap()
    private val andanesDe: Map<String, Int> =
        ofertes.provincies.flatMap { p -> p.opcions.map { it.id to it.nodes.size } }.toMap()

    /** Quantes andanes s'han triat a una província. */
    fun andanes(provincia: String): Int =
        triades.filter { provinciaDe[it] == provincia }.sumOf { andanesDe[it] ?: 0 }

    val total: Int get() = triades.sumOf { andanesDe[it] ?: 0 }

    /** Si encara es pot triar aquesta opció sense passar del màxim de la província. */
    fun espot(opcio: String): Boolean {
        if (opcio in triades) return true
        val provincia = provinciaDe[opcio] ?: return false
        return andanes(provincia) + (andanesDe[opcio] ?: 0) <= ofertes.maximPerProvincia
    }

    /** Tria o destria una opció. Si triar-la passaria del màxim, torna null. */
    fun commuta(opcio: String): SeleccioRegal? = when {
        opcio in triades -> copy(triades = triades - opcio)
        espot(opcio) -> copy(triades = triades + opcio)
        else -> null
    }
}
