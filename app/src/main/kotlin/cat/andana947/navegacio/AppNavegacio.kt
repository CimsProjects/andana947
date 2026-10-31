package cat.andana947.navegacio

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cat.andana947.R
import cat.andana947.aprop.APropScreen
import cat.andana947.arribada.AvisosArribada
import cat.andana947.arribada.ArribadesViewModel
import cat.andana947.avatar.EditorAvatarScreen
import cat.andana947.cobrament.BenvingudaCobrament
import cat.andana947.cobrament.CobramentViewModel
import cat.andana947.compra.CompraScreen
import cat.andana947.compte.CompteScreen
import cat.andana947.compte.SenseConnexioScreen
import cat.andana947.flota.FlotaScreen
import cat.andana947.social.JuntaScreen
import cat.andana947.social.SocialScreen
import cat.andana947.data.compte.EstatCompte
import cat.andana947.imperi.ImperiScreen
import cat.andana947.introduccio.IntroduccioScreen
import cat.andana947.maestria.AndanaScreen
import cat.andana947.maestria.MaestriesScreen
import cat.andana947.mapa.MapaScreen
import cat.andana947.perfil.PerfilScreen
import cat.andana947.temporada.TemporadaScreen
import cat.andana947.ui.components.Obertura
import cat.andana947.ui.theme.Colors

/** Les pantalles del joc (GDD, secció 7.1). La fitxa del node i l'arbre tecnològic arriben amb les fases següents. */
object Rutes {
    const val MAPA = "mapa"
    const val A_PROP = "a-prop"
    const val IMPERI = "imperi"
    const val PERFIL = "perfil"
    const val AVATAR = "avatar"
    const val COMPRA = "compra"
    const val TEMPORADA = "temporada"
    const val MAESTRIES = "maestries"
    const val FLOTA = "flota"
    const val SOCIAL = "social"
    const val JUNTA = "junta/{id}"

    fun junta(id: String) = "junta/" + Uri.encode(id)
    const val ANDANA = "andana/{id}"

    fun andana(id: String) = "andana/" + Uri.encode(id)
}

private data class Pestanya(val ruta: String, val etiqueta: Int, val icona: ImageVector)

private val pestanyes = listOf(
    Pestanya(Rutes.MAPA, R.string.pestanya_mapa, Icons.Outlined.Map),
    Pestanya(Rutes.A_PROP, R.string.pestanya_a_prop, Icons.Outlined.NearMe),
    Pestanya(Rutes.COMPRA, R.string.pestanya_compra, Icons.Outlined.ShoppingCart),
    Pestanya(Rutes.IMPERI, R.string.pestanya_imperi, Icons.Outlined.AccountBalance),
    Pestanya(Rutes.PERFIL, R.string.pestanya_perfil, Icons.Outlined.Person),
)

@Composable
fun AppNavegacio(viewModel: AppViewModel = hiltViewModel()) {
    val compte by viewModel.compte.collectAsStateWithLifecycle()
    when (compte) {
        EstatCompte.Carregant -> Obertura(Modifier.fillMaxSize())
        EstatCompte.SenseSessio -> CompteScreen()
        EstatCompte.SenseConnexio -> SenseConnexioScreen()
        is EstatCompte.Llest -> PortaJoc()
    }
}

@Composable
private fun PortaJoc(viewModel: PortaJocViewModel = hiltViewModel()) {
    val porta by viewModel.porta.collectAsStateWithLifecycle()
    when (porta) {
        Porta.Carregant -> Obertura(Modifier.fillMaxSize())
        Porta.Introduccio -> IntroduccioScreen(onAcabada = viewModel::introduccioAcabada)
        Porta.Regal -> IntroduccioScreen(onAcabada = viewModel::introduccioAcabada, nomesRegal = true)
        Porta.Joc -> PantallesJoc()
    }
}

@Composable
private fun PantallesJoc(
    cobrament: CobramentViewModel = hiltViewModel(),
    arribades: ArribadesViewModel = hiltViewModel(),
) {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    // Cada cop que el jugador torna a l'app, mira si s'ha cobrat res mentre no hi era.
    val cicle = LocalLifecycleOwner.current
    LaunchedEffect(cicle) {
        cicle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { cobrament.comprova() }
    }
    val pendent by cobrament.pendent.collectAsStateWithLifecycle()
    // Mentre l'app és oberta, avisa de cada tren o bus que arriba a una andana del jugador.
    LaunchedEffect(cicle) {
        cicle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { arribades.vigila() }
    }
    val avisos by arribades.avisos.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Joc(nav, rutaActual)
        AvisosArribada(avisos, onDescarta = arribades::descarta, modifier = Modifier.align(Alignment.TopCenter))
        pendent?.let { BenvingudaCobrament(it, onRecull = cobrament::recull) }
    }
}

@Composable
private fun Joc(nav: NavHostController, rutaActual: String?) {
    Scaffold(
        containerColor = Colors.Fons,
        // Cada pantalla gestiona les barres del sistema (el mapa ocupa tota la pantalla).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BarraInferior(nav, rutaActual) },
    ) { padding ->
        NavHost(nav, startDestination = Rutes.MAPA, modifier = Modifier.padding(padding)) {
            composable(Rutes.MAPA) { MapaScreen() }
            composable(Rutes.A_PROP) { APropScreen() }
            composable(Rutes.IMPERI) {
                ImperiScreen(
                    onObreAndana = { nav.navigate(Rutes.andana(it)) },
                    onObreFlota = { nav.navigate(Rutes.FLOTA) },
                    onObreSocial = { nav.navigate(Rutes.SOCIAL) },
                )
            }
            composable(Rutes.FLOTA) { FlotaScreen(onTorna = { nav.popBackStack() }) }
            composable(Rutes.SOCIAL) {
                SocialScreen(onTorna = { nav.popBackStack() }, onObreJunta = { nav.navigate(Rutes.junta(it)) })
            }
            composable(Rutes.JUNTA) { entrada ->
                JuntaScreen(id = entrada.arguments?.getString("id").orEmpty(), onTorna = { nav.popBackStack() })
            }
            composable(Rutes.COMPRA) { CompraScreen() }
            composable(Rutes.PERFIL) {
                PerfilScreen(
                    onEditaAvatar = { nav.navigate(Rutes.AVATAR) },
                    onObreTemporada = { nav.navigate(Rutes.TEMPORADA) },
                    onObreMaestries = { nav.navigate(Rutes.MAESTRIES) },
                    onObreFlota = { nav.navigate(Rutes.FLOTA) },
                    onObreSocial = { nav.navigate(Rutes.SOCIAL) },
                )
            }
            composable(Rutes.MAESTRIES) {
                MaestriesScreen(onTorna = { nav.popBackStack() }, onObreAndana = { nav.navigate(Rutes.andana(it)) })
            }
            composable(Rutes.ANDANA) { entrada ->
                AndanaScreen(id = entrada.arguments?.getString("id").orEmpty(), onTorna = { nav.popBackStack() })
            }
            composable(Rutes.TEMPORADA) { TemporadaScreen(onTorna = { nav.popBackStack() }) }
            composable(Rutes.AVATAR) { EditorAvatarScreen(onTorna = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun BarraInferior(nav: NavHostController, rutaActual: String?) {
    // Com a municipis: sense la píndola de Material, una ratlla de tinta a dalt i, a la pestanya triada, una barra del
    // color fort.
    val tinta = Colors.Text
    val fort = Colors.Fort
    NavigationBar(
        containerColor = Colors.Fons,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind { drawLine(tinta, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) },
    ) {
        for (p in pestanyes) {
            val seleccionada = p.ruta == rutaActual
            NavigationBarItem(
                modifier = Modifier.drawBehind {
                    if (seleccionada) drawRect(fort, Offset(size.width * .22f, 0f), Size(size.width * .56f, 4.dp.toPx()))
                },
                selected = seleccionada,
                onClick = {
                    if (!seleccionada) {
                        nav.navigate(p.ruta) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Box { Icon(p.icona, contentDescription = null, modifier = Modifier.size(24.dp)) } },
                label = { Text(stringResource(p.etiqueta)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Colors.Text,
                    selectedTextColor = Colors.Text,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Colors.TextSecundari,
                    unselectedTextColor = Colors.TextSecundari,
                ),
            )
        }
    }
}
