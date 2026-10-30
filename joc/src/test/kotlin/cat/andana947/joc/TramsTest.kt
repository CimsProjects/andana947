package cat.andana947.joc

import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.EstatTram
import cat.andana947.joc.trams.FormulesTrams
import cat.andana947.joc.trams.Mida
import cat.andana947.joc.trams.Trams
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TramsTest {
    private val config = Repositori.config
    private val trams = Trams(config)

    @Test
    fun `els valors són els del GDD`() {
        with(config.trams) {
            assertEquals(1..5, trams.recompensa(Mida.PETIT))
            assertEquals(5..10, trams.recompensa(Mida.MITJA))
            assertEquals(4, diesRetorn)
            assertEquals(14, duradaTemporadaDies)
            assertTrue(horaIniciServei < horaFiServei)
        }
    }

    @Test
    fun `un tram mitjà dona més per arribada que un de petit`() {
        assertTrue(trams.recompensa(Mida.MITJA)!!.first >= trams.recompensa(Mida.PETIT)!!.last)
        assertNull(trams.recompensa(Mida.GRAN))
    }

    @Test
    fun `comprar un tram és una andana, i val quatre dies de monedes`() {
        assertEquals(36, trams.preu(36, 12, Mida.PETIT))
        assertEquals(390, trams.preu(273, 21, Mida.MITJA))
        assertEquals(config.trams.preuMinim, trams.preu(1, 3, Mida.PETIT))
        assertNull(trams.preu(5000, 30, Mida.GRAN))
    }

    @Test
    fun `cada andana més costa més que l'anterior`() {
        val costos = (1..20).map { trams.costMillora(36, it) }
        assertTrue(costos.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(costos.first() >= 36)
        assertEquals(FormulesTrams.arrodoneix(36 * config.trams.creixementMillora, config.trams.arrodonimentPreu), costos.first())
    }

    @Test
    fun `les monedes per minut creixen amb les andanes`() {
        // 57 arribades en 19 parades: 3 per andana i dia; amb 2 andanes, 6 arribades × 3 monedes en 19 hores.
        val perMinut = FormulesTrams.monedesPerMinut(57, 19, 2, 3.0, trams.minutsServei)
        assertEquals(18.0 / trams.minutsServei, perMinut, 1e-12)
    }

    @Test
    fun `la mida es parteix en terços`() {
        val talls = trams.talls((1..9).toList())
        assertEquals(4 to 7, talls)
        assertEquals(Mida.PETIT, trams.mida(3, talls))
        assertEquals(Mida.MITJA, trams.mida(4, talls))
        assertEquals(Mida.GRAN, trams.mida(7, talls))
    }

    @Test
    fun `el color del tram al mapa`() {
        assertEquals(EstatTram.TEU, EstatTram.de(400, Mida.PETIT, 0, esTeu = true))
        assertEquals(EstatTram.ES_POT_COMPRAR, EstatTram.de(400, Mida.PETIT, 400, esTeu = false))
        assertEquals(EstatTram.NO_ET_LA_POTS_PERMETRE, EstatTram.de(400, Mida.PETIT, 399, esTeu = false))
        assertEquals(EstatTram.NO_ET_LA_POTS_PERMETRE, EstatTram.de(null, Mida.GRAN, 1_000_000, esTeu = false))
    }

    @Test
    fun `les arribades es reparteixen entre l'inici i la fi del servei`() {
        assertEquals(0.0, trams.arribadesFinsA(190.0, 3.0))
        assertEquals(190.0, trams.arribadesFinsA(190.0, 24.0))
        val mig = (config.trams.horaIniciServei + config.trams.horaFiServei) / 2.0
        assertEquals(95.0, trams.arribadesFinsA(190.0, mig), 1e-9)
    }

    @Test
    fun `els trams generats quadren amb les regles`() {
        val fitxer = File(Repositori.arrel, "dades/generades/trams.csv")
        val files = fitxer.readLines().drop(1).map { it.split(",") }
        val capçalera = fitxer.readLines().first().split(",")
        val arribades = capçalera.indexOf("arribades_feiner")
        val mida = capçalera.indexOf("mida")
        val preu = capçalera.indexOf("preu")
        val parades = capçalera.indexOf("parades")
        // El nom pot portar comes entre cometes: es compta des del final.
        fun camp(fila: List<String>, i: Int) = fila[fila.size - (capçalera.size - i)]
        val talls = trams.talls(files.map { camp(it, arribades).toInt() })
        for (fila in files) {
            val a = camp(fila, arribades).toInt()
            val m = trams.mida(a, talls)
            assertEquals(m, Mida.de(camp(fila, mida)), fila.first())
            assertEquals(trams.preu(a, camp(fila, parades).toInt(), m), camp(fila, preu).toIntOrNull(), fila.first())
            assertTrue(camp(fila, parades).toInt() >= config.trams.minimParades, fila.first())
        }
        // Amb el capital inicial es pot comprar qualsevol tram petit, però no gaire més: és just per a un.
        val petits = files.filter { trams.mida(camp(it, arribades).toInt(), talls) == Mida.PETIT }.map { camp(it, preu).toInt() }
        assertTrue(petits.all { it <= config.trams.capitalInicial })
        assertTrue(config.trams.capitalInicial < petits.max() + config.trams.preuMinim * 2)
    }
}

class CatalegTramsTest {
    private val cataleg = CatalegTrams.llegeix(File(Repositori.arrel, "dades/generades/app/trams.json").readText())

    @Test
    fun `el catàleg de l'app quadra amb la configuració`() {
        val config = Repositori.config.trams
        assertEquals(config.capitalInicial, cataleg.capitalInicial)
        assertEquals(config.recompensaPetitMinima..config.recompensaPetitMaxima, cataleg.recompensa(Mida.PETIT))
        assertEquals(config.recompensaMitjaMinima..config.recompensaMitjaMaxima, cataleg.recompensa(Mida.MITJA))
        assertNull(cataleg.recompensa(Mida.GRAN))
        assertEquals(config.creixementMillora, cataleg.creixementMillora)
        assertEquals(Trams(Repositori.config).minutsServei, cataleg.minutsServei)
    }

    @Test
    fun `un tram es millora fins que té totes les parades`() {
        val tram = cataleg.trams.first { it.midaTram == Mida.PETIT }
        assertEquals(Trams(Repositori.config).costMillora(tram.preu!!, 1), cataleg.costMillora(tram, 1))
        assertNull(cataleg.costMillora(tram, tram.parades))
        assertTrue(cataleg.monedesPerMinut(tram, 2) > cataleg.monedesPerMinut(tram, 1))
    }

    @Test
    fun `els trams grans no tenen preu i els altres sí`() {
        for (tram in cataleg.trams) {
            assertEquals(tram.midaTram == Mida.GRAN, tram.preu == null, tram.id)
        }
    }

    @Test
    fun `gairebé tots els trams es poden dibuixar amb els traçats dels horaris`() {
        val horaris = HorarisTrens.llegeix(File(Repositori.arrel, "dades/generades/app/circulacions.json").readText())
        val tracats = cataleg.tracats(horaris)
        assertTrue(tracats.size >= cataleg.trams.size * 9 / 10, "Només ${tracats.size} de ${cataleg.trams.size}")
        assertTrue(tracats.values.flatten().all { it in horaris.tracats.indices })
    }
}
