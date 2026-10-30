package cat.andana947.data.joc

/**
 * Accions de joc de la fase 4 (GDD 4 i 7): consultar nodes propers, consultar l'estat de l'imperi
 * i enviar el check-in per conquerir, renovar o comprar accions.
 */
interface ServeiJoc {
    suspend fun nodesAProp(lat: Double, lon: Double, radiMetres: Double = 1000.0, maxim: Int = 50): Result<List<NodeAProp>>

    suspend fun estatImperi(): Result<EstatImperi>

    suspend fun ferCheckin(
        nodeId: String,
        lat: Double,
        lon: Double,
        precisio: Double,
        velocitat: Double,
    ): Result<ResultatCheckinRpc>

    /** Retorna si el jugador autenticat té el mode desenvolupador activat. */
    suspend fun llegirModeDev(): Result<Boolean>

    /** Pròxim cobrament i cobraments que el jugador encara no ha vist. */
    suspend fun estatCobraments(): Result<EstatCobraments>

    /** Marca com a vistos els cobraments fins a [fins], l'hora del servidor que va tornar [estatCobraments]. */
    suspend fun marcaCobramentsVistos(fins: String): Result<Unit>

    /** L'avatar i si el jugador ja ha fet la introducció (docs/lore.md). */
    suspend fun llegirPerfilJugador(): Result<PerfilJugador>

    /** Desa l'avatar; el servidor comprova cada peça i torna l'avatar desat. */
    suspend fun desaAvatar(avatar: cat.andana947.avatar.Avatar): Result<cat.andana947.avatar.Avatar>

    /** Marca la introducció com a feta. Cal tenir avatar. */
    suspend fun completaIntroduccio(): Result<Unit>

    /** Les andanes de regal del Director; null si el jugador ja les ha triades. */
    suspend fun ofertesRegal(): Result<OfertesRegal?>

    /** Tria les andanes de regal (identificadors de les opcions). Torna els noms de les andanes rebudes. */
    suspend fun triaRegal(opcions: List<String>): Result<List<String>>

    /** El resum de la pantalla Perfil. */
    suspend fun resumPerfil(): Result<ResumPerfil>

    /** Les andanes del jugador amb les persones i els diners de cada arribada. */
    suspend fun andanesJugador(): Result<List<AndanaJugador>>

    /** Andanes per comprar: pel nom o el municipi o, sense text, les properes a la posició. */
    suspend fun cercaAndanes(text: String, lat: Double?, lon: Double?): Result<List<AndanaCerca>>

    /** Comprar una andana des de qualsevol lloc. */
    suspend fun compraAndana(id: String): Result<ResultatCheckinRpc>

    /** Comprar accions d'una gran estació des de qualsevol lloc. */
    suspend fun compraAccions(id: String, quantitat: Int): Result<ResultatCheckinRpc>

    /** La temporada: rànquing, objectius, insígnies i skins. */
    suspend fun estatTemporada(): Result<EstatTemporada>

    suspend fun desaNom(nom: String): Result<String>

    suspend fun desaSkin(skin: String): Result<String>

    /** La fitxa d'una andana pròpia (maestria, línies i millores); null si no és del jugador. */
    suspend fun estatAndana(id: String): Result<EstatAndana?>

    /** Comprar una millora per a una andana pròpia. El servidor en comprova el nivell i el saldo. */
    suspend fun compraMillora(node: String, millora: String): Result<ResultatCheckinRpc>

    /** Les maestries de les línies i les andanes del jugador i la seva xarxa més gran. */
    suspend fun estatMaestries(): Result<EstatMaestries>

    // Mode desenvolupador: el servidor comprova que el jugador el té activat.

    /** Suma experiència a una andana pròpia i a les seves línies. */
    suspend fun devSumaMaestria(node: String, xp: Int): Result<ResultatCheckinRpc>

    /** Desbloqueja totes les skins. */
    suspend fun devDesbloquejaSkins(): Result<ResultatCheckinRpc>

    /** Cobra ara 4 hores d'arribades, com el cobrament periòdic. */
    suspend fun devCobraAra(): Result<ResultatCheckinRpc>
}
