package cat.andana947.avatar

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una capa d'una peça: profunditat, camí SVG en un quadrat de 200 i colors (hexadecimal o rol). */
data class Capa(val z: Int, val d: String, val fill: String?, val stroke: String?, val w: Float)

/** Una peça de l'avatar. Les del director ([npc]) no es poden triar. */
data class Peca(val id: String, val ranura: String, @StringRes val nom: Int?, val npc: Boolean, val capes: List<Capa>)

data class ColorAvatar(val id: String, val argb: Long, @StringRes val nom: Int) {
    val color: Color get() = Color(argb)
}

data class Ranura(val id: String, val obligatoria: Boolean, @StringRes val nom: Int)

/** L'avatar d'un jugador, tal com es desa a `perfils.avatar`. */
@Serializable
data class Avatar(
    val cos: String = "noi",
    val pell: String = "pell_2",
    val cabell: String = "cabell_curt",
    @SerialName("color_cabell")
    val colorCabell: String = "cabell_castany",
    val cap: String? = null,
    val tronc: String = "tronc_samarreta",
    val coll: String? = null,
    val ulls: String? = null,
    val orelles: String? = null,
    val bigoti: String? = null,
) {
    /** La peça triada a una ranura de l'editor. */
    fun a(ranura: String): String? = when (ranura) {
        "cabell" -> cabell
        "cap" -> cap
        "tronc" -> tronc
        "coll" -> coll
        "ulls" -> ulls
        "orelles" -> orelles
        else -> null
    }

    fun amb(ranura: String, peca: String?): Avatar = when (ranura) {
        "cabell" -> copy(cabell = peca ?: cabell)
        "cap" -> copy(cap = peca)
        "tronc" -> copy(tronc = peca ?: tronc)
        "coll" -> copy(coll = peca)
        "ulls" -> copy(ulls = peca)
        "orelles" -> copy(orelles = peca)
        else -> this
    }
}

/** Les peces i els colors de l'avatar (generats a partir de scripts/avatar/cataleg.py). */
object CatalegAvatar {
    val peces: List<Peca> get() = CatalegGenerat.peces
    val tonsPell: List<ColorAvatar> get() = CatalegGenerat.tonsPell
    val colorsCabell: List<ColorAvatar> get() = CatalegGenerat.colorsCabell
    val ranures: List<Ranura> get() = CatalegGenerat.ranures
    val director: Avatar get() = CatalegGenerat.director
    val inicial: Avatar get() = CatalegGenerat.inicial

    private val perId: Map<String, Peca> by lazy { peces.associateBy { it.id } }

    fun peca(id: String?): Peca? = id?.let { perId[it] }

    /** Les peces que pot triar el jugador a una ranura. */
    fun opcions(ranura: String): List<Peca> = peces.filter { it.ranura == ranura && !it.npc }

    /** Totes les capes de l'avatar, de la més llunyana a la més propera. Les peces desconegudes s'ignoren. */
    fun capes(avatar: Avatar, cara: String? = null): List<Capa> {
        val caraId = cara ?: if (avatar.cos == "noia") "cara_noia" else "cara_noi"
        val ids = listOf(avatar.cabell, avatar.cap, avatar.tronc, avatar.coll, avatar.ulls, avatar.orelles, avatar.bigoti, caraId)
        return (CatalegGenerat.cos + ids.mapNotNull { peca(it) }.flatMap { it.capes }).sortedBy { it.z }
    }
}

private val Tinta = Color(0xFF111111)
private val pathsEnMemoria = HashMap<String, Path>()

private fun path(d: String): Path = synchronized(pathsEnMemoria) {
    pathsEnMemoria.getOrPut(d) { PathParser().parsePathString(d).toPath() }
}

/**
 * Dibuixa l'avatar. [cara] força una expressió (les del director: `cara_director`, `cara_director_ullet`). Els colors de
 * les peces no depenen del tema: l'avatar es veu igual en clar i en fosc.
 */
@Composable
fun DibuixAvatar(avatar: Avatar, modifier: Modifier = Modifier, cara: String? = null) {
    val capes = remember(avatar, cara) { CatalegAvatar.capes(avatar, cara) }
    val pell = CatalegAvatar.tonsPell.firstOrNull { it.id == avatar.pell }?.color ?: Color(0xFFEEC1A0)
    val cabell = CatalegAvatar.colorsCabell.firstOrNull { it.id == avatar.colorCabell }?.color ?: Color(0xFF6B4226)
    val rols = remember(pell, cabell) {
        mapOf(
            "pell" to pell,
            "pell_ombra" to lerp(pell, Color.Black, 0.18f),
            "cabell" to cabell,
            "cabell_ombra" to lerp(cabell, Color.Black, 0.18f),
            "tinta" to Tinta,
        )
    }
    Canvas(modifier) {
        val escala = minOf(size.width, size.height) / 200f
        translate((size.width - 200f * escala) / 2, (size.height - 200f * escala) / 2) {
            scale(escala, pivot = Offset.Zero) {
                for (capa in capes) {
                    val p = path(capa.d)
                    color(capa.fill, rols)?.let { drawPath(p, it) }
                    color(capa.stroke, rols)?.let {
                        drawPath(p, it, style = Stroke(width = capa.w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

private fun color(valor: String?, rols: Map<String, Color>): Color? = when {
    valor == null -> null
    valor.startsWith("#") -> Color(("FF" + valor.substring(1)).toLong(16))
    else -> rols[valor]
}
