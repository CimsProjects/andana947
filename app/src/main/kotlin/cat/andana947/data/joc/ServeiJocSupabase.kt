package cat.andana947.data.joc

import cat.andana947.avatar.Avatar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
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
