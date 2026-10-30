package cat.andana947

import cat.andana947.aprop.APropViewModel
import cat.andana947.data.joc.NodeAProp
import cat.andana947.data.joc.ServeiJocLocal
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.data.ubicacio.ServeiUbicacio
import cat.andana947.imperi.ImperiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class Fase4Test {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun abans() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun despres() {
        Dispatchers.resetMain()
    }

    private class UbicacioMock : ServeiUbicacio {
        val flux = MutableSharedFlow<Posicio>()
        override fun tePermis(): Boolean = true
        override fun seguiment(): Flow<Posicio> = flux
    }

    @Test
    fun serveiJocLocal_retornaNodesAPropIOrdenatsPerDistancia() = runTest {
        val servei = ServeiJocLocal()
        // Plaça Catalunya
        val nodes = servei.nodesAProp(41.3870, 2.1700).getOrThrow()
        assertTrue(nodes.isNotEmpty(), "Hi ha d'haver nodes a prop")
        assertEquals("tmb:metro:catalunya", nodes.first().id, "El més proper ha de ser Catalunya")
    }

    @Test
    fun serveiJocLocal_fluxDeConquestaIRenovacio() = runTest {
        val servei = ServeiJocLocal()
        val abansImperi = servei.estatImperi().getOrThrow()
        assertEquals(200.00, abansImperi.saldo, "El saldo inicial ha de ser de 200 €")
        assertEquals(0, abansImperi.concessionsActives.size)

        // Check-in massa lluny
        val resLluny = servei.ferCheckin(
            nodeId = "tmb:metro:baro-viver",
            lat = 41.3870,
            lon = 2.1700,
            precisio = 10.0,
            velocitat = 0.0,
        ).getOrThrow()
        assertEquals("massa_lluny", resLluny.status)

        // Check-in a la parada Baró de Viver (41.4468, 2.2014)
        val resConquesta = servei.ferCheckin(
            nodeId = "tmb:metro:baro-viver",
            lat = 41.4468,
            lon = 2.2014,
            precisio = 10.0,
            velocitat = 0.0,
        ).getOrThrow()
        assertEquals("valid", resConquesta.status)
        assertEquals("conquesta", resConquesta.tipusAccio)

        val despresImperi = servei.estatImperi().getOrThrow()
        assertEquals(1, despresImperi.concessionsActives.size)
        assertTrue(despresImperi.saldo < 200.00, "El cost s'ha d'haver descomptat")

        // Segon check-in -> renovació
        val resRenovacio = servei.ferCheckin(
            nodeId = "tmb:metro:baro-viver",
            lat = 41.4468,
            lon = 2.2014,
            precisio = 10.0,
            velocitat = 0.0,
        ).getOrThrow()
        assertEquals("valid", resRenovacio.status)
        assertEquals("renovacio", resRenovacio.tipusAccio)
    }

    @Test
    fun aPropViewModel_carregaNodesQuanArribaUbicacio() = runTest {
        val servei = ServeiJocLocal()
        val ubicacio = UbicacioMock()
        val viewModel = APropViewModel(servei, ubicacio)

        // Emetem posició
        ubicacio.flux.emit(
            Posicio(latitud = 41.3870, longitud = 2.1700, precisio = 10.0, velocitat = 0.0, hora = 1000L),
        )

        val estat = viewModel.uiState.value
        assertTrue(estat.nodes.isNotEmpty(), "Els nodes s'han de carregar en rebre la posició")
        assertEquals("Catalunya", estat.nodes.first().nom)
    }

    @Test
    fun imperiViewModel_carregaEstatImperi() = runTest {
        val servei = ServeiJocLocal()
        val viewModel = ImperiViewModel(servei)

        val estat = viewModel.uiState.value
        assertNotNull(estat.imperi)
        assertEquals(200.00, estat.imperi?.saldo)
        assertEquals(1, estat.imperi?.nivell)
        assertEquals(12, estat.imperi?.maximConcessions) // 10 + 2 * 1
    }
}
