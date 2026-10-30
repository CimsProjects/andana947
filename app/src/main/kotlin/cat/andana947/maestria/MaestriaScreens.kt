package cat.andana947.maestria

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.caos.TargetaEsdeveniment
import cat.andana947.data.joc.EstatAndana
import cat.andana947.data.joc.Maestria
import cat.andana947.data.joc.MilloraAndana
import cat.andana947.data.joc.XarxaUnida
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.DialegResultat
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.util.Locale

// --- La fitxa d'una andana ------------------------------------------------------------------------------------------

/** La fitxa d'una andana pròpia: la seva maestria, la de les seves línies i les millores que s'hi poden fer. */
@Composable
fun AndanaScreen(id: String, onTorna: () -> Unit, viewModel: AndanaViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.carrega(id) }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Capcalera(ui.andana?.nom ?: stringResource(R.string.andana_titol), onTorna)
        val a = ui.andana
        if (a == null) {
            Centrat {
                when {
                    ui.carregant -> CircularProgressIndicator(color = Colors.Fort)
                    ui.error != null -> Text(stringResource(R.string.compra_error, ui.error.orEmpty()), color = Colors.Error)
                    else -> Text(stringResource(R.string.andana_no_es_teva), color = Colors.TextSecundari)
                }
            }
            return@Column
        }
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ResumAndana(a) }
            if (ui.esdeveniments.isNotEmpty()) {
                item { Seccio(stringResource(R.string.andana_seccio_caos)) }
                items(ui.esdeveniments, key = { "caos_${it.id}" }) { e -> TargetaEsdeveniment(e, mostraAndanes = false) }
            }
            item { Seccio(stringResource(R.string.andana_seccio_maestria)) }
            item {
                TargetaMaestria(a.maestria, gran = true)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.andana_com_maestria), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
            }
            if (a.millores.isNotEmpty()) {
                item { Seccio(stringResource(R.string.andana_seccio_millores)) }
                items(a.millores, key = { "millora_${it.id}" }) { m ->
                    ItemMillora(m, comprant = ui.comprant == m.id, onCompra = { viewModel.compra(m.id) })
                }
            }
            if (ui.modeDev) {
                item { Seccio(stringResource(R.string.dev_seccio)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (xp in listOf(100, 1_000, 10_000)) {
                            BotoSecundari(stringResource(R.string.dev_suma_xp, xp), { viewModel.devSumaXp(xp) }, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotoSecundari(stringResource(R.string.dev_avaria), { viewModel.devProvoca(0.5) }, Modifier.weight(1f))
                        BotoSecundari(stringResource(R.string.dev_festa), { viewModel.devProvoca(1.5) }, Modifier.weight(1f))
                    }
                }
            }
            if (a.linies.isNotEmpty()) {
                item { Seccio(stringResource(R.string.andana_seccio_linies)) }
                items(a.linies, key = { "linia_${it.id}" }) { l ->
                    FilaMaestria(
                        titol = l.codi,
                        subtitol = stringResource(R.string.andana_linia_arribades, l.arribadesDia),
                        color = l.color,
                        maestria = l.maestria,
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    ui.resultat?.let { DialegResultat(it, onTanca = viewModel::tancaResultat) }
}

@Composable
private fun ResumAndana(a: EstatAndana) {
    Column(Modifier.fillMaxWidth().ombraDura().background(Colors.Fort).border(2.dp, Colors.Text).padding(16.dp)) {
        Text(
            stringResource(R.string.imperi_per_dia, euros(a.ingresDiari)),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.TintaFort,
        )
        Text(
            stringResource(R.string.andana_arribades_dia, a.arribadesDia),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Colors.TintaFort,
        )
        val multiplicadors = buildList {
            if (a.multiplicador > 1.0) add(stringResource(R.string.andana_multiplicador_concessio, decimal(a.multiplicador)))
            if (a.factorMillores > 1.0) add(stringResource(R.string.andana_multiplicador_millores, percent(a.factorMillores - 1)))
        }
        if (multiplicadors.isNotEmpty()) {
            Text(multiplicadors.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Colors.TintaFort)
        }
        Text(
            stringResource(R.string.andana_manteniment, euros(a.mantenimentDiari)),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TintaFort,
        )
    }
}

@Composable
private fun ItemMillora(m: MilloraAndana, comprant: Boolean, onCompra: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (m.comprada) Colors.FortSuau else Colors.Superficie)
            .border(1.5.dp, if (m.comprada) Colors.Fort else Colors.Text)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nomMillora(m.id), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
                Text(descripcioMillora(m.id), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.andana_millora_bonus, percent(m.bonus)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Colors.FortText,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            m.comprada -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Colors.FortText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.andana_millora_feta), style = MaterialTheme.typography.labelLarge, color = Colors.FortText)
            }
            !m.desbloquejada -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Colors.TextSecundari, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.andana_millora_bloquejada, m.nivellMinim, euros(m.cost)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Colors.TextSecundari,
                )
            }
            else -> BotoPrincipal(
                stringResource(R.string.andana_millora_compra, euros(m.cost)),
                onClick = onCompra,
                modifier = Modifier.fillMaxWidth(),
                carregant = comprant,
            )
        }
    }
}

// --- Les maestries del jugador --------------------------------------------------------------------------------------

/** Les maestries de les línies i de les andanes del jugador i la seva xarxa més gran. */
@Composable
fun MaestriesScreen(onTorna: () -> Unit, onObreAndana: (String) -> Unit, viewModel: MaestriesViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.carrega() }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Capcalera(stringResource(R.string.maestries_titol), onTorna)
        val e = ui.estat
        if (e == null) {
            Centrat {
                if (ui.error != null) {
                    Text(stringResource(R.string.compra_error, ui.error.orEmpty()), color = Colors.Error)
                } else {
                    CircularProgressIndicator(color = Colors.Fort)
                }
            }
            return@Column
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { TargetaXarxa(e.xarxaMesGran) }
            if (e.linies.isEmpty() && e.andanes.isEmpty()) {
                item { Text(stringResource(R.string.maestries_buit), color = Colors.TextSecundari) }
            }
            if (e.linies.isNotEmpty()) {
                item { Seccio(stringResource(R.string.maestries_seccio_linies)) }
                items(e.linies, key = { "linia_${it.id}" }) { l ->
                    FilaMaestria(titol = l.codi, subtitol = l.nom, color = l.color, maestria = l.maestria)
                }
            }
            if (e.andanes.isNotEmpty()) {
                item { Seccio(stringResource(R.string.maestries_seccio_andanes)) }
                items(e.andanes, key = { "andana_${it.id}" }) { a ->
                    FilaMaestria(titol = a.nom, subtitol = null, color = null, maestria = a.maestria, onClick = { onObreAndana(a.id) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TargetaXarxa(x: XarxaUnida) {
    Column(Modifier.fillMaxWidth().ombraDura().background(Colors.Superficie).border(2.dp, Colors.Text).padding(16.dp)) {
        Text(
            stringResource(R.string.maestries_xarxa),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = Colors.TextSecundari,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Xifra(x.andanes, stringResource(R.string.maestries_xarxa_andanes))
            Xifra(x.municipis, stringResource(R.string.maestries_xarxa_municipis))
            Xifra(x.comarques, stringResource(R.string.maestries_xarxa_comarques))
            Xifra(x.provincies, stringResource(R.string.maestries_xarxa_provincies))
            Xifra(x.linies, stringResource(R.string.maestries_xarxa_linies))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.maestries_xarxa_explicacio), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
    }
}

@Composable
private fun Xifra(valor: Int, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
        )
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = Colors.TextSecundari, textAlign = TextAlign.Center)
    }
}

// --- Peces comunes -------------------------------------------------------------------------------------------------

@Composable
private fun FilaMaestria(titol: String, subtitol: String?, color: String?, maestria: Maestria, onClick: (() -> Unit)? = null) {
    val base = Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text)
    Row(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Insignia(maestria.nivell, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titol, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text, maxLines = 1)
            if (!subtitol.isNullOrBlank()) {
                Text(subtitol, style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Barra(maestria.progres)
            Text(textXp(maestria), style = MaterialTheme.typography.labelSmall, color = Colors.TextSecundari)
        }
    }
}

@Composable
private fun TargetaMaestria(m: Maestria, gran: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Insignia(m.nivell, null, if (gran) 56 else 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.andana_nivell, m.nivell),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Barra(m.progres)
            Spacer(Modifier.height(2.dp))
            Text(textXp(m), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        }
    }
}

@Composable
private fun textXp(m: Maestria): String {
    val seg = m.xpSeguent
    return if (seg == null) {
        stringResource(R.string.andana_xp_maxim, m.xp.toInt())
    } else {
        stringResource(R.string.andana_xp, m.xp.toInt(), seg.toInt())
    }
}

/** L'insígnia del nivell: un cercle amb el número, del color de la línia (o el fort). */
@Composable
private fun Insignia(nivell: Int, color: String?, mida: Int = 40) {
    val fons = color?.let { colorDe(it) } ?: Colors.Fort
    Box(
        Modifier.size(mida.dp).clip(CircleShape).background(fons).border(2.dp, Colors.Text, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nivell.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black, fontSize = (mida / 2.2).sp),
            color = if (fons.luminancia() > 0.55f) Color(0xFF111111) else Color.White,
        )
    }
}

@Composable
private fun Barra(progres: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(Colors.Fons).border(1.5.dp, Colors.Text)) {
        Box(Modifier.fillMaxWidth(progres.coerceIn(0f, 1f)).fillMaxHeight().background(Colors.Fort))
    }
}

@Composable
private fun Capcalera(titol: String, onTorna: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        IconButton(onClick = onTorna) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.avatar_torna), tint = Colors.Text)
        }
        Text(
            titol,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun Seccio(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = Colors.TextSecundari,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Centrat(contingut: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { contingut() }
}

/** Un percentatge sense decimals: 0,15 → «15». */
internal fun percent(fraccio: Double): Int = Math.round(fraccio * 100).toInt()

private fun decimal(valor: Double): String = String.format(Locale.ROOT, "%.1f", valor).replace('.', ',')

private fun colorDe(hex: String): Color = runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrDefault(Color.Gray)

private fun Color.luminancia(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun nomMillora(id: String): String = stringResource(
    when (id) {
        "marquesina" -> R.string.millora_marquesina
        "aparcament_bicis" -> R.string.millora_aparcament_bicis
        "pantalles" -> R.string.millora_pantalles
        "maquines_bitllets" -> R.string.millora_maquines_bitllets
        "ascensor" -> R.string.millora_ascensor
        "cafeteria" -> R.string.millora_cafeteria
        else -> R.string.millora_desconeguda
    },
)

@Composable
private fun descripcioMillora(id: String): String = stringResource(
    when (id) {
        "marquesina" -> R.string.millora_marquesina_desc
        "aparcament_bicis" -> R.string.millora_aparcament_bicis_desc
        "pantalles" -> R.string.millora_pantalles_desc
        "maquines_bitllets" -> R.string.millora_maquines_bitllets_desc
        "ascensor" -> R.string.millora_ascensor_desc
        "cafeteria" -> R.string.millora_cafeteria_desc
        else -> R.string.millora_desconeguda
    },
)
