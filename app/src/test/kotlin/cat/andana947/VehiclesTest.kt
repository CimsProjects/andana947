package cat.andana947

import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.ServeiJocLocal
import cat.andana947.data.preferencies.PreferenciesVehicles
import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.data.vehicles.RespostaVehicles
import cat.andana947.data.vehicles.ServeiVehiclesReals
import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.TipusVehicle
import cat.andana947.joc.circulacio.VehicleAlMapa
import cat.andana947.joc.circulacio.VehicleReal
import cat.andana947.mapa.CapaVehicles
import cat.andana947.mapa.VehiclesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.File
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HorarisEnMemoria(private val circulacions: Circulacions) : FontHoraris {
    override suspend fun circulacions() = circulacions
}

class PreferenciesVehiclesEnMemoria : PreferenciesVehicles {
    val estat = MutableStateFlow(TipusVehicle.TOTS)

    override fun visibles(): Flow<Set<TipusVehicle>> = estat

    override suspend fun desaVisibles(visibles: Set<TipusVehicle>) {
        estat.value = visibles
    }
}

class VehiclesRealsFalsos(var resposta: RespostaVehicles?) : ServeiVehiclesReals {
    var crides = 0

    override suspend fun consulta(): RespostaVehicles? {
        crides++
        return resposta
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VehiclesTest {
    // Els horaris de veritat, els mateixos que van als assets de l'app.
    private val circulacions by lazy {
        Circulacions(HorarisTrens.llegeix(File("../dades/generades/app/circulacions.json").readText()))
    }

    @Before
    fun prepara() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun neteja() = Dispatchers.resetMain()

    @Test
    fun `els colors massa clars es dibuixen amb la tinta`() {
        assertEquals("#CE1126", CapaVehicles.colorDe("CE1126", "#111111"))
        assertEquals("#FF0080", CapaVehicles.colorDe("#ff0080", "#111111"))
        // Els regionals de Renfe, gairebé blancs al GTFS.
        assertEquals("#111111", CapaVehicles.colorDe("F2F5F5", "#111111"))
        assertEquals("#111111", CapaVehicles.colorDe("", "#111111"))
        assertEquals("#111111", CapaVehicles.colorDe(null, "#111111"))
    }

    @Test
    fun `la resposta de la funció vehicles es llegeix`() {
        val resposta = RespostaVehicles.llegeix(
            """{"generat":1793000000000,"fonts":{"renfe":true,"fgc":false},"vehicles":[{"id":"25603","font":"renfe","linia":"R1","lat":41.5,"lon":2.4},{"id":"7","font":"renfe","lat":41.4,"lon":2.1,"nou":1}]}""",
        )
        assertEquals(setOf("renfe"), resposta.fontsOk)
        assertEquals(VehicleReal("25603", "renfe", "R1", 41.5, 2.4), resposta.vehicles.first())
        assertEquals(null, resposta.vehicles[1].linia)
    }

    @Test
    fun `els trens tenen cotxes i vidre a tots els zooms i la mida s'adapta`() {
        val vehicles = circulacions.enCirculacio(LocalDateTime.of(2026, 11, 4, 8, 0))
            .filter { circulacions.horaris.linies[it.linia!!].mode != "bus" }.take(50)
        fun llargades(zoom: Double): List<Double> {
            val col = CapaVehicles.features(vehicles, circulacions, CapaVehicles.metresPerPixel(zoom, 41.4), "#111111")
            // Cada tren són els seus cotxes i el vidre del davant.
            assertTrue(col.features()!!.size > 50 * 4)
            assertTrue(col.features()!!.all { it.geometry()!!.type() == "Polygon" && it.getNumberProperty("alt").toDouble() > 0 })
            // L'alçada creix amb la mida: la llargada en surt proporcional.
            return col.features()!!.map { it.getNumberProperty("alt").toDouble() }
        }
        // De lluny, els blocs fan molts més metres (per veure's igual a la pantalla); de molt a prop, la mida mínima.
        assertTrue(llargades(8.0).min() > 10 * llargades(15.0).max())
        assertEquals(llargades(18.0), llargades(19.0))
        assertEquals(0.896, CapaVehicles.metresPerPixel(16.0, 41.4), 0.005)
        // De lluny, una mica més petits a la pantalla; de prop, la mida sencera.
        assertEquals(0.45, CapaVehicles.metresPerPixelDeDibuix(7.0, 41.4) / CapaVehicles.metresPerPixel(7.0, 41.4), 1e-9)
        assertEquals(1.0, CapaVehicles.metresPerPixelDeDibuix(12.0, 41.4) / CapaVehicles.metresPerPixel(12.0, 41.4), 1e-9)
    }

    @Test
    fun `els vehicles de les línies del jugador són de color i la resta, grisos`() {
        val vehicles = circulacions.enCirculacio(LocalDateTime.of(2026, 11, 4, 8, 0)).take(20)
        val meva = vehicles.first().linia!!
        val col = CapaVehicles.features(vehicles, circulacions, 1.0, "#111111", setOf(meva), "#BDBDBD").features()!!
        // Del color de la línia als de la línia del jugador, grisos a la resta (si n'hi ha d'altres línies).
        val colors = col.map { it.getStringProperty("color") }.toSet()
        assertTrue("#BDBDBD" in colors || vehicles.all { it.linia == meva })
        assertTrue(CapaVehicles.colorDe(circulacions.horaris.linies[meva].color, "#111111") in colors)
        assertTrue(col.all { it.hasProperty("vehicle") })
    }

    @Test
    fun `cada bus és un sol cos amb vidre, més petit que els trens`() {
        val ara = LocalDateTime.of(2026, 11, 4, 8, 0)
        fun esBus(v: VehicleAlMapa) = circulacions.horaris.linies[v.linia!!].mode == "bus"
        val busos = circulacions.enCirculacio(ara).filter(::esBus).take(50)
        val trens = circulacions.enCirculacio(ara).filterNot(::esBus).take(50)
        assertEquals(50, busos.size)
        val metresPerPixel = CapaVehicles.metresPerPixel(14.0, 41.4)
        val colBusos = CapaVehicles.features(busos, circulacions, metresPerPixel, "#111111")
        // Un cos i el vidre del davant per bus.
        assertEquals(100, colBusos.features()!!.size)
        val altBus = colBusos.features()!!.maxOf { it.getNumberProperty("alt").toDouble() }
        val altTren = CapaVehicles.features(trens, circulacions, metresPerPixel, "#111111").features()!!
            .minOf { it.getNumberProperty("alt").toDouble() }
        assertTrue(altBus < altTren, "bus $altBus, tren $altTren")
    }

    /** Sense servidor: el jugador no té andanes. */
    private fun xarxaLocal() = RepositoriXarxa(ServeiJocLocal(), HorarisEnMemoria(circulacions))

    @Test
    fun `la flota es carrega i hi arriben les posicions reals`() = runTest {
        val tren = VehicleReal("25603", "renfe", "R1", 41.45, 2.25)
        val reals = VehiclesRealsFalsos(RespostaVehicles(0, mapOf("renfe" to true), listOf(tren)))
        val vm = VehiclesViewModel(HorarisEnMemoria(circulacions), reals, PreferenciesVehiclesEnMemoria(), xarxaLocal())
        val recollida = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.flota.collect {} }
        runCurrent()
        val flota = assertNotNull(vm.flota.value)
        assertEquals(1, reals.crides)
        val ara = LocalDateTime.of(2026, 11, 4, 8, 0)
        val vehicles = synchronized(flota) { flota.vehicles(ara, System.currentTimeMillis()) }
        assertEquals(1, vehicles.count { it.real })
        // Amb Renfe en temps real, cap tren de Rodalies segons l'horari.
        assertTrue(vehicles.none { !it.real && circulacions.horaris.linies[it.linia!!].id.startsWith("rodalies:") })

        // Al cap de l'interval es tornen a demanar; si fallen, tot torna a l'horari.
        reals.resposta = null
        advanceTimeBy(VehiclesViewModel.INTERVAL_REALS_MS + 1)
        assertEquals(2, reals.crides)
        assertTrue(synchronized(flota) { flota.vehicles(ara, System.currentTimeMillis()) }.none { it.real })
        recollida.cancel()
    }

    @Test
    fun `els trens i els busos es poden amagar i l'app ho recorda`() = runTest {
        val preferencies = PreferenciesVehiclesEnMemoria()
        val vm = VehiclesViewModel(HorarisEnMemoria(circulacions), VehiclesRealsFalsos(null), preferencies, xarxaLocal())
        assertEquals(TipusVehicle.TOTS, vm.visibles.value)
        vm.commuta(TipusVehicle.BUS)
        assertEquals(setOf(TipusVehicle.TREN), vm.visibles.value)
        assertEquals(setOf(TipusVehicle.TREN), preferencies.estat.value)
        vm.commuta(TipusVehicle.TREN)
        assertEquals(emptySet<TipusVehicle>(), vm.visibles.value)
        vm.commuta(TipusVehicle.BUS)
        assertEquals(setOf(TipusVehicle.BUS), vm.visibles.value)
    }
}
