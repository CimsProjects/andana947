-- Es cobra per cada tren o bus que arriba a una andana del jugador (GDD 4.8).
--
-- La base de dades no té l'horari de cada parada, així que el dia de servei s'estima de 05:00 a 24:00 (hora de
-- Catalunya) [estimat] i les arribades s'hi reparteixen igual: cada cobrament de 4 h paga la part del dia de servei que
-- cau dins del període. De matinada no es cobra; de dia, una mica més. Un dia sencer dona el mateix que abans.
--
-- Per a l'avís de l'app («han pujat X persones · +Y €»), el servidor dona, per a cada andana del jugador, les persones
-- i els diners de cada arribada: els viatgers i l'ingrés del dia repartits entre les expedicions que hi paren [estimat].
-- Els valors són a dades/configuracio_joc.json (bloc «economia»: serveiIniciHora i serveiFiHora).

-- 1. Hores de servei. Han de coincidir amb dades/configuracio_joc.json (tests a supabase/tests).
create or replace function public.config_servei()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object('inici_hora', 5, 'fi_hora', 24);
$$;

-- 2. La part del dia de servei que cau entre dos moments (de 0 a 1 per dia). Sumada per a tots els períodes d'un dia,
--    dona 1, també els dies del canvi d'hora.
create or replace function public.fraccio_servei(p_inici timestamptz, p_fi timestamptz)
returns numeric
language sql
stable
set search_path = ''
as $$
    with cfg as (
        select (public.config_servei() ->> 'inici_hora')::int as inici,
               (public.config_servei() ->> 'fi_hora')::int as fi
    ),
    dies as (
        select d::date as dia
        from generate_series(
            (p_inici at time zone 'Europe/Madrid')::date - 1,
            (p_fi at time zone 'Europe/Madrid')::date,
            interval '1 day'
        ) d
    ),
    servei as (
        select (dies.dia + make_interval(hours => cfg.inici)) at time zone 'Europe/Madrid' as des_de,
               (dies.dia + make_interval(hours => cfg.fi)) at time zone 'Europe/Madrid' as fins_a,
               cfg.fi - cfg.inici as hores
        from dies, cfg
    )
    select coalesce(round(sum(
        greatest(0, extract(epoch from (least(p_fi, s.fins_a) - greatest(p_inici, s.des_de))))
        / (s.hores * 3600.0)
    ), 6), 0)
    from servei s;
$$;

comment on function public.fraccio_servei is 'Part del dia de servei (05:00–24:00, estimat) que cau entre dos moments.';

-- 3. L'ingrés diari d'una concessió, amb el Bo de Cohesió Territorial (GDD 4.6). Una sola fórmula per al cobrament i
--    per a l'avís de cada arribada.
create or replace function public.ingres_diari_concessio(p_node text, p_factor_dia double precision)
returns numeric
language sql
stable
set search_path = ''
as $$
    select case
        when (m.densitat is not null and m.densitat < 20) or n.comarca in ('04', '05', '15', '25', '26', '39')
            then greatest(public.ingres_diari((n.viatgers_feiner::double precision * p_factor_dia)::numeric), 3.00)
        else public.ingres_diari((n.viatgers_feiner::double precision * p_factor_dia)::numeric)
    end
    from public.nodes n
    left join public.municipis m on m.codi = n.municipi
    where n.id = p_node;
$$;

-- 4. ingres_jugador fa servir la fórmula de les concessions.
create or replace function public.ingres_jugador(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns numeric
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_factor_dia double precision := case when p_feiner then 1.0 else 0.6 end;
    v_node record;
    v_total numeric(12, 2) := 0;
    v_viatgers double precision;
    v_ingres_node numeric(12, 2);
begin
    for v_node in
        select c.node_id
        from public.concessions c
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    loop
        v_total := v_total + round(public.ingres_diari_concessio(v_node.node_id, v_factor_dia) * p_fraccio, 2);
    end loop;

    -- Dividends d'accions (només si s'ha visitat l'estació en els darrers 30 dies).
    for v_node in
        select a.quantitat, n.viatgers_feiner
        from public.accions a
        join public.nodes n on n.id = a.node_id
        where a.jugador_id = p_jugador and a.darrera_visita_el >= (now() - interval '30 days')
    loop
        v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
        v_ingres_node := round(((public.ingres_diari(v_viatgers::numeric)::double precision / 100.0) * v_node.quantitat)::numeric, 2);
        v_total := v_total + round(v_ingres_node * p_fraccio, 2);
    end loop;

    return v_total;
end;
$$;

-- 5. El tic de 4 h paga les hores de servei del període que acaba ara.
create or replace function public.tic_4h()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_periode integer := public.periode_cobrament_segons();
    v_fi timestamptz := to_timestamp(floor(extract(epoch from now()) / v_periode) * v_periode);
    v_inici timestamptz := v_fi - make_interval(secs => v_periode);
    v_fraccio numeric := public.fraccio_servei(v_inici, v_fi);
    v_feiner boolean;
begin
    v_feiner := extract(isodow from ((v_inici + make_interval(secs => v_periode / 2)) at time zone 'Europe/Madrid'))
        between 1 and 5;
    if v_fraccio <= 0 then
        -- De matinada no passa cap tren: només es tanquen les concessions caducades.
        update public.concessions set activa = false where activa = true and caduca_el < now();
        return jsonb_build_object('status', 'ok', 'fraccio', 0, 'jugadors_liquidats', 0, 'catalins_repartits', 0);
    end if;
    return public.liquida_periode(v_feiner, least(v_fraccio, 1));
end;
$$;

comment on function public.tic_4h is 'Tic econòmic cada 4 hores (pg_cron): paga les hores de servei del període (05:00–24:00, estimat).';

-- 6. estat_cobraments: la previsió del pròxim cobrament compta les hores de servei.
create or replace function public.estat_cobraments()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid;
    v_ara timestamptz := now();
    v_periode integer := public.periode_cobrament_segons();
    v_darrer timestamptz;
    v_proper timestamptz;
    v_feiner boolean;
    v_fraccio numeric;
    v_vistos_fins timestamptz;
    v_saldo numeric(12, 2);
    v_pendent_total numeric(12, 2);
    v_pendent_nombre integer;
begin
    v_user_id := auth.uid();
    if v_user_id is null then
        return null;
    end if;

    select saldo, cobraments_vistos_fins into v_saldo, v_vistos_fins
    from public.perfils
    where id = v_user_id;

    if not found then
        return null;
    end if;

    v_darrer := to_timestamp(floor(extract(epoch from v_ara) / v_periode) * v_periode);
    v_proper := v_darrer + make_interval(secs => v_periode);
    -- El dia del cobrament és el del mig del període (hora de Catalunya), com a tic_4h.
    v_feiner := extract(isodow from ((v_proper - make_interval(secs => v_periode / 2)) at time zone 'Europe/Madrid')) between 1 and 5;
    v_fraccio := public.fraccio_servei(v_darrer, v_proper);

    select coalesce(sum(m.import), 0), count(*)
    into v_pendent_total, v_pendent_nombre
    from public.moviments m
    where m.jugador_id = v_user_id
      and m.tipus = 'cobrament'
      and m.creat_el > v_vistos_fins;

    return jsonb_build_object(
        'ara', v_ara,
        'darrer_cobrament_el', v_darrer,
        'proper_cobrament_el', v_proper,
        'periode_segons', v_periode,
        'cobrament_previst', public.ingres_jugador(v_user_id, v_feiner, v_fraccio),
        'fraccio_servei', v_fraccio,
        'saldo', v_saldo,
        'pendent_total', v_pendent_total,
        'pendent_nombre', v_pendent_nombre
    );
end;
$$;
-- 7. Les andanes del jugador, per a l'avís de cada arribada: on són, quines línies hi paren, i les persones i els
--    diners de cada arribada [estimat] (els viatgers i l'ingrés del dia repartits entre les expedicions d'un feiner).
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
begin
    if v_user_id is null then
        return null;
    end if;
    v_factor := case when extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5 then 1.0 else 0.6 end;
    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', n.id,
            'nom', n.nom,
            'mode', n.mode,
            'lat', extensions.st_y(n.posicio::extensions.geometry),
            'lon', extensions.st_x(n.posicio::extensions.geometry),
            'linies', n.linies,
            'regal', c.regal,
            'pujades', case when n.expedicions_feiner > 0
                then round((n.viatgers_feiner::double precision * v_factor / n.expedicions_feiner)::numeric) else 0 end,
            'valor', case when n.expedicions_feiner > 0
                then round(public.ingres_diari_concessio(n.id, v_factor) / n.expedicions_feiner, 2) else 0 end
        ) order by n.nom)
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
    ), '[]'::jsonb);
end;
$$;

comment on function public.andanes_jugador is 'Les andanes del jugador amb les persones i els diners de cada arribada (estimat).';

-- 8. Permisos.
revoke execute on function public.ingres_jugador(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.tic_4h() from public, anon, authenticated;
revoke execute on function public.andanes_jugador() from public, anon;
revoke execute on function public.estat_cobraments() from public, anon;
grant execute on function public.ingres_jugador(uuid, boolean, numeric) to service_role;
grant execute on function public.tic_4h() to service_role;
grant execute on function public.andanes_jugador() to authenticated;
grant execute on function public.estat_cobraments() to authenticated;
