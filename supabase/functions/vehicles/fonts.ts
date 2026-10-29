// Les posicions reals dels trens de Catalunya a partir de les fonts obertes dels operadors (docs/vehicles.md).
// Aquí només hi ha la lectura de cada font, sense xarxa, perquè es pugui provar: deno test supabase/functions/vehicles

import GtfsRealtimeBindings from "npm:gtfs-realtime-bindings@1.1.1";

/** Un tren, tal com el rep l'app (joc/…/circulacio/VehiclesReals.kt, `VehicleReal`). */
export interface Vehicle {
  id: string;
  font: string;
  linia: string | null;
  lat: number;
  lon: number;
}

/** Un requadre generós de Catalunya: els trens de fora no interessen. */
export const CATALUNYA = { latMin: 40.45, latMax: 42.9, lonMin: 0.1, lonMax: 3.4 };

export function aCatalunya(lat: number, lon: number): boolean {
  return lat >= CATALUNYA.latMin && lat <= CATALUNYA.latMax && lon >= CATALUNYA.lonMin && lon <= CATALUNYA.lonMax;
}

/**
 * El codi de línia d'un tren de Renfe Cercanías. Els identificadors del GTFS de Rodalies el porten al final: la ruta
 * `51T0007R2S` i l'expedició `5151X25603R1`. Si no, l'etiqueta del vehicle (`R2S-77019-…`).
 */
export function codiLiniaRenfe(ruta?: string | null, viatge?: string | null, etiqueta?: string | null): string | null {
  const deRuta = ruta?.trim().match(/^\d{2}T\d{4}([A-Z][A-Z0-9]*)$/);
  if (deRuta) return deRuta[1];
  const deViatge = viatge?.trim().match(/\d([A-Z]+\d*[A-Z]?)$/);
  if (deViatge) return deViatge[1];
  const deEtiqueta = etiqueta?.trim().match(/^([A-Z]+\d*[A-Z]?)-/);
  return deEtiqueta ? deEtiqueta[1] : null;
}

/**
 * El GTFS-Realtime de posicions de Renfe Cercanías (tot l'Estat): només els trens de Rodalies de Catalunya, que són
 * els de línies que comencen per R (les dels altres nuclis comencen per C) i que són dins de Catalunya.
 */
export function llegeixRenfe(protobuf: Uint8Array): Vehicle[] {
  const missatge = GtfsRealtimeBindings.transit_realtime.FeedMessage.decode(protobuf);
  const vehicles: Vehicle[] = [];
  for (const entitat of missatge.entity) {
    const v = entitat.vehicle;
    const posicio = v?.position;
    if (!v || !posicio) continue;
    const { latitude: lat, longitude: lon } = posicio;
    const linia = codiLiniaRenfe(v.trip?.routeId, v.trip?.tripId, v.vehicle?.label);
    if (!linia?.startsWith("R") || !aCatalunya(lat, lon)) continue;
    vehicles.push({ id: v.vehicle?.id || v.trip?.tripId || entitat.id, font: "renfe", linia, lat, lon });
  }
  return vehicles;
}

// deno-lint-ignore no-explicit-any
type Registre = Record<string, any>;

/**
 * El posicionament dels trens d'FGC (Geotren, dades obertes d'FGC). Cada registre té la unitat, la línia i el punt
 * (`geo_point_2d` d'Opendatasoft). Els noms dels camps es llegeixen amb alternatives per si canvien.
 */
export function llegeixFgc(registres: Registre[]): Vehicle[] {
  const vehicles: Vehicle[] = [];
  for (const r of registres) {
    const punt = r.geo_point_2d ?? r.geopoint ?? null;
    const lat = Number(punt?.lat ?? r.latitud ?? r.lat);
    const lon = Number(punt?.lon ?? r.longitud ?? r.lon);
    const id = r.id ?? r.ut ?? r.unitat ?? r.codi;
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || id == null || !aCatalunya(lat, lon)) continue;
    const linia = r.lin ?? r.linia ?? null;
    vehicles.push({ id: String(id), font: "fgc", linia: linia == null ? null : String(linia).trim(), lat, lon });
  }
  return vehicles;
}
