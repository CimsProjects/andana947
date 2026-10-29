package cat.andana947.joc

import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.Flota
import cat.andana947.joc.circulacio.Forma
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.LiniaCirculacio
import cat.andana947.joc.circulacio.Patro
import cat.andana947.joc.circulacio.Polilinia
import cat.andana947.joc.circulacio.Punt
import cat.andana947.joc.circulacio.Silueta
import cat.andana947.joc.circulacio.TipusDia
import cat.andana947.joc.circulacio.Tracat
import cat.andana947.joc.circulacio.VehicleReal
import cat.andana947.joc.geo.distanciaMetres
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CirculacionsTest {
    // Una via recta de nord a sud d'uns 1.112 m (0,01° de latitud), amb una parada al mig.
    private val nord = Punt(41.41, 2.2)
    private val sud = Punt(41.40, 2.2)
    private val llargada = distanciaMetres(41.41, 2.2, 41.40, 2.2)

    private fun horaris(expedicions: Map<String, List<Int>>) = HorarisTrens(
        versio = 1,
        generat = "2026-11-02",
        linies = listOf(
            LiniaCirculacio("rodalies:T", "R1", "rodalies", "Renfe", "7DBCEC"),
            LiniaCirculacio("tmb:M", "L1", "metro", "TMB", "CE1126"),
        ),
        // La mateixa via codificada com ho fa el script ("_p~iF~ps|U" és el punt de l'exemple de Google).
        tracats = listOf(codifica(listOf(nord, sud))),
        patrons = listOf(
            // 0 → 120 s fins al mig, s'hi espera 30 s i arriba al final als 240 s.
            Patro(linia = 0, tracat = 0, distancies = listOf(0, (llargada / 2).toInt(), llargada.toInt()),
                arribades = listOf(0, 120, 240), sortides = listOf(0, 150, 240)),
            Patro(linia = 1, tracat = 0, distancies = listOf(0, llargada.toInt()), arribades = listOf(0, 600)),
        ),
        expedicions = expedicions,
    )

    @Test
    fun `la polilínia de Google es descodifica`() {
        val punts = Polilinia.descodifica("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), punts.map { it.lat to it.lon })
    }

    @Test
    fun `el tipus de dia surt del dia de la setmana`() {
        assertEquals(TipusDia.FEINER, TipusDia.de(LocalDate.of(2026, 11, 4)))
        assertEquals(TipusDia.DISSABTE, TipusDia.de(LocalDate.of(2026, 11, 7)))
        assertEquals(TipusDia.FESTIU, TipusDia.de(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `el tren avança per la via, s'atura a la parada i arriba al final`() {
        val c = Circulacions(horaris(mapOf("feiner" to listOf(0, 8 * 3600))))
        fun a(segons: Int) = c.enCirculacio(TipusDia.FEINER, 8 * 3600 + segons)

        assertTrue(c.enCirculacio(TipusDia.FEINER, 8 * 3600 - 1).isEmpty())
        assertEquals(0.0, a(0).single().distancia!!, 0.1)
        // A mig camí de la primera parada, a mitja distància (la corba és simètrica).
        assertEquals(llargada / 4, a(60).single().distancia!!, 1.0)
        // Accelera: als 30 s ha fet menys d'una quarta part del tros.
        assertTrue(a(30).single().distancia!! < llargada / 8)
        val aturat = a(135).single()
        assertTrue(aturat.aturat)
        assertEquals((llargada / 2).toInt().toDouble(), aturat.distancia!!, 0.1)
        assertFalse(a(200).single().aturat)
        assertEquals(sud.lat, a(240).single().posicio.lat, 1e-5)
        assertTrue(a(241).isEmpty())
        // Va de nord a sud: rumb 180°.
        assertEquals(180.0, a(60).single().posicio.rumb, 0.5)
    }

    @Test
    fun `les expedicions d'ahir que passen de mitjanit continuen`() {
        // Surt a les 23:58 del dimarts: dimecres a les 00:01 encara circula.
        val c = Circulacions(horaris(mapOf("feiner" to listOf(1, 23 * 3600 + 58 * 60))))
        val vehicles = c.enCirculacio(LocalDateTime.of(2026, 11, 4, 0, 1))
        assertEquals(1, vehicles.size)
        assertEquals(1, vehicles.single().linia)
    }

    @Test
    fun `les línies es busquen pel codi`() {
        val c = Circulacions(horaris(emptyMap()))
        assertEquals(listOf(0), c.liniesAmbCodi("rodalies:", "r1"))
        assertTrue(c.liniesAmbCodi("fgc:", "R1").isEmpty())
        assertEquals(setOf(0), c.tracatsDe(listOf(0)))
    }

    @Test
    fun `el tren real substitueix els de la seva línia i es mou per la via`() {
        val c = Circulacions(horaris(mapOf("feiner" to listOf(0, 8 * 3600, 1, 8 * 3600))))
        val flota = Flota(c, duradaAnimacioMs = 10_000)
        val ara = LocalDateTime.of(2026, 11, 4, 8, 1)
        assertEquals(2, flota.vehicles(ara, 0).size)

        // Un tren real de la R1 a 50 m de la via, a un quart del recorregut.
        val v1 = VehicleReal("23537", "renfe", "R1", 41.4075, 2.2006)
        flota.actualitzaReals(listOf(v1), setOf("renfe"), 0)
        val vehicles = flota.vehicles(ara, 0)
        assertEquals(2, vehicles.size)
        val real = vehicles.single { it.real }
        assertEquals("renfe:23537", real.id)
        assertEquals(0, real.linia)
        assertEquals(llargada / 4, real.distancia!!, 3.0)
        // El metro (sense font real) continua segons l'horari.
        assertEquals(1, vehicles.single { !it.real }.linia)

        // Arriba la posició següent: a mig camí de l'animació, el tren és a mig camí sobre la via.
        flota.actualitzaReals(listOf(v1.copy(lat = 41.4025, lon = 2.2)), setOf("renfe"), 1_000)
        val mig = flota.vehicles(ara, 6_000).single { it.real }
        assertEquals(llargada / 2, mig.distancia!!, 3.0)
        val final = flota.vehicles(ara, 20_000).single { it.real }
        assertEquals(llargada * 3 / 4, final.distancia!!, 3.0)

        // Si Renfe falla, la R1 torna a l'horari.
        flota.senseReals(30_000)
        assertTrue(flota.vehicles(ara, 30_000).none { it.real })
        assertEquals(2, flota.vehicles(ara, 30_000).size)
    }

    @Test
    fun `un tren real lluny de la via o sense línia coneguda va en línia recta`() {
        val c = Circulacions(horaris(emptyMap()))
        val flota = Flota(c, duradaAnimacioMs = 10_000)
        val ara = LocalDateTime.of(2026, 11, 4, 8, 0)
        flota.actualitzaReals(listOf(VehicleReal("1", "fgc", "S1", 41.40, 2.21)), setOf("fgc"), 0)
        flota.actualitzaReals(listOf(VehicleReal("1", "fgc", "S1", 41.41, 2.21)), setOf("fgc"), 0)
        val v = flota.vehicles(ara, 5_000).single()
        assertEquals(null, v.tracat)
        assertEquals(41.405, v.posicio.lat, 1e-6)
        assertEquals(0.0, v.posicio.rumb, 0.5)
        // Les fonts que no coneixem no compten.
        flota.actualitzaReals(listOf(VehicleReal("2", "tmb", "L1", 41.40, 2.2)), setOf("tmb"), 0)
        assertTrue(flota.vehicles(ara, 0).isEmpty())
    }

    @Test
    fun `la silueta té cotxes separats, morro i vidre, i fa les mides demanades`() {
        val tracat = Tracat(listOf(nord, sud))
        val planta = Silueta.sobreLaVia(tracat, cap = 500.0, llarg = 100.0, ample = 10.0, forma = Forma(cotxes = 4))
        assertEquals(4, planta.cotxes.size)
        for (cotxe in planta.cotxes) assertEquals(cotxe.first(), cotxe.last())
        val totes = planta.cotxes.flatten()
        assertEquals(100.0, distanciaMetres(totes.maxOf { it.lat }, 2.2, totes.minOf { it.lat }, 2.2), 0.5)
        assertEquals(10.0, distanciaMetres(41.405, totes.maxOf { it.lon }, 41.405, totes.minOf { it.lon }), 0.2)
        // Va cap al sud: el primer cotxe és el de més al sud, i hi ha espai entre cotxes.
        assertTrue(planta.cotxes[0].minOf { it.lat } < planta.cotxes[1].minOf { it.lat })
        assertTrue(planta.cotxes[0].maxOf { it.lat } < planta.cotxes[1].minOf { it.lat })
        // El morro és més estret que el cotxe: el punt del davant de tot no arriba a l'ample sencer.
        val punta = planta.cotxes[0].filter { distanciaMetres(it.lat, 2.2, totes.minOf { p -> p.lat }, 2.2) < 0.1 }
        assertTrue(distanciaMetres(41.405, punta.maxOf { it.lon }, 41.405, punta.minOf { it.lon }) < 7.0)
        // El vidre és dins del primer cotxe.
        assertTrue(planta.vidre.all { it.lat >= planta.cotxes[0].minOf { p -> p.lat } && it.lat <= planta.cotxes[0].maxOf { p -> p.lat } })

        // Al començament de la via, el tren hi cap sencer.
        val inici = Silueta.sobreLaVia(tracat, cap = 10.0, llarg = 100.0, ample = 10.0, forma = Forma(cotxes = 1)).cotxes.single()
        assertEquals(100.0, distanciaMetres(inici.maxOf { it.lat }, 2.2, inici.minOf { it.lat }, 2.2), 0.5)

        val recte = Silueta.recte(Punt(41.4, 2.2, rumb = 90.0), llarg = 40.0, ample = 10.0, forma = Forma(cotxes = 2))
        assertEquals(2, recte.cotxes.size)
        val punts = recte.cotxes.flatten()
        assertEquals(40.0, distanciaMetres(41.4, punts.maxOf { it.lon }, 41.4, punts.minOf { it.lon }), 0.3)
    }

    @Test
    fun `amb les dades reals, a hora punta hi ha trens de totes les xarxes i són a Catalunya`() {
        val fitxer = File(Repositori.arrel, "dades/generades/app/circulacions.json")
        val c = Circulacions(HorarisTrens.llegeix(fitxer.readText()))
        // Un dimecres a les 8 del matí.
        val vehicles = c.enCirculacio(LocalDateTime.of(2026, 11, 4, 8, 0))
        val modes = vehicles.groupingBy { c.horaris.linies[it.linia!!].mode }.eachCount()
        assertTrue(vehicles.size > 300, "Només hi ha ${vehicles.size} trens")
        for (mode in listOf("metro", "fgc", "rodalies", "tram")) assertTrue((modes[mode] ?: 0) > 5, "$mode: $modes")
        // Tots dins d'un requadre generós de Catalunya i el seu entorn (els regionals arriben a Aragó i al País Valencià).
        for (v in vehicles) {
            assertTrue(v.posicio.lat in 39.0..43.5 && v.posicio.lon in -1.5..3.5, "${v.id} fora: ${v.posicio}")
        }
        // A les 4 de la matinada no hi ha metro.
        val nit = c.enCirculacio(LocalDateTime.of(2026, 11, 4, 4, 0))
        assertTrue(nit.none { c.horaris.linies[it.linia!!].mode == "metro" })
    }

    private fun codifica(punts: List<Punt>): String {
        val sortida = StringBuilder()
        var latAnterior = 0L
        var lonAnterior = 0L
        for (p in punts) {
            val lat = Math.round(p.lat * 1e5)
            val lon = Math.round(p.lon * 1e5)
            for (valor in listOf(lat - latAnterior, lon - lonAnterior)) {
                var v = if (valor < 0) (valor shl 1).inv() else valor shl 1
                while (v >= 0x20) {
                    sortida.append(((0x20 or (v and 0x1F).toInt()) + 63).toChar())
                    v = v shr 5
                }
                sortida.append((v + 63).toInt().toChar())
            }
            latAnterior = lat
            lonAnterior = lon
        }
        return sortida.toString()
    }
}
