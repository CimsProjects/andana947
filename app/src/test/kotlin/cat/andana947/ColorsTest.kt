package cat.andana947

import androidx.compose.ui.graphics.Color
import cat.andana947.mapa.EstilMapa
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.Tema
import cat.andana947.ui.theme.contrast
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contrast de 4,5:1 com a mínim per a tot el text (GDD, secció 7.5), en clar i en fosc. */
class ColorsTest {
    @After
    fun torna() {
        Tema.fosc = false
    }

    private fun perTemes(prova: () -> Unit) {
        for (fosc in listOf(false, true)) {
            Tema.fosc = fosc
            prova()
        }
    }

    private fun assertLlegible(text: Color, fons: Color, que: String) {
        val c = contrast(text, fons)
        assertTrue(c >= 4.5f, "$que: contrast de $c (fosc = ${Tema.fosc})")
    }

    @Test
    fun `el color fort és el magenta i la tinta s'hi llegeix`() {
        assertEquals(Color(0xFFFF3D7F), Colors.Fort)
        assertLlegible(Colors.TintaFort, Colors.Fort, "Tinta sobre el color fort")
    }

    @Test
    fun `els textos es llegeixen sobre el fons`() = perTemes {
        assertLlegible(Colors.Text, Colors.Fons, "Text")
        assertLlegible(Colors.TextSecundari, Colors.Fons, "Text secundari")
        assertLlegible(Colors.FortText, Colors.Fons, "Color fort com a text")
        assertLlegible(Colors.Error, Colors.Fons, "Error")
        assertLlegible(Colors.Text, Colors.Superficie, "Text sobre superfície")
    }

    @Test
    fun `en clar, el magenta no es fa servir com a text sobre blanc`() {
        Tema.fosc = false
        assertTrue(contrast(Colors.Fort, Colors.Fons) < 4.5f)
        assertEquals(Colors.Text, Colors.FortText)
    }

    @Test
    fun `els noms del mapa es llegeixen`() {
        for (fosc in listOf(false, true)) {
            val p = EstilMapa.paleta(fosc).mapValues { Color(0xFF000000 or it.value.removePrefix("#").toLong(16)) }
            assertLlegible(p.getValue("text"), p.getValue("fons"), "Noms del mapa")
            assertLlegible(p.getValue("text_secundari"), p.getValue("fons"), "Noms secundaris del mapa")
        }
    }
}
