// Funció `vehicles`: les posicions reals dels trens de Catalunya (docs/vehicles.md). Són dades obertes i no hi ha cap
// acció econòmica, així que no demana sessió (config.toml: verify_jwt = false). Les respostes es guarden uns segons
// perquè tots els jugadors comparteixin una sola consulta a cada operador.

import { llegeixFgc, llegeixRenfe, type Vehicle } from "./fonts.ts";

const RENFE = "https://gtfsrt.renfe.com/vehicle_positions.pb";
const FGC = "https://dadesobertes.fgc.cat/api/explore/v2.1/catalog/datasets/posicionament-dels-trens/exports/json";

/** Segons que es reaprofita una resposta. Les fonts s'actualitzen cada mig minut, més o menys. */
const MEMORIA_SEGONS = 15;
const TEMPS_MAXIM_MS = 8_000;

interface Resposta {
  generat: number;
  /** Per a cada font, si ha respost bé. Si no, l'app mou els trens d'aquella font segons l'horari. */
  fonts: Record<string, boolean>;
  vehicles: Vehicle[];
}

let memoria: { fins: number; text: string } | null = null;

async function baixa(url: string): Promise<Response> {
  const resposta = await fetch(url, {
    headers: { "User-Agent": "Andana947/1.0 (+https://github.com/CimsProjects/andana947)" },
    signal: AbortSignal.timeout(TEMPS_MAXIM_MS),
  });
  if (!resposta.ok) throw new Error(`${url}: HTTP ${resposta.status}`);
  return resposta;
}

async function font(nom: string, llegeix: () => Promise<Vehicle[]>): Promise<[string, Vehicle[] | null]> {
  try {
    return [nom, await llegeix()];
  } catch (error) {
    console.error(`vehicles: la font ${nom} ha fallat`, error);
    return [nom, null];
  }
}

async function consulta(): Promise<Resposta> {
  const resultats = await Promise.all([
    font("renfe", async () => llegeixRenfe(new Uint8Array(await (await baixa(RENFE)).arrayBuffer()))),
    font("fgc", async () => {
      const vehicles = llegeixFgc(await (await baixa(FGC)).json());
      // Una resposta sense cap tren llegible vol dir que els camps han canviat: millor l'horari.
      if (vehicles.length === 0) throw new Error("cap tren llegible");
      return vehicles;
    }),
  ]);
  return {
    generat: Date.now(),
    fonts: Object.fromEntries(resultats.map(([nom, v]) => [nom, v !== null])),
    vehicles: resultats.flatMap(([, v]) => v ?? []),
  };
}

Deno.serve(async (peticio) => {
  if (peticio.method === "OPTIONS") return new Response(null, { status: 204 });
  if (!memoria || memoria.fins < Date.now()) {
    memoria = { fins: Date.now() + MEMORIA_SEGONS * 1000, text: JSON.stringify(await consulta()) };
  }
  return new Response(memoria.text, {
    headers: { "Content-Type": "application/json", "Cache-Control": `public, max-age=${MEMORIA_SEGONS}` },
  });
});
