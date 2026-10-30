# Lore d'Andana 947

La història que embolcalla el joc. Tot el que hi ha aquí és **ficció**: cap personatge, empresa ni fet representa cap
persona ni cap operador real (TMB, Renfe, FGC, ATM…). Les parades, les línies i les xifres del joc, en canvi, són
reals (`docs/abast.md`).

## El Director

**Valentí Casas i Mir**, «el Director». Tothom a les andanes li diu així, i ell fa veure que no li agrada.

- **Edat:** 68 anys. Es jubila el 2026, el dia que comença el joc.
- **Trajectòria:** va començar als 16 anys, el 1974, portant encàrrecs en una estació de rodalia. Després va ser
  revisor, cap d'estació i, al final, director de la Companyia d'Andanes 947. Fa cinquanta-dos anys que treballa entre
  vies.
- **Com és:** proper, bromista i una mica antic. Parla de tu al jugador des del primer moment. Porta un rellotge de
  butxaca que sempre va dos minuts avançat («perquè el tren no m'esperi a mi; jo l'espero a ell»). Col·lecciona bitllets
  de cartró.
- **Frase:** «Una andana és una promesa: algú hi esperarà.»
- **Comiat:** «Viatgers, al tren!»
- **Com es veu:** bust amb gorra vermella de cap d'estació amb cinta daurada, ulleres rodones, bigoti gris, calb amb
  dos flocs grisos, americana blava d'uniforme, corbata del color fort i una placa daurada. És una combinació fixa de
  peces de l'avatar (`DIRECTOR` a `scripts/avatar/cataleg.py`); les seves peces no les pot triar el jugador.
- **Expressions:** `cara_director` (somriu amb els ulls tancats) i `cara_director_ullet` (picada d'ullet, per als
  moments de complicitat).

## La Companyia d'Andanes 947

Una companyia inventada, nascuda d'una idea del Director: que es pugui arribar amb transport públic a cadascun dels
**947 municipis de Catalunya**. No ho ha aconseguit. En jubilar-se, no la ven: la deixa en mans del jugador.

El nom ve dels 947 municipis i del projecte germà, [municipis](https://github.com/CimsProjects/municipis).

## El paper del Director al joc

1. **Introducció** (ara). Es presenta, et fa fer el carnet d'operador (l'avatar) i t'explica el somni i les regles
   bàsiques: check-in, cobraments cada 4 hores i renovacions.
2. **Les andanes de regal** (fase següent). Et deixa triar algunes andanes reals per començar: com a màxim dues per
   província, que poden ser dues andanes seguides d'una mateixa línia. Són seves, i per això **no caduquen**.
3. **Més endavant.** Consells en moments clau i cartes quan arribes a fites (primera comarca, primer nivell, primera
   línia…).

## To

Català col·loquial i càlid, frases curtes, humor de ferroviari. Mai sermons. El Director no dona ordres: convida.
Els textos són a `app/src/main/res/values/strings.xml` (`director_*`).
