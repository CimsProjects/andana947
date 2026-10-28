package cat.andana947.joc

import cat.andana947.joc.economia.Concessions
import cat.andana947.joc.economia.ResultatRenovacio
import cat.andana947.joc.progressio.Prestigi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressioTest {
    private val prestigi = Prestigi(Repositori.config)
    private val concessions = Concessions(Repositori.config)

    @Test
    fun `els nivells segueixen el GDD`() {
        // GDD 5.1: el 2 demana 100 punts, el 3 283, el 4 520 i el 5 800.
        assertEquals(listOf(0, 100, 283, 520, 800), (1..5).map(prestigi::puntsPerNivell))
        assertEquals(1, prestigi.nivell(0))
        assertEquals(1, prestigi.nivell(99))
        assertEquals(2, prestigi.nivell(100))
        assertEquals(4, prestigi.nivell(730))
        assertEquals(5, prestigi.nivell(800))
    }

    @Test
    fun `la llicència creix amb el nivell`() {
        assertEquals(12, prestigi.maximConcessions(1))
        assertEquals(20, prestigi.maximConcessions(5))
    }

    @Test
    fun `una concessió dura 14 dies`() {
        val c = concessions.nova("x", dia = 1)
        assertTrue(c.activa(14))
        assertFalse(c.activa(15))
    }

    @Test
    fun `el check-in de la segona setmana la renova`() {
        val c = concessions.nova("x", dia = 1) // fins al 14
        assertEquals(ResultatRenovacio.VIGENT, concessions.checkin(c, 7).second)
        val (renovada, resultat) = concessions.checkin(c, 8)
        assertEquals(ResultatRenovacio.RENOVADA, resultat)
        assertEquals(21, renovada.finsA)
    }

    @Test
    fun `una concessió caducada es reactiva sense tornar-la a pagar`() {
        val c = concessions.nova("x", dia = 1)
        val (reactivada, resultat) = concessions.checkin(c, 20)
        assertEquals(ResultatRenovacio.REACTIVADA, resultat)
        assertEquals(33, reactivada.finsA)
    }
}
