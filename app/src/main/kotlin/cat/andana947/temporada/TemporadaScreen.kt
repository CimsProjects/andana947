package cat.andana947.temporada

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.avatar.CatalegAvatar
import cat.andana947.avatar.DibuixAvatar
import cat.andana947.data.joc.EstatTemporada
import cat.andana947.data.joc.Fita
import cat.andana947.data.joc.FilaRanking
import cat.andana947.data.joc.SkinAndana
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.time.Duration
import java.time.OffsetDateTime

private enum class Pestanya(val nom: Int) {
    RANKING(R.string.temporada_ranking),
    OBJECTIUS(R.string.temporada_objectius),
    INSIGNIES(R.string.temporada_insignies),
    SKINS(R.string.temporada_skins),
}

/** La temporada: el rànquing (el que es fa a la temporada), els objectius, les insígnies i les skins. */
@Composable
fun TemporadaScreen(onTorna: () -> Unit, viewModel: TemporadaViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.carrega() }
    var pestanya by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onTorna) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.avatar_torna), tint = Colors.Text)
            }
            Text(
                ui.estat?.let { stringResource(R.string.temporada_titol, it.temporada.numero) } ?: stringResource(R.string.temporada),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
            )
        }
        val e = ui.estat
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                if (ui.error != null) {
                    Text(stringResource(R.string.compra_error, ui.error.orEmpty()), color = Colors.Error)
                } else {
                    CircularProgressIndicator(color = Colors.Fort)
                }
            }
            return@Column
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Resum(e)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pestanya.entries.forEachIndexed { i, p -> Xip(stringResource(p.nom), i == pestanya) { pestanya = i } }
            }
            Spacer(Modifier.height(12.dp))
            ui.error?.let { Text(it, color = Colors.Error, style = MaterialTheme.typography.bodySmall) }
        }
        Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (Pestanya.entries[pestanya]) {
                Pestanya.RANKING -> Ranking(e, viewModel::desaNom)
                Pestanya.OBJECTIUS -> Objectius(e.fites)
                Pestanya.INSIGNIES -> Insignies(e)
                Pestanya.SKINS -> Skins(e.skins, viewModel::posaSkin)
            }
        }
    }
}

@Composable
private fun Resum(e: EstatTemporada) {
    val dies = runCatching {
        Duration.between(OffsetDateTime.now(), OffsetDateTime.parse(e.temporada.fi)).toDays().coerceAtLeast(0)
    }.getOrDefault(0L)
    Row(
        Modifier.fillMaxWidth().ombraDura().background(Colors.Fort).border(2.dp, Colors.Text).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                e.jo?.let { stringResource(R.string.temporada_posicio, it.posicio, e.jugadors) } ?: stringResource(R.string.temporada_sense_punts),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.TintaFort,
            )
            Text(stringResource(R.string.temporada_queden, dies.toInt()), style = MaterialTheme.typography.bodyMedium, color = Colors.TintaFort)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (e.jo?.punts ?: 0).toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.TintaFort,
            )
            Text(stringResource(R.string.temporada_punts), style = MaterialTheme.typography.labelSmall, color = Colors.TintaFort)
        }
    }
}

@Composable
private fun Ranking(e: EstatTemporada, onDesaNom: (String) -> Unit) {
    var nom by rememberSaveable(e.nom) { mutableStateOf(e.nom) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.temporada_com_punts), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        }
        if (e.ranking.isEmpty()) {
            item { Text(stringResource(R.string.temporada_ranking_buit), color = Colors.TextSecundari) }
        }
        items(e.ranking, key = { "${it.posicio}:${it.nom}:${it.jo}" }) { FilaJugador(it) }
        item {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.temporada_nom_public), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Colors.TextSecundari)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it.take(20) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Colors.Text, unfocusedBorderColor = Colors.Text),
                )
                BotoSecundari(stringResource(R.string.avatar_desa), { onDesaNom(nom) }, enabled = nom.trim().length >= 3 && nom != e.nom)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilaJugador(f: FilaRanking) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (f.jo) Colors.FortSuau else Colors.Superficie)
            .border(if (f.jo) 2.dp else 1.dp, if (f.jo) Colors.Fort else Colors.Linia)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#${f.posicio}",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
            modifier = Modifier.width(48.dp),
        )
        DibuixAvatar(f.avatar ?: CatalegAvatar.inicial, Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Text(f.nom, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Colors.Text, modifier = Modifier.weight(1f))
        Text(f.punts.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Colors.FortText)
    }
}

@Composable
private fun Objectius(fites: List<Fita>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(fites, key = { it.id }) { f ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(if (f.aconseguida) Colors.FortSuau else Colors.Superficie)
                    .border(1.5.dp, if (f.aconseguida) Colors.Fort else Colors.Text)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nomFita(f.id, f.tipus, f.objectiu),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Colors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.temporada_fita_punts, f.punts), style = MaterialTheme.typography.labelMedium, color = Colors.TextSecundari)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).background(Colors.Fons).border(1.5.dp, Colors.Text)) {
                    Box(
                        Modifier
                            .fillMaxWidth((f.progres.toFloat() / f.objectiu).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Colors.Fort),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("${f.progres} / ${f.objectiu}")
                    } + (f.skin?.let { " · " + stringResource(R.string.temporada_premi_skin, nomSkin(it)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Insignies(e: EstatTemporada) {
    val fetes = e.fites.filter { it.aconseguida }
    if (fetes.isEmpty() && e.premis.isEmpty()) {
        Text(stringResource(R.string.temporada_sense_insignies), color = Colors.TextSecundari)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(e.premis, key = { "premi${it.temporada}" }) { p ->
            Insignia(stringResource(R.string.temporada_premi, p.temporada, p.posicio), color = p.skin?.let { s -> e.skins.firstOrNull { it.id == s }?.color })
        }
        items(fetes, key = { it.id }) { f -> Insignia(nomFita(f.id, f.tipus, f.objectiu), color = null) }
    }
}

@Composable
private fun Insignia(text: String, color: String?) {
    Column(
        Modifier.ombraDura(3.dp).background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(color?.let { colorDe(it) } ?: Colors.Fort).border(2.dp, Colors.Text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Colors.TintaFort, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 14.sp), color = Colors.Text, maxLines = 3)
    }
}

@Composable
private fun Skins(skins: List<SkinAndana>, onPosa: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(skins, key = { it.id }) { s ->
            Column(
                Modifier
                    .background(if (s.posada) Colors.FortSuau else Colors.Superficie)
                    .border(if (s.posada) 3.dp else 1.5.dp, if (s.posada) Colors.Fort else Colors.Linia)
                    .clickable(enabled = s.teva && !s.posada, role = Role.RadioButton) { onPosa(s.id) }
                    .semantics { selected = s.posada }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colorDe(s.color)).border(2.dp, Colors.Text, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!s.teva) Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(nomSkin(s.id), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
            }
        }
    }
}

@Composable
private fun Xip(text: String, triat: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (triat) Colors.FortSuau else Colors.Fons)
            .border(if (triat) 2.dp else 1.5.dp, if (triat) Colors.Text else Colors.Linia)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = triat }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (triat) FontWeight.Bold else FontWeight.Medium), color = Colors.Text)
    }
}

private fun colorDe(hex: String): Color = runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrDefault(Color.Gray)
