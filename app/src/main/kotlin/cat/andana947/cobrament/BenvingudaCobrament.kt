package cat.andana947.cobrament

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.andana947.R
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Caveat
import cat.andana947.ui.theme.Colors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

/** Fons fosc i clar fixos: la benvinguda es veu igual amb el tema clar i el fosc. */
private val Fosc = Color(0xFF0F0F0F)
private val Clar = Color(0xFFF2F2F2)

/**
 * Pantalla sencera en tornar a l'app amb cobraments per veure: cau una pluja de monedes, la targeta entra amb un bot i
 * l'import puja de 0 fins al total. Els catalins ja són al saldo; «Recull» només ho dona per vist.
 */
@Composable
fun BenvingudaCobrament(pendent: CobramentPendent, onRecull: () -> Unit) {
    BackHandler(onBack = onRecull)
    val vibracio = LocalHapticFeedback.current

    val fons = remember { Animatable(0f) }
    val entrada = remember { Animatable(0.4f) }
    val comptador = remember { Animatable(0f) }
    var comptat by remember { mutableStateOf(false) }
    val bot = remember { Animatable(1f) }

    LaunchedEffect(pendent) {
        fons.animateTo(1f, tween(350))
        entrada.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(pendent) {
        kotlinx.coroutines.delay(450)
        // Com més s'ha cobrat, més dura el comptador (entre 1,4 i 2,6 s).
        val durada = (1_400 + pendent.total * 40).toInt().coerceAtMost(2_600)
        comptador.animateTo(pendent.total.toFloat(), tween(durada, easing = FastOutSlowInEasing))
        vibracio.performHapticFeedback(HapticFeedbackType.LongPress)
        comptat = true
        bot.animateTo(1.15f, tween(120))
        bot.animateTo(1f, spring(dampingRatio = 0.35f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fons.value }
            .background(Fosc.copy(alpha = 0.92f))
            // Els tocs no passen a les pantalles de sota.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        PlujaDeMonedes(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.benvinguda_titol),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Caveat, color = Clar),
            )
            Spacer(Modifier.height(16.dp))

            // La targeta: el color fort, vora de tinta i ombra dura clara.
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = entrada.value
                        scaleY = entrada.value
                        rotationZ = (1f - entrada.value) * -10f
                    }
                    .fillMaxWidth()
                    .padding(end = 6.dp, bottom = 6.dp)
                    .drawBehind { drawRect(Clar, topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = size) }
                    .background(Colors.Fort)
                    .border(3.dp, Colors.TintaFort)
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.benvinguda_has_cobrat),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Colors.TintaFort,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.imperi_cobrament_import, euros(comptador.value.toDouble())),
                    modifier = Modifier.graphicsLayer {
                        scaleX = bot.value
                        scaleY = bot.value
                    },
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = Archivo,
                        fontWeight = FontWeight.Black,
                        fontSize = 52.sp,
                        color = Colors.TintaFort,
                    ),
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = pluralStringResource(R.plurals.benvinguda_cobraments, pendent.nombre, pendent.nombre),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Colors.TintaFort,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.benvinguda_saldo, euros(pendent.saldoAra)),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Clar,
            )
            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(visible = comptat, enter = fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 2 }) {
                BotoPrincipal(
                    text = stringResource(R.string.benvinguda_recull),
                    onClick = onRecull,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private class Moneda(val x: Float, val retard: Float, val durada: Float, val mida: Float, val gir: Float)

/** Monedes que cauen i giren. Es dibuixen amb el Canvas: sense imatges ni biblioteques d'animació. */
@Composable
private fun PlujaDeMonedes(modifier: Modifier) {
    val monedes = remember {
        val r = Random(947)
        List(36) {
            Moneda(
                x = r.nextFloat(),
                retard = r.nextFloat() * 1.4f,
                durada = 1.8f + r.nextFloat() * 1.4f,
                mida = 14f + r.nextFloat() * 16f,
                gir = 3f + r.nextFloat() * 5f,
            )
        }
    }
    var temps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val inici = withFrameNanos { it }
        while (true) {
            withFrameNanos { temps = (it - inici) / 1_000_000_000f }
        }
    }

    val fort = Colors.Fort
    val tinta = Colors.TintaFort
    Canvas(modifier) {
        for (m in monedes) {
            val t = temps - m.retard
            if (t < 0f) continue
            // Cada moneda torna a caure en acabar; la primera tanda surt de cop.
            val cicle = (t % m.durada) / m.durada
            val radi = m.mida.dp.toPx() / 2
            val y = -radi * 2 + cicle * (size.height + radi * 4)
            val x = m.x * size.width
            val ample = max(0.12f, abs(cos(t * m.gir))) * radi * 2
            val alt = radi * 2
            val origen = Offset(x - ample / 2, y - alt / 2)
            drawOval(fort, topLeft = origen, size = Size(ample, alt))
            drawOval(tinta, topLeft = origen, size = Size(ample, alt), style = Stroke(width = 2.dp.toPx()))
            val dins = 0.55f
            drawOval(
                tinta.copy(alpha = 0.5f),
                topLeft = Offset(x - ample * dins / 2, y - alt * dins / 2),
                size = Size(ample * dins, alt * dins),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}
