package cat.andana947.joc

import cat.andana947.joc.checkin.Posicio
import cat.andana947.joc.checkin.ResultatCheckin
import cat.andana947.joc.checkin.ValidadorCheckin
import cat.andana947.joc.model.FontAfluencia
import cat.andana947.joc.model.Mode
import cat.andana947.joc.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidadorCheckinTest {
    private val validador = ValidadorCheckin(Repositori.config)
    private val parada = Node("bus", "Parada", Mode.BUS, 41.4, 2.2, 300.0, FontAfluencia.ESTIMAT)
    private val estacio = parada.copy(id = "tren", mode = Mode.RODALIES)

    // 0,0009° de latitud ≈ 100 m.
    private fun a(metresNord: Double, precisio: Double = 10.0, velocitat: Double = 0.0, instantMs: Long = 0) =
        Posicio(41.4 + metresNord / 111_195.0, 2.2, precisio, velocitat, instantMs)

    @Test
    fun `a la parada és vàlid`() {
        assertEquals(ResultatCheckin.Valid, validador.valida(parada, a(20.0)))
    }

    @Test
    fun `la precisió del GPS compta a favor`() {
        // 100 m de distància, 60 m de radi, 40 m de precisió: just a tocar.
        assertEquals(ResultatCheckin.Valid, validador.valida(parada, a(99.0, precisio = 40.0)))
        val lluny = validador.valida(parada, a(140.0, precisio = 40.0))
        assertIs<ResultatCheckin.MassaLluny>(lluny)
        assertTrue(lluny.metres in 40..41, "Falten ${lluny.metres} m")
    }

    @Test
    fun `les estacions tenen més radi`() {
        assertIs<ResultatCheckin.MassaLluny>(validador.valida(parada, a(130.0)))
        assertEquals(ResultatCheckin.Valid, validador.valida(estacio, a(130.0)))
    }

    @Test
    fun `es rebutja una posició poc precisa o en moviment`() {
        assertEquals(ResultatCheckin.PrecisioInsuficient, validador.valida(parada, a(0.0, precisio = 60.0)))
        assertEquals(ResultatCheckin.EnMoviment, validador.valida(parada, a(0.0, velocitat = 30.0)))
    }

    @Test
    fun `es rebutja un salt impossible des del check-in anterior`() {
        // 100 km en 10 minuts són 600 km/h.
        val anterior = a(-100_000.0, instantMs = 0)
        assertEquals(ResultatCheckin.VelocitatImpossible, validador.valida(parada, a(0.0, instantMs = 600_000), anterior))
        // En una hora, sí (100 km/h).
        assertEquals(ResultatCheckin.Valid, validador.valida(parada, a(0.0, instantMs = 3_600_000), anterior))
    }

    @Test
    fun `només una acció per node i per dia`() {
        assertEquals(ResultatCheckin.JaFetAvui, validador.valida(parada, a(0.0), accionsAvuiEnAquestNode = 1))
    }
}
