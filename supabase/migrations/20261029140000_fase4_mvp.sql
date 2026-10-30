-- Fase 4: MVP jugable — Perfils, concessions, accions, moviments, check-in i economia diària (GDD 4 i 7).
-- L'economia és autoritativa al servidor: el client envia la posició i el servidor valida i cobra.

-- 1. Perfil del jugador (saldo, prestigi i nivell)
create table public.perfils (
    id uuid primary key references auth.users (id) on delete cascade,
    saldo numeric(12, 2) not null default 200.00 check (saldo >= 0),
    prestigi integer not null default 0 check (prestigi >= 0),
    nivell integer not null default 1 check (nivell >= 1),
    creat_el timestamptz not null default now(),
    actualitzat_el timestamptz not null default now()
);

comment on table public.perfils is 'Cartera i progrés de cada jugador. Comença amb 200 € (catalins) de capital inicial (GDD 4 i configuracio_joc.json).';

-- 2. Concessions de parades (individuals, GDD 1.4 i 4.2)
create table public.concessions (
    id bigserial primary key,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    node_id text not null references public.nodes (id) on delete cascade,
    adquirida_el timestamptz not null default now(),
    caduca_el timestamptz not null default (now() + interval '14 days'),
    renovada_el timestamptz,
    activa boolean not null default true,
    constraint concessions_jugador_node_unique unique (jugador_id, node_id)
);

comment on table public.concessions is 'Concessions de parades de menys de 20.000 viatgers. Duren 14 dies i es renoven gratuïtament la segona setmana.';
create index concessions_jugador_idx on public.concessions (jugador_id);
create index concessions_node_idx on public.concessions (node_id);

-- 3. Accionariat de les grans estacions (compartit, GDD 1.4 i 4.3)
create table public.accions (
    node_id text not null references public.nodes (id) on delete cascade,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    quantitat integer not null check (quantitat > 0 and quantitat <= 20),
    darrera_visita_el timestamptz not null default now(),
    primary key (node_id, jugador_id)
);

comment on table public.accions is '100 accions per gran estació (>=20.000 viatgers). Màxim 20 per jugador. Deixen de pagar dividends si no es visiten en 30 dies.';
create index accions_jugador_idx on public.accions (jugador_id);
create index accions_node_idx on public.accions (node_id);

-- 4. Llibre de moviments comptables (GDD 4.8)
create table public.moviments (
    id bigserial primary key,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    concepte text not null,
    import numeric(12, 2) not null,
    node_id text references public.nodes (id) on delete set null,
    creat_el timestamptz not null default now()
);

comment on table public.moviments is 'Llibre d''apunts comptables. El saldo és la suma d''aquests moviments.';
create index moviments_jugador_idx on public.moviments (jugador_id, creat_el desc);

-- 5. Registre de check-ins (GDD 7.2 i 7.3)
create table public.checkins (
    id bigserial primary key,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    node_id text not null references public.nodes (id) on delete cascade,
    posicio extensions.geography(point, 4326) not null,
    precisio_metres double precision not null check (precisio_metres >= 0),
    velocitat_kmh double precision not null check (velocitat_kmh >= 0),
    resultat text not null,
    creat_el timestamptz not null default now()
);

comment on table public.checkins is 'Historial de check-ins fets pels jugadors per a validació de proximitat i control de velocitat.';
create index checkins_jugador_node_idx on public.checkins (jugador_id, node_id, creat_el desc);
create index checkins_jugador_temps_idx on public.checkins (jugador_id, creat_el desc);

-- 6. RLS
alter table public.perfils enable row level security;
alter table public.concessions enable row level security;
alter table public.accions enable row level security;
alter table public.moviments enable row level security;
alter table public.checkins enable row level security;

create policy "El jugador pot llegir el seu perfil" on public.perfils for select to authenticated using (id = auth.uid());
create policy "El jugador pot llegir les seves concessions" on public.concessions for select to authenticated using (jugador_id = auth.uid());
create policy "Tothom pot llegir les accions d'estacions" on public.accions for select to authenticated using (true);
create policy "El jugador pot llegir els seus moviments" on public.moviments for select to authenticated using (jugador_id = auth.uid());
create policy "El jugador pot llegir els seus check-ins" on public.checkins for select to authenticated using (jugador_id = auth.uid());

revoke insert, update, delete, truncate on public.perfils, public.concessions, public.accions, public.moviments, public.checkins from anon, authenticated;

-- 7. Trigger de creació automàtica de perfil en registrar-se un usuari a auth.users
create or replace function public.handle_nou_usuari()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.perfils (id, saldo, prestigi, nivell)
    values (new.id, 200.00, 0, 1)
    on conflict (id) do nothing;

    insert into public.moviments (jugador_id, concepte, import)
    values (new.id, 'Capital inicial d''operador', 200.00);

    return new;
end;
$$;

create or replace trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_nou_usuari();

-- 8. Fórmules econòmiques i de progrés (GDD 4 i 5)

-- Càlcul del nivell a partir dels punts de prestigi: 100 * (n-1)^1.5 punts per nivell n
create or replace function public.calcular_nivell(p_prestigi integer)
returns integer
language plpgsql
immutable
as $$
declare
    v_n integer := 1;
begin
    if p_prestigi is null or p_prestigi <= 0 then
        return 1;
    end if;
    while ceil(100.0 * power(v_n, 1.5)) <= p_prestigi loop
        v_n := v_n + 1;
    end loop;
    return v_n;
end;
$$;

-- Ingrés diari d'una concessió (GDD 4.1): t * q * P_ref * (P / P_ref)^α
create or replace function public.ingres_diari(p_viatgers numeric)
returns numeric
language plpgsql
immutable
as $$
declare
    v_relatiu double precision;
begin
    if p_viatgers is null or p_viatgers <= 0 then
        return 0.00;
    end if;
    v_relatiu := (p_viatgers::double precision) / 1000.0;
    return round((1.30 * 0.01 * 1000.0 * power(v_relatiu, 0.6))::numeric, 2);
end;
$$;

-- Preu d'una concessió (GDD 4.2): max(10, 10 * ingres_diari)
create or replace function public.preu_concessio(p_viatgers_feiner numeric)
returns numeric
language plpgsql
immutable
as $$
declare
    v_ingres double precision;
begin
    if p_viatgers_feiner is null or p_viatgers_feiner <= 0 then
        return 10.00;
    end if;
    v_ingres := (public.ingres_diari(p_viatgers_feiner))::double precision;
    return round(greatest(10.00::double precision, 10.0 * v_ingres)::numeric, 2);
end;
$$;

-- Preu per acció d'una gran estació: max(1, preu_concessio / 100)
create or replace function public.preu_accio(p_viatgers_feiner numeric)
returns numeric
language plpgsql
immutable
as $$
begin
    return round(greatest(1.00::double precision, (public.preu_concessio(p_viatgers_feiner)::double precision) / 100.0)::numeric, 2);
end;
$$;

-- 9. Check-in geolocalitzat amb validació i compra/renovació (GDD 7.2 i 7.3)
create or replace function public.fer_checkin(
    p_node_id text,
    p_lat double precision,
    p_lon double precision,
    p_precisio double precision,
    p_velocitat double precision
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid;
    v_node record;
    v_perfil record;
    v_punt extensions.geography;
    v_dist_metres double precision;
    v_radi double precision;
    v_sobrant double precision;
    v_darrer_checkin record;
    v_hores double precision;
    v_dist_km double precision;
    v_concessio record;
    v_accions_player integer;
    v_total_accions integer;
    v_comprar integer;
    v_cost numeric(12, 2);
    v_dies_restants integer;
    v_maxim_concessions integer;
    v_concessions_actives integer;
    v_prestigi_afegir integer := 0;
    v_nou_saldo numeric(12, 2);
    v_nou_prestigi integer;
    v_nou_nivell integer;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;

    -- Obtenir node
    select n.id, n.nom, n.mode, n.operador, n.posicio, n.viatgers_feiner, n.municipi, n.comarca
    into v_node
    from public.nodes n
    where n.id = p_node_id;

    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquest node no existeix.');
    end if;

    -- Assegurar perfil del jugador
    insert into public.perfils (id, saldo, prestigi, nivell)
    values (v_user_id, 200.00, 0, 1)
    on conflict (id) do nothing;

    select id, saldo, prestigi, nivell
    into v_perfil
    from public.perfils
    where id = v_user_id
    for update;

    -- Validació: màxim 1 acció/check-in per node i dia
    if exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id
          and c.node_id = p_node_id
          and c.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent')
          and c.creat_el >= date_trunc('day', now() at time zone 'Europe/Madrid') at time zone 'Europe/Madrid'
    ) then
        return jsonb_build_object('status', 'ja_fet_avui', 'missatge', 'Ja has fet check-in avui en aquest node.');
    end if;

    -- Validació: precisió del GPS (<= 50 m)
    if p_precisio > 50.0 then
        return jsonb_build_object('status', 'precisio_insuficient', 'missatge', 'La precisió del GPS és insuficient (> 50 m).');
    end if;

    -- Validació: velocitat en el moment del check-in (<= 10 km/h)
    if p_velocitat > 10.0 then
        return jsonb_build_object('status', 'en_moviment', 'missatge', 'Vas massa ràpid (> 10 km/h). El vehicle o tren ha d''estar aturat.');
    end if;

    -- Punt GPS del jugador
    v_punt := extensions.st_setsrid(extensions.st_makepoint(p_lon, p_lat), 4326)::extensions.geography;

    -- Validació: velocitat entre check-ins consecutius (<= 330 km/h)
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

    -- Validació: distància al node (150 m per a ferrocarril/metro, 60 m per a bus/tram)
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

    -- Comprovació si és el primer check-in en aquest node
    if not exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id and c.node_id = p_node_id and c.resultat not in ('massa_lluny', 'precisio_insuficient', 'en_moviment', 'velocitat_impossible')
    ) then
        v_prestigi_afegir := v_prestigi_afegir + 20;
    end if;

    -- Branques segons categoria: Gran Estació (>= 20.000 viatgers) o Parada
    if v_node.viatgers_feiner >= 20000 then
        -- ACCIONARIAT
        select coalesce(quantitat, 0) into v_accions_player
        from public.accions
        where node_id = p_node_id and jugador_id = v_user_id;

        select coalesce(sum(quantitat), 0) into v_total_accions
        from public.accions
        where node_id = p_node_id;

        if v_accions_player >= 20 then
            -- Ja té el límit de 20 accions: actualitza la visita per no perdre dividends
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

        if v_perfil.saldo < v_cost then
            return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou diners: calen ' || v_cost || ' €.');
        end if;

        -- Descompta saldo
        update public.perfils
        set saldo = saldo - v_cost,
            prestigi = prestigi + v_prestigi_afegir + (v_comprar * 2),
            nivell = public.calcular_nivell(prestigi + v_prestigi_afegir + (v_comprar * 2)),
            actualitzat_el = now()
        where id = v_user_id
        returning saldo, prestigi, nivell into v_nou_saldo, v_nou_prestigi, v_nou_nivell;

        -- Registra accions
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
        select c.id, c.caduca_el, c.activa
        into v_concessio
        from public.concessions c
        where c.node_id = p_node_id and c.jugador_id = v_user_id;

        if found then
            v_dies_restants := ceil(extract(epoch from (v_concessio.caduca_el - now())) / 86400.0);

            if v_concessio.activa and v_dies_restants >= 7 then
                -- Encara vigent en la primera setmana: no cal renovar
                insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
                values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, 'vigent');

                return jsonb_build_object(
                    'status', 'valid',
                    'tipus_accio', 'vigent',
                    'missatge', 'La concessió de ' || v_node.nom || ' és vigent (' || v_dies_restants || ' dies restants).',
                    'saldo', v_perfil.saldo,
                    'prestigi', v_perfil.prestigi,
                    'nivell', v_perfil.nivell,
                    'dies_restants', v_dies_restants
                );

            elsif v_concessio.activa and v_dies_restants < 7 then
                -- Finestra de renovació: 14 dies més gratuïts
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
                -- Caducada: es reactiva gratuïtament per 14 dies
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
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now();

            if v_concessions_actives >= v_maxim_concessions then
                return jsonb_build_object(
                    'status', 'limit_llicencia',
                    'missatge', 'Has assolit el límit de la llicència (' || v_maxim_concessions || ' concessions). Puja de nivell!'
                );
            end if;

            v_cost := public.preu_concessio(v_node.viatgers_feiner);
            if v_perfil.saldo < v_cost then
                return jsonb_build_object(
                    'status', 'sense_saldo',
                    'missatge', 'No tens prou diners: costa ' || v_cost || ' € (tens ' || v_perfil.saldo || ' €).'
                );
            end if;

            -- Bonificacions de prestigi per territori nou
            v_prestigi_afegir := v_prestigi_afegir + 30; -- Conquesta

            if v_node.municipi is not null and not exists (
                select 1 from public.concessions c join public.nodes n2 on n2.id = c.node_id
                where c.jugador_id = v_user_id and n2.municipi = v_node.municipi
            ) then
                v_prestigi_afegir := v_prestigi_afegir + 50; -- Primer node del municipi
            end if;

            if v_node.comarca is not null and not exists (
                select 1 from public.concessions c join public.nodes n2 on n2.id = c.node_id
                where c.jugador_id = v_user_id and n2.comarca = v_node.comarca
            ) then
                v_prestigi_afegir := v_prestigi_afegir + 200; -- Primer node de la comarca
            end if;

            -- Descomptar saldo i actualitzar prestigi
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

comment on function public.fer_checkin is 'Valida el check-in (distància PostGIS, precisió, velocitat) i adquireix o renova concessions o compra accions.';
grant execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) to authenticated;

-- 10. Consulta de l''estat de l''imperi del jugador
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
        'dies_restants', greatest(0, ceil(extract(epoch from (c.caduca_el - now())) / 86400.0)::int)
    ) order by c.caduca_el asc), '[]'::jsonb)
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
        'concessions_actives', v_concessions_actives,
        'concessions_caducades', v_concessions_caducades,
        'accions', v_accions,
        'moviments', v_moviments,
        'ingres_estimat_dia', v_ingres_concessions + v_ingres_accions
    );
end;
$$;

comment on function public.estat_imperi is 'Retorna l''estat complet de l''imperi del jugador autenticat: saldo, concessions, accions i moviments.';
grant execute on function public.estat_imperi() to authenticated;

-- 11. Nodes a prop del jugador, amb l''estat de concessió/accions (pantalla «A prop»)
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
    accions_disponibles integer
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
           case when c.id is not null then greatest(0, ceil(extract(epoch from (c.caduca_el - now())) / 86400.0)::int) else null end as dies_restants,
           coalesce(a.quantitat, 0)::int as accions_jugador,
           case when n.viatgers_feiner >= 20000 then (100 - coalesce(tot.total, 0)) else 0 end as accions_disponibles
    from public.nodes n
    cross join punt
    left join public.concessions c on c.node_id = n.id and c.jugador_id = auth.uid()
    left join public.accions a on a.node_id = n.id and a.jugador_id = auth.uid()
    left join accions_totals tot on tot.node_id = n.id
    where extensions.st_dwithin(n.posicio, punt.g, least(radi_metres, 5000))
    order by extensions.st_distance(n.posicio, punt.g)
    limit least(greatest(maxim, 1), 200);
$$;

comment on function public.nodes_a_prop_jugador is 'Retorna els nodes a prop d''un punt, enriquits amb la relació del jugador (concessió o accions).';
grant execute on function public.nodes_a_prop_jugador(double precision, double precision, double precision, integer) to anon, authenticated;

-- 12. Tic econòmic diari (GDD 4.8)
create or replace function public.tic_diari(p_feiner boolean default true)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_factor_dia double precision;
    v_usuari record;
    v_node record;
    v_total_jugadors integer := 0;
    v_total_catalins numeric(12, 2) := 0;
    v_ingres_concessions numeric(12, 2);
    v_ingres_accions numeric(12, 2);
    v_ingres_total numeric(12, 2);
    v_viatgers double precision;
    v_ingres_node numeric(12, 2);
begin
    v_factor_dia := case when p_feiner then 1.0 else 0.6 end;

    -- 1. Marcar caducades les concessions expirades
    update public.concessions
    set activa = false
    where activa = true and caduca_el < now();

    -- 2. Liquidació per a cada jugador amb compte
    for v_usuari in select id, saldo from public.perfils loop
        v_ingres_concessions := 0;
        v_ingres_accions := 0;

        -- Liquidar concessions actives del jugador
        for v_node in
            select c.id as concessio_id, n.id as node_id, n.nom, n.viatgers_feiner, n.comarca, m.densitat
            from public.concessions c
            join public.nodes n on n.id = c.node_id
            left join public.municipis m on m.codi = n.municipi
            where c.jugador_id = v_usuari.id and c.activa and c.caduca_el >= now()
        loop
            v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
            v_ingres_node := public.ingres_diari(v_viatgers::numeric);

            -- Bo de Cohesió Territorial (GDD 4.6): municipi < 20 hab/km² o comarques de l'Alt Pirineu i Aran
            if (v_node.densitat is not null and v_node.densitat < 20) or (v_node.comarca in ('04', '05', '15', '25', '26', '39')) then
                v_ingres_node := greatest(v_ingres_node, 3.00);
            end if;

            v_ingres_concessions := v_ingres_concessions + v_ingres_node;
        end loop;

        -- Liquidar dividends d'accions (només si s'ha visitat en els darrers 30 dies)
        for v_node in
            select a.quantitat, n.nom, n.viatgers_feiner
            from public.accions a
            join public.nodes n on n.id = a.node_id
            where a.jugador_id = v_usuari.id and a.darrera_visita_el >= (now() - interval '30 days')
        loop
            v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
            v_ingres_node := round(((public.ingres_diari(v_viatgers::numeric)::double precision / 100.0) * v_node.quantitat)::numeric, 2);
            v_ingres_accions := v_ingres_accions + v_ingres_node;
        end loop;

        v_ingres_total := v_ingres_concessions + v_ingres_accions;

        if v_ingres_total > 0 then
            update public.perfils
            set saldo = saldo + v_ingres_total,
                actualitzat_el = now()
            where id = v_usuari.id;

            insert into public.moviments (jugador_id, concepte, import)
            values (v_usuari.id, 'Liquidació diària (' || case when p_feiner then 'feiner' else 'festiu' end || ')', v_ingres_total);

            v_total_jugadors := v_total_jugadors + 1;
            v_total_catalins := v_total_catalins + v_ingres_total;
        end if;
    end loop;

    return jsonb_build_object(
        'status', 'ok',
        'jugadors_liquidats', v_total_jugadors,
        'catalins_repartits', v_total_catalins,
        'tipus_dia', case when p_feiner then 'feiner' else 'no_feiner' end
    );
end;
$$;

comment on function public.tic_diari is 'Liquidació econòmica diària a les 04:00 (GDD 4.8): caduca concessions, suma ingressos i dividends i aplica el bo de cohesió.';
grant execute on function public.tic_diari(boolean) to service_role;
