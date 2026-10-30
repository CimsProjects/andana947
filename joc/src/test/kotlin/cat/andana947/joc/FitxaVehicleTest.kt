package cat.andana947.joc

import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.Fitxes
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.LiniaCirculacio
import cat.andana947.joc.circulacio.ParadaHora
import cat.andana947.joc.circulacio.Patro
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitxaVehicleTest {
    // Un dimecres (feiner) a les 08:01:00.
    private val dimecres = LocalDateTime.of(2026, 11, 4, 8, 1, 0)

    // El traçat no es fa servir per a la fitxa: n'hi ha prou amb una via qualsevol («??_ibE_ibE» = 0,0 → 0,1).
    private fun horaris(patro: Patro, inicis: List<Int> = listOf(8 * 3600)) = HorarisTrens(
        versio = 1,
        generat = "2026-11-02",
        linies = listOf(LiniaCirculacio("rodalies:T", "R1", "rodalies", "Renfe", "7DBCEC")),
        tracats = listOf("??_ibE_ibE"),
        patrons = listOf(patro),
        parades = listOf("Nord", "Mig", "Sud", "Portbou"),
        expedicions = mapOf("feiner" to inicis.flatMap { listOf(0, it) }, "dissabte" to emptyList(), "festiu" to emptyList()),
    )

    // Nord a les 08:00, Mig a les 08:02 (s'hi espera fins a les 08:02:30) i Sud a les 08:04.
    private val patro = Patro(
        linia = 0, tracat = 0, distancies = listOf(0, 500, 1000),
        arribades = listOf(0, 120, 240), sortides = listOf(0, 150, 240), parades = listOf(0, 1, 2),
    )

    @Test
    fun `d'on ve, cap on va i la pròxima parada, amb l'hora`() {
        val f = Fitxes.estimat(Circulacions(horaris(patro)), "d0:0", dimecres)!!
        assertEquals("R1", f.linia?.codi)
        assertEquals(ParadaHora("Nord", 8 * 3600), f.origen)
        assertEquals(ParadaHora("Sud", 8 * 3600 + 240), f.desti)
        assertEquals(ParadaHora("Mig", 8 * 3600 + 120), f.propera)
        assertNull(f.aturatA)
    }

    @Test
    fun `aturat a l'andana`() {
        val f = Fitxes.estimat(Circulacions(horaris(patro)), "d0:0", dimecres.withMinute(2).withSecond(10))!!
        assertEquals("Mig", f.aturatA?.nom)
        assertEquals("Sud", f.propera?.nom)
    }

    @Test
    fun `si surt de Catalunya, el destí és el de veritat`() {
        // L'última parada és virtual (la frontera); el tren acaba a Portbou als 600 s.
        val surt = patro.copy(parades = listOf(0, 1, -1), desti = listOf(3, 600))
        val f = Fitxes.estimat(Circulacions(horaris(surt)), "d0:0", dimecres)!!
        assertEquals(ParadaHora("Portbou", 8 * 3600 + 600), f.desti)
    }

    @Test
    fun `els identificadors que no són d'un vehicle estimat no tenen fitxa`() {
        val c = Circulacions(horaris(patro))
        assertNull(Fitxes.estimat(c, "renfe:123", dimecres))
        assertNull(Fitxes.estimat(c, "d0:9", dimecres))
    }

    @Test
    fun `un vehicle real té els noms però no les hores`() {
        val f = Fitxes.real(Circulacions(horaris(patro)), linia = 0, tracat = 0, distancia = 300.0)
        assertTrue(f.real)
        assertEquals(ParadaHora("Nord", null), f.origen)
        assertEquals(ParadaHora("Mig", null), f.propera)
        assertEquals(ParadaHora("Sud", null), f.desti)
    }

    @Test
    fun `sense noms de parades, només la línia`() {
        val f = Fitxes.estimat(Circulacions(horaris(patro.copy(parades = null))), "d0:0", dimecres)!!
        assertEquals("R1", f.linia?.codi)
        assertNull(f.origen)
        assertNull(f.propera)
    }
}
