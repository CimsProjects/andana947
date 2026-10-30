package cat.andana947

import cat.andana947.data.joc.AndanaRegal
import cat.andana947.data.joc.OfertesRegal
import cat.andana947.data.joc.OpcioRegal
import cat.andana947.data.joc.ProvinciaRegal
import cat.andana947.introduccio.SeleccioRegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeleccioRegalTest {
    private fun andana(id: String) = AndanaRegal(id, id, "rodalies", "Renfe", "Municipi", emptyList(), 100.0, "estimat", 3.0)

    private val ofertes = OfertesRegal(
        maximPerProvincia = 2,
        provincies = listOf(
            ProvinciaRegal(
                "08", "Barcelona",
                listOf(
                    OpcioRegal("tram:a+b", "R5", "009EE0", listOf(andana("a"), andana("b"))),
                    OpcioRegal("c", nodes = listOf(andana("c"))),
                    OpcioRegal("d", nodes = listOf(andana("d"))),
                ),
            ),
            ProvinciaRegal("17", "Girona", listOf(OpcioRegal("e", nodes = listOf(andana("e"))))),
        ),
    )

    @Test
    fun `un tram compta com dues andanes`() {
        val s = SeleccioRegal(ofertes).commuta("tram:a+b")!!
        assertEquals(2, s.andanes("08"))
        assertTrue(s.ofertes.provincies.first().opcions.first().esTram)
    }

    @Test
    fun `no deixa passar del màxim per província`() {
        val s = SeleccioRegal(ofertes).commuta("tram:a+b")!!
        assertFalse(s.espot("c"))
        assertNull(s.commuta("c"))
    }

    @Test
    fun `dues andanes soles caben, una tercera no`() {
        val s = SeleccioRegal(ofertes).commuta("c")!!.commuta("d")!!
        assertEquals(2, s.andanes("08"))
        assertNull(s.commuta("tram:a+b"))
    }

    @Test
    fun `les províncies es compten per separat`() {
        val s = SeleccioRegal(ofertes).commuta("tram:a+b")!!.commuta("e")!!
        assertEquals(3, s.total)
        assertEquals(1, s.andanes("17"))
    }

    @Test
    fun `tornar a tocar una opció la destria`() {
        val s = SeleccioRegal(ofertes).commuta("c")!!.commuta("c")!!
        assertEquals(0, s.total)
    }
}
