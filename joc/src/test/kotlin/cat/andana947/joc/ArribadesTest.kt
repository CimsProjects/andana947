package cat.andana947.joc

import cat.andana947.joc.circulacio.AndanaPropia
import cat.andana947.joc.circulacio.Arribades
import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.LiniaCirculacio
import cat.andana947.joc.circulacio.Patro
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArribadesTest {
    // Un traçat de (0, 0) a (1, 1) («??_ibE_ibE»), d'uns 157 km: la primera parada és a (0, 0) i l'última, més enllà
    // del final, queda a (1, 1).
    private val horaris = HorarisTrens(
        versio = 1,
        generat = "2026-11-02",
        linies = listOf(
            LiniaCirculacio("rodalies:R5", "R5", "rodalies", "Renfe", "009EE0"),
            LiniaCirculacio("bus:R5", "R5", "bus", "Bus", "000000"),
        ),
        tracats = listOf("??_ibE_ibE"),
        patrons = listOf(
            Patro(linia = 0, tracat = 0, distancies = listOf(0, 200_000), arribades = listOf(0, 3_600), parades = listOf(0, 1)),
            // Un bus amb el mateix codi i el mateix recorregut: no arriba a una estació de tren.
            Patro(linia = 1, tracat = 0, distancies = listOf(0, 200_000), arribades = listOf(0, 3_600), parades = listOf(0, 1)),
        ),
        parades = listOf("Inici", "Final"),
        // Feiners: el tren surt a les 08:00 i a les 23:30 (arriba a la primera parada a l'hora de sortir).
        expedicions = mapOf(
            "feiner" to listOf(0, 8 * 3600, 1, 8 * 3600, 0, 23 * 3600 + 1800),
            "dissabte" to emptyList(),
            "festiu" to emptyList(),
        ),
    )
    private val circulacions = Circulacions(horaris)

    // A uns 55 m de la primera parada.
    private val andana = AndanaPropia("renfe:1", lat = 0.0005, lon = 0.0, linies = listOf("R5"), mode = "rodalies")
    private val dimecres = LocalDateTime.of(2026, 11, 4, 0, 0)

    @Test
    fun `troba les arribades del tren a l'andana`() {
        val a = Arribades(circulacions, listOf(andana))
        val arribades = a.entre(dimecres.withHour(7), dimecres.withHour(9))
        assertEquals(1, arribades.size)
        assertEquals("renfe:1", arribades[0].andana)
        assertEquals("R5", arribades[0].linia.codi)
        assertEquals("rodalies", arribades[0].linia.mode)
        assertEquals(dimecres.withHour(8), arribades[0].hora)
    }

    @Test
    fun `la finestra exclou l'inici i inclou el final`() {
        val a = Arribades(circulacions, listOf(andana))
        assertTrue(a.entre(dimecres.withHour(8), dimecres.withHour(9)).isEmpty())
        assertEquals(1, a.entre(dimecres.withHour(7), dimecres.withHour(8)).size)
    }

    @Test
    fun `una andana lluny de les parades o d'una altra línia no té arribades`() {
        assertFalse(Arribades(circulacions, listOf(andana.copy(lat = 0.5))).teArribades)
        assertFalse(Arribades(circulacions, listOf(andana.copy(linies = listOf("R1")))).teArribades)
    }

    @Test
    fun `els busos arriben a les parades de bus`() {
        // Les parades de bus s'aparellen més a prop (a uns 11 m).
        val parada = andana.copy(id = "bus:1", mode = "bus", lat = 0.0001)
        val arribades = Arribades(circulacions, listOf(parada)).entre(dimecres.withHour(7), dimecres.withHour(9))
        assertEquals(listOf("bus"), arribades.map { it.linia.mode })
    }

    @Test
    fun `una parada de bus a l'altra banda del carrer no és la mateixa`() {
        assertFalse(Arribades(circulacions, listOf(andana.copy(id = "bus:1", mode = "bus"))).teArribades)
    }

    @Test
    fun `les arribades de l'altra banda de mitjanit compten al dia següent`() {
        // A l'andana final, el tren de les 23:30 arriba a les 00:30 de dijous.
        val final = andana.copy(id = "renfe:2", lat = 1.0, lon = 1.0005)
        val a = Arribades(circulacions, listOf(final))
        val arribades = a.entre(dimecres.plusDays(1), dimecres.plusDays(1).withHour(1))
        assertEquals(listOf(dimecres.plusDays(1).withMinute(30)), arribades.map { it.hora })
    }
}
