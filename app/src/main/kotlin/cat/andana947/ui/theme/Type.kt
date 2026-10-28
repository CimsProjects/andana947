package cat.andana947.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cat.andana947.R

/**
 * Archivo Black per als títols i les xifres. Només té un gruix: es registra per a tots els que es demanen perquè
 * Android no se n'inventi un de més gruixut.
 */
val Archivo = FontFamily(
    Font(R.font.archivo_black, FontWeight.Normal),
    Font(R.font.archivo_black, FontWeight.Medium),
    Font(R.font.archivo_black, FontWeight.SemiBold),
    Font(R.font.archivo_black, FontWeight.Bold),
)

/** Instrument Sans per al text. */
val Instrument = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

/** Caveat, escrita a mà: només per a les frases fetes i les pistes. */
val Caveat = FontFamily(Font(R.font.caveat_semibold, FontWeight.SemiBold))

/** Els estils de text, amb el color del tema (clar o fosc) que hi hagi en el moment de cridar-la. */
fun tipografia(): Typography {
    val titol = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em, color = Colors.Text)
    val text = TextStyle(fontFamily = Instrument, fontWeight = FontWeight.Normal, color = Colors.Text)
    return Typography(
        displayLarge = titol.copy(fontSize = 40.sp, lineHeight = 42.sp),
        displayMedium = titol.copy(fontSize = 36.sp, lineHeight = 38.sp),
        displaySmall = titol.copy(fontSize = 28.sp, lineHeight = 30.sp),
        headlineLarge = titol.copy(fontSize = 34.sp, lineHeight = 36.sp),
        headlineMedium = titol.copy(fontSize = 24.sp, lineHeight = 28.sp),
        headlineSmall = titol.copy(fontSize = 20.sp, lineHeight = 24.sp),
        titleLarge = titol.copy(fontSize = 18.sp, lineHeight = 22.sp),
        titleMedium = text.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        titleSmall = text.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        bodyLarge = text.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = text.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = text.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Colors.TextSecundari),
        labelLarge = text.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        labelMedium = text.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        labelSmall = text.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
    )
}
