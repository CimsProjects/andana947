-- Comprar vehicles (fase 5, GDD 4.7) i perfil assegurat.
--
-- Fins ara els vehicles només es llogaven. Ara també es poden comprar, al preu real a l'escala de la quota de concessió
-- (1 %, com tot el joc): un vehicle comprat no paga la quota del lloguer, només l'explotació per km, però costa molt
-- més d'entrada. Es pot vendre: val el preu menys el que s'ha amortitzat (lineal en la vida útil), i se'n cobra un
-- [marge_venda] (bloc «flota» de configuracio_joc.json). Els llogats i els comprats sumen per al màxim de la línia.
--
-- Perfil: els comptes d'abans del trigger de registre no en tenien; `assegura_perfil()` el crea en entrar a l'app.

-- 1. Configuració -------------------------------------------------------------------------------------------------------

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
        'marge_venda', 0.7,
        -- La quota de concessió (economia.quotaConcessio): totes les xifres reals, a l'1 %.
        'quota', 0.01
    );
$$;

-- 2. Vehicles propis ------------------------------------------------------------------------------------------------------

create table if not exists public.vehicles_propis (
    id bigserial primary key,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    linia text not null references public.flota_linies (linia) on delete cascade,
    model text not null references public.models_vehicle (id),
    preu numeric(12, 2) not null check (preu > 0),
    comprat_el timestamptz not null default now()
);
comment on table public.vehicles_propis is 'Vehicles comprats pel jugador: el preu que va pagar i quan, per al valor de venda.';
create index if not exists vehicles_propis_jugador_idx on public.vehicles_propis (jugador_id, linia);

alter table public.vehicles_propis enable row level security;
create policy "El jugador llegeix els seus vehicles" on public.vehicles_propis for select to authenticated using (jugador_id = auth.uid());
revoke insert, update, delete, truncate on public.vehicles_propis from anon, authenticated;

-- Abans de comprar o vendre, es cobra el que s'ha guanyat fins ara.
drop trigger if exists vehicles_propis_liquida on public.vehicles_propis;
create trigger vehicles_propis_liquida before insert or delete on public.vehicles_propis
    for each row execute function public.liquida_flota_abans();

-- El preu de comprar un vehicle per a una línia: el real del model, a l'1 %.
create or replace function public.preu_compra_vehicle(p_linia text)
returns numeric
language sql
stable
set search_path = ''
as $$
    select round(m.preu * (public.config_flota() ->> 'quota')::numeric, 2)
    from public.models_vehicle m where m.id = public.model_linia(p_linia);
$$;

-- El que es cobra venent un vehicle: el preu menys l'amortització (lineal en la vida útil), pel marge de venda.
create or replace function public.valor_venda(p_preu numeric, p_comprat_el timestamptz, p_mode public.mode_transport)
returns numeric
language sql
stable
set search_path = ''
as $$
    select round(p_preu * greatest(0, 1 - extract(epoch from (now() - p_comprat_el)) / 86400.0
        / (365.0 * case when p_mode = 'bus' then (public.config_flota() ->> 'vida_bus_anys')::numeric
                        else (public.config_flota() ->> 'vida_tren_anys')::numeric end))
        * (public.config_flota() ->> 'marge_venda')::numeric, 2);
$$;

-- Quants vehicles té el jugador a una línia (llogats i comprats).
create or replace function public.vehicles_a_linia(p_jugador uuid, p_linia text)
returns integer
language sql
stable
set search_path = ''
as $$
    select coalesce((select fj.vehicles from public.flota_jugador fj where fj.jugador_id = p_jugador and fj.linia = p_linia), 0)
         + (select count(*)::int from public.vehicles_propis vp where vp.jugador_id = p_jugador and vp.linia = p_linia);
$$;

-- 3. Llogar, comprar i vendre ---------------------------------------------------------------------------------------------

-- Llogar: el màxim de la línia compta també els comprats.
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
            'missatge', 'Per tenir vehicles de la ' || v_linia.codi || ' cal tenir-hi alguna andana.');
    end if;
    v_tinc := public.vehicles_a_linia(v_user_id, p_linia);
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

create or replace function public.compra_vehicle(p_linia text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_perfil record;
    v_linia record;
    v_maxim integer;
    v_preu numeric(12, 2);
    v_cost numeric(12, 2);
    v_saldo numeric(12, 2);
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
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
            'missatge', 'Per tenir vehicles de la ' || v_linia.codi || ' cal tenir-hi alguna andana.');
    end if;
    if public.vehicles_a_linia(v_user_id, p_linia) + 1 > v_maxim then
        return jsonb_build_object('status', 'limit_flota',
            'missatge', 'Amb les andanes que tens a la ' || v_linia.codi || ', hi pots tenir com a molt ' || v_maxim
                || ' vehicles. Compra més andanes de la línia!');
    end if;
    v_preu := public.preu_compra_vehicle(p_linia);
    v_cost := case when v_perfil.mode_dev then 0 else v_preu end;
    if v_perfil.saldo < v_cost then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: el vehicle costa ' || v_cost || ' €.');
    end if;

    -- Al mode desenvolupador és gratis, i es ven per 0 (no es poden fer diners comprant i venent).
    insert into public.vehicles_propis (jugador_id, linia, model, preu)
    values (v_user_id, p_linia, public.model_linia(p_linia), greatest(v_cost, 0.01));
    update public.perfils set saldo = saldo - v_cost, actualitzat_el = now() where id = v_user_id returning saldo into v_saldo;
    insert into public.moviments (jugador_id, concepte, import, tipus)
    values (v_user_id, 'Compra d''un vehicle de la ' || v_linia.codi, -v_cost, 'flota');

    return jsonb_build_object(
        'status', 'valid',
        'tipus_accio', 'flota',
        'missatge', 'Teu! Un vehicle nou per a la ' || v_linia.codi || ': no paga lloguer, només el que costa fer-lo córrer.',
        'cost', v_cost,
        'saldo', v_saldo
    );
end;
$$;

-- Vendre el vehicle comprat més nou d'una línia (el que val més).
create or replace function public.ven_vehicle(p_linia text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_vehicle record;
    v_valor numeric(12, 2);
    v_saldo numeric(12, 2);
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select vp.id, vp.preu, vp.comprat_el, f.mode, f.codi into v_vehicle
    from public.vehicles_propis vp join public.flota_linies f on f.linia = vp.linia
    where vp.jugador_id = v_user_id and vp.linia = p_linia
    order by vp.comprat_el desc limit 1
    for update of vp;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'No tens cap vehicle comprat en aquesta línia.');
    end if;
    v_valor := public.valor_venda(v_vehicle.preu, v_vehicle.comprat_el, v_vehicle.mode);
    delete from public.vehicles_propis where id = v_vehicle.id;
    update public.perfils set saldo = saldo + v_valor, actualitzat_el = now() where id = v_user_id returning saldo into v_saldo;
    if v_valor > 0 then
        insert into public.moviments (jugador_id, concepte, import, tipus)
        values (v_user_id, 'Venda d''un vehicle de la ' || v_vehicle.codi, v_valor, 'flota');
    end if;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'flota',
        'missatge', 'Venut per ' || v_valor || ' €.', 'saldo', v_saldo);
end;
$$;

-- 4. L'ingrés: els vehicles comprats no paguen la quota del lloguer ----------------------------------------------------

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
    linies as (
        select x.linia,
               coalesce((select fj.vehicles from public.flota_jugador fj where fj.jugador_id = p_jugador and fj.linia = x.linia), 0) as llogats,
               (select count(*) from public.vehicles_propis vp where vp.jugador_id = p_jugador and vp.linia = x.linia) as propis
        from (select fj.linia from public.flota_jugador fj where fj.jugador_id = p_jugador
              union select vp.linia from public.vehicles_propis vp where vp.jugador_id = p_jugador) x
    ),
    flota as (
        select coalesce(sum(
            (l.llogats + l.propis) * (e.ingres * public.factor_caos_linia(l.linia, p_moment) - e.explotacio)
            - l.llogats * e.quota), 0) as total
        from linies l
        cross join dia
        cross join lateral public.economia_vehicle(l.linia, dia.factor) e
    )
    select greatest((select total from andanes) + (select total from dividends) + (select total from flota), 0) * p_fraccio;
$$;

-- 5. La pantalla de flota: llogats i comprats ---------------------------------------------------------------------------

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
        propis as (
            select vp.linia, count(*)::int as n,
                   -- El que es cobraria venent el més nou (el que es ven primer).
                   (array_agg(public.valor_venda(vp.preu, vp.comprat_el, f.mode) order by vp.comprat_el desc))[1] as venda
            from public.vehicles_propis vp join public.flota_linies f on f.linia = vp.linia
            where vp.jugador_id = v_user_id
            group by vp.linia
        ),
        totes as materialized (
            -- Primer les línies del jugador, i només per a aquestes es calcula el model i l'economia.
            select fl.*, m.nom as model, m.preu as preu_model, m.font as font_model
            from (select linia from meves
                  union select fj.linia from public.flota_jugador fj where fj.jugador_id = v_user_id
                  union select linia from propis) x
            join public.flota_linies fl on fl.linia = x.linia
            cross join lateral (select public.model_linia(fl.linia) as id) mi
            join public.models_vehicle m on m.id = mi.id
        )
        select jsonb_agg(x.fila order by x.vehicles desc, x.andanes desc, x.codi)
        from (
            select fl.codi, coalesce(fj.vehicles, 0) + coalesce(p.n, 0) as vehicles, coalesce(a.andanes, 0) as andanes,
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
                       'propis', coalesce(p.n, 0),
                       'fianca', coalesce(fj.fianca, 0),
                       'model', fl.model,
                       'preu_model', fl.preu_model,
                       'font_model', fl.font_model,
                       'preu_compra', round(fl.preu_model * (public.config_flota() ->> 'quota')::numeric, 2),
                       'valor_venda', p.venda,
                       'ingres_dia', round(e.ingres, 2),
                       'cost_dia', round(e.quota + e.explotacio, 2),
                       'net_dia', round(e.ingres - e.quota - e.explotacio, 2),
                       'cost_dia_propi', round(e.explotacio, 2),
                       'net_dia_propi', round(e.ingres - e.explotacio, 2),
                       'fianca_vehicle', round((public.config_flota() ->> 'dies_fianca')::numeric * (e.quota + e.explotacio), 2),
                       'factor_caos', public.factor_caos_linia(fl.linia, now())
                   ) as fila
            from totes fl
            left join meves a on a.linia = fl.linia
            left join public.flota_jugador fj on fj.jugador_id = v_user_id and fj.linia = fl.linia
            left join propis p on p.linia = fl.linia
            cross join lateral public.economia_vehicle(fl.linia, 1.0) e
        ) x
    ), '[]'::jsonb);
end;
$$;

-- 6. Perfil assegurat ---------------------------------------------------------------------------------------------------

-- Crea el perfil del jugador si no en té (comptes d'abans del trigger de registre), amb el capital inicial.
create or replace function public.assegura_perfil()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        return false;
    end if;
    if exists (select 1 from public.perfils p where p.id = v_user_id) then
        return true;
    end if;
    insert into public.perfils (id, saldo, prestigi, nivell) values (v_user_id, 200.00, 0, 1) on conflict (id) do nothing;
    insert into public.moviments (jugador_id, concepte, import) values (v_user_id, 'Capital inicial d''operador', 200.00);
    return true;
end;
$$;

-- 7. Permisos -------------------------------------------------------------------------------------------------------------
revoke execute on function public.preu_compra_vehicle(text) from public, anon, authenticated;
revoke execute on function public.valor_venda(numeric, timestamptz, public.mode_transport) from public, anon, authenticated;
revoke execute on function public.vehicles_a_linia(uuid, text) from public, anon, authenticated;
revoke execute on function public.ingres_jugador_exacte(uuid, boolean, numeric, timestamptz) from public, anon, authenticated;

revoke execute on function public.compra_vehicle(text) from public, anon;
revoke execute on function public.ven_vehicle(text) from public, anon;
revoke execute on function public.assegura_perfil() from public, anon;
grant execute on function public.compra_vehicle(text) to authenticated;
grant execute on function public.ven_vehicle(text) to authenticated;
grant execute on function public.assegura_perfil() to authenticated;
