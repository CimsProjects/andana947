package cat.andana947

import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.ResultatCompra
import cat.andana947.data.joc.ServeiJocLocal
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.EstatTram
import cat.andana947.joc.trams.TramDelJoc
import cat.andana947.mapa.CapaTrams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Trams de prova (no són del joc). */
private val cataleg = CatalegTrams(
    versio = 1,
    capitalInicial = 500,
    recompenses = mapOf("petit" to listOf(1, 5), "mitja" to listOf(5, 10)),
    trams = listOf(
        TramDelJoc("p", "P1", "Petit", "bus", "", "petit", 300, 5, 57, listOf("l1")),
        TramDelJoc("m", "M1", "Mitjà", "bus", "", "mitja", 3000, 20, 300, listOf("l2")),
        TramDelJoc("g", "G1", "Gran", "metro", "", "gran", null, 30, 9000, listOf("l3")),
    ),
)

private object FontDeProva : FontTrams {
    override suspend fun cataleg() = cataleg
}

class TramsTest {
    @Test
    fun `la partida local comença amb el capital inicial i compra com el servidor`() = runTest {
        val joc = ServeiJocLocal(FontDeProva)
        assertEquals(500, joc.estat().saldo)
        assertEquals(ResultatCompra.GRAN.clau, joc.compra("g").resultat)
        assertEquals(ResultatCompra.SENSE_MONEDES.clau, joc.compra("m").resultat)
        assertEquals(ResultatCompra.NO_EXISTEIX.clau, joc.compra("x").resultat)
        val compra = joc.compra("p")
        assertEquals(ResultatCompra.COMPRAT.clau, compra.resultat)
        assertEquals(200L, compra.saldo)
        assertEquals(ResultatCompra.JA_ES_TEU.clau, joc.compra("p").resultat)
        assertEquals(listOf("p"), joc.estat().trams)
    }

    @Test
    fun `una resposta desconeguda del servidor es tracta com si no hi hagués connexió`() {
        assertEquals(ResultatCompra.COMPRAT, ResultatCompra.de("comprat"))
        assertEquals(ResultatCompra.SENSE_CONNEXIO, ResultatCompra.de("???"))
    }

    @Test
    fun `cada estat té un color diferent en els dos temes`() {
        for (fosc in listOf(false, true)) {
            val colors = CapaTrams.colors(fosc)
            assertEquals(EstatTram.entries.toSet(), colors.keys)
            assertEquals(colors.size, colors.values.toSet().size)
            assertTrue(colors.values.all { Regex("#[0-9A-F]{6}").matches(it) })
        }
    }
}
