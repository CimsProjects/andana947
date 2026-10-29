package cat.andana947.mapa

import cat.andana947.data.preferencies.Camera

/**
 * El mapa només mostra Catalunya: la càmera no en surt i no s'allunya més de [ZOOM_MINIM], de manera que només es
 * baixen les teselles de Catalunya i del seu voltant immediat, i no les del món sencer. El que queda fora de Catalunya
 * dins de la pantalla el tapa la capa `fora` de l'estil, amb el contorn de [FITXER_CONTORN].
 *
 * El contorn i la capsa surten de `scripts/genera_contorn.py`, a partir dels límits municipals de `dades/territori/`.
 */
object LimitsCatalunya {
    /** El món amb Catalunya com a forats, a `assets/`. L'estil el carrega com a `asset://`. */
    const val FITXER_CONTORN = "mapa/catalunya.geojson"

    /** La capsa de Catalunya, en graus (la propietat `capsa` del contorn). */
    const val OEST = 0.1591
    const val SUD = 40.5231
    const val EST = 3.3236
    const val NORD = 42.8614

    /** Prou per veure tot Catalunya en un mòbil dret (la càmera inicial, Camera.CATALUNYA, és a 6,8). */
    const val ZOOM_MINIM = 6.0

    /** Si [camera] és fora de Catalunya (per exemple, una de desada abans d'aquest límit), torna a la de tot Catalunya. */
    fun limita(camera: Camera): Camera =
        if (camera.latitud in SUD..NORD && camera.longitud in OEST..EST) {
            camera.copy(zoom = maxOf(camera.zoom, ZOOM_MINIM))
        } else {
            Camera.CATALUNYA
        }
}
