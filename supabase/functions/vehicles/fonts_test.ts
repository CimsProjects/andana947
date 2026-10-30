import { assertEquals } from "jsr:@std/assert@1";
import GtfsRealtimeBindings from "npm:gtfs-realtime-bindings@1.1.1";
import { aCatalunya, codiLiniaRenfe, llegeixFgc, llegeixRenfe } from "./fonts.ts";

const { FeedMessage } = GtfsRealtimeBindings.transit_realtime;

// Un missatge GTFS-Realtime fet a mida, amb els identificadors com els del GTFS de Rodalies (dades de prova).
function missatge(entitats: unknown[]): Uint8Array {
  return FeedMessage.encode(FeedMessage.fromObject({
    header: { gtfsRealtimeVersion: "2.0", timestamp: 1_793_000_000 },
    entity: entitats,
  })).finish();
}

Deno.test("el codi de línia surt de la ruta, de l'expedició o de l'etiqueta", () => {
  assertEquals(codiLiniaRenfe("51T0007R2S", null, null), "R2S");
  assertEquals(codiLiniaRenfe(null, "5151X25603R1", null), "R1");
  assertEquals(codiLiniaRenfe("", "1051X19799C1", null), "C1");
  assertEquals(codiLiniaRenfe(null, null, "RG1-15802-PLATF.(1)"), "RG1");
  assertEquals(codiLiniaRenfe(null, null, null), null);
});

Deno.test("Renfe: només els trens de Rodalies de Catalunya", () => {
  const vehicles = llegeixRenfe(missatge([
    {
      id: "a",
      vehicle: {
        trip: { tripId: "5151X25603R1" },
        position: { latitude: 41.5, longitude: 2.4 },
        vehicle: { id: "25603" },
      },
    },
    // Un tren de Madrid i un de Rodalies sense posició.
    { id: "b", vehicle: { trip: { tripId: "1051X19799C1" }, position: { latitude: 40.4, longitude: -3.7 } } },
    { id: "c", vehicle: { trip: { tripId: "5151X25605R1" } } },
  ]));
  assertEquals(vehicles.length, 1);
  assertEquals(vehicles[0].id, "25603");
  assertEquals(vehicles[0].font, "renfe");
  assertEquals(vehicles[0].linia, "R1");
  assertEquals(Math.round(vehicles[0].lat * 10), 415);
});

Deno.test("FGC: llegeix el punt i la línia de cada unitat", () => {
  const vehicles = llegeixFgc([
    { id: "113.03", lin: "S1", geo_point_2d: { lat: 41.47, lon: 2.08 } },
    { ut: "213", linia: "R5 ", latitud: "41.6", longitud: "1.83" },
    { id: "sense posició", lin: "S2" },
    { id: "lluny", lin: "S2", geo_point_2d: { lat: 48.8, lon: 2.3 } },
  ]);
  assertEquals(vehicles, [
    { id: "113.03", font: "fgc", linia: "S1", lat: 41.47, lon: 2.08 },
    { id: "213", font: "fgc", linia: "R5", lat: 41.6, lon: 1.83 },
  ]);
});

Deno.test("el requadre de Catalunya", () => {
  assertEquals(aCatalunya(41.38, 2.17), true); // Barcelona
  assertEquals(aCatalunya(42.7, 0.8), true); // Vielha
  assertEquals(aCatalunya(40.7, 0.58), true); // Amposta
  assertEquals(aCatalunya(41.65, -0.88), false); // Saragossa
});
