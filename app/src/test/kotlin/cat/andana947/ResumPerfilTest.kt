package cat.andana947

import cat.andana947.data.joc.ResumPerfil
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumPerfilTest {
    private fun resum(prestigi: Int, inici: Int, seguent: Int) =
        ResumPerfil(0.0, prestigi, 2, inici, seguent, "2026-10-30T12:00:00Z")

    @Test
    fun `el progrés compta dins el tram del nivell`() {
        assertEquals(0f, resum(100, 100, 283).progresNivell, 0.0001f)
        assertEquals(50f / 183f, resum(150, 100, 283).progresNivell, 0.0001f)
    }

    @Test
    fun `el progrés no surt de 0 a 1`() {
        assertEquals(1f, resum(400, 100, 283).progresNivell, 0.0001f)
        assertEquals(1f, resum(0, 0, 0).progresNivell, 0.0001f)
    }
}
