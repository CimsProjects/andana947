package cat.andana947.joc.simulacio

import cat.andana947.joc.config.ConfiguracioJoc
import cat.andana947.joc.economia.Economia
import cat.andana947.joc.economia.TipusDia
import cat.andana947.joc.model.Categoria
import cat.andana947.joc.model.FontAfluencia
import cat.andana947.joc.model.Mode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Escriu el resultat d'una simulació en Markdown, amb el format de xifres català (1.234,56 €). */
class Informe(private val config: ConfiguracioJoc) {
    private val economia = Economia(config)
    private val simbols = DecimalFormatSymbols(Locale.forLanguageTag("ca-ES"))
    private val dosDecimals = DecimalFormat("#,##0.00", simbols)
    private val enter = DecimalFormat("#,##0", simbols)
    private val data = DateTimeFormatter.ofPattern("d/M")

    fun euros(valor: Double) = "${dosDecimals.format(valor)} €"

    fun enter(valor: Double): String = enter.format(valor)

    fun escriu(r: ResultatSimulacio, filesDetall: List<Int> = listOf(1, 7, 14, 21, 30)): String = buildString {
        val e = r.escenari
        appendLine("# ${e.nom}")
        appendLine()
        if (e.descripcio.isNotBlank()) appendLine(e.descripcio).also { appendLine() }
        appendLine("## Nodes")
        appendLine()
        appendLine("| Node | Mode | Viatgers/feiner | Afluència | Ingrés sencer/dia | Forma | Preu |")
        appendLine("|---|---|---:|---|---:|---|---:|")
        for (n in e.nodes.sortedBy { it.viatgersFeiner }) {
            val gran = economia.categoria(n) == Categoria.GRAN_ESTACIO
            val preu = if (gran) "${euros(economia.preuAccio(n.viatgersFeiner))}/acció" else euros(economia.preuConcessio(n.viatgersFeiner))
            val font = if (n.font == FontAfluencia.REAL) "real" else "estimat"
            appendLine(
                "| ${n.nom} | ${nomMode(n.mode)} | ${enter(n.viatgersFeiner)} | $font | " +
                    "${euros(economia.ingresDiari(economia.viatgersDelDia(n.viatgersFeiner, TipusDia.FEINER)))} | " +
                    "${if (gran) "Accions" else "Concessió"} | $preu |",
            )
        }
        appendLine()
        appendLine("## Evolució")
        appendLine()
        appendLine("| Dia | Data | Ingrés del dia | Saldo | Concessions | Accions | Nivell |")
        appendLine("|---:|---|---:|---:|---:|---:|---:|")
        for (d in r.dies.filter { it.dia in filesDetall }) {
            appendLine(
                "| ${d.dia} | ${DIES[d.data.dayOfWeek.value - 1]} ${d.data.format(data)} | ${euros(d.ingres)} | " +
                    "${euros(d.saldo)} | ${d.concessionsActives} | ${d.accions} | ${d.nivell} |",
            )
        }
        appendLine()
        val final = r.dies.last()
        appendLine(
            "En tot el període ingressa ${euros(r.ingressos)} (${euros(r.ingressosConcessions)} de concessions i " +
                "${euros(r.dividends)} de dividends) i n'inverteix ${euros(r.inversio)}. Acaba amb un saldo de " +
                "${euros(final.saldo)}, ${final.prestigi} punts de prestigi (nivell ${final.nivell}).",
        )
        appendLine()
        if (r.accions.isNotEmpty()) {
            val nomDe = e.nodes.associate { it.id to it.nom }
            appendLine("Accions finals: " + r.accions.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${nomDe[it.key]} ${it.value}" } + ".")
            appendLine()
        }
        if (r.concessions.isNotEmpty()) {
            val nomDe = e.nodes.associate { it.id to it.nom }
            appendLine("Concessions: " + r.concessions.keys.joinToString(", ") { id ->
                val n = r.renovacions[id] ?: 0
                nomDe.getValue(id) + if (n > 0) " (renovada $n ${if (n == 1) "vegada" else "vegades"})" else ""
            } + ".")
            appendLine()
        }
        if (r.intermodalitat.isNotEmpty()) {
            val nomDe = e.nodes.associate { it.id to it.nom }
            appendLine("Intermodalitat Perfecta: " + r.intermodalitat.entries.joinToString(", ") {
                "${nomDe[it.key]} ×${dosDecimals.format(it.value)}"
            } + ".")
            appendLine()
        }
        appendLine("## Detall diari")
        appendLine()
        appendLine("| Dia | Data | Ingrés | Saldo | Fets |")
        appendLine("|---:|---|---:|---:|---|")
        for (d in r.dies) {
            appendLine("| ${d.dia} | ${d.data} | ${euros(d.ingres)} | ${euros(d.saldo)} | ${d.fets.joinToString("; ")} |")
        }
    }

    private fun nomMode(mode: Mode) = when (mode) {
        Mode.METRO -> "Metro"
        Mode.RODALIES -> "Rodalies"
        Mode.REGIONAL -> "Regional"
        Mode.FGC -> "FGC"
        Mode.TRAM -> "TRAM"
        Mode.BUS -> "Bus"
    }

    companion object {
        private val DIES = listOf("dl.", "dt.", "dc.", "dj.", "dv.", "ds.", "dg.")
    }
}
