"""La migració de Supabase de la flota i el caos té els mateixos valors que els fitxers de dades/."""

import json
import pathlib
import unittest

ARREL = pathlib.Path(__file__).resolve().parent.parent.parent
MIGRACIO = (ARREL / "supabase/migrations/20261030235500_flota_caos.sql").read_text(encoding="utf-8")


def numero(x):
    return f"{x:g}" if isinstance(x, float) else str(x)


class TestCoherencia(unittest.TestCase):
    def test_cataleg(self):
        cataleg = json.loads((ARREL / "dades/flota_cataleg.json").read_text(encoding="utf-8"))
        for m in cataleg["models"]:
            i = MIGRACIO.index(f"('{m['id']}', ")
            tros = MIGRACIO[i:MIGRACIO.index("'https", i)]
            self.assertIn(f", {m['preu']},", tros, m["id"])
            self.assertIn("{" + ",".join(m["modes"]) + "}", tros, m["id"])

    def test_esdeveniments_aleatoris(self):
        config = json.loads((ARREL / "dades/configuracio_joc.json").read_text(encoding="utf-8"))["caos"]
        for e in config["esdeveniments"]:
            esperat = (f"('{e['id']}', {numero(e['probabilitat'])}, {numero(e['multiplicador'])}, {e['duradaDies'][0]}, "
                       f"{e['duradaDies'][1]}, {str(e['nomesFeiner']).lower()}, {str(e['tardor']).lower()},")
            self.assertIn(esperat, MIGRACIO, e["id"])
            for ambit in e["ambits"]:
                for valor in ambit.get("linies", []) + ambit.get("comarques", []):
                    self.assertIn(f'"{valor}"', MIGRACIO, e["id"])

    def test_calendari(self):
        calendari = json.loads((ARREL / "dades/esdeveniments_calendari.json").read_text(encoding="utf-8"))
        for e in calendari["esdeveniments"]:
            esperat = f"'{e['id']}', date '{e['des_de']}', date '{e['fins']}', {numero(e['multiplicador'])}"
            self.assertIn(esperat, MIGRACIO, e["id"])


if __name__ == "__main__":
    unittest.main()
