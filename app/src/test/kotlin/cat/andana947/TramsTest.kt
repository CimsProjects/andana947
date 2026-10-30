package cat.andana947

import cat.andana947.compra.CompraViewModel
import cat.andana947.compra.FiltreCompra
import cat.andana947.data.joc.EstatPartida
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.Resultat
import cat.andana947.data.joc.ServeiJocLocal
import cat.andana947.data.joc.TramJugador
import cat.andana947.imperi.ImperiViewModel
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.EstatTram
import cat.andana947.joc.trams.TramDelJoc
import cat.andana947.mapa.CapaTrams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trams de prova (no són del joc). */
private val cataleg = CatalegTrams(
    versio = 1,
    capitalInicial = 230,
    recompenses = mapOf("petit" to listOf(1, 5), "mitja" to listOf(5, 10)),
    creixementMillora = 1.08,
    arrodonimentPreu = 1,
    minutsServei = 1140,
    trams = listOf(
        TramDelJoc("p", "P1", "Petit de Gràcia", "bus", "", "petit", 100, 5, 57, listOf("l1")),
        TramDelJoc("c", "C1", "Curt", "bus", "", "petit", 10, 3, 30, listOf("l4")),
        TramDelJoc("m", "M1", "Mitjà", "bus", "", "mitja", 300, 20, 300, listOf("l2")),
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
        assertEquals(230, joc.estat().saldo)
        assertEquals(Resultat.GRAN.clau, joc.compra("g").resultat)
        assertEquals(Resultat.SENSE_MONEDES.clau, joc.compra("m").resultat)
        assertEquals(Resultat.NO_EXISTEIX.clau, joc.compra("x").resultat)
        val compra = joc.compra("p")
        assertEquals(Resultat.COMPRAT.clau, compra.resultat)
        assertEquals(130L, compra.saldo)
        assertEquals(1, compra.andanes)
        assertEquals(Resultat.JA_ES_TEU.clau, joc.compra("p").resultat)
        assertEquals(listOf(TramJugador("p", 1)), joc.estat().trams)
    }

    @Test
    fun `les millores afegeixen andanes fins a totes les parades`() = runTest {
        val joc = ServeiJocLocal(FontDeProva)
        assertEquals(Resultat.NO_ES_TEU.clau, joc.millora("c").resultat)
        joc.compra("c")
        val millora = joc.millora("c")
        assertEquals(Resultat.MILLORAT.clau, millora.resultat)
        assertEquals(2, millora.andanes)
        assertEquals(230L - 10 - 11, millora.saldo)
        assertEquals(3, joc.millora("c").andanes)
        assertEquals(Resultat.MAXIM.clau, joc.millora("c").resultat)
    }

    @Test
    fun `una resposta desconeguda del servidor es tracta com si no hi hagués connexió`() {
        assertEquals(Resultat.COMPRAT, Resultat.de("comprat"))
        assertEquals(Resultat.MILLORAT, Resultat.de("millorat"))
        assertEquals(Resultat.SENSE_CONNEXIO, Resultat.de("???"))
    }

    @Test
    fun `la llista de compra no té grans ni trams propis, i va del més barat al més car`() {
        val partida = EstatPartida(temporada = 1, saldo = 50, trams = listOf(TramJugador("c", 1)))
        val llista = CompraViewModel.enVenda(cataleg, partida, "", FiltreCompra.TOTS)
        assertEquals(listOf("p", "m"), llista.map { it.tram.id })
        assertFalse(llista.first().esPotPermetre)
        assertEquals(emptyList(), CompraViewModel.enVenda(cataleg, partida, "", FiltreCompra.ASSEQUIBLES))
        // Sense fer cas de majúscules ni d'accents.
        assertEquals(listOf("p"), CompraViewModel.enVenda(cataleg, partida, "gracia", FiltreCompra.TOTS).map { it.tram.id })
    }

    @Test
    fun `l'imperi diu el cost de la millora i el que es guanyaria`() {
        val partida = EstatPartida(temporada = 1, saldo = 20, trams = listOf(TramJugador("c", 1), TramJugador("p", 5)))
        val (curt, petit) = ImperiViewModel.propis(cataleg, partida)
        assertEquals(11, curt.costMillora)
        assertTrue(curt.esPotPermetre)
        assertTrue(curt.monedesPerMinutMillorat!! > curt.monedesPerMinut)
        assertNull(petit.costMillora)
        assertFalse(petit.esPotPermetre)
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
