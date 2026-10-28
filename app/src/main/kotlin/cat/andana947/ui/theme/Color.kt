package cat.andana947.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/** Si l'app es pinta en fosc. Segueix el mòbil; ho posa AndanaTheme. És un estat de Compose: en canviar, es repinta. */
object Tema {
    var fosc by mutableStateOf(false)
}

/**
 * Els colors de l'estètica de municipis (docs/disseny.md de municipis): blanc i negre i un sol color fort, que aquí és
 * el magenta `#FF3D7F` (docs/abast.md, secció 9). Tots canvien amb el tema clar o fosc.
 */
object Colors {
    private fun c(clar: Long, fosc: Long) = Color(if (Tema.fosc) fosc else clar)

    /** L'únic color fort de l'app: els botons principals i tot el que és del jugador (al mapa, la seva posició). */
    val Fort = Color(0xFFFF3D7F)

    /** Text i icones sobre el color fort. */
    val TintaFort = Color(0xFF111111)

    val Fons: Color get() = c(0xFFFFFFFF, 0xFF0F0F0F)
    val Superficie: Color get() = c(0xFFF5F5F5, 0xFF1A1A1A)
    val Superficie2: Color get() = c(0xFFECECEC, 0xFF242424)
    val Linia: Color get() = c(0xFFE4E4E4, 0xFF2A2A2A)

    /** La tinta: textos, vores i ombres dures. */
    val Text: Color get() = c(0xFF111111, 0xFFF2F2F2)
    val TextSecundari: Color get() = c(0xFF6B6B6B, 0xFF9A9A9A)

    /**
     * El color fort quan és text, icona o vora sobre el fons. En fosc es llegeix bé; en clar, el magenta sobre blanc no
     * arriba al contrast de 4,5:1 (GDD, secció 7.5), i es fa servir la tinta.
     */
    val FortText: Color get() = if (Tema.fosc) Fort else Text

    /** Fons tènue del color fort, per a opcions triades. */
    val FortSuau: Color get() = lerp(Fons, Fort, if (Tema.fosc) .14f else .16f)

    val Error: Color get() = c(0xFFC62D1F, 0xFFF08A7A)
}

/** El contrast entre dos colors segons les WCAG 2: d'1 (cap) a 21 (negre sobre blanc). */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
}
