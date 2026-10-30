package cat.andana947.joc.circulacio

/**
 * Com és un vehicle vist des de dalt: quants cotxes té, quin espai hi ha entre cotxes (fracció del llarg d'un cotxe),
 * quant fa el morro (en amples) i com d'estret és a la punta (fracció de l'ample). Cada mode en té una (CapaVehicles).
 */
data class Forma(
    val cotxes: Int,
    val separacio: Double = 0.06,
    val morro: Double = 0.9,
    val puntaMorro: Double = 0.55,
)

/**
 * La planta d'un vehicle: un polígon tancat per cotxe i el vidre del davant, que l'app aixeca en 3D. El primer cotxe és
 * el del davant (el sentit de la marxa). Els detalls: el vidre de la cabina del darrere (els trens en tenen als dos
 * extrems), el sostre de cada cotxe (una franja més estreta i una mica més alta, d'un color més clar) i la franja de
 * les finestres, que es veu als costats quan s'inclina el mapa.
 */
data class Planta(
    val cotxes: List<List<Punt>>,
    val vidre: List<Punt>,
    val vidreDarrere: List<Punt>? = null,
    val sostres: List<List<Punt>> = emptyList(),
    val finestres: List<List<Punt>> = emptyList(),
)

/**
 * La planta dels vehicles al mapa. Sobre la via, el vehicle ocupa el tros de traçat que hi ha darrere el cap i en
 * segueix les corbes, cotxe a cotxe; fora de la via, va recte segons el rumb.
 */
object Silueta {
    /** El vehicle de [llarg] × [ample] metres amb el cap a [cap] metres del traçat. */
    fun sobreLaVia(tracat: Tracat, cap: Double, llarg: Double, ample: Double, forma: Forma): Planta {
        // Al principi de la via, el vehicle hi cap sencer: el cap no queda mai a menys d'un vehicle de l'inici.
        val fins = cap.coerceIn(minOf(llarg, tracat.llargada), tracat.llargada)
        val inici = fins - minOf(llarg, tracat.llargada)
        val total = fins - inici
        val n = forma.cotxes.coerceAtLeast(1)
        // n cotxes i n - 1 espais: cada espai fa `separacio` cotxes.
        val cotxe = total / (n + (n - 1) * forma.separacio)
        val espai = cotxe * forma.separacio
        val morro = minOf(ample * forma.morro, cotxe / 3)
        val trams = (0 until n).map { i ->
            val davant = fins - i * (cotxe + espai)
            davant - cotxe to davant
        }
        // El morro, als dos extrems del tren; entre cotxes, els extrems són rectes.
        val cotxes = trams.mapIndexed { i, (darrere, davant) ->
            cos(tracat, darrere, davant, ample, if (i == n - 1) morro else 0.0, if (i == 0) morro else 0.0, forma.puntaMorro)
        }
        // El sostre i les finestres van per la part recta de cada cotxe (sense el morro ni el vidre).
        val marge = maxOf(morro, ample * 0.3) * 1.7
        val rectes = trams.mapIndexed { i, (darrere, davant) ->
            (darrere + if (i == n - 1) marge else ample * 0.25) to (davant - if (i == 0) marge else ample * 0.25)
        }.filter { (a, b) -> b - a > ample * 0.2 }
        return Planta(
            cotxes = cotxes,
            vidre = vidre(tracat, fins, ample, morro, forma.puntaMorro, endavant = true),
            vidreDarrere = if (n > 1) vidre(tracat, inici, ample, morro, forma.puntaMorro, endavant = false) else null,
            sostres = rectes.map { (a, b) -> cos(tracat, a, b, ample * AMPLE_SOSTRE, 0.0, 0.0, 1.0) },
            finestres = rectes.map { (a, b) -> cos(tracat, a, b, ample * AMPLE_FINESTRES, 0.0, 0.0, 1.0) },
        )
    }

    /** El mateix vehicle fora de la via: recte, centrat a [centre] i orientat segons el seu rumb. */
    fun recte(centre: Punt, llarg: Double, ample: Double, forma: Forma): Planta {
        val tracat = Tracat(listOf(Tracat.desplaca(centre, centre.rumb, -llarg / 2), Tracat.desplaca(centre, centre.rumb, llarg / 2)))
        return sobreLaVia(tracat, tracat.llargada, tracat.llargada, ample, forma)
    }

    /**
     * Un cotxe entre [darrere] i [davant] metres del traçat. Els extrems amb morro (de [morroDarrere] i [morroDavant]
     * metres) s'estrenyen fins a [punta] de l'ample.
     */
    private fun cos(
        tracat: Tracat,
        darrere: Double,
        davant: Double,
        ample: Double,
        morroDarrere: Double,
        morroDavant: Double,
        punta: Double,
    ): List<Punt> {
        val eix = tracat.tros(darrere + morroDarrere, davant - morroDavant).toMutableList()
        val amples = MutableList(eix.size) { ample / 2 }
        if (morroDarrere > 0) {
            eix.add(0, tracat.posicio(darrere))
            amples.add(0, ample * punta / 2)
        }
        if (morroDavant > 0) {
            eix.add(tracat.posicio(davant))
            amples.add(ample * punta / 2)
        }
        val dreta = eix.indices.map { Tracat.desplaca(eix[it], eix[it].rumb, 0.0, amples[it]) }
        val esquerra = eix.indices.map { Tracat.desplaca(eix[it], eix[it].rumb, 0.0, -amples[it]) }
        return tanca(dreta + esquerra.reversed())
    }

    /**
     * El vidre d'una cabina: una franja fosca a l'arrel del morro, una mica més estreta que el cotxe. [cap] és l'extrem
     * del vehicle; [endavant], si és el del davant (si no, el vidre va cap enrere).
     */
    private fun vidre(tracat: Tracat, cap: Double, ample: Double, morro: Double, punta: Double, endavant: Boolean): List<Punt> {
        val fons = morro.coerceAtLeast(ample * 0.3)
        val sentit = if (endavant) -1.0 else 1.0
        val darrere = tracat.posicio(cap + sentit * fons * 1.6)
        val davant = tracat.posicio(cap + sentit * fons * 0.5)
        val ampleDavant = ample * (punta + (1 - punta) * 0.5) * 0.8
        return tanca(
            listOf(
                Tracat.desplaca(darrere, darrere.rumb, 0.0, ample * 0.4),
                Tracat.desplaca(davant, davant.rumb, 0.0, ampleDavant / 2),
                Tracat.desplaca(davant, davant.rumb, 0.0, -ampleDavant / 2),
                Tracat.desplaca(darrere, darrere.rumb, 0.0, -ample * 0.4),
            ),
        )
    }

    private fun tanca(punts: List<Punt>) = punts + punts.first()

    /** El sostre és més estret que el cotxe; la franja de les finestres, una mica més ampla (sobresurt als costats). */
    private const val AMPLE_SOSTRE = 0.6
    private const val AMPLE_FINESTRES = 1.04
}
