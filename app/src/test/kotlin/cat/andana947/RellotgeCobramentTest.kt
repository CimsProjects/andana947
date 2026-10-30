package cat.andana947

import cat.andana947.data.joc.EstatCobraments
import cat.andana947.imperi.Durada
import cat.andana947.imperi.RellotgeCobrament
import org.junit.Assert.assertEquals
import org.junit.Test

class RellotgeCobramentTest {
    private val estat = EstatCobraments(
        ara = "2026-10-30T11:46:30+00:00",
        darrerCobramentEl = "2026-10-30T08:00:00+00:00",
        properCobramentEl = "2026-10-30T12:00:00+00:00",
        periodeSegons = 14_400,
        cobramentPrevist = 6.67,
        saldo = 200.0,
    )

    @Test
    fun `el compte enrere parteix de l'hora del servidor`() {
        val r = RellotgeCobrament.de(estat, rebutLocalMs = 1_000)
        assertEquals(13 * 60_000L + 30_000L, r.restantMs(araLocalMs = 1_000))
        assertEquals(13 * 60_000L, r.restantMs(araLocalMs = 31_000))
    }

    @Test
    fun `passada l'hora, queda zero i el progrés és complet`() {
        val r = RellotgeCobrament.de(estat, rebutLocalMs = 0)
        assertEquals(0L, r.restantMs(araLocalMs = 3_600_000))
        assertEquals(1f, r.progres(araLocalMs = 3_600_000), 0.0001f)
    }

    @Test
    fun `el progrés compta des del darrer cobrament`() {
        val r = RellotgeCobrament.de(estat, rebutLocalMs = 0)
        // 3 h 46 min 30 s de 4 h
        assertEquals(13_590f / 14_400f, r.progres(araLocalMs = 0), 0.0001f)
    }

    @Test
    fun `accepta dates en UTC amb Z`() {
        val r = RellotgeCobrament.de(estat.copy(ara = "2026-10-30T11:59:00Z"), rebutLocalMs = 0)
        assertEquals(60_000L, r.restantMs(araLocalMs = 0))
    }

    @Test
    fun `la durada es parteix en hores, minuts i segons`() {
        assertEquals(Durada(1, 23, 5), Durada.de((3600 + 23 * 60 + 5) * 1000L))
        assertEquals(Durada(0, 0, 0), Durada.de(-5))
    }
}
