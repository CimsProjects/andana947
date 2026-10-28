package cat.andana947.joc.simulacio

import cat.andana947.joc.config.ConfiguracioJoc
import java.io.File
import kotlin.system.exitProcess

/**
 * Executa la simulació del primer mes (GDD, secció 8) amb l'escenari que genera
 * `scripts/prepara_simulacio.py` a partir de les dades reals:
 *
 *     ./gradlew -p joc simulacio
 *
 * Escriu l'informe a `dades/generades/simulacio_primer_mes.md`.
 */
fun main(args: Array<String>) {
    val arrel = File(args.firstOrNull() ?: "..")
    val config = ConfiguracioJoc.llegeix(File(arrel, "dades/configuracio_joc.json").readText())
    val fitxerEscenari = File(arrel, "dades/generades/escenari_primer_mes.json")
    if (!fitxerEscenari.exists()) {
        System.err.println("Falta ${fitxerEscenari.path}: genera'l amb python3 scripts/prepara_simulacio.py")
        exitProcess(1)
    }
    val escenari = Escenari.llegeix(fitxerEscenari.readText())
    val informe = Informe(config).escriu(Simulacio(config).executa(escenari))
    val sortida = File(arrel, "dades/generades/simulacio_primer_mes.md")
    sortida.writeText(informe)
    println(informe)
    println("Informe desat a ${sortida.path}")
}
