-- Fase 5 (flota) i fase 6 (motor de caos). GDD 4.7 i 6; valors a dades/configuracio_joc.json (blocs «flota» i
-- «caos»), el material a dades/flota_cataleg.json i el calendari a dades/esdeveniments_calendari.json.
--
-- Flota: el jugador lloga vehicles reals per a les línies on té andanes, com a molt la part de la flota que li toca
-- per les andanes que hi té. Cada vehicle cobra la seva part dels viatgers de la línia (amb la subvenció) i paga la
-- quota del lloguer (el preu real repartit en la vida útil) i l'explotació per km. Tot a l'escala de la quota de
-- concessió (1 %), com les andanes. Els vehicles es veuen al mapa amb el color del jugador.
--
-- Caos: esdeveniments de l'àmbit (línia, comarca, municipi o a prop d'un lloc), iguals per a tothom, que multipliquen
-- l'ingrés de les andanes i dels vehicles mentre duren. Els aleatoris se sortegen cada dia amb una llavor que es desa;
-- els de calendari són reals, amb la seva font.

-- 1. Les dades de la flota ----------------------------------------------------------------------------------------------

create table if not exists public.flota_linies (
    linia text primary key,
    mode public.mode_transport not null,
    codi text not null,
    operador text not null default '',
    vehicles_punta integer not null check (vehicles_punta > 0),
    expedicions_feiner integer not null check (expedicions_feiner >= 0),
    km_feiner numeric(12, 1) not null check (km_feiner >= 0),
    viatgers_feiner numeric(12, 1) not null check (viatgers_feiner >= 0),
    nodes integer not null check (nodes >= 0)
);
comment on table public.flota_linies is 'Flota de cada línia (mode:codi): vehicles en hora punta [estimat], km i viatgers d''un feiner (scripts/genera_flota.py).';

create table if not exists public.models_vehicle (
    id text primary key,
    nom text not null,
    modes public.mode_transport[] not null,
    operadors text[] not null default '{}',
    preu numeric(12, 2) not null check (preu > 0),
    font text not null,
    url text not null
);
comment on table public.models_vehicle is 'Material rodant real (dades/flota_cataleg.json): preu de contracte per vehicle.';

insert into public.models_vehicle (id, nom, modes, operadors, preu, font, url) values
    ('metro_alstom_7000', 'Metro sèries 7000 i 8000 (Alstom, 5 cotxes)', '{metro}', '{}', 6380952,
     'TMB adjudica a Alstom 42 trens per a les línies 1 i 3 per 268 milions d''euros (Betevé, 29/07/2019)',
     'https://beteve.cat/mobilitat/tmb-nous-trens-metro-alstom/'),
    ('fgc_stadler_115', 'FGC sèrie 115 (Stadler, 4 cotxes)', '{fgc}', '{}', 6633333,
     'FGC adjudica a Stadler Valencia 15 trens de la sèrie 115 per 99,5 milions d''euros sense IVA (Valencia Plaza, 27/12/2017)',
     'https://valenciaplaza.com/stadler-valencia-se-adjudica-por-120-millones-la-construccion-de-15-trenes-para-cataluna'),
    ('rodalies_alstom_100', 'Rodalies d''alta capacitat (Alstom, 100 m)', '{rodalies}', '{}', 7510204,
     'Renfe compra 49 trens de 100 m a Alstom per a Rodalies per 368 milions d''euros (Valencia Plaza, 15/12/2022)',
     'https://valenciaplaza.com/valenciaplaza/renfe-firma-con-alstom-stadler-y-caf-la-compra-de-97-trenes-para-rodalies-por-858-4-millones'),
    ('regional_caf_md', 'Mitja distància (CAF)', '{regional}', '{}', 6535714,
     'Renfe compra 28 trens de mitja distància a CAF per a Catalunya per 183 milions d''euros (Valencia Plaza, 15/12/2022)',
     'https://valenciaplaza.com/valenciaplaza/renfe-firma-con-alstom-stadler-y-caf-la-compra-de-97-trenes-para-rodalies-por-858-4-millones'),
    ('tram_alstom_citadis', 'Tramvia Citadis (Alstom)', '{tram}', '{}', 2585714,
     'Alstom i la RATP signen 70 tramvies Citadis per 181 milions d''euros (Alstom, 22/12/2003). No hem trobat el preu dels Citadis del TRAM de Barcelona: és el mateix model',
     'https://www.alstom.com/press-releases-news/2003/12/ALSTOM-and-the-RATP-sign-a-181million-contract-for-70-CITADIS-trams-20031222'),
    ('bus_electric_12', 'Bus elèctric de 12 m (BYD i Solaris)', '{bus}', '{TMB,AMB}', 604898,
     'TMB encarrega 49 busos elèctrics de 12 m (25 BYD i 24 Solaris) per 29,64 milions d''euros (TMB, 18/09/2021)',
     'https://noticies.tmb.cat/sala-de-premsa/tmb-encarrega-210-busos-electrics-hibrids-activar-renovacio-descarbonitzacio-de'),
    ('autocar_interurba', 'Autocar interurbà de més de 55 places', '{bus}', '{"Generalitat de Catalunya"}', 266237,
     'Observatorio de costes del transporte de viajeros en autocar, 1 de gener de 2026 (Ministeri de Transports), preu sense IVA',
     'https://confebus.org/assets/uploads/docs/Observatorio_Costes_Viajeros_Autocar_2026%2001.pdf')
on conflict (id) do update set nom = excluded.nom, modes = excluded.modes, operadors = excluded.operadors,
    preu = excluded.preu, font = excluded.font, url = excluded.url;

create table if not exists public.flota_jugador (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    linia text not null references public.flota_linies (linia) on delete cascade,
    vehicles integer not null check (vehicles > 0),
    fianca numeric(12, 2) not null default 0 check (fianca >= 0),
    llogat_el timestamptz not null default now(),
    primary key (jugador_id, linia)
);
comment on table public.flota_jugador is 'Els vehicles que el jugador té llogats a cada línia i la fiança que hi ha deixat.';

alter table public.flota_linies enable row level security;
alter table public.models_vehicle enable row level security;
alter table public.flota_jugador enable row level security;
create policy "Tothom pot llegir la flota de les línies" on public.flota_linies for select to anon, authenticated using (true);
create policy "Tothom pot llegir els models" on public.models_vehicle for select to anon, authenticated using (true);
create policy "El jugador llegeix la seva flota" on public.flota_jugador for select to authenticated using (jugador_id = auth.uid());
revoke insert, update, delete, truncate on public.flota_linies, public.models_vehicle, public.flota_jugador from anon, authenticated;

-- Abans de canviar la flota, es cobra el que s'ha guanyat fins ara (com amb les andanes).
create or replace function public.liquida_flota_abans()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.liquida_jugador(coalesce(new.jugador_id, old.jugador_id));
    return coalesce(new, old);
end;
$$;

drop trigger if exists flota_liquida on public.flota_jugador;
create trigger flota_liquida before insert or update or delete on public.flota_jugador
    for each row execute function public.liquida_flota_abans();

-- 2. Configuració -------------------------------------------------------------------------------------------------------

create or replace function public.config_flota()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'dies_fianca', 30,
        'vida_bus_anys', 12,
        'vida_tren_anys', 30,
        'subvencio_per_ingres', 1.083,
        'cost_km_bus', 1.4395,
        'cost_km_tren', 2.0,
        'cost_km_tramvia', 1.5,
        -- La quota de concessió (economia.quotaConcessio): totes les xifres reals, a l'1 %.
        'quota', 0.01
    );
$$;

create or replace function public.config_caos()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object('multiplicador_tardor', 3);
$$;

-- 3. Economia d'un vehicle --------------------------------------------------------------------------------------------

-- El model que es lloga per a una línia: el del seu mode (i del seu operador, si n'hi ha un d'específic).
create or replace function public.model_linia(p_linia text)
returns text
language sql
stable
set search_path = ''
as $$
    select m.id
    from public.flota_linies f
    join public.models_vehicle m on f.mode = any (m.modes)
    where f.linia = p_linia and (m.operadors = '{}' or f.operador = any (m.operadors))
    order by m.operadors = '{}', m.id
    limit 1;
$$;

-- L'ingrés, la quota i l'explotació d'un vehicle en un dia ([p_factor_dia]: 1 feiner, 0,6 la resta).
create or replace function public.economia_vehicle(p_linia text, p_factor_dia double precision)
returns table (ingres numeric, quota numeric, explotacio numeric)
language sql
stable
set search_path = ''
as $$
    -- El model es busca una sola vegada (al join, es calcularia per a cada línia).
    with cfg as (select public.config_flota() as c),
    model as (select m.preu from public.models_vehicle m where m.id = public.model_linia(p_linia))
    select
        public.ingres_diari((f.viatgers_feiner::double precision * p_factor_dia)::numeric)
            * (1 + (cfg.c ->> 'subvencio_per_ingres')::numeric) / f.vehicles_punta,
        model.preu / (case when f.mode = 'bus' then (cfg.c ->> 'vida_bus_anys')::numeric else (cfg.c ->> 'vida_tren_anys')::numeric end * 365)
            * (cfg.c ->> 'quota')::numeric,
        f.km_feiner * p_factor_dia::numeric / f.vehicles_punta
            * case f.mode when 'bus' then (cfg.c ->> 'cost_km_bus')::numeric
                          when 'tram' then (cfg.c ->> 'cost_km_tramvia')::numeric
                          else (cfg.c ->> 'cost_km_tren')::numeric end
            * (cfg.c ->> 'quota')::numeric
    from public.flota_linies f
    cross join model
    cross join cfg
    where f.linia = p_linia;
$$;

-- Les andanes del jugador en una línia.
create or replace function public.andanes_a_linia(p_jugador uuid, p_linia text)
returns integer
language sql
stable
set search_path = ''
as $$
    select count(distinct c.node_id)::int
    from public.concessions c
    join public.linia_nodes ln on ln.node_id = c.node_id
    join public.linies l on l.id = ln.linia_id
    where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now() and l.mode::text || ':' || l.codi = p_linia;
$$;

-- Quants vehicles pot llogar el jugador a una línia: la part de la flota que li toca per les andanes que hi té.
create or replace function public.maxim_vehicles(p_jugador uuid, p_linia text)
returns integer
language sql
stable
set search_path = ''
as $$
    select case when f.nodes > 0
        then ceil(f.vehicles_punta * least(public.andanes_a_linia(p_jugador, p_linia), f.nodes)::numeric / f.nodes)::int
        else 0 end
    from public.flota_linies f where f.linia = p_linia;
$$;

-- 4. Esdeveniments ------------------------------------------------------------------------------------------------------

create table if not exists public.tipus_esdeveniment (
    id text primary key,
    probabilitat numeric(6, 4) not null check (probabilitat >= 0 and probabilitat < 0.05),
    multiplicador numeric(4, 2) not null check (multiplicador > 0),
    durada_min integer not null check (durada_min >= 1),
    durada_max integer not null check (durada_max >= durada_min),
    nomes_feiner boolean not null default false,
    tardor boolean not null default false,
    ambits jsonb not null
);
comment on table public.tipus_esdeveniment is 'Els esdeveniments aleatoris del motor de caos (GDD 6.2, bloc «caos»).';

insert into public.tipus_esdeveniment (id, probabilitat, multiplicador, durada_min, durada_max, nomes_feiner, tardor, ambits) values
    ('robatori_coure', 0.004, 0.4, 1, 1, false, false,
     '[{"linies": ["rodalies:R3"]}, {"linies": ["rodalies:R4"]}, {"linies": ["rodalies:R8"]}]'),
    ('animals_via', 0.003, 0.85, 1, 1, false, false,
     '[{"linies": ["fgc:S1", "fgc:S2", "fgc:L6", "fgc:L7", "fgc:L12"]}, {"linies": ["rodalies:R3"]}]'),
    ('incidencia_costa', 0.003, 0.5, 1, 1, false, false, '[{"linies": ["rodalies:R2S"]}, {"linies": ["rodalies:R1"]}]'),
    ('avaria_infraestructura', 0.003, 0.7, 1, 1, false, false, '[{"perLiniaDelsModes": ["rodalies", "regional"]}]'),
    ('temporal', 0.002, 0.6, 1, 3, false, true, '[{"comarques": ["09", "22", "30", "37"], "nom": "Terres de l''Ebre"}, {"comarques": ["21"], "nom": "Maresme"}]'),
    ('manifestacio', 0.01, 0.5, 1, 1, true, false, '[{"modes": ["bus"], "centreNode": "tmb:P.6660126", "radiMetres": 1500}]')
on conflict (id) do update set probabilitat = excluded.probabilitat, multiplicador = excluded.multiplicador,
    durada_min = excluded.durada_min, durada_max = excluded.durada_max, nomes_feiner = excluded.nomes_feiner,
    tardor = excluded.tardor, ambits = excluded.ambits;

create table if not exists public.esdeveniments (
    id bigserial primary key,
    tipus text not null,
    origen text not null check (origen in ('aleatori', 'calendari', 'prova')),
    clau text not null,
    inici timestamptz not null,
    fi timestamptz not null check (fi > inici),
    multiplicador numeric(4, 2) not null check (multiplicador > 0),
    linies text[],
    modes public.mode_transport[],
    comarques text[],
    municipis text[],
    centre extensions.geography(point, 4326),
    radi_metres double precision,
    nom text,
    llavor bigint,
    font text,
    unique (tipus, clau, inici)
);
comment on table public.esdeveniments is 'Esdeveniments del motor de caos (GDD 6): àmbit, durada i multiplicador de l''ingrés. Iguals per a tothom.';
create index if not exists esdeveniments_temps_idx on public.esdeveniments (inici, fi);

alter table public.tipus_esdeveniment enable row level security;
alter table public.esdeveniments enable row level security;
create policy "Tothom pot llegir els tipus d'esdeveniment" on public.tipus_esdeveniment for select to authenticated using (true);
create policy "Tothom pot llegir els esdeveniments" on public.esdeveniments for select to authenticated using (true);
revoke insert, update, delete, truncate on public.tipus_esdeveniment, public.esdeveniments from anon, authenticated;

-- El calendari real (dades/esdeveniments_calendari.json): dies sencers de Catalunya.
create or replace function public.dia_catalunya(p_dia date)
returns timestamptz
language sql
immutable
set search_path = ''
as $$
    select (p_dia::timestamp) at time zone 'Europe/Madrid';
$$;

insert into public.esdeveniments (tipus, origen, clau, inici, fi, multiplicador, modes, municipis, centre, radi_metres, nom, font)
select x.tipus, 'calendari', x.clau, public.dia_catalunya(x.des_de), public.dia_catalunya(x.fins + 1), x.multiplicador,
       x.modes::public.mode_transport[], x.municipis, (select n.posicio from public.nodes n where n.id = x.centre_node),
       x.radi, x.nom, x.font
from (values
    ('fira', 'smart_city_2026', date '2026-11-02', date '2026-11-05', 1.2, null::text[], null::text[], 'fgc:EU', 1000::double precision,
     'Smart City Expo World Congress', 'Nordic Edge: Smart City Expo World Congress, 2–5 de novembre de 2026'),
    ('fira', 'mwc_2027', date '2027-03-01', date '2027-03-04', 1.4, null, null, 'fgc:EU', 1000,
     'MWC Barcelona', 'GSMA: MWC Barcelona 2027, de l''1 al 4 de març de 2027, Fira Gran Via'),
    ('marato', 'marato_2027_bus', date '2027-03-14', date '2027-03-14', 0.2, '{bus}', '{08019}', null, null,
     'Zurich Marató de Barcelona', 'Barcelona Urbana: la Marató de Barcelona 2027 es corre el diumenge 14 de març'),
    ('marato', 'marato_2027_metro', date '2027-03-14', date '2027-03-14', 1.2, '{metro}', '{08019}', null, null,
     'Zurich Marató de Barcelona', 'Barcelona Urbana: la Marató de Barcelona 2027 es corre el diumenge 14 de març'),
    ('sant_jordi', 'sant_jordi_2027', date '2027-04-23', date '2027-04-23', 1.3, null, '{08019,17079,25120,43148}', null, null,
     'Sant Jordi', 'Diada de Sant Jordi: el 23 d''abril de cada any')
) as x (tipus, clau, des_de, fins, multiplicador, modes, municipis, centre_node, radi, nom, font)
-- Si el node del centre ja no existeix (dades noves), l'esdeveniment no es crea.
where x.centre_node is null or exists (select 1 from public.nodes n where n.id = x.centre_node)
on conflict (tipus, clau, inici) do nothing;

-- Si un esdeveniment toca un node.
create or replace function public.esdeveniment_toca(p_esdeveniment public.esdeveniments, p_node text)
returns boolean
language sql
stable
set search_path = ''
as $$
    select exists (
        select 1 from public.nodes n
        where n.id = p_node
          and (p_esdeveniment.modes is null or n.mode = any (p_esdeveniment.modes))
          and (p_esdeveniment.comarques is null or n.comarca = any (p_esdeveniment.comarques))
          and (p_esdeveniment.municipis is null or n.municipi = any (p_esdeveniment.municipis))
          and (p_esdeveniment.centre is null
               or extensions.st_dwithin(n.posicio, p_esdeveniment.centre, coalesce(p_esdeveniment.radi_metres, 0)))
          and (p_esdeveniment.linies is null or exists (
                select 1 from public.linia_nodes ln join public.linies l on l.id = ln.linia_id
                where ln.node_id = n.id and l.mode::text || ':' || l.codi = any (p_esdeveniment.linies)))
    );
$$;

-- El multiplicador del caos per a una andana en un moment: el producte dels esdeveniments que la toquen.
create or replace function public.factor_caos(p_node text, p_moment timestamptz)
returns numeric
language sql
stable
set search_path = ''
as $$
    select coalesce(round(exp(sum(ln(e.multiplicador)))::numeric, 4), 1)
    from public.esdeveniments e
    where e.inici <= p_moment and p_moment < e.fi and public.esdeveniment_toca(e, p_node);
$$;

-- I per als vehicles d'una línia: els esdeveniments de la línia o del seu mode sencer.
create or replace function public.factor_caos_linia(p_linia text, p_moment timestamptz)
returns numeric
language sql
stable
set search_path = ''
as $$
    select coalesce(round(exp(sum(ln(e.multiplicador)))::numeric, 4), 1)
    from public.esdeveniments e
    where e.inici <= p_moment and p_moment < e.fi
      and (p_linia = any (e.linies)
           or (e.linies is null and e.comarques is null and e.municipis is null and e.centre is null
               and split_part(p_linia, ':', 1) = any (e.modes::text[])));
$$;

-- Un nombre de 0 a 1 que sempre és el mateix per a un text (la llavor del sorteig).
create or replace function public.atzar(p_text text)
returns double precision
language sql
immutable
set search_path = ''
as $$
    select ('x' || substr(md5(p_text), 1, 8))::bit(32)::bigint / 4294967296.0;
$$;

-- Sorteja els esdeveniments aleatoris d'un dia (a l'hora de Catalunya). Sempre dona el mateix resultat per al mateix
-- dia: la llavor és el dia, el tipus i l'àmbit, i es desa. Torna quants n'han sortit.
create or replace function public.sorteja_esdeveniments(p_dia date)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_tipus record;
    v_ambit record;
    v_clau text;
    v_prob numeric;
    v_durada integer;
    v_nous integer := 0;
    v_feiner boolean := extract(isodow from p_dia) between 1 and 5;
    v_tardor boolean := extract(month from p_dia) between 9 and 11;
    v_centre extensions.geography;
begin
    for v_tipus in select * from public.tipus_esdeveniment loop
        if v_tipus.nomes_feiner and not v_feiner then
            continue;
        end if;
        v_prob := v_tipus.probabilitat
            * case when v_tipus.tardor and v_tardor then (public.config_caos() ->> 'multiplicador_tardor')::numeric else 1 end;
        for v_ambit in
            -- Els àmbits de la configuració; «perLiniaDelsModes» es desplega en una línia cada un.
            select a.valor as ambit
            from jsonb_array_elements(v_tipus.ambits) as a (valor)
            where not (a.valor ? 'perLiniaDelsModes')
            union all
            select jsonb_build_object('linies', jsonb_build_array(f.linia))
            from jsonb_array_elements(v_tipus.ambits) as a (valor)
            join public.flota_linies f on f.mode::text in (select jsonb_array_elements_text(a.valor -> 'perLiniaDelsModes'))
            where a.valor ? 'perLiniaDelsModes'
        loop
            v_clau := md5(v_ambit.ambit::text);
            if public.atzar(v_tipus.id || '|' || v_clau || '|' || p_dia) >= v_prob then
                continue;
            end if;
            v_durada := v_tipus.durada_min + floor(public.atzar('durada|' || v_tipus.id || '|' || v_clau || '|' || p_dia)
                * (v_tipus.durada_max - v_tipus.durada_min + 1))::int;
            v_centre := null;
            if v_ambit.ambit ? 'centreNode' then
                select n.posicio into v_centre from public.nodes n where n.id = v_ambit.ambit ->> 'centreNode';
                if v_centre is null then
                    continue;
                end if;
            end if;
            insert into public.esdeveniments (tipus, origen, clau, inici, fi, multiplicador, linies, modes, comarques,
                                              centre, radi_metres, nom, llavor)
            values (
                v_tipus.id, 'aleatori', v_clau, public.dia_catalunya(p_dia), public.dia_catalunya(p_dia + v_durada),
                v_tipus.multiplicador,
                (select array_agg(x) from jsonb_array_elements_text(v_ambit.ambit -> 'linies') as x),
                (select array_agg(x::public.mode_transport) from jsonb_array_elements_text(v_ambit.ambit -> 'modes') as x),
                (select array_agg(x) from jsonb_array_elements_text(v_ambit.ambit -> 'comarques') as x),
                v_centre, (v_ambit.ambit ->> 'radiMetres')::double precision, v_ambit.ambit ->> 'nom',
                ('x' || substr(md5(v_tipus.id || '|' || v_clau || '|' || p_dia), 1, 8))::bit(32)::bigint
            )
            on conflict (tipus, clau, inici) do nothing;
            if found then
                v_nous := v_nous + 1;
            end if;
        end loop;
    end loop;
    return v_nous;
end;
$$;

-- Cada dia (pg_cron, a les 03:00 UTC: abans que comenci el servei a les 05:00), el sorteig del dia de Catalunya.
create or replace function public.sorteja_avui()
returns integer
language sql
security definer
set search_path = ''
as $$
    select public.sorteja_esdeveniments((now() at time zone 'Europe/Madrid')::date);
$$;

-- 5. L'ingrés compta la flota i el caos ------------------------------------------------------------------------------

drop function if exists public.ingres_jugador_exacte(uuid, boolean, numeric);
create or replace function public.ingres_jugador_exacte(p_jugador uuid, p_feiner boolean, p_fraccio numeric,
                                                         p_moment timestamptz default now())
returns numeric
language sql
stable
security definer
set search_path = ''
as $$
    with dia as (select case when p_feiner then 1.0 else 0.6 end::double precision as factor),
    andanes as (
        select coalesce(sum(
            public.ingres_diari_concessio(c.node_id, dia.factor)
                * case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
                * public.factor_millores(p_jugador, c.node_id)
                * public.factor_caos(c.node_id, p_moment)
            - public.manteniment_diari(c.node_id, p_jugador)), 0) as total
        from public.concessions c cross join dia
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    ),
    dividends as (
        select coalesce(sum(
            (public.ingres_diari((n.viatgers_feiner::double precision * dia.factor)::numeric) / 100.0) * a.quantitat
                * case when a.presencial_fins >= now() then public.multiplicador_concessio() else 1 end), 0) as total
        from public.accions a join public.nodes n on n.id = a.node_id cross join dia
        where a.jugador_id = p_jugador and a.darrera_visita_el >= (now() - interval '30 days')
    ),
    flota as (
        select coalesce(sum(fj.vehicles * (e.ingres * public.factor_caos_linia(fj.linia, p_moment) - e.quota - e.explotacio)), 0) as total
        from public.flota_jugador fj
        cross join dia
        cross join lateral public.economia_vehicle(fj.linia, dia.factor) e
        where fj.jugador_id = p_jugador
    )
    select greatest((select total from andanes) + (select total from dividends) + (select total from flota), 0) * p_fraccio;
$$;

-- liquida_jugador: el caos de cada tros es mira a la meitat del tros.
create or replace function public.liquida_jugador(p_jugador uuid)
returns numeric
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ara timestamptz := now();
    v_desde timestamptz;
    v_resta numeric;
    v_inici timestamptz;
    v_fi timestamptz;
    v_feiner boolean;
    v_fraccio numeric;
    v_total numeric := 0;
    v_arribades numeric := 0;
    v_pagar numeric(12, 2);
begin
    select p.cobrat_fins, p.resta_cobrament into v_desde, v_resta
    from public.perfils p where p.id = p_jugador
    for update;
    if not found then
        return 0;
    end if;
    -- Com a molt, els últims 60 dies (un jugador que torna després de molt de temps).
    v_desde := greatest(coalesce(v_desde, v_ara), v_ara - interval '60 days');
    if v_ara - v_desde < interval '1 second' then
        return 0;
    end if;

    v_inici := v_desde;
    while v_inici < v_ara loop
        -- Fins a la mitjanit de Catalunya següent (o fins ara).
        v_fi := least(v_ara,
            (date_trunc('day', v_inici at time zone 'Europe/Madrid') + interval '1 day') at time zone 'Europe/Madrid');
        v_feiner := extract(isodow from (v_inici at time zone 'Europe/Madrid')) between 1 and 5;
        v_fraccio := public.fraccio_servei(v_inici, v_fi);
        if v_fraccio > 0 then
            v_total := v_total + public.ingres_jugador_exacte(p_jugador, v_feiner, v_fraccio, v_inici + (v_fi - v_inici) / 2);
            v_arribades := v_arribades + public.arribades_jugador(p_jugador, v_feiner, v_fraccio);
            perform public.suma_maestria_jugador(p_jugador, v_feiner, v_fraccio);
        end if;
        v_inici := v_fi;
    end loop;

    -- Els cèntims sencers es paguen; la resta es guarda.
    v_pagar := floor((v_total + v_resta) * 100) / 100;
    update public.perfils
    set cobrat_fins = v_ara,
        resta_cobrament = v_total + v_resta - v_pagar,
        saldo = saldo + v_pagar,
        pendent_benvinguda = pendent_benvinguda + v_pagar,
        pendent_arribades = pendent_arribades + v_arribades,
        actualitzat_el = case when v_pagar > 0 then v_ara else actualitzat_el end
    where id = p_jugador;

    if v_pagar > 0 then
        insert into public.moviments (jugador_id, concepte, import, tipus, periode)
        values (p_jugador,
                'Arribades de les ' || extract(hour from (v_ara at time zone 'Europe/Madrid'))::int || ' h',
                v_pagar, 'cobrament', date_trunc('hour', v_ara))
        on conflict (jugador_id, periode) where periode is not null
        do update set import = public.moviments.import + excluded.import;
    end if;
    return v_pagar;
end;
$$;

-- andanes_jugador: el valor de cada arribada compta el caos d'ara (els avisos de l'app).
create or replace function public.andanes_jugador()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_factor double precision;
    v_skin text;
begin
    if v_user_id is null then
        return null;
    end if;
    v_factor := case when extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5 then 1.0 else 0.6 end;
    select s.color into v_skin from public.perfils p join public.skins s on s.id = p.skin_andana where p.id = v_user_id;
    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', n.id,
            'nom', n.nom,
            'mode', n.mode,
            'lat', extensions.st_y(n.posicio::extensions.geometry),
            'lon', extensions.st_x(n.posicio::extensions.geometry),
            'linies', n.linies,
            'regal', c.regal,
            'presencial', coalesce(c.presencial_fins >= now(), false),
            'color', coalesce(v_skin, '#FF3D7F'),
            'pujades', case when n.expedicions_feiner > 0
                then round((n.viatgers_feiner::double precision * v_factor / n.expedicions_feiner)::numeric) else 0 end,
            'valor', case when n.expedicions_feiner > 0
                then round(public.ingres_diari_concessio(n.id, v_factor)
                    * case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
                    * public.factor_millores(v_user_id, n.id)
                    * public.factor_caos(n.id, now())
                    / n.expedicions_feiner, 2) else 0 end
        ) order by n.nom)
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
    ), '[]'::jsonb);
end;
$$;

-- 6. Llogar i tornar vehicles -------------------------------------------------------------------------------------------

create or replace function public.lloga_vehicles(p_linia text, p_quantitat integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_perfil record;
    v_linia record;
    v_tinc integer;
    v_maxim integer;
    v_eco record;
    v_fianca numeric(12, 2);
    v_saldo numeric(12, 2);
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    if p_quantitat is null or p_quantitat < 1 or p_quantitat > 100 then
        return jsonb_build_object('status', 'error', 'missatge', 'Quantitat no vàlida.');
    end if;
    select p.saldo, coalesce(p.mode_dev, false) as mode_dev into v_perfil
    from public.perfils p where p.id = v_user_id for update;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'El perfil no existeix.');
    end if;
    select f.* into v_linia from public.flota_linies f where f.linia = p_linia;
    if not found or public.model_linia(p_linia) is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta línia no té flota.');
    end if;
    v_maxim := public.maxim_vehicles(v_user_id, p_linia);
    if v_maxim < 1 then
        return jsonb_build_object('status', 'sense_andanes',
            'missatge', 'Per llogar vehicles de la ' || v_linia.codi || ' cal tenir-hi alguna andana.');
    end if;
    select coalesce(max(fj.vehicles), 0) into v_tinc from public.flota_jugador fj
    where fj.jugador_id = v_user_id and fj.linia = p_linia;
    if v_tinc + p_quantitat > v_maxim then
        return jsonb_build_object('status', 'limit_flota',
            'missatge', 'Amb les andanes que tens a la ' || v_linia.codi || ', hi pots tenir com a molt ' || v_maxim
                || ' vehicles. Compra més andanes de la línia!');
    end if;

    select * into v_eco from public.economia_vehicle(p_linia, 1.0);
    v_fianca := case when v_perfil.mode_dev then 0
        else round((public.config_flota() ->> 'dies_fianca')::numeric * (v_eco.quota + v_eco.explotacio) * p_quantitat, 2) end;
    if v_perfil.saldo < v_fianca then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: la fiança és de ' || v_fianca || ' €.');
    end if;

    insert into public.flota_jugador as fj (jugador_id, linia, vehicles, fianca)
    values (v_user_id, p_linia, p_quantitat, v_fianca)
    on conflict (jugador_id, linia) do update set vehicles = fj.vehicles + excluded.vehicles, fianca = fj.fianca + excluded.fianca;
    update public.perfils set saldo = saldo - v_fianca, actualitzat_el = now() where id = v_user_id returning saldo into v_saldo;
    insert into public.moviments (jugador_id, concepte, import, tipus)
    values (v_user_id, 'Fiança: ' || p_quantitat || ' vehicles de la ' || v_linia.codi, -v_fianca, 'flota');

    return jsonb_build_object(
        'status', 'valid',
        'tipus_accio', 'flota',
        'missatge', case when p_quantitat = 1 then 'Llogat 1 vehicle' else 'Llogats ' || p_quantitat || ' vehicles' end
            || ' per a la ' || v_linia.codi || '. Ja circula amb el teu color!',
        'cost', v_fianca,
        'saldo', v_saldo
    );
end;
$$;

create or replace function public.retorna_vehicles(p_linia text, p_quantitat integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_fila record;
    v_retorn numeric(12, 2);
    v_saldo numeric(12, 2);
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select fj.vehicles, fj.fianca into v_fila from public.flota_jugador fj
    where fj.jugador_id = v_user_id and fj.linia = p_linia for update;
    if not found or p_quantitat is null or p_quantitat < 1 or p_quantitat > v_fila.vehicles then
        return jsonb_build_object('status', 'error', 'missatge', 'No tens tants vehicles en aquesta línia.');
    end if;
    -- Es retorna la part de la fiança d'aquests vehicles.
    v_retorn := round(v_fila.fianca * p_quantitat / v_fila.vehicles, 2);
    if p_quantitat = v_fila.vehicles then
        delete from public.flota_jugador where jugador_id = v_user_id and linia = p_linia;
    else
        update public.flota_jugador set vehicles = vehicles - p_quantitat, fianca = fianca - v_retorn
        where jugador_id = v_user_id and linia = p_linia;
    end if;
    update public.perfils set saldo = saldo + v_retorn, actualitzat_el = now() where id = v_user_id returning saldo into v_saldo;
    if v_retorn > 0 then
        insert into public.moviments (jugador_id, concepte, import, tipus)
        values (v_user_id, 'Retorn de la fiança: ' || p_quantitat || ' vehicles de la ' || split_part(p_linia, ':', 2), v_retorn, 'flota');
    end if;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'flota',
        'missatge', 'Has tornat ' || p_quantitat || ' vehicles. Et tornem ' || v_retorn || ' € de fiança.', 'saldo', v_saldo);
end;
$$;

-- La flota del jugador: les línies on té andanes, amb el que pot llogar i el que dona cada vehicle (feiner).
create or replace function public.estat_flota()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        return null;
    end if;
    return coalesce((
        with meves as (
            -- Les línies on el jugador té andanes, amb quantes n'hi té (una sola consulta per a totes).
            select l.mode::text || ':' || l.codi as linia, count(distinct c.node_id)::int as andanes
            from public.concessions c
            join public.linia_nodes ln on ln.node_id = c.node_id
            join public.linies l on l.id = ln.linia_id
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
            group by 1
        ),
        totes as materialized (
            -- Primer les línies del jugador, i només per a aquestes es calcula el model i l'economia.
            select fl.*, m.nom as model, m.preu as preu_model, m.font as font_model
            from (select linia from meves union select fj.linia from public.flota_jugador fj where fj.jugador_id = v_user_id) x
            join public.flota_linies fl on fl.linia = x.linia
            cross join lateral (select public.model_linia(fl.linia) as id) mi
            join public.models_vehicle m on m.id = mi.id
        )
        select jsonb_agg(x.fila order by x.vehicles desc, x.andanes desc, x.codi)
        from (
            select fl.codi, coalesce(fj.vehicles, 0) as vehicles, coalesce(a.andanes, 0) as andanes,
                   jsonb_build_object(
                       'linia', fl.linia,
                       'codi', fl.codi,
                       'mode', fl.mode,
                       'color', (select l.color from public.linies l where l.mode = fl.mode and l.codi = fl.codi
                                 order by l.expedicions_feiner desc limit 1),
                       'andanes', coalesce(a.andanes, 0),
                       'nodes', fl.nodes,
                       'vehicles_punta', fl.vehicles_punta,
                       'maxim', case when fl.nodes > 0
                           then ceil(fl.vehicles_punta * least(coalesce(a.andanes, 0), fl.nodes)::numeric / fl.nodes)::int else 0 end,
                       'vehicles', coalesce(fj.vehicles, 0),
                       'fianca', coalesce(fj.fianca, 0),
                       'model', fl.model,
                       'preu_model', fl.preu_model,
                       'font_model', fl.font_model,
                       'ingres_dia', round(e.ingres, 2),
                       'cost_dia', round(e.quota + e.explotacio, 2),
                       'net_dia', round(e.ingres - e.quota - e.explotacio, 2),
                       'fianca_vehicle', round((public.config_flota() ->> 'dies_fianca')::numeric * (e.quota + e.explotacio), 2),
                       'factor_caos', public.factor_caos_linia(fl.linia, now())
                   ) as fila
            from totes fl
            left join meves a on a.linia = fl.linia
            left join public.flota_jugador fj on fj.jugador_id = v_user_id and fj.linia = fl.linia
            cross join lateral public.economia_vehicle(fl.linia, 1.0) e
        ) x
    ), '[]'::jsonb);
end;
$$;

-- 7. Els esdeveniments per a l'app ---------------------------------------------------------------------------------------

-- Els esdeveniments d'avui (els que duren ara i els que comencen avui), amb les andanes del jugador que toquen.
create or replace function public.esdeveniments_avui()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_fi_avui timestamptz := public.dia_catalunya((now() at time zone 'Europe/Madrid')::date + 1);
begin
    if v_user_id is null then
        return null;
    end if;
    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', e.id,
            'tipus', e.tipus,
            'origen', e.origen,
            'nom', e.nom,
            'inici', e.inici,
            'fi', e.fi,
            'multiplicador', e.multiplicador,
            'linies', coalesce((select jsonb_agg(split_part(x, ':', 2)) from unnest(e.linies) as x), '[]'::jsonb),
            'municipis', coalesce((select jsonb_agg(m.nom order by m.nom) from public.municipis m where m.codi = any (e.municipis)), '[]'::jsonb),
            'modes', coalesce(to_jsonb(e.modes), '[]'::jsonb),
            'a_prop_de', (select n.nom from public.nodes n
                          where e.centre is not null and extensions.st_dwithin(n.posicio, e.centre, 1)
                          order by n.id limit 1),
            'andanes', coalesce((
                select jsonb_agg(c.node_id order by c.node_id)
                from public.concessions c
                where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now() and public.esdeveniment_toca(e, c.node_id)
            ), '[]'::jsonb)
        ) order by e.inici, e.tipus)
        from public.esdeveniments e
        where e.fi > now() and e.inici < v_fi_avui
    ), '[]'::jsonb);
end;
$$;

-- Mode desenvolupador: un esdeveniment de prova d'avui a les línies d'una andana pròpia (afecta tothom, com tots).
create or replace function public.dev_provoca_esdeveniment(p_node text, p_multiplicador numeric)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_linies text[];
begin
    if v_user_id is null or not coalesce((select p.mode_dev from public.perfils p where p.id = v_user_id), false) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només amb el mode desenvolupador.');
    end if;
    if p_multiplicador is null or p_multiplicador <= 0 or p_multiplicador > 3 then
        return jsonb_build_object('status', 'error', 'missatge', 'El multiplicador ha de ser entre 0 i 3.');
    end if;
    select array_agg(distinct l.mode::text || ':' || l.codi) into v_linies
    from public.linia_nodes ln join public.linies l on l.id = ln.linia_id where ln.node_id = p_node;
    if v_linies is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta andana no té línies.');
    end if;
    perform public.liquida_jugador(v_user_id);
    insert into public.esdeveniments (tipus, origen, clau, inici, fi, multiplicador, linies, nom)
    values (case when p_multiplicador < 1 then 'avaria_infraestructura' else 'fira' end, 'prova',
            'prova|' || p_node || '|' || extract(epoch from now()), now(), now() + interval '1 hour', p_multiplicador,
            v_linies, 'Prova')
    on conflict (tipus, clau, inici) do nothing;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'caos',
        'missatge', 'Esdeveniment de prova durant 1 hora a les línies d''aquesta andana (×' || p_multiplicador || ').');
end;
$$;

-- 8. El sorteig de cada dia (pg_cron) i el d'avui ----------------------------------------------------------------------

do $cron$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.unschedule(jobid) from cron.job where jobname = 'sorteja_esdeveniments';
        perform cron.schedule('sorteja_esdeveniments', '0 3 * * *', 'select public.sorteja_avui();');
    else
        raise notice 'pg_cron no està activat: activa''l (Database → Extensions) i torna a executar aquesta migració.';
    end if;
end;
$cron$;

do $avui$
begin
    perform public.sorteja_avui();
end;
$avui$;

-- 9. Permisos ----------------------------------------------------------------------------------------------------------
revoke execute on function public.liquida_flota_abans() from public, anon, authenticated;
revoke execute on function public.economia_vehicle(text, double precision) from public, anon, authenticated;
revoke execute on function public.andanes_a_linia(uuid, text) from public, anon, authenticated;
revoke execute on function public.maxim_vehicles(uuid, text) from public, anon, authenticated;
revoke execute on function public.sorteja_esdeveniments(date) from public, anon, authenticated;
revoke execute on function public.sorteja_avui() from public, anon, authenticated;
revoke execute on function public.ingres_jugador_exacte(uuid, boolean, numeric, timestamptz) from public, anon, authenticated;
grant execute on function public.sorteja_avui() to service_role;

revoke execute on function public.lloga_vehicles(text, integer) from public, anon;
revoke execute on function public.retorna_vehicles(text, integer) from public, anon;
revoke execute on function public.estat_flota() from public, anon;
revoke execute on function public.esdeveniments_avui() from public, anon;
revoke execute on function public.dev_provoca_esdeveniment(text, numeric) from public, anon;
grant execute on function public.lloga_vehicles(text, integer) to authenticated;
grant execute on function public.retorna_vehicles(text, integer) to authenticated;
grant execute on function public.estat_flota() to authenticated;
grant execute on function public.esdeveniments_avui() to authenticated;
grant execute on function public.dev_provoca_esdeveniment(text, numeric) to authenticated;
