package cat.andana947

import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.data.vehicles.RespostaVehicles
import cat.andana947.data.vehicles.ServeiVehiclesReals
import cat.andana947.joc.circulacio.Circulacions
import cat.andana947.joc.circulacio.HorarisTrens
import cat.andana947.joc.circulacio.VehicleReal
import cat.andana947.mapa.CapaVehicles
import cat.andana947.mapa.VehiclesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `cada mode té el seu bloc i de lluny són punts`() {
        val vehicles = circulacions.enCirculacio(LocalDateTime.of(2026, 11, 4, 8, 0)).take(50)
        val (punts, blocs) = CapaVehicles.features(vehicles, circulacions, de3d = true, tinta = "#111111")
        assertTrue(punts.features()!!.isEmpty())
        assertEquals(50, blocs.features()!!.size)
        assertTrue(blocs.features()!!.all { it.geometry()!!.type() == "Polygon" && it.getNumberProperty("alt").toDouble() > 0 })
        val (punts2, blocs2) = CapaVehicles.features(vehicles, circulacions, de3d = false, tinta = "#111111")
        assertEquals(50, punts2.features()!!.size)
        assertTrue(blocs2.features()!!.isEmpty())
    }

    @Test
    fun `la flota es carrega i hi arriben les posicions reals`() = runTest {
        val tren = VehicleReal("25603", "renfe", "R1", 41.45, 2.25)
        val reals = VehiclesRealsFalsos(RespostaVehicles(0, mapOf("renfe" to true), listOf(tren)))
        val vm = VehiclesViewModel(HorarisEnMemoria(circulacions), reals)
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
}
