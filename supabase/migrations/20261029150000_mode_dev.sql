-- Mode desenvolupador: permet fer check-in sense estar físicament a la parada.
-- Només per a proves internes. S'activa manualment des del panell de Supabase;
-- la política RLS impedeix que qualsevol client s'ho posi ell mateix a true.

-- 1. Columna mode_dev a la taula de perfils.
alter table public.perfils
    add column if not exists mode_dev boolean not null default false;

comment on column public.perfils.mode_dev is
    'Mode desenvolupador: omet totes les validacions de GPS del check-in. '
    'Activar-lo des del panell de Supabase; els clients no el poden modificar.';

-- 2. Política RLS: el client pot editar el seu perfil però no activar mode_dev.
drop policy if exists "jugador actualitza el seu perfil" on public.perfils;

create policy "jugador actualitza el seu perfil (sense mode_dev)"
    on public.perfils for update
    using (auth.uid() = id)
    with check (
        auth.uid() = id
        and mode_dev = (select p2.mode_dev from public.perfils p2 where p2.id = auth.uid())
    );

-- 3. Funció fer_checkin amb mode_dev integrat.
--    Idèntica a la de la migració anterior excepte en el bloc de validacions GPS,
--    que s'omet completament quan mode_dev és true.
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
        select c.id, c.caduca_el, c.activa
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
                    'missatge', 'La concessió de ' || v_node.nom || ' és vigent (' || v_dies_restants || ' dies restants).',
                    'saldo', v_perfil.saldo,
                    'prestigi', v_perfil.prestigi,
                    'nivell', v_perfil.nivell,
                    'dies_restants', v_dies_restants
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
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now();

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

comment on function public.fer_checkin is
    'Valida el check-in (distància PostGIS, precisió, velocitat) i adquireix o renova concessions o compra accions. '
    'Si el perfil té mode_dev = true, omet totes les validacions GPS, de saldo i de límit de llicència (per a proves internes). '
    'En mode_dev el cost de qualsevol acció és 0 per no distorsionar el saldo de proves.';

grant execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) to authenticated;
