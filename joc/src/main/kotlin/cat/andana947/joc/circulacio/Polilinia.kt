package cat.andana947.joc.circulacio

/**
 * Les polilínies codificades de Google (precisió de 5 decimals), com les escriu `scripts/andana/circulacions.py`:
 * https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
object Polilinia {
    fun descodifica(text: String, precisio: Int = 5): List<Punt> {
        var factor = 1.0
        repeat(precisio) { factor *= 10 }
        val punts = mutableListOf<Punt>()
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < text.length) {
            val valors = LongArray(2)
            for (k in 0..1) {
                var resultat = 0L
                var desplacament = 0
                while (true) {
                    require(i < text.length) { "Polilínia truncada" }
                    val b = text[i++].code - 63
                    resultat = resultat or ((b and 0x1F).toLong() shl desplacament)
                    desplacament += 5
                    if (b < 0x20) break
                }
                valors[k] = if (resultat and 1L != 0L) (resultat shr 1).inv() else resultat shr 1
            }
            lat += valors[0]
            lon += valors[1]
            punts += Punt(lat / factor, lon / factor)
        }
        return punts
    }
}
