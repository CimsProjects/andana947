package cat.andana947.data.joc

import cat.andana947.avatar.Avatar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServeiJocSupabase @Inject constructor(
    private val supabase: SupabaseClient,
) : ServeiJoc {

    override suspend fun nodesAProp(
        lat: Double,
        lon: Double,
        radiMetres: Double,
        maxim: Int,
    ): Result<List<NodeAProp>> = runCatching {
        supabase.postgrest.rpc(
            function = "nodes_a_prop_jugador",
            parameters = buildJsonObject {
                put("lat", lat)
                put("lon", lon)
                put("radi_metres", radiMetres)
                put("maxim", maxim)
            },
        ).decodeList<NodeAProp>()
    }

    override suspend fun estatImperi(): Result<EstatImperi> = runCatching {
        supabase.postgrest.rpc(
            function = "estat_imperi",
        ).decodeAs<EstatImperi>()
    }

    override suspend fun ferCheckin(
        nodeId: String,
        lat: Double,
        lon: Double,
        precisio: Double,
        velocitat: Double,
    ): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "fer_checkin",
            parameters = buildJsonObject {
                put("p_node_id", nodeId)
                put("p_lat", lat)
                put("p_lon", lon)
                put("p_precisio", precisio)
                put("p_velocitat", velocitat)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun llegirModeDev(): Result<Boolean> = runCatching {
        @Serializable
        data class FilaModeDev(@SerialName("mode_dev") val modeDev: Boolean)
        supabase.from("perfils")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("mode_dev")) {
                limit(1)
            }
            .decodeSingle<FilaModeDev>()
            .modeDev
    }

    override suspend fun estatCobraments(): Result<EstatCobraments> = runCatching {
        supabase.postgrest.rpc(function = "estat_cobraments").decodeAs<EstatCobraments>()
    }

    override suspend fun cobraArribades(): Result<EstatCobraments> = runCatching {
        supabase.postgrest.rpc(function = "cobra_arribades").decodeAs<EstatCobraments>()
    }

    override suspend fun marcaCobramentsVistos(fins: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = "marca_cobraments_vistos",
            parameters = buildJsonObject { put("p_fins", fins) },
        )
        Unit
    }

    override suspend fun llegirPerfilJugador(): Result<PerfilJugador> = runCatching {
        supabase.from("perfils")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("avatar", "introduccio_feta")) {
                limit(1)
            }
            .decodeSingle<PerfilJugador>()
    }

    override suspend fun desaAvatar(avatar: Avatar): Result<Avatar> = runCatching {
        supabase.postgrest.rpc(
            function = "desa_avatar",
            parameters = buildJsonObject { put("p_avatar", jsonAvatar.encodeToJsonElement(Avatar.serializer(), avatar)) },
        ).decodeAs<Avatar>()
    }

    override suspend fun completaIntroduccio(): Result<Unit> = runCatching {
        supabase.postgrest.rpc(function = "completa_introduccio")
        Unit
    }

    override suspend fun ofertesRegal(): Result<OfertesRegal?> = runCatching {
        val text = supabase.postgrest.rpc(function = "ofertes_regal").data
        if (text.isBlank() || text == "null") null else jsonResposta.decodeFromString(OfertesRegal.serializer(), text)
    }

    override suspend fun triaRegal(opcions: List<String>): Result<List<String>> = runCatching {
        supabase.postgrest.rpc(
            function = "tria_regal",
            parameters = buildJsonObject { put("p_opcions", JsonArray(opcions.map { JsonPrimitive(it) })) },
        ).decodeAs<ResultatRegal>().andanes
    }

    override suspend fun resumPerfil(): Result<ResumPerfil> = runCatching {
        supabase.postgrest.rpc(function = "resum_perfil").decodeAs<ResumPerfil>()
    }

    override suspend fun andanesJugador(): Result<List<AndanaJugador>> = runCatching {
        supabase.postgrest.rpc(function = "andanes_jugador").decodeList<AndanaJugador>()
    }

    override suspend fun cercaAndanes(text: String, lat: Double?, lon: Double?): Result<List<AndanaCerca>> = runCatching {
        supabase.postgrest.rpc(
            function = "cerca_andanes",
            parameters = buildJsonObject {
                put("p_text", text)
                put("p_lat", lat)
                put("p_lon", lon)
            },
        ).decodeList<AndanaCerca>()
    }

    override suspend fun compraAndana(id: String): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(function = "compra_andana", parameters = buildJsonObject { put("p_node", id) })
            .decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun compraAccions(id: String, quantitat: Int): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "compra_accions",
            parameters = buildJsonObject {
                put("p_node", id)
                put("p_quantitat", quantitat)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun estatTemporada(): Result<EstatTemporada> = runCatching {
        jsonResposta.decodeFromString(EstatTemporada.serializer(), supabase.postgrest.rpc(function = "estat_temporada").data)
    }

    override suspend fun desaNom(nom: String): Result<String> = runCatching {
        supabase.postgrest.rpc(function = "desa_nom", parameters = buildJsonObject { put("p_nom", nom) }).decodeAs<String>()
    }

    override suspend fun desaSkin(skin: String): Result<String> = runCatching {
        supabase.postgrest.rpc(function = "desa_skin", parameters = buildJsonObject { put("p_skin", skin) }).decodeAs<String>()
    }

    override suspend fun estatAndana(id: String): Result<EstatAndana?> = runCatching {
        val text = supabase.postgrest.rpc(function = "estat_andana", parameters = buildJsonObject { put("p_node", id) }).data
        if (text.isBlank() || text == "null") null else jsonResposta.decodeFromString(EstatAndana.serializer(), text)
    }

    override suspend fun compraMillora(node: String, millora: String): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "compra_millora",
            parameters = buildJsonObject {
                put("p_node", node)
                put("p_millora", millora)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun estatMaestries(): Result<EstatMaestries> = runCatching {
        jsonResposta.decodeFromString(EstatMaestries.serializer(), supabase.postgrest.rpc(function = "estat_maestries").data)
    }

    override suspend fun estatFlota(): Result<List<LiniaFlota>> = runCatching {
        jsonResposta.decodeFromString(ListSerializer(LiniaFlota.serializer()), supabase.postgrest.rpc(function = "estat_flota").data)
    }

    override suspend fun llogaVehicles(linia: String, quantitat: Int): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "lloga_vehicles",
            parameters = buildJsonObject {
                put("p_linia", linia)
                put("p_quantitat", quantitat)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun retornaVehicles(linia: String, quantitat: Int): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "retorna_vehicles",
            parameters = buildJsonObject {
                put("p_linia", linia)
                put("p_quantitat", quantitat)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun esdevenimentsAvui(): Result<List<Esdeveniment>> = runCatching {
        jsonResposta.decodeFromString(ListSerializer(Esdeveniment.serializer()), supabase.postgrest.rpc(function = "esdeveniments_avui").data)
    }

    override suspend fun devProvocaEsdeveniment(node: String, multiplicador: Double): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "dev_provoca_esdeveniment",
            parameters = buildJsonObject {
                put("p_node", node)
                put("p_multiplicador", multiplicador)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun devSumaMaestria(node: String, xp: Int): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(
            function = "dev_suma_maestria",
            parameters = buildJsonObject {
                put("p_node", node)
                put("p_xp", xp)
            },
        ).decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun devDesbloquejaSkins(): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(function = "dev_desbloqueja_skins").decodeAs<ResultatCheckinRpc>()
    }

    override suspend fun devCobraAra(): Result<ResultatCheckinRpc> = runCatching {
        supabase.postgrest.rpc(function = "dev_cobra_ara").decodeAs<ResultatCheckinRpc>()
    }

    @Serializable
    private data class ResultatRegal(val andanes: List<String> = emptyList())

    private companion object {
        val jsonResposta = Json { ignoreUnknownKeys = true }

        /** Tots els camps obligatoris s'envien encara que tinguin el valor per defecte; els nuls, no. */
        val jsonAvatar = Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
