package cat.andana947.joc.circulacio

/**
 * La planta d'un vehicle al mapa: un polígon tancat que l'app aixeca en 3D. Sobre la via, el vehicle ocupa el tros de
 * traçat que hi ha darrere el cap i en segueix les corbes; fora de la via, és un rectangle orientat segons el rumb.
 */
object Silueta {
    /** El vehicle de [llarg] × [ample] metres amb el cap a [cap] metres del traçat. */
    fun sobreLaVia(tracat: Tracat, cap: Double, llarg: Double, ample: Double): List<Punt> {
        // Al principi de la via, el vehicle hi cap sencer: el cap no queda mai a menys d'un vehicle de l'inici.
        val fins = cap.coerceIn(minOf(llarg, tracat.llargada), tracat.llargada)
        val eix = tracat.tros(fins - llarg, fins)
        val esquerra = eix.map { Tracat.desplaca(it, it.rumb, 0.0, -ample / 2) }
        val dreta = eix.map { Tracat.desplaca(it, it.rumb, 0.0, ample / 2) }
        return tanca(dreta + esquerra.reversed())
    }

    /** Un rectangle de [llarg] × [ample] metres centrat a [centre] i orientat segons el seu rumb. */
    fun rectangle(centre: Punt, llarg: Double, ample: Double): List<Punt> {
        val r = centre.rumb
        return tanca(
            listOf(
                Tracat.desplaca(centre, r, -llarg / 2, ample / 2),
                Tracat.desplaca(centre, r, llarg / 2, ample / 2),
                Tracat.desplaca(centre, r, llarg / 2, -ample / 2),
                Tracat.desplaca(centre, r, -llarg / 2, -ample / 2),
            ),
        )
    }

    private fun tanca(punts: List<Punt>) = punts + punts.first()
}
