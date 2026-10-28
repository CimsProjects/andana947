import csv
import os
import unittest

from test.utils import ARREL

import genera_dades
from andana.xarxa import Linia


def linia(nom, codi, id_="renfe_md:0404071300VRR"):
    return Linia(id=id_, operador="Renfe", mode="regional", codi=codi, nom=nom, color="", color_text="")


class TestNomDeLinia(unittest.TestCase):
    def test_fa_servir_el_nom_llarg(self):
        self.assertEqual("Barcelona - Lleida", genera_dades.nom_de_linia(linia("Barcelona - Lleida", "R14")))

    def test_sense_nom_llarg_fa_servir_el_curt(self):
        # Com la línia renfe_md:0404071300VRR del GTFS de Renfe, amb route_long_name buit.
        self.assertEqual("R14", genera_dades.nom_de_linia(linia("", "R14")))
        self.assertEqual("R14", genera_dades.nom_de_linia(linia("   ", "R14")))

    def test_sense_cap_nom_fa_servir_l_identificador(self):
        self.assertEqual("renfe_md:0404071300VRR", genera_dades.nom_de_linia(linia("", "")))

    @unittest.skipUnless(os.environ.get("ANDANA_DADES_NOVES"),
                         "Només amb dades acabades de generar (ANDANA_DADES_NOVES=1, al workflow Dades)")
    def test_cap_linia_generada_sense_nom(self):
        with open(ARREL / "dades" / "generades" / "linies.csv", encoding="utf-8") as f:
            buides = [fila["id"] for fila in csv.DictReader(f) if not fila["nom"].strip()]
        self.assertEqual([], buides)

if __name__ == "__main__":
    unittest.main()
