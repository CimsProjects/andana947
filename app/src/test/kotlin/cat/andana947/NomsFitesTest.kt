package cat.andana947

import cat.andana947.temporada.tipusIObjectiu
import org.junit.Assert.assertEquals
import org.junit.Test

class NomsFitesTest {
    @Test
    fun `el tipus i l'objectiu surten de l'identificador de la fita`() {
        assertEquals("andanes" to 10, tipusIObjectiu("andanes_10"))
        assertEquals("municipis" to 947, tipusIObjectiu("municipis_947"))
        assertEquals("linies_completes" to 5, tipusIObjectiu("linia_completa_5"))
        assertEquals("comarques" to null, tipusIObjectiu("comarques_totes"))
    }
}
