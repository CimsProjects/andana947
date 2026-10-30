-- Cobraments visibles: quan arriba el pròxim cobrament, quant serà, i què s'ha cobrat mentre el jugador no hi era.
--
-- Tot ho calcula el servidor. L'app només ho mostra: un compte enrere fins al pròxim tic de 4 h i, en tornar a
-- entrar, una animació amb la suma dels cobraments que encara no ha vist. Qui ho ha vist es desa al perfil, de
-- manera que l'animació no es repeteix en un altre mòbil.

-- 1. Els cobraments es marquen al llibre de moviments, per no dependre del text del concepte.
alter table public.moviments
    add column if not exists tipus text;

comment on column public.moviments.tipus is 'Tipus d''apunt. ''cobrament'' per a les liquidacions periòdiques; null per a la resta.';

create index if not exists moviments_cobraments_idx
    on public.moviments (jugador_id, creat_el)
    where tipus = 'cobrament';

-- 2. Fins a quin moment el jugador ha vist els cobraments. Els jugadors que ja existeixen comencen sense pendents.
alter table public.perfils
    add column if not exists cobraments_vistos_fins timestamptz not null default now();

comment on column public.perfils.cobraments_vistos_fins is 'Els cobraments posteriors a aquest moment encara no s''han mostrat al jugador.';

-- 3. Durada d'un període de cobrament. Ha de coincidir amb el job tic_4h de pg_cron ('0 */4 * * *', en UTC).
create or replace function public.periode_cobrament_segons()
returns integer
language sql
immutable
set search_path = ''
as $$
    select 4 * 3600;
$$;

-- 4. Ingrés d'un jugador per a una fracció del dia. És l'única fórmula: la fan servir la liquidació i la previsió.
create or replace function public.ingres_jugador(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns numeric
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_factor_dia double precision;
    v_node record;
    v_total numeric(12, 2) := 0;
    v_viatgers double precision;
    v_ingres_node numeric(12, 2);
begin
    v_factor_dia := case when p_feiner then 1.0 else 0.6 end;

    -- Concessions actives, amb el Bo de Cohesió Territorial (GDD 4.6).
    for v_node in
        select n.viatgers_feiner, n.comarca, m.densitat
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        left join public.municipis m on m.codi = n.municipi
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    loop
        v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
        v_ingres_node := public.ingres_diari(v_viatgers::numeric);

        if (v_node.densitat is not null and v_node.densitat < 20) or (v_node.comarca in ('04', '05', '15', '25', '26', '39')) then
            v_ingres_node := greatest(v_ingres_node, 3.00);
        end if;

        v_total := v_total + round(v_ingres_node * p_fraccio, 2);
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

comment on function public.ingres_jugador is 'Ingrés d''un jugador (concessions, bo de cohesió i dividends) per a una fracció del dia.';

-- 5. La liquidació fa servir ingres_jugador i marca els moviments com a cobrament.
create or replace function public.liquida_periode(p_feiner boolean, p_fraccio numeric)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_usuari record;
    v_total_jugadors integer := 0;
    v_total_catalins numeric(12, 2) := 0;
    v_ingres numeric(12, 2);
    v_concepte text;
begin
    if p_fraccio is null or p_fraccio <= 0 or p_fraccio > 1 then
        raise exception 'La fracció del període ha de ser entre 0 i 1 (és %).', p_fraccio;
    end if;

    -- Marcar caducades les concessions expirades.
    update public.concessions
    set activa = false
    where activa = true and caduca_el < now();

    v_concepte := case when p_fraccio = 1 then 'Liquidació diària' else 'Liquidació de 4 h' end
        || ' (' || case when p_feiner then 'feiner' else 'festiu' end || ')';

    for v_usuari in select id from public.perfils loop
        v_ingres := public.ingres_jugador(v_usuari.id, p_feiner, p_fraccio);

        if v_ingres > 0 then
            update public.perfils
            set saldo = saldo + v_ingres,
                actualitzat_el = now()
            where id = v_usuari.id;

            insert into public.moviments (jugador_id, concepte, import, tipus)
            values (v_usuari.id, v_concepte, v_ingres, 'cobrament');

            v_total_jugadors := v_total_jugadors + 1;
            v_total_catalins := v_total_catalins + v_ingres;
        end if;
    end loop;

    return jsonb_build_object(
        'status', 'ok',
        'jugadors_liquidats', v_total_jugadors,
        'catalins_repartits', v_total_catalins,
        'fraccio', p_fraccio,
        'tipus_dia', case when p_feiner then 'feiner' else 'no_feiner' end
    );
end;
$$;

-- 6. Estat dels cobraments del jugador autenticat.
--    'ara' és l'hora del servidor: l'app compta el temps restant a partir d'aquí i no del rellotge del mòbil.
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
    v_feiner := extract(isodow from (v_proper at time zone 'Europe/Madrid')) between 1 and 5;

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
        'cobrament_previst', public.ingres_jugador(v_user_id, v_feiner, 1.0 / 6),
        'saldo', v_saldo,
        'pendent_total', v_pendent_total,
        'pendent_nombre', v_pendent_nombre
    );
end;
$$;

comment on function public.estat_cobraments is 'Pròxim cobrament (hora i import previst) i cobraments que el jugador encara no ha vist.';

-- 7. El jugador ha vist els cobraments fins a p_fins (l''«ara» que li va tornar estat_cobraments).
--    Mai es mou enrere ni més enllà d'ara: un cobrament arribat després de la consulta seguirà pendent.
create or replace function public.marca_cobraments_vistos(p_fins timestamptz)
returns void
language sql
security definer
set search_path = ''
as $$
    update public.perfils
    set cobraments_vistos_fins = greatest(cobraments_vistos_fins, least(p_fins, now()))
    where id = auth.uid();
$$;

comment on function public.marca_cobraments_vistos is 'Marca com a vistos els cobraments fins a un moment (com a màxim, ara).';

-- 8. Permisos.
revoke execute on function public.ingres_jugador(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.liquida_periode(boolean, numeric) from public, anon, authenticated;
revoke execute on function public.estat_cobraments() from public, anon;
revoke execute on function public.marca_cobraments_vistos(timestamptz) from public, anon;
grant execute on function public.ingres_jugador(uuid, boolean, numeric) to service_role;
grant execute on function public.liquida_periode(boolean, numeric) to service_role;
grant execute on function public.estat_cobraments() to authenticated;
grant execute on function public.marca_cobraments_vistos(timestamptz) to authenticated;
