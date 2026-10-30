-- Les andanes de regal del Director (docs/lore.md, docs/abast.md).
--
-- A la introducció, el Director ofereix 5 opcions reals per província: andanes soles i trams de dues andanes seguides
-- d'una línia. Se'n poden triar com a màxim 2 andanes per província. Són concessions de regal: no caduquen i no compten
-- per al límit de la llicència. Els valors són a dades/configuracio_joc.json (bloc «regal»).

-- 1. Concessions de regal i si el jugador ja ha triat.
alter table public.concessions
    add column if not exists regal boolean not null default false;

comment on column public.concessions.regal is 'Regal del Director: no caduca i no compta per al límit de la llicència.';

alter table public.perfils
    add column if not exists regal_triat boolean not null default false;

comment on column public.perfils.regal_triat is 'El jugador ja ha triat les andanes de regal del Director.';

-- 2. Valors del regal. Han de coincidir amb el bloc «regal» de dades/configuracio_joc.json (tests a supabase/tests).
create or replace function public.config_regal()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'opcions_per_provincia', 5,
        'trams_per_provincia', 2,
        'maxim_andanes_per_provincia', 2,
        'viatgers_minims', 20,
        'viatgers_maxims', 3000
    );
$$;

-- 3. Les opcions d'un jugador. Són sempre les mateixes per a cada jugador (s'ordenen amb un hash del seu id), però
--    diferents entre jugadors.
--    El GTFS carregat no desa l'ordre de les parades de cada línia: les dues andanes d'un tram són una andana i la
--    més propera de la mateixa línia, que és la del costat.
create or replace function public.ofertes_regal_de(p_jugador uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    with cfg as (
        select (c ->> 'opcions_per_provincia')::int as opcions,
               (c ->> 'trams_per_provincia')::int as trams,
               (c ->> 'viatgers_minims')::numeric as minim,
               (c ->> 'viatgers_maxims')::numeric as maxim
        from (select public.config_regal() as c) x
    ),
    provincies (codi, nom, ordre) as (
        values ('08', 'Barcelona', 1), ('17', 'Girona', 2), ('25', 'Lleida', 3), ('43', 'Tarragona', 4)
    ),
    candidats as (
        select n.id, n.nom, n.mode, n.operador, n.linies, n.viatgers_feiner, n.font_afluencia, n.posicio,
               left(n.municipi, 2) as provincia, m.nom as municipi
        from public.nodes n
        join public.municipis m on m.codi = n.municipi
        cross join cfg
        where n.mode <> 'bus' and n.viatgers_feiner >= cfg.minim and n.viatgers_feiner < cfg.maxim
    ),
    veins as (
        select distinct on (la.linia_id, a.id) la.linia_id, a.id as a_id, lb.node_id as b_id
        from candidats a
        join public.linia_nodes la on la.node_id = a.id
        join public.linia_nodes lb on lb.linia_id = la.linia_id and lb.node_id <> a.id
        join public.nodes b on b.id = lb.node_id
        order by la.linia_id, a.id, extensions.st_distance(a.posicio, b.posicio)
    ),
    trams as (
        select distinct on (least(v.a_id, v.b_id), greatest(v.a_id, v.b_id))
               least(v.a_id, v.b_id) as a, greatest(v.a_id, v.b_id) as b, v.linia_id, ca.provincia
        from veins v
        join candidats ca on ca.id = v.a_id
        join candidats cb on cb.id = v.b_id and cb.provincia = ca.provincia
        order by least(v.a_id, v.b_id), greatest(v.a_id, v.b_id), v.linia_id
    ),
    trams_ordenats as (
        select t.*, row_number() over (partition by t.provincia order by md5(p_jugador::text || t.a || t.b)) as rn
        from trams t
    ),
    -- Com a màxim dos trams per província, i el segon no pot compartir cap andana amb el primer.
    primer_tram as (
        select t.* from trams_ordenats t, cfg where t.rn = 1 and cfg.trams >= 1
    ),
    segon_tram as (
        select distinct on (t.provincia) t.*
        from trams_ordenats t
        join primer_tram p on p.provincia = t.provincia
        cross join cfg
        where cfg.trams >= 2 and t.a not in (p.a, p.b) and t.b not in (p.a, p.b)
        order by t.provincia, t.rn
    ),
    trams_triats as (
        select a, b, linia_id, provincia from primer_tram
        union all
        select a, b, linia_id, provincia from segon_tram
    ),
    soles as (
        select c.*
        from (
            select c.id, c.provincia,
                   row_number() over (partition by c.provincia order by md5(p_jugador::text || c.id)) as rn
            from candidats c
            where not exists (select 1 from trams_triats t where c.id in (t.a, t.b))
        ) c, cfg
        where c.rn <= cfg.opcions - (select count(*) from trams_triats t where t.provincia = c.provincia)
    ),
    opcions as (
        select t.provincia, 'tram:' || t.a || '+' || t.b as id, array[t.a, t.b] as nodes, t.linia_id, 0 as ordre
        from trams_triats t
        union all
        select s.provincia, s.id, array[s.id], null, 1
        from soles s
    ),
    opcions_json as (
        select o.provincia, jsonb_agg(jsonb_build_object(
            'id', o.id,
            'linia', l.codi,
            'color', l.color,
            'nodes', (
                select jsonb_agg(jsonb_build_object(
                    'id', c.id,
                    'nom', c.nom,
                    'mode', c.mode,
                    'operador', c.operador,
                    'municipi', c.municipi,
                    'linies', c.linies,
                    'viatgers_feiner', c.viatgers_feiner,
                    'font_afluencia', c.font_afluencia,
                    'ingres_diari', public.ingres_diari(c.viatgers_feiner)
                ) order by array_position(o.nodes, c.id))
                from candidats c
                where c.id = any (o.nodes)
            )
        ) order by o.ordre, o.id) as opcions
        from opcions o
        left join public.linies l on l.id = o.linia_id
        group by o.provincia
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'provincia', p.codi,
        'nom', p.nom,
        'opcions', coalesce(oj.opcions, '[]'::jsonb)
    ) order by p.ordre), '[]'::jsonb)
    from provincies p
    left join opcions_json oj on oj.provincia = p.codi;
$$;

comment on function public.ofertes_regal_de is 'Les andanes de regal que el Director ofereix a un jugador, per províncies.';

-- 4. Les opcions del jugador autenticat. null si ja ha triat.
create or replace function public.ofertes_regal()
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
        raise exception 'Cal iniciar sessió.';
    end if;
    if exists (select 1 from public.perfils where id = v_user_id and regal_triat) then
        return null;
    end if;
    return jsonb_build_object(
        'maxim_andanes_per_provincia', (public.config_regal() ->> 'maxim_andanes_per_provincia')::int,
        'provincies', public.ofertes_regal_de(v_user_id)
    );
end;
$$;

-- 5. Triar el regal. Es valida tot al servidor: que les opcions siguin del jugador, el màxim per província i que
--    només es pugui fer un cop.
create or replace function public.tria_regal(p_opcions text[])
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_maxim integer := (public.config_regal() ->> 'maxim_andanes_per_provincia')::int;
    v_ofertes jsonb;
    v_triat jsonb;
    v_desconeguda text;
    v_node record;
    v_noms text[] := '{}';
begin
    if v_user_id is null then
        raise exception 'Cal iniciar sessió.';
    end if;
    if p_opcions is null or cardinality(p_opcions) = 0 then
        raise exception 'Tria com a mínim una andana.';
    end if;

    perform 1 from public.perfils where id = v_user_id for update;
    if not found then
        raise exception 'El perfil no existeix.';
    end if;
    if exists (select 1 from public.perfils where id = v_user_id and regal_triat) then
        raise exception 'Ja has triat les andanes de regal.';
    end if;

    v_ofertes := public.ofertes_regal_de(v_user_id);

    -- Les andanes de les opcions triades, amb la seva província.
    select coalesce(jsonb_agg(jsonb_build_object(
        'opcio', o ->> 'id', 'provincia', p ->> 'provincia', 'node_id', n ->> 'id', 'nom', n ->> 'nom'
    )), '[]'::jsonb)
    into v_triat
    from jsonb_array_elements(v_ofertes) p,
         jsonb_array_elements(p -> 'opcions') o,
         jsonb_array_elements(o -> 'nodes') n
    where o ->> 'id' = any (p_opcions);

    select u.opcio into v_desconeguda
    from unnest(p_opcions) as u (opcio)
    where not exists (select 1 from jsonb_array_elements(v_triat) t where t ->> 'opcio' = u.opcio)
    limit 1;
    if v_desconeguda is not null then
        raise exception 'L''opció «%» no és de les que t''ofereix el Director.', v_desconeguda;
    end if;

    if jsonb_array_length(v_triat) <> (select count(distinct t ->> 'node_id') from jsonb_array_elements(v_triat) t) then
        raise exception 'Has triat la mateixa andana dues vegades.';
    end if;

    if exists (
        select 1 from jsonb_array_elements(v_triat) t
        group by t ->> 'provincia'
        having count(*) > v_maxim
    ) then
        raise exception 'Com a màxim pots triar % andanes per província.', v_maxim;
    end if;

    for v_node in
        select t ->> 'node_id' as node_id, t ->> 'nom' as nom
        from jsonb_array_elements(v_triat) t
        order by t ->> 'provincia', t ->> 'opcio'
    loop
        insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal)
        values (v_user_id, v_node.node_id, now(), now() + interval '100 years', true, true)
        on conflict (jugador_id, node_id) do update
            set caduca_el = excluded.caduca_el, activa = true, regal = true;

        insert into public.moviments (jugador_id, concepte, import, node_id)
        values (v_user_id, 'Regal del Director: ' || v_node.nom, 0, v_node.node_id);

        v_noms := v_noms || v_node.nom;
    end loop;

    update public.perfils set regal_triat = true, actualitzat_el = now() where id = v_user_id;

    return jsonb_build_object('status', 'ok', 'andanes', to_jsonb(v_noms));
end;
$$;

comment on function public.tria_regal is 'Dona al jugador autenticat les andanes de regal triades, si compleixen les regles.';

-- 6. Les funcions del joc coneixen les concessions de regal.
--    fer_checkin: les de regal no compten per al límit de la llicència i el missatge diu que no caduquen.
create or replace function public.fer_checkin(
    p_node_id   text,
    p_lat       double precision,
    p_lon       double precision,
    p_precisio  double precision,
    p_velocitat double precision
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id             uuid;
    v_mode_dev            boolean;
    v_node                record;
    v_perfil              record;
    v_punt                extensions.geography;
    v_dist_metres         double precision;
    v_radi                double precision;
    v_sobrant             double precision;
    v_darrer_checkin      record;
    v_hores               double precision;
    v_dist_km             double precision;
    v_concessio           record;
    v_accions_player      integer;
    v_total_accions       integer;
    v_comprar             integer;
    v_cost                numeric(12, 2);
    v_dies_restants       integer;
    v_maxim_concessions   integer;
    v_concessions_actives integer;
    v_prestigi_afegir     integer := 0;
    v_nou_saldo           numeric(12, 2);
    v_nou_prestigi        integer;
    v_nou_nivell          integer;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;

    -- Llegim mode_dev del perfil (null si el perfil no existeix encara).
    select mode_dev into v_mode_dev
    from public.perfils
    where id = v_user_id;
    v_mode_dev := coalesce(v_mode_dev, false);

    -- Obtenir node.
    select n.id, n.nom, n.mode, n.operador, n.posicio, n.viatgers_feiner, n.municipi, n.comarca
    into v_node
    from public.nodes n
    where n.id = p_node_id;

    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquest node no existeix.');
    end if;

    -- Assegurar perfil del jugador.
    insert into public.perfils (id, saldo, prestigi, nivell)
    values (v_user_id, 200.00, 0, 1)
    on conflict (id) do nothing;

    select id, saldo, prestigi, nivell
    into v_perfil
    from public.perfils
    where id = v_user_id
    for update;

    -- Validació: màxim 1 acció/check-in per node i dia.
    if exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id
          and c.node_id = p_node_id
          and c.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent')
          and c.creat_el >= date_trunc('day', now() at time zone 'Europe/Madrid') at time zone 'Europe/Madrid'
    ) then
        return jsonb_build_object('status', 'ja_fet_avui', 'missatge', 'Ja has fet check-in avui en aquest node.');
    end if;

    if v_mode_dev then
        -- Mode desenvolupador: usem la posició de la parada com a posició del jugador.
        v_punt := v_node.posicio;
    else
        -- Validació: precisió del GPS (<= 50 m).
        if p_precisio > 50.0 then
            return jsonb_build_object('status', 'precisio_insuficient', 'missatge', 'La precisió del GPS és insuficient (> 50 m).');
        end if;

        -- Validació: velocitat en el moment del check-in (<= 10 km/h).
        if p_velocitat > 10.0 then
            return jsonb_build_object('status', 'en_moviment', 'missatge', 'Vas massa ràpid (> 10 km/h). El vehicle o tren ha d''estar aturat.');
        end if;

        -- Punt GPS del jugador.
        v_punt := extensions.st_setsrid(extensions.st_makepoint(p_lon, p_lat), 4326)::extensions.geography;

        -- Validació: velocitat entre check-ins consecutius (<= 330 km/h).
        select c.creat_el, c.posicio
        into v_darrer_checkin
        from public.checkins c
        where c.jugador_id = v_user_id
        order by c.creat_el desc
        limit 1;

        if found then
            v_hores := extract(epoch from (now() - v_darrer_checkin.creat_el)) / 3600.0;
            if v_hores > 0 then
                v_dist_km := extensions.st_distance(v_darrer_checkin.posicio, v_punt) / 1000.0;
                if (v_dist_km / v_hores) > 330.0 then
                    return jsonb_build_object('status', 'velocitat_impossible', 'missatge', 'Distància entre check-ins recorreguda a velocitat impossible.');
                end if;
            end if;
        end if;

        -- Validació: distància al node (150 m per a ferrocarril/metro, 60 m per a bus/tram).
        v_radi := case when v_node.mode in ('metro', 'rodalies', 'regional', 'fgc') then 150.0 else 60.0 end;
        v_dist_metres := extensions.st_distance(v_node.posicio, v_punt);
        v_sobrant := v_dist_metres - p_precisio - v_radi;

        if v_sobrant > 0 then
            return jsonb_build_object(
                'status', 'massa_lluny',
                'missatge', 'Ets massa lluny: et falten ' || ceil(v_sobrant)::int || ' m.',
                'metres_falten', ceil(v_sobrant)::int
            );
        end if;
    end if;

    -- A partir d'aquí la lògica és la mateixa per a tothom.

    -- Comprovació si és el primer check-in en aquest node.
    if not exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id and c.node_id = p_node_id and c.resultat not in ('massa_lluny', 'precisio_insuficient', 'en_moviment', 'velocitat_impossible')
    ) then
        v_prestigi_afegir := v_prestigi_afegir + 20;
    end if;

    -- Branques: Gran Estació (>= 20.000 viatgers) o Parada.
    if v_node.viatgers_feiner >= 20000 then
        -- ACCIONARIAT
        select coalesce(quantitat, 0) into v_accions_player
        from public.accions
        where node_id = p_node_id and jugador_id = v_user_id;

        select coalesce(sum(quantitat), 0) into v_total_accions
        from public.accions
        where node_id = p_node_id;

        if v_accions_player >= 20 then
            update public.accions
            set darrera_visita_el = now()
            where node_id = p_node_id and jugador_id = v_user_id;

            v_prestigi_afegir := v_prestigi_afegir + 2;

            insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
            values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'visita_estacio');

            update public.perfils
            set prestigi = prestigi + v_prestigi_afegir,
                nivell = public.calcular_nivell(prestigi + v_prestigi_afegir),
                actualitzat_el = now()
            where id = v_user_id
            returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

            return jsonb_build_object(
                'status', 'valid',
                'tipus_accio', 'visita_estacio',
                'missatge', 'Visita registrada! Ja tens el màxim de 20 accions a ' || v_node.nom || '.',
                'saldo', v_nou_saldo,
                'prestigi', v_nou_prestigi,
                'nivell', v_nou_nivell,
                'accions_posseides', v_accions_player
            );
        end if;

        v_comprar := least(5, 20 - coalesce(v_accions_player, 0), 100 - v_total_accions);

        if v_comprar <= 0 then
            return jsonb_build_object('status', 'sense_accions', 'missatge', 'Totes les 100 accions d''aquesta estació estan venudes.');
        end if;

        v_cost := v_comprar * public.preu_accio(v_node.viatgers_feiner);

        if not v_mode_dev and v_perfil.saldo < v_cost then
            return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou diners: calen ' || v_cost || ' €.');
        end if;
        -- En mode_dev el cost és 0 per no alterar el saldo del jugador de proves.
        if v_mode_dev then v_cost := 0; end if;

        update public.perfils
        set saldo = saldo - v_cost,
            prestigi = prestigi + v_prestigi_afegir + (v_comprar * 2),
            nivell = public.calcular_nivell(prestigi + v_prestigi_afegir + (v_comprar * 2)),
            actualitzat_el = now()
        where id = v_user_id
        returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

        insert into public.accions (node_id, jugador_id, quantitat, darrera_visita_el)
        values (p_node_id, v_user_id, v_comprar, now())
        on conflict (node_id, jugador_id) do update
        set quantitat = public.accions.quantitat + v_comprar,
            darrera_visita_el = now();

        insert into public.moviments (jugador_id, concepte, import, node_id)
        values (v_user_id, 'Compra de ' || v_comprar || ' accions: ' || v_node.nom, -v_cost, p_node_id);

        insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
        values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'accions');

        return jsonb_build_object(
            'status', 'valid',
            'tipus_accio', 'accions',
            'missatge', '+' || v_comprar || ' accions a ' || v_node.nom || '!',
            'saldo', v_nou_saldo,
            'prestigi', v_nou_prestigi,
            'nivell', v_nou_nivell,
            'accions_comprades', v_comprar,
            'accions_totals', coalesce(v_accions_player, 0) + v_comprar
        );

    else
        -- CONCESSIÓ INDIVIDUAL
        select c.id, c.caduca_el, c.activa, c.regal
        into v_concessio
        from public.concessions c
        where c.node_id = p_node_id and c.jugador_id = v_user_id;

        if found then
            v_dies_restants := ceil(extract(epoch from (v_concessio.caduca_el - now())) / 86400.0);

            if v_concessio.activa and v_dies_restants >= 7 then
                insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
                values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'vigent');

                return jsonb_build_object(
                    'status', 'valid',
                    'tipus_accio', 'vigent',
                    'missatge', case when v_concessio.regal
                        then v_node.nom || ' és un regal del Director: no caduca mai.'
                        else 'La concessió de ' || v_node.nom || ' és vigent (' || v_dies_restants || ' dies restants).' end,
                    'saldo', v_perfil.saldo,
                    'prestigi', v_perfil.prestigi,
                    'nivell', v_perfil.nivell,
                    'dies_restants', case when v_concessio.regal then null else v_dies_restants end
                );

            elsif v_concessio.activa and v_dies_restants < 7 then
                update public.concessions
                set caduca_el = greatest(caduca_el, now()) + interval '14 days',
                    renovada_el = now(),
                    activa = true
                where id = v_concessio.id;

                v_prestigi_afegir := v_prestigi_afegir + 5;

                update public.perfils
                set prestigi = prestigi + v_prestigi_afegir,
                    nivell = public.calcular_nivell(prestigi + v_prestigi_afegir),
                    actualitzat_el = now()
                where id = v_user_id
                returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

                insert into public.moviments (jugador_id, concepte, import, node_id)
                values (v_user_id, 'Renovació de concessió: ' || v_node.nom, 0.00, p_node_id);

                insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
                values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'renovacio');

                return jsonb_build_object(
                    'status', 'valid',
                    'tipus_accio', 'renovacio',
                    'missatge', 'Renovada! Concessió ampliada 14 dies més.',
                    'saldo', v_nou_saldo,
                    'prestigi', v_nou_prestigi,
                    'nivell', v_nou_nivell,
                    'dies_restants', v_dies_restants + 14
                );

            else
                update public.concessions
                set caduca_el = now() + interval '14 days',
                    renovada_el = now(),
                    activa = true
                where id = v_concessio.id;

                v_prestigi_afegir := v_prestigi_afegir + 5;

                update public.perfils
                set prestigi = prestigi + v_prestigi_afegir,
                    nivell = public.calcular_nivell(prestigi + v_prestigi_afegir),
                    actualitzat_el = now()
                where id = v_user_id
                returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

                insert into public.moviments (jugador_id, concepte, import, node_id)
                values (v_user_id, 'Reactivació de concessió: ' || v_node.nom, 0.00, p_node_id);

                insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
                values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'reactivacio');

                return jsonb_build_object(
                    'status', 'valid',
                    'tipus_accio', 'reactivacio',
                    'missatge', 'Reactivada! Concessió de nou activa durant 14 dies.',
                    'saldo', v_nou_saldo,
                    'prestigi', v_nou_prestigi,
                    'nivell', v_nou_nivell,
                    'dies_restants', 14
                );
            end if;

        else
            -- CONQUESTA NOVA
            v_maxim_concessions := 10 + 2 * v_perfil.nivell;
            select count(*) into v_concessions_actives
            from public.concessions c
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now() and not c.regal;

            if not v_mode_dev and v_concessions_actives >= v_maxim_concessions then
                return jsonb_build_object(
                    'status', 'limit_llicencia',
                    'missatge', 'Has assolit el límit de la llicència (' || v_maxim_concessions || ' concessions). Puja de nivell!'
                );
            end if;

            v_cost := public.preu_concessio(v_node.viatgers_feiner);
            if not v_mode_dev and v_perfil.saldo < v_cost then
                return jsonb_build_object(
                    'status', 'sense_saldo',
                    'missatge', 'No tens prou diners: costa ' || v_cost || ' € (tens ' || v_perfil.saldo || ' €).'
                );
            end if;
            -- En mode_dev el cost és 0 per no alterar el saldo del jugador de proves.
            if v_mode_dev then v_cost := 0; end if;

            v_prestigi_afegir := v_prestigi_afegir + 30;

            if v_node.municipi is not null and not exists (
                select 1 from public.concessions c join public.nodes n2 on n2.id = c.node_id
                where c.jugador_id = v_user_id and n2.municipi = v_node.municipi
            ) then
                v_prestigi_afegir := v_prestigi_afegir + 50;
            end if;

            if v_node.comarca is not null and not exists (
                select 1 from public.concessions c join public.nodes n2 on n2.id = c.node_id
                where c.jugador_id = v_user_id and n2.comarca = v_node.comarca
            ) then
                v_prestigi_afegir := v_prestigi_afegir + 200;
            end if;

            update public.perfils
            set saldo = saldo - v_cost,
                prestigi = prestigi + v_prestigi_afegir,
                nivell = public.calcular_nivell(prestigi + v_prestigi_afegir),
                actualitzat_el = now()
            where id = v_user_id
            returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

            insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa)
            values (v_user_id, p_node_id, now(), now() + interval '14 days', true);

            insert into public.moviments (jugador_id, concepte, import, node_id)
            values (v_user_id, 'Concessió: ' || v_node.nom, -v_cost, p_node_id);

            insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
            values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'conquesta');

            return jsonb_build_object(
                'status', 'valid',
                'tipus_accio', 'conquesta',
                'missatge', 'Teva! Concessió de ' || v_node.nom || ' adquirida per 14 dies.',
                'saldo', v_nou_saldo,
                'prestigi', v_nou_prestigi,
                'nivell', v_nou_nivell,
                'cost', v_cost,
                'dies_restants', 14
            );
        end if;
    end if;
end;
$$;
--    estat_imperi: marca les de regal i diu quantes concessions compten per a la llicència.
create or replace function public.estat_imperi()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid;
    v_perfil record;
    v_concessions_actives jsonb;
    v_concessions_caducades jsonb;
    v_accions jsonb;
    v_moviments jsonb;
    v_ingres_concessions numeric(12, 2) := 0;
    v_ingres_accions numeric(12, 2) := 0;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return null;
    end if;

    select id, saldo, prestigi, nivell
    into v_perfil
    from public.perfils
    where id = v_user_id;

    if not found then
        insert into public.perfils (id, saldo, prestigi, nivell)
        values (v_user_id, 200.00, 0, 1)
        on conflict (id) do nothing;

        select id, saldo, prestigi, nivell
        into v_perfil
        from public.perfils
        where id = v_user_id;
    end if;

    -- Concessions actives
    select coalesce(jsonb_agg(jsonb_build_object(
        'node_id', n.id,
        'nom', n.nom,
        'mode', n.mode,
        'operador', n.operador,
        'viatgers_feiner', n.viatgers_feiner,
        'ingres_diari', public.ingres_diari(n.viatgers_feiner),
        'caduca_el', c.caduca_el,
        'dies_restants', case when c.regal then null else greatest(0, ceil(extract(epoch from (c.caduca_el - now())) / 86400.0)::int) end,
        'regal', c.regal
    ) order by c.regal, c.caduca_el asc), '[]'::jsonb)
    into v_concessions_actives
    from public.concessions c
    join public.nodes n on n.id = c.node_id
    where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now();

    -- Ingrés diari estimat de concessions
    select coalesce(sum(public.ingres_diari(n.viatgers_feiner)), 0)
    into v_ingres_concessions
    from public.concessions c
    join public.nodes n on n.id = c.node_id
    where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now();

    -- Concessions caducades
    select coalesce(jsonb_agg(jsonb_build_object(
        'node_id', n.id,
        'nom', n.nom,
        'mode', n.mode,
        'operador', n.operador,
        'viatgers_feiner', n.viatgers_feiner,
        'caduca_el', c.caduca_el
    ) order by c.caduca_el desc), '[]'::jsonb)
    into v_concessions_caducades
    from public.concessions c
    join public.nodes n on n.id = c.node_id
    where c.jugador_id = v_user_id and (not c.activa or c.caduca_el < now());

    -- Accions
    select coalesce(jsonb_agg(jsonb_build_object(
        'node_id', n.id,
        'nom', n.nom,
        'mode', n.mode,
        'operador', n.operador,
        'quantitat', a.quantitat,
        'dividend_diari', round(((public.ingres_diari(n.viatgers_feiner)::double precision / 100.0) * a.quantitat)::numeric, 2),
        'darrera_visita_el', a.darrera_visita_el,
        'paga_dividend', a.darrera_visita_el >= (now() - interval '30 days')
    ) order by a.quantitat desc), '[]'::jsonb)
    into v_accions
    from public.accions a
    join public.nodes n on n.id = a.node_id
    where a.jugador_id = v_user_id;

    -- Ingrés diari estimat d'accions
    select coalesce(sum(round(((public.ingres_diari(n.viatgers_feiner)::double precision / 100.0) * a.quantitat)::numeric, 2)), 0)
    into v_ingres_accions
    from public.accions a
    join public.nodes n on n.id = a.node_id
    where a.jugador_id = v_user_id and a.darrera_visita_el >= (now() - interval '30 days');

    -- Darrers moviments
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', m.id,
        'concepte', m.concepte,
        'import', m.import,
        'node_id', m.node_id,
        'creat_el', m.creat_el
    ) order by m.creat_el desc), '[]'::jsonb)
    into v_moviments
    from (
        select id, concepte, import, node_id, creat_el
        from public.moviments
        where jugador_id = v_user_id
        order by creat_el desc
        limit 20
    ) m;

    return jsonb_build_object(
        'saldo', v_perfil.saldo,
        'prestigi', v_perfil.prestigi,
        'nivell', v_perfil.nivell,
        'maxim_concessions', 10 + 2 * v_perfil.nivell,
        'concessions_llicencia', (
            select count(*) from public.concessions c
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now() and not c.regal
        ),
        'concessions_actives', v_concessions_actives,
        'concessions_caducades', v_concessions_caducades,
        'accions', v_accions,
        'moviments', v_moviments,
        'ingres_estimat_dia', v_ingres_concessions + v_ingres_accions
    );
end;
$$;
--    nodes_a_prop_jugador: torna si la concessió és de regal (canvia el tipus de retorn: cal esborrar-la abans).
drop function if exists public.nodes_a_prop_jugador(double precision, double precision, double precision, integer);
create or replace function public.nodes_a_prop_jugador(
    lat double precision,
    lon double precision,
    radi_metres double precision default 500,
    maxim integer default 50
)
returns table (
    id text,
    nom text,
    mode public.mode_transport,
    operador text,
    lat double precision,
    lon double precision,
    linies text[],
    viatgers_feiner numeric,
    font_afluencia public.font_afluencia,
    distancia_metres double precision,
    categoria text,
    preu numeric,
    ingres_diari numeric,
    te_concessio boolean,
    concessio_caducada boolean,
    dies_restants integer,
    accions_jugador integer,
    accions_disponibles integer,
    regal boolean
)
language sql
stable
security invoker
set search_path = ''
as $$
    with punt as (
        select extensions.st_setsrid(extensions.st_makepoint(lon, lat), 4326)::extensions.geography as g
    ),
    accions_totals as (
        select a.node_id, coalesce(sum(a.quantitat), 0)::int as total
        from public.accions a
        group by a.node_id
    )
    select n.id, n.nom, n.mode, n.operador,
           extensions.st_y(n.posicio::extensions.geometry) as lat,
           extensions.st_x(n.posicio::extensions.geometry) as lon,
           n.linies, n.viatgers_feiner, n.font_afluencia,
           extensions.st_distance(n.posicio, punt.g) as distancia_metres,
           case when n.viatgers_feiner >= 20000 then 'gran_estacio' else 'parada' end as categoria,
           case when n.viatgers_feiner >= 20000 then public.preu_accio(n.viatgers_feiner)
                else public.preu_concessio(n.viatgers_feiner) end as preu,
           public.ingres_diari(n.viatgers_feiner) as ingres_diari,
           (c.id is not null and c.activa and c.caduca_el >= now()) as te_concessio,
           (c.id is not null and (not c.activa or c.caduca_el < now())) as concessio_caducada,
           case when c.id is not null and not c.regal then greatest(0, ceil(extract(epoch from (c.caduca_el - now())) / 86400.0)::int) else null end as dies_restants,
           coalesce(a.quantitat, 0)::int as accions_jugador,
           case when n.viatgers_feiner >= 20000 then (100 - coalesce(tot.total, 0)) else 0 end as accions_disponibles,
           coalesce(c.regal, false) as regal
    from public.nodes n
    cross join punt
    left join public.concessions c on c.node_id = n.id and c.jugador_id = auth.uid()
    left join public.accions a on a.node_id = n.id and a.jugador_id = auth.uid()
    left join accions_totals tot on tot.node_id = n.id
    where extensions.st_dwithin(n.posicio, punt.g, least(radi_metres, 5000))
    order by extensions.st_distance(n.posicio, punt.g)
    limit least(greatest(maxim, 1), 200);
$$;
-- 7. Permisos.
revoke execute on function public.ofertes_regal_de(uuid) from public, anon, authenticated;
revoke execute on function public.ofertes_regal() from public, anon;
revoke execute on function public.tria_regal(text[]) from public, anon;
grant execute on function public.ofertes_regal() to authenticated;
grant execute on function public.tria_regal(text[]) to authenticated;
grant execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) to authenticated;
grant execute on function public.estat_imperi() to authenticated;
grant execute on function public.nodes_a_prop_jugador(double precision, double precision, double precision, integer) to anon, authenticated;
