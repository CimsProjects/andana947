package cat.andana947.perfil

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import cat.andana947.BuildConfig
import cat.andana947.R
import cat.andana947.ui.components.DialegResultat
import cat.andana947.data.joc.LiniaFlota
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.avatar.Avatar
import cat.andana947.avatar.CatalegAvatar
import cat.andana947.avatar.DibuixAvatar
import cat.andana947.data.compte.EstatCompte
import cat.andana947.data.compte.ServeiCompte
import cat.andana947.data.joc.ResumPerfil
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Els municipis de Catalunya (dada real, i el nom del joc). */
private const val MUNICIPIS_CATALUNYA = 947

@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val compte: ServeiCompte,
    private val joc: ServeiJoc,
) : ViewModel() {
    val estat: StateFlow<EstatCompte> = compte.estat

    private val _modeDev = MutableStateFlow<Boolean?>(null)
    val modeDev: StateFlow<Boolean?> = _modeDev.asStateFlow()

    private val _avatar = MutableStateFlow<Avatar?>(null)
    val avatar: StateFlow<Avatar?> = _avatar.asStateFlow()

    private val _resum = MutableStateFlow<ResumPerfil?>(null)
    val resum: StateFlow<ResumPerfil?> = _resum.asStateFlow()

    /** La flota del jugador (fase 5). */
    private val _flota = MutableStateFlow<List<LiniaFlota>>(emptyList())
    val flota: StateFlow<List<LiniaFlota>> = _flota.asStateFlow()

    /** El resultat d'una eina del mode desenvolupador. */
    private val _resultatDev = MutableStateFlow<ResultatCheckinRpc?>(null)
    val resultatDev: StateFlow<ResultatCheckinRpc?> = _resultatDev.asStateFlow()

    init {
        if (!BuildConfig.SENSE_SERVIDOR) {
            viewModelScope.launch {
                joc.llegirModeDev()
                    .onSuccess { _modeDev.value = it }
                    .onFailure { _modeDev.value = false }
            }
        }
    }

    /** Es crida cada cop que es torna a la pantalla (per exemple, després de canviar l'avatar). */
    fun carrega() {
        viewModelScope.launch { joc.llegirPerfilJugador().onSuccess { _avatar.value = it.avatar } }
        viewModelScope.launch { joc.resumPerfil().onSuccess { _resum.value = it } }
        viewModelScope.launch { joc.estatFlota().onSuccess { _flota.value = it } }
    }

    fun surt() {
        viewModelScope.launch { compte.surt() }
    }

    fun devDesbloquejaSkins() = eina { joc.devDesbloquejaSkins() }

    fun devCobraAra() = eina { joc.devCobraAra() }

    fun tancaResultatDev() {
        _resultatDev.value = null
    }

    private fun eina(crida: suspend () -> Result<ResultatCheckinRpc>) {
        viewModelScope.launch {
            crida()
                .onSuccess { _resultatDev.value = it }
                .onFailure { _resultatDev.value = ResultatCheckinRpc(status = "error", missatge = it.message.orEmpty()) }
            carrega()
        }
    }
}

/** El perfil (GDD, secció 7.1): l'avatar, el progrés, la xarxa, la trajectòria i la flota. El rànquing arriba a la fase 7. */
@Composable
fun PerfilScreen(
    onEditaAvatar: () -> Unit = {},
    onObreTemporada: () -> Unit = {},
    onObreMaestries: () -> Unit = {},
    onObreFlota: () -> Unit = {},
    onObreSocial: () -> Unit = {},
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    val resum by viewModel.resum.collectAsStateWithLifecycle()
    val modeDev by viewModel.modeDev.collectAsStateWithLifecycle()
    val resultatDev by viewModel.resultatDev.collectAsStateWithLifecycle()
    val flota by viewModel.flota.collectAsStateWithLifecycle()
    resultatDev?.let { DialegResultat(it, onTanca = viewModel::tancaResultatDev) }
    val cicle = LocalLifecycleOwner.current
    LaunchedEffect(cicle) { cicle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.carrega() } }
    val correu = (estat as? EstatCompte.Llest)?.compte?.correu

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.perfil_titol), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })

        Capcalera(avatar ?: CatalegAvatar.inicial, resum, onEditaAvatar)
        BotoSecundari(stringResource(R.string.perfil_edita_avatar), onEditaAvatar)
        // La temporada: rànquing, objectius, insígnies i skins.
        BotoSecundari(stringResource(R.string.perfil_temporada), onObreTemporada, icona = Icons.Default.EmojiEvents)
        // La maestria de les línies i les andanes, i la xarxa més gran.
        BotoSecundari(stringResource(R.string.perfil_maestries), onObreMaestries, icona = Icons.Default.MilitaryTech)
        // La part social: mercat d'accions, juntes i cooperatives.
        BotoSecundari(stringResource(R.string.perfil_social), onObreSocial, icona = Icons.Default.Groups)

        resum?.let { r ->
            Seccio(stringResource(R.string.perfil_seccio_xarxa))
            Fila {
                Xifra(
                    stringResource(R.string.perfil_xifra_de, r.concessions, r.maximConcessions),
                    stringResource(R.string.perfil_concessions),
                )
                Xifra(r.regals.toString(), stringResource(R.string.perfil_regals))
            }
            Fila {
                Xifra(r.accions.toString(), stringResource(R.string.perfil_accions, r.estacionsAmbAccions))
                Xifra(
                    stringResource(R.string.perfil_xifra_de, r.municipis, MUNICIPIS_CATALUNYA),
                    stringResource(R.string.perfil_municipis),
                )
            }

            Seccio(stringResource(R.string.perfil_seccio_trajectoria))
            Fila {
                Xifra(r.checkins.toString(), stringResource(R.string.perfil_checkins))
                Xifra(r.nodesVisitats.toString(), stringResource(R.string.perfil_andanes_visitades))
            }
            Fila {
                Xifra(r.comarques.toString(), stringResource(R.string.perfil_comarques))
                Xifra(stringResource(R.string.a_prop_preu, euros(r.totalCobrat)), stringResource(R.string.perfil_total_cobrat))
            }
        }

        // La flota (fase 5): els vehicles llogats a les línies on el jugador té andanes.
        Seccio(stringResource(R.string.perfil_seccio_flota))
        Fila {
            Xifra(flota.filter { it.mode != "bus" }.sumOf { it.total }.toString(), stringResource(R.string.perfil_trens), Icons.Default.Train)
            Xifra(flota.filter { it.mode == "bus" }.sumOf { it.total }.toString(), stringResource(R.string.perfil_busos), Icons.Default.DirectionsBus)
            Xifra(flota.count { it.total > 0 }.toString(), stringResource(R.string.perfil_linies_amb_flota), Icons.Default.DirectionsRailway)
        }
        BotoSecundari(stringResource(R.string.perfil_flota), onObreFlota, Modifier.fillMaxWidth(), icona = Icons.Default.Train)

        Seccio(stringResource(R.string.perfil_compte))
        Targeta(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(correu ?: stringResource(R.string.perfil_sense_correu), style = MaterialTheme.typography.titleMedium)
                resum?.let { r ->
                    Text(
                        stringResource(R.string.perfil_membre_des_de, data(r.membreDesDe)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.TextSecundari,
                    )
                }
                if (BuildConfig.SENSE_SERVIDOR) {
                    Text(stringResource(R.string.perfil_sense_servidor), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BotoSecundari(stringResource(R.string.compte_surt), viewModel::surt)

        // Mode desenvolupador: visible només quan hi ha servidor i s'ha carregat el valor.
        if (!BuildConfig.SENSE_SERVIDOR && modeDev != null) {
            Targeta(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.perfil_mode_dev),
                        style = MaterialTheme.typography.labelSmall.copy(color = Colors.TextSecundari),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (modeDev == true) stringResource(R.string.perfil_mode_dev_activat)
                            else stringResource(R.string.perfil_mode_dev_desactivat),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // Lectura: el client no pot activar mode_dev (la RLS ho impedeix).
                        Switch(checked = modeDev == true, onCheckedChange = null)
                    }
                    Text(
                        stringResource(R.string.perfil_mode_dev_descripcio),
                        style = MaterialTheme.typography.bodySmall.copy(color = Colors.TextSecundari),
                    )
                    if (modeDev == true) {
                        // Eines per provar sense esperar (el servidor comprova el mode desenvolupador).
                        BotoSecundari(stringResource(R.string.dev_desbloqueja_skins), viewModel::devDesbloquejaSkins, Modifier.fillMaxWidth())
                        BotoSecundari(stringResource(R.string.dev_cobra_ara), viewModel::devCobraAra, Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.dev_maestria_pista),
                            style = MaterialTheme.typography.bodySmall.copy(color = Colors.TextSecundari),
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.perfil_credits), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.perfil_credits_mapa), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.perfil_credits_fonts), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.perfil_versio, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
    }
}

/** L'avatar, el nivell i la barra de prestigi fins al nivell següent. */
@Composable
private fun Capcalera(avatar: Avatar, resum: ResumPerfil?, onEditaAvatar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .ombraDura()
            .background(Colors.Superficie)
            .border(2.dp, Colors.Text)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val descripcio = stringResource(R.string.avatar_descripcio)
        Box(
            Modifier
                .size(104.dp)
                .background(Colors.Fons)
                .border(2.dp, Colors.Text)
                .clickable(role = Role.Button, onClick = onEditaAvatar),
        ) {
            DibuixAvatar(avatar, Modifier.fillMaxSize().padding(6.dp).semantics { contentDescription = descripcio })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (resum == null) {
                Text(stringResource(R.string.carregant), color = Colors.TextSecundari)
                return@Column
            }
            Box(Modifier.background(Colors.Fort).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.imperi_nivell, resum.nivell),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                    color = Colors.TintaFort,
                )
            }
            Text(
                stringResource(R.string.perfil_prestigi, resum.prestigi),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Colors.Text,
            )
            val progres by animateFloatAsState(resum.progresNivell, tween(700), label = "progrés del nivell")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Colors.Fons)
                    .border(2.dp, Colors.Text),
            ) {
                Box(Modifier.fillMaxWidth(progres).fillMaxHeight().background(Colors.Fort))
            }
            Text(
                stringResource(
                    R.string.perfil_falten,
                    (resum.prestigiSeguentNivell - resum.prestigi).coerceAtLeast(0),
                    resum.nivell + 1,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Colors.TextSecundari,
            )
        }
    }
}

@Composable
private fun Seccio(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = Colors.TextSecundari,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Fila(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun RowScope.Xifra(valor: String, etiqueta: String, icona: ImageVector? = null) {
    Column(
        Modifier
            .weight(1f)
            .background(Colors.Superficie)
            .border(1.5.dp, Colors.Text)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icona != null) {
                Icon(icona, contentDescription = null, tint = Colors.Text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
            }
            Text(
                valor,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
                maxLines = 1,
            )
        }
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
    }
}

private fun data(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d/M/yyyy"))
}.getOrDefault("")
