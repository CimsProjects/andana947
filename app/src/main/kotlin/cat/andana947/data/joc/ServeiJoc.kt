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
}
