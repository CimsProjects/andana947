package cat.andana947.introduccio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.avatar.CatalegAvatar
import cat.andana947.avatar.DibuixAvatar
import cat.andana947.avatar.EditorAvatar
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Caveat
import cat.andana947.ui.theme.Colors
import kotlinx.coroutines.delay

/** Velocitat de la màquina d'escriure dels diàlegs. */
private const val MS_PER_LLETRA = 22L

/**
 * La introducció del Director (docs/lore.md): uns quants diàlegs, el carnet d'operador (l'avatar) i el comiat. En
 * acabar, el servidor ho marca i es crida [onAcabada].
 */
@Composable
fun IntroduccioScreen(onAcabada: () -> Unit, viewModel: IntroduccioViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(estat.acabada) { if (estat.acabada) onAcabada() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Fons)
            .systemBarsPadding(),
    ) {
        Progres(estat.pas, passosIntroduccio.size)
        AnimatedContent(
            targetState = estat.pas,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(240))) togetherWith
                    (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(180)))
            },
            modifier = Modifier.weight(1f),
            label = "pas de la introducció",
        ) { pas ->
            when (val p = passosIntroduccio[pas]) {
                is PasIntroduccio.Dialeg -> Dialeg(
                    text = stringResource(p.text),
                    cara = p.cara,
                    darrer = pas == passosIntroduccio.lastIndex,
                    desant = estat.desant,
                    error = estat.error,
                    onEndavant = viewModel::endavant,
                )
                PasIntroduccio.TriaRegal -> estat.regal?.let { seleccio ->
                    TriaRegal(
                        seleccio = seleccio,
                        avisMaxim = estat.avisMaxim,
                        desant = estat.desant,
                        error = estat.error,
                        onCommuta = viewModel::commutaRegal,
                        onConfirma = viewModel::endavant,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                PasIntroduccio.FesAvatar -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Text(
                        stringResource(R.string.avatar_titol),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                        color = Colors.Text,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    EditorAvatar(estat.avatar, viewModel::canviaAvatar, Modifier.weight(1f))
                    estat.error?.let {
                        Text(stringResource(R.string.avatar_error, it), color = Colors.Error, style = MaterialTheme.typography.bodySmall)
                    }
                    BotoPrincipal(
                        text = stringResource(R.string.avatar_fet),
                        onClick = viewModel::endavant,
                        carregant = estat.desant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Progres(pas: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(if (i <= pas) Colors.Fort else Colors.Linia),
            )
        }
    }
}

@Composable
private fun Dialeg(
    text: String,
    cara: String,
    darrer: Boolean,
    desant: Boolean,
    error: String?,
    onEndavant: () -> Unit,
) {
    // Màquina d'escriure: el primer toc acaba la frase; el segon, passa a la següent.
    var lletres by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (lletres < text.length) {
            delay(MS_PER_LLETRA)
            lletres++
        }
    }
    val complet = lletres >= text.length
    val tocar: () -> Unit = {
        if (!complet) {
            lletres = text.length
        } else if (!darrer) {
            onEndavant()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = tocar)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        Bafarada(text.take(lletres))
        Spacer(Modifier.height(4.dp))
        Retrat(cara)
        Spacer(Modifier.weight(0.6f))
        when {
            darrer && complet -> {
                error?.let {
                    Text(stringResource(R.string.introduccio_error, it), color = Colors.Error, style = MaterialTheme.typography.bodySmall)
                }
                BotoPrincipal(
                    text = stringResource(R.string.introduccio_comenca),
                    onClick = onEndavant,
                    carregant = desant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
            complet -> Text(
                stringResource(R.string.director_toca),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Caveat, fontSize = 20.sp),
                color = Colors.TextSecundari,
                modifier = Modifier.padding(bottom = 28.dp),
            )
            else -> Spacer(Modifier.height(56.dp))
        }
    }
}

/** La bafarada: vora de tinta, ombra dura i una punta cap al Director. */
@Composable
private fun Bafarada(text: String) {
    val tinta = Colors.Text
    val fons = Colors.Superficie
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val d = 4.dp.toPx()
                val punta = Path().apply {
                    moveTo(size.width / 2 - 14.dp.toPx(), size.height - 1f)
                    lineTo(size.width / 2, size.height + 16.dp.toPx())
                    lineTo(size.width / 2 + 14.dp.toPx(), size.height - 1f)
                    close()
                }
                drawRect(tinta, topLeft = Offset(d, d), size = Size(size.width, size.height))
                drawPath(punta, fons)
                drawPath(punta, tinta, style = Stroke(2.dp.toPx()))
            }
            .background(fons)
            .border(2.dp, tinta)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.director_nom),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = Colors.FortText,
        )
        Spacer(Modifier.height(6.dp))
        // Es reserva l'espai de tres línies perquè la bafarada no salti mentre s'escriu.
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 24.sp),
            color = Colors.Text,
            minLines = 3,
        )
    }
}

@Composable
private fun Retrat(cara: String) {
    val entrada = remember { Animatable(0.85f) }
    LaunchedEffect(cara) {
        entrada.snapTo(0.92f)
        entrada.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    val descripcio = stringResource(R.string.director_descripcio)
    DibuixAvatar(
        CatalegAvatar.director,
        Modifier
            .padding(top = 18.dp)
            .size(240.dp)
            .graphicsLayer {
                scaleX = entrada.value
                scaleY = entrada.value
            }
            .semantics { contentDescription = descripcio },
        cara = cara,
    )
}
