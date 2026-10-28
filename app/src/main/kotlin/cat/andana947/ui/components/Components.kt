package cat.andana947.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.andana947.R
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.LocalReduirAnimacions

/**
 * La base dels botons, com a municipis: vora de tinta de 2 dp i ombra dura. En prémer, el botó s'enfonsa fins a tocar
 * l'ombra. Si el text canvia (per exemple, «Teva!» després del check-in), el canvi s'anima: el botó contesta.
 */
@Composable
private fun BotoAmbOmbra(
    onClick: () -> Unit,
    fons: Color,
    contingut: Color,
    modifier: Modifier,
    enabled: Boolean,
    ombra: Dp = 4.dp,
    alcadaMinima: Dp = 56.dp,
    ample: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaccio = remember { MutableInteractionSource() }
    val premut by interaccio.collectIsPressedAsState()
    val reduir = LocalReduirAnimacions.current
    val enfonsat by animateDpAsState(if (premut && enabled) ombra else 0.dp, tween(if (reduir) 0 else 70), label = "enfonsat")
    val tinta = Colors.Text
    val vora = if (enabled) tinta else Colors.Linia
    Box(
        modifier
            .then(if (ample) Modifier.fillMaxWidth() else Modifier)
            .padding(end = ombra, bottom = ombra)
            .drawBehind {
                // L'ombra dura: un rectangle de tinta desplaçat, que s'amaga a mesura que el botó s'enfonsa.
                if (enabled) {
                    val d = ombra.toPx()
                    drawRect(tinta, topLeft = Offset(d, d), size = size)
                }
            },
    ) {
        Row(
            Modifier
                .offset(enfonsat, enfonsat)
                .then(if (ample) Modifier.fillMaxWidth() else Modifier)
                .heightIn(min = alcadaMinima)
                .background(if (enabled) fons else Colors.Superficie2)
                .border(2.dp, vora)
                .clickable(interactionSource = interaccio, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) contingut else Colors.TextSecundari) { content() }
        }
    }
}

/** El text d'un botó; si canvia, el vell surt per dalt i el nou entra per baix. */
@Composable
private fun TextQueContesta(text: String, estil: TextStyle) {
    val reduir = LocalReduirAnimacions.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            if (reduir) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(tween(260)) { it } + fadeIn(tween(200))) togetherWith (slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)))
            }
        },
        label = "text del botó",
    ) { t -> Text(t, style = estil.copy(color = LocalContentColor.current)) }
}

/** L'acció principal d'una pantalla, amb el color fort. */
@Composable
fun BotoPrincipal(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    carregant: Boolean = false,
) {
    BotoAmbOmbra(onClick, Colors.Fort, Colors.TintaFort, modifier, enabled && !carregant) {
        if (carregant) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Colors.TintaFort, strokeWidth = 2.dp)
        } else {
            TextQueContesta(text, MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }
    }
}

@Composable
fun BotoSecundari(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    enabled: Boolean = true,
) {
    BotoAmbOmbra(onClick, Colors.Fons, Colors.Text, modifier, enabled, ombra = 3.dp, alcadaMinima = 48.dp) {
        if (icona != null) {
            Icon(icona, contentDescription = null, modifier = Modifier.size(18.dp))
            Box(Modifier.width(8.dp))
        }
        TextQueContesta(text, MaterialTheme.typography.labelLarge)
    }
}

/** Botó d'icona quadrat de 48 dp (GDD, secció 7.5), blanc amb vora i ombra dura, per posar sobre el mapa. */
@Composable
fun BotoIcona(
    icona: ImageVector,
    descripcio: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BotoAmbOmbra(onClick, Colors.Fons, Colors.Text, modifier, enabled = true, ombra = 3.dp, alcadaMinima = 48.dp, ample = false) {
        Icon(icona, contentDescription = descripcio, modifier = Modifier.size(22.dp))
    }
}

/** L'ombra dura de les peces importants: un rectangle de tinta desplaçat cap avall i a la dreta. */
fun Modifier.ombraDura(ombra: Dp = 4.dp): Modifier = this
    .padding(end = ombra, bottom = ombra)
    .drawBehind {
        val d = ombra.toPx()
        drawRect(Colors.Text, topLeft = Offset(d, d), size = size)
    }

@Composable
fun Targeta(
    modifier: Modifier = Modifier,
    fons: Color = Colors.Fons,
    vora: Color = Colors.Text,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .background(fons)
            .border(1.5.dp, vora)
            .padding(16.dp),
    ) { content() }
}

/**
 * La marca de l'app: un quadrat del color fort amb vora i ombra dura, amb la parada i la vora de l'andana (com la
 * icona), i el nom al costat. Es llegeix com un sol text: «Andana 947».
 */
@Composable
fun Marca(modifier: Modifier = Modifier, gran: Boolean = false) {
    val nom = stringResource(R.string.app_name)
    val costat = if (gran) 64.dp else 40.dp
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = nom },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (gran) 14.dp else 10.dp),
    ) {
        val tinta = Colors.TintaFort
        Canvas(
            Modifier
                .ombraDura(if (gran) 4.dp else 3.dp)
                .size(costat)
                .background(Colors.Fort)
                .border(2.dp, Colors.Text),
        ) {
            // Les mateixes proporcions que la icona (drawable/ic_launcher_foreground.xml): un quadrat de 42.
            val u = size.width / 42f
            drawCircle(tinta, radius = 7 * u, center = Offset(21 * u, 14 * u))
            drawRect(tinta, topLeft = Offset(6 * u, 28 * u), size = Size(30 * u, 6 * u))
        }
        Column {
            Text(stringResource(R.string.marca_andana), fontFamily = Archivo, fontSize = if (gran) 34.sp else 20.sp, lineHeight = if (gran) 34.sp else 20.sp, color = Colors.Text)
            Text(stringResource(R.string.marca_947), fontFamily = Archivo, fontSize = if (gran) 18.sp else 13.sp, lineHeight = if (gran) 20.sp else 15.sp, color = Colors.TextSecundari)
        }
    }
}

/**
 * Mentre es carrega: un tren (un rectangle del color fort) que arriba a l'andana i en torna a marxar.
 * Amb «Reduir animacions», quiet.
 */
@Composable
fun Carregant(modifier: Modifier = Modifier) {
    val text = stringResource(R.string.carregant)
    val reduir = LocalReduirAnimacions.current
    val transicio = rememberInfiniteTransition(label = "carregant")
    val t by transicio.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "tren",
    )
    val fort = Colors.Fort
    val tinta = Colors.Text
    Canvas(modifier.size(width = 120.dp, height = 24.dp).semantics { contentDescription = text }) {
        val via = 3.dp.toPx()
        drawRect(tinta, topLeft = Offset(0f, size.height - via), size = Size(size.width, via))
        val ample = size.width * .3f
        val x = if (reduir) (size.width - ample) / 2 else (size.width - ample) * t
        val alt = size.height - via - 4.dp.toPx()
        drawRect(fort, topLeft = Offset(x, 0f), size = Size(ample, alt))
        drawRect(tinta, topLeft = Offset(x, 0f), size = Size(ample, alt), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

/** La pantalla d'obertura: la marca i el tren mentre l'app es prepara. */
@Composable
fun Obertura(modifier: Modifier = Modifier) {
    Column(
        modifier.background(Colors.Fons),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Marca(gran = true)
        Carregant()
    }
}
