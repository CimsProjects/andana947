package cat.andana947.social

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
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
import cat.andana947.data.joc.EstatCooperatives
import cat.andana947.data.joc.EstatJunta
import cat.andana947.data.joc.FilaCooperativa
import cat.andana947.data.joc.JuntaMeva
import cat.andana947.data.joc.OfertaAccions
import cat.andana947.data.joc.PropostaJunta
import cat.andana947.imperi.euros
import cat.andana947.maestria.nomMillora
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.DialegResultat
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import kotlin.math.roundToInt

private enum class Pestanya(val nom: Int) {
    MERCAT(R.string.social_mercat),
    JUNTES(R.string.social_juntes),
    COOPERATIVA(R.string.social_cooperativa),
}

/** La part social (fase 7): el mercat d'accions, les juntes de les grans estacions i les cooperatives. */
@Composable
fun SocialScreen(onTorna: () -> Unit, onObreJunta: (String) -> Unit, viewModel: SocialViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.carrega() }
    var pestanya by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Capcalera(stringResource(R.string.social_titol), onTorna)
        Row(
            Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pestanya.entries.forEachIndexed { i, p -> Xip(stringResource(p.nom), i == pestanya) { pestanya = i } }
        }
        Spacer(Modifier.height(12.dp))
        ui.error?.let { Text(stringResource(R.string.compra_error, it), color = Colors.Error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (ui.carregant) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Colors.Fort) }
            return@Column
        }
        Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (Pestanya.entries[pestanya]) {
                Pestanya.MERCAT -> Mercat(ui.mercat, ui.ocupat, viewModel::compra, viewModel::retira)
                Pestanya.JUNTES -> Juntes(ui.juntes, onObreJunta)
                Pestanya.COOPERATIVA -> Cooperativa(ui.cooperatives, ui.ocupat, viewModel::creaCooperativa, viewModel::uneix, viewModel::surt)
            }
        }
    }
    ui.resultat?.let { DialegResultat(it, onTanca = viewModel::tancaResultat) }
}

// --- Mercat ------------------------------------------------------------------------------------------------------------

@Composable
private fun Mercat(
    ofertes: List<OfertaAccions>,
    ocupat: Boolean,
    onCompra: (OfertaAccions, Int) -> Unit,
    onRetira: (OfertaAccions) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.social_mercat_explicacio), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari) }
        if (ofertes.isEmpty()) {
            item { Text(stringResource(R.string.social_mercat_buit), color = Colors.TextSecundari) }
        }
        items(ofertes, key = { it.id }) { o -> TargetaOferta(o, ocupat, { q -> onCompra(o, q) }, { onRetira(o) }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun TargetaOferta(o: OfertaAccions, ocupat: Boolean, onCompra: (Int) -> Unit, onRetira: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.nom, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
                Text(
                    stringResource(R.string.social_oferta_venedor, o.venedor, o.quantitat),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.social_preu_accio, euros(o.preu)),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                    color = Colors.FortText,
                )
                Text(
                    stringResource(R.string.social_respecte_referencia, percentRelatiu(o.preu, o.preuReferencia)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Colors.TextSecundari,
                )
            }
        }
        Text(
            stringResource(R.string.social_dividend_accio, euros(o.dividendAccio)),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
        Spacer(Modifier.height(8.dp))
        if (o.meva) {
            BotoSecundari(stringResource(R.string.social_retira), onRetira, Modifier.fillMaxWidth(), enabled = !ocupat)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotoPrincipal(stringResource(R.string.social_compra_una), { onCompra(1) }, Modifier.weight(1f), carregant = ocupat)
                if (o.quantitat > 1) {
                    BotoSecundari(
                        stringResource(R.string.social_compra_totes, o.quantitat, euros(o.preu * o.quantitat)),
                        { onCompra(o.quantitat) },
                        Modifier.weight(1f),
                        enabled = !ocupat,
                    )
                }
            }
        }
    }
}

// --- Juntes ------------------------------------------------------------------------------------------------------------

@Composable
private fun Juntes(juntes: List<JuntaMeva>, onObreJunta: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.social_juntes_explicacio), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari) }
        if (juntes.isEmpty()) {
            item { Text(stringResource(R.string.social_juntes_buit), color = Colors.TextSecundari) }
        }
        items(juntes, key = { it.nodeId }) { j ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (j.perVotar > 0) Colors.FortSuau else Colors.Superficie)
                    .border(1.5.dp, Colors.Text)
                    .clickable(role = Role.Button) { onObreJunta(j.nodeId) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (j.president) {
                            Icon(Icons.Default.Star, contentDescription = stringResource(R.string.social_president), tint = Colors.FortText)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(j.nom, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
                    }
                    Text(
                        stringResource(R.string.social_junta_accions, j.meves, j.venudes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.TextSecundari,
                    )
                }
                if (j.perVotar > 0) {
                    Text(
                        stringResource(R.string.social_per_votar, j.perVotar),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                        color = Colors.FortText,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// --- Cooperativa ---------------------------------------------------------------------------------------------------------

@Composable
private fun Cooperativa(
    estat: EstatCooperatives?,
    ocupat: Boolean,
    onCrea: (String, String) -> Unit,
    onUneix: (Long) -> Unit,
    onSurt: () -> Unit,
) {
    val e = estat ?: EstatCooperatives()
    var nom by rememberSaveable { mutableStateOf("") }
    var comarca by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.social_coop_explicacio, e.maximMembres), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari) }
        val meva = e.meva
        if (meva != null) {
            item {
                Column(Modifier.fillMaxWidth().ombraDura().background(Colors.Fort).border(2.dp, Colors.Text).padding(16.dp)) {
                    Text(
                        meva.nom,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                        color = Colors.TintaFort,
                    )
                    Text(
                        stringResource(R.string.social_coop_resum, meva.comarca, meva.membresTotal, meva.posicio, meva.punts),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Colors.TintaFort,
                    )
                }
            }
            items(meva.membres, key = { "membre_${it.nom}_${it.punts}_${it.rol}" }) { m ->
                Row(Modifier.fillMaxWidth().background(if (m.jo) Colors.FortSuau else Colors.Superficie).padding(10.dp)) {
                    Text(
                        m.nom + if (m.rol == "fundador") " · " + stringResource(R.string.social_fundador) else "",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (m.jo) FontWeight.Bold else FontWeight.Normal),
                        color = Colors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.social_punts, m.punts), style = MaterialTheme.typography.labelLarge, color = Colors.FortText)
                }
            }
            item { BotoSecundari(stringResource(R.string.social_surt), onSurt, Modifier.fillMaxWidth(), enabled = !ocupat) }
        } else {
            if (e.aProp.isNotEmpty()) {
                item { Seccio(stringResource(R.string.social_coop_a_prop)) }
                items(e.aProp, key = { "aprop_${it.id}" }) { c ->
                    FilaCoop(c) { BotoSecundari(stringResource(R.string.social_uneix), { onUneix(c.id) }, enabled = !ocupat) }
                }
            }
            item { Seccio(stringResource(R.string.social_coop_funda)) }
            if (e.comarques.isEmpty()) {
                item { Text(stringResource(R.string.social_coop_sense_comarques), color = Colors.TextSecundari) }
            } else {
                item {
                    OutlinedTextField(
                        value = nom,
                        onValueChange = { nom = it.take(30) },
                        label = { Text(stringResource(R.string.social_coop_nom)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Colors.Text, unfocusedBorderColor = Colors.Linia),
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in e.comarques) Xip(c.nom, comarca == c.codi) { comarca = c.codi }
                    }
                }
                item {
                    BotoPrincipal(
                        stringResource(R.string.social_coop_crea),
                        { comarca?.let { onCrea(nom.trim(), it) } },
                        Modifier.fillMaxWidth(),
                        enabled = nom.trim().length >= 3 && comarca != null,
                        carregant = ocupat,
                    )
                }
            }
        }
        item { Seccio(stringResource(R.string.social_coop_ranking)) }
        if (e.ranking.isEmpty()) {
            item { Text(stringResource(R.string.social_coop_ranking_buit), color = Colors.TextSecundari) }
        }
        items(e.ranking, key = { "rank_${it.id}" }) { c -> FilaCoop(c) {} }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FilaCoop(c: FilaCooperativa, accio: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Colors.Superficie).border(1.dp, Colors.Linia).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.nom, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
            Text(stringResource(R.string.social_coop_fila, c.comarca, c.membres), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        }
        Text(stringResource(R.string.social_punts, c.punts), style = MaterialTheme.typography.labelLarge, color = Colors.FortText)
        Spacer(Modifier.width(8.dp))
        accio()
    }
}

// --- La junta d'una estació -----------------------------------------------------------------------------------------------

/** La junta d'una gran estació: els accionistes (amb el president), les propostes de millora i el mercat de l'estació. */
@Composable
fun JuntaScreen(id: String, onTorna: () -> Unit, viewModel: JuntaViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.carrega(id) }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Capcalera(ui.junta?.nom ?: stringResource(R.string.social_junta), onTorna)
        val j = ui.junta
        if (j == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (ui.carregant) CircularProgressIndicator(color = Colors.Fort) else Text(ui.error.orEmpty(), color = Colors.Error)
            }
            return@Column
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ResumJunta(j) }
            if (j.propostes.isNotEmpty()) {
                item { Seccio(stringResource(R.string.social_propostes)) }
                items(j.propostes, key = { "proposta_${it.id}" }) { p ->
                    TargetaProposta(p, j, ui.ocupat, onVota = { a -> viewModel.vota(p.id, a) })
                }
            }
            item { Seccio(stringResource(R.string.social_accionistes)) }
            items(j.accionistes, key = { "accionista_${it.nom}_${it.quantitat}_${it.president}" }) { a ->
                Row(Modifier.fillMaxWidth().background(if (a.jo) Colors.FortSuau else Colors.Superficie).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (a.president) {
                        Icon(Icons.Default.Star, contentDescription = stringResource(R.string.social_president), tint = Colors.FortText)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(a.nom, style = MaterialTheme.typography.bodyLarge, color = Colors.Text, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.social_accions_percent, a.quantitat, if (j.venudes > 0) a.quantitat * 100 / j.venudes else 0),
                        style = MaterialTheme.typography.labelLarge,
                        color = Colors.TextSecundari,
                    )
                }
            }
            if (j.meves > 0) {
                item { Seccio(stringResource(R.string.social_millores_estacio)) }
                items(j.millores, key = { "millora_${it.id}" }) { m ->
                    Row(Modifier.fillMaxWidth().background(if (m.feta) Colors.FortSuau else Colors.Superficie).border(1.dp, Colors.Linia).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nomMillora(m.id), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
                            Text(
                                stringResource(R.string.social_millora_estacio, (m.bonus * 100).roundToInt(), euros(m.cost)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Colors.TextSecundari,
                            )
                        }
                        when {
                            m.feta -> Text(stringResource(R.string.andana_millora_feta), style = MaterialTheme.typography.labelMedium, color = Colors.FortText)
                            m.proposta != null -> Text(stringResource(R.string.social_en_votacio), style = MaterialTheme.typography.labelMedium, color = Colors.TextSecundari)
                            else -> BotoSecundari(stringResource(R.string.social_proposa), { viewModel.proposa(m.id) }, enabled = !ui.ocupat)
                        }
                    }
                }
                item { Seccio(stringResource(R.string.social_ven_accions)) }
                item { VendaAccions(j, ui.ocupat, viewModel::posaVenda) }
            }
            if (ui.ofertes.isNotEmpty()) {
                item { Seccio(stringResource(R.string.social_ofertes_estacio)) }
                items(ui.ofertes, key = { "oferta_${it.id}" }) { o ->
                    TargetaOferta(o, ui.ocupat, onCompra = { viewModel.compra(o) }, onRetira = { viewModel.retira(o) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    ui.resultat?.let { DialegResultat(it, onTanca = viewModel::tancaResultat) }
}

@Composable
private fun ResumJunta(j: EstatJunta) {
    Column(Modifier.fillMaxWidth().ombraDura().background(Colors.Superficie).border(2.dp, Colors.Text).padding(16.dp)) {
        Text(
            stringResource(R.string.social_junta_meves, j.meves, j.venudes),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
        )
        Text(stringResource(R.string.social_dividend_accio, euros(j.dividendAccio)), style = MaterialTheme.typography.bodyMedium, color = Colors.Text)
        if (j.factorMillores > 1.0) {
            Text(
                stringResource(R.string.andana_multiplicador_millores, ((j.factorMillores - 1) * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Colors.FortText,
            )
        }
        Text(stringResource(R.string.social_junta_explicacio), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
    }
}

@Composable
private fun TargetaProposta(p: PropostaJunta, j: EstatJunta, ocupat: Boolean, onVota: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(12.dp)) {
        Text(
            stringResource(R.string.social_proposta, nomMillora(p.millora), euros(p.cost)),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Colors.Text,
        )
        Text(stringResource(R.string.social_proposada_per, p.proposadaPer), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        Spacer(Modifier.height(6.dp))
        // La barra: vots a favor sobre totes les accions venudes; cal passar de la meitat.
        val total = j.venudes.coerceAtLeast(1)
        Box(Modifier.fillMaxWidth().height(10.dp).background(Colors.Fons).border(1.5.dp, Colors.Text)) {
            Box(Modifier.fillMaxWidth((p.aFavor.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(Colors.Fort))
        }
        Text(
            stringResource(R.string.social_vots, p.aFavor, p.enContra, total / 2 + 1),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
        if (j.meves > 0) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotoPrincipal(
                    stringResource(if (p.elMeuVot == true) R.string.social_has_votat_a_favor else R.string.social_vota_a_favor),
                    { onVota(true) },
                    Modifier.weight(1f),
                    enabled = p.elMeuVot != true,
                    carregant = ocupat,
                )
                BotoSecundari(
                    stringResource(if (p.elMeuVot == false) R.string.social_has_votat_en_contra else R.string.social_vota_en_contra),
                    { onVota(false) },
                    Modifier.weight(1f),
                    enabled = p.elMeuVot != false && !ocupat,
                )
            }
            Text(stringResource(R.string.social_qui_paga), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        }
    }
}

@Composable
private fun VendaAccions(j: EstatJunta, ocupat: Boolean, onPosa: (Int, Double) -> Unit) {
    var quantitat by rememberSaveable { mutableIntStateOf(1) }
    // El preu es tria al voltant del de referència (el servidor accepta de la meitat al triple).
    var relatiu by rememberSaveable { mutableIntStateOf(100) }
    val preu = j.preuReferencia * relatiu / 100.0
    Column(Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.social_quantes), style = MaterialTheme.typography.bodyMedium, color = Colors.Text, modifier = Modifier.weight(1f))
            BotoSecundari("−", { quantitat = (quantitat - 1).coerceAtLeast(1) })
            Text(quantitat.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Colors.Text)
            BotoSecundari("+", { quantitat = (quantitat + 1).coerceAtMost(j.meves) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in listOf(80, 100, 120, 150, 200)) Xip("${r - 100}%".let { if (r >= 100) "+$it" else it }, relatiu == r) { relatiu = r }
        }
        Spacer(Modifier.height(8.dp))
        BotoPrincipal(
            stringResource(R.string.social_posa_venda, quantitat, euros(preu)),
            { onPosa(quantitat, Math.round(preu * 100) / 100.0) },
            Modifier.fillMaxWidth(),
            enabled = j.meves > 0,
            carregant = ocupat,
        )
        Text(stringResource(R.string.social_comissio), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
    }
}

// --- Peces comunes -----------------------------------------------------------------------------------------------------

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

/** «+20 %» o «−10 %» respecte del preu de referència. */
private fun percentRelatiu(preu: Double, referencia: Double): String {
    if (referencia <= 0) return "—"
    val p = ((preu / referencia - 1) * 100).roundToInt()
    return (if (p >= 0) "+" else "−") + kotlin.math.abs(p) + " %"
}
