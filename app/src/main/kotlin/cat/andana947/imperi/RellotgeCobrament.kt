package cat.andana947.imperi

import cat.andana947.data.joc.EstatCobraments
import java.time.OffsetDateTime

/**
 * El compte enrere fins al pròxim cobrament. Parteix de l'hora del servidor ([araServidorMs]) i hi suma el temps que
 * ha passat al mòbil des que es va rebre ([rebutLocalMs], un rellotge monòton com `SystemClock.elapsedRealtime`). Així,
 * si el rellotge del mòbil va avançat o algú el canvia, el compte enrere no s'equivoca.
 */
data class RellotgeCobrament(
    val darrerMs: Long,
    val properMs: Long,
    val araServidorMs: Long,
    val rebutLocalMs: Long,
) {
    fun restantMs(araLocalMs: Long): Long =
        (properMs - (araServidorMs + (araLocalMs - rebutLocalMs))).coerceAtLeast(0)

    /** De 0 (acaba de cobrar) a 1 (és l'hora del cobrament). */
    fun progres(araLocalMs: Long): Float {
        val total = properMs - darrerMs
        if (total <= 0) return 1f
        return ((total - restantMs(araLocalMs)).toFloat() / total).coerceIn(0f, 1f)
    }

    companion object {
        fun de(estat: EstatCobraments, rebutLocalMs: Long) = RellotgeCobrament(
            darrerMs = ms(estat.darrerCobramentEl),
            properMs = ms(estat.properCobramentEl),
            araServidorMs = ms(estat.ara),
            rebutLocalMs = rebutLocalMs,
        )

        private fun ms(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }
}

/** Hores, minuts i segons d'una durada, per escriure-la. */
data class Durada(val hores: Long, val minuts: Long, val segons: Long) {
    companion object {
        fun de(ms: Long): Durada {
            val s = ms.coerceAtLeast(0) / 1000
            return Durada(s / 3600, s % 3600 / 60, s % 60)
        }
    }
}
