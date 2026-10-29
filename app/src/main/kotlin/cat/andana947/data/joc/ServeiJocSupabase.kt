package cat.andana947.data.joc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
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
}
