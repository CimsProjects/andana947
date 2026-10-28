package cat.andana947.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun esquema(fosc: Boolean): ColorScheme {
    val base = if (fosc) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Colors.Fort,
        onPrimary = Colors.TintaFort,
        primaryContainer = Colors.FortSuau,
        onPrimaryContainer = Colors.Text,
        secondary = Colors.Text,
        onSecondary = Colors.Fons,
        background = Colors.Fons,
        onBackground = Colors.Text,
        surface = Colors.Fons,
        onSurface = Colors.Text,
        surfaceVariant = Colors.Superficie,
        onSurfaceVariant = Colors.TextSecundari,
        surfaceContainerLowest = Colors.Fons,
        surfaceContainerLow = Colors.Superficie,
        surfaceContainer = Colors.Superficie,
        surfaceContainerHigh = Colors.Superficie2,
        surfaceContainerHighest = Colors.Superficie2,
        outline = Colors.Text,
        outlineVariant = Colors.Linia,
        error = Colors.Error,
        onError = Colors.Fons,
    )
}

/** Cantonades rectes: res de píndoles. */
private val formes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/** Si l'usuari ha demanat al sistema que es redueixin les animacions (GDD, secció 7.5). */
val LocalReduirAnimacions = staticCompositionLocalOf { false }

/** El tema de l'app: clar o fosc segons el mòbil. */
@Composable
fun AndanaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduir = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val fosc = isSystemInDarkTheme()
    // Els colors es llegeixen d'aquest estat mentre es pinta: s'ha de posar abans de pintar res.
    Tema.fosc = fosc
    val esquema = remember(fosc) { esquema(fosc) }
    val tipografia = remember(fosc) { tipografia() }
    CompositionLocalProvider(LocalReduirAnimacions provides reduir) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, shapes = formes, content = content)
    }
}
