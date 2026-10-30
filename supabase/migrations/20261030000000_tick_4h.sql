-- Tic econòmic cada 4 hores (substitueix el tic diari únic).
--
-- Cada tic liquida 1/6 de l'ingrés diari: el jugador veu créixer el saldo durant el dia. La lògica és la del
-- tic diari de la fase 4 (concessions, bo de cohesió i dividends), ara en una sola funció amb una fracció.
-- Els festius no es distingeixen encara: dissabte i diumenge compten com a no feiners (factor 0,6).
--
-- També tanca un forat: les funcions de liquidació eren executables per qualsevol usuari (PostgreSQL dona
-- EXECUTE a PUBLIC per defecte), i un jugador podia cobrar un dia sencer quan volgués cridant tic_diari.

-- 1. Liquidació d'un període (fracció del dia).
create or replace function public.liquida_periode(p_feiner boolean, p_fraccio numeric)
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
    v_concepte text;
begin
    if p_fraccio is null or p_fraccio <= 0 or p_fraccio > 1 then
        raise exception 'La fracció del període ha de ser entre 0 i 1 (és %).', p_fraccio;
    end if;

    v_factor_dia := case when p_feiner then 1.0 else 0.6 end;

    -- 1. Marcar caducades les concessions expirades.
    update public.concessions
    set activa = false
    where activa = true and caduca_el < now();

    -- 2. Liquidació per a cada jugador.
    for v_usuari in select id from public.perfils loop
        v_ingres_concessions := 0;
        v_ingres_accions := 0;

        -- Concessions actives: ingrés diari (amb el bo de cohesió) per la fracció del període.
        for v_node in
            select n.viatgers_feiner, n.comarca, m.densitat
            from public.concessions c
            join public.nodes n on n.id = c.node_id
            left join public.municipis m on m.codi = n.municipi
            where c.jugador_id = v_usuari.id and c.activa and c.caduca_el >= now()
        loop
            v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
            v_ingres_node := public.ingres_diari(v_viatgers::numeric);

            -- Bo de Cohesió Territorial (GDD 4.6): municipi < 20 hab/km² o comarques de l'Alt Pirineu i Aran.
            if (v_node.densitat is not null and v_node.densitat < 20) or (v_node.comarca in ('04', '05', '15', '25', '26', '39')) then
                v_ingres_node := greatest(v_ingres_node, 3.00);
            end if;

            v_ingres_concessions := v_ingres_concessions + round(v_ingres_node * p_fraccio, 2);
        end loop;

        -- Dividends d'accions (només si s'ha visitat l'estació en els darrers 30 dies).
        for v_node in
            select a.quantitat, n.viatgers_feiner
            from public.accions a
            join public.nodes n on n.id = a.node_id
            where a.jugador_id = v_usuari.id and a.darrera_visita_el >= (now() - interval '30 days')
        loop
            v_viatgers := (v_node.viatgers_feiner::double precision) * v_factor_dia;
            v_ingres_node := round(((public.ingres_diari(v_viatgers::numeric)::double precision / 100.0) * v_node.quantitat)::numeric, 2);
            v_ingres_accions := v_ingres_accions + round(v_ingres_node * p_fraccio, 2);
        end loop;

        v_ingres_total := v_ingres_concessions + v_ingres_accions;

        if v_ingres_total > 0 then
            update public.perfils
            set saldo = saldo + v_ingres_total,
                actualitzat_el = now()
            where id = v_usuari.id;

            v_concepte := case when p_fraccio = 1 then 'Liquidació diària' else 'Liquidació de 4 h' end
                || ' (' || case when p_feiner then 'feiner' else 'festiu' end || ')';

            insert into public.moviments (jugador_id, concepte, import)
            values (v_usuari.id, v_concepte, v_ingres_total);

            v_total_jugadors := v_total_jugadors + 1;
            v_total_catalins := v_total_catalins + v_ingres_total;
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

comment on function public.liquida_periode is 'Liquida una fracció del dia (1 = dia sencer, 1/6 = 4 h): caduca concessions, suma ingressos i dividends i aplica el bo de cohesió.';

-- 2. El tic diari es manté (mateixa signatura) però ara passa per liquida_periode.
create or replace function public.tic_diari(p_feiner boolean default true)
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select public.liquida_periode(p_feiner, 1);
$$;

-- 3. Tic de 4 hores: decideix si avui és feiner segons l'hora de Catalunya.
--    Una primera versió (mai fusionada correctament) retornava void; cal treure-la abans.
drop function if exists public.tic_4h();

create or replace function public.tic_4h()
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select public.liquida_periode(
        extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5,
        1.0 / 6
    );
$$;

comment on function public.tic_4h is 'Tic econòmic cada 4 hores (pg_cron): liquida 1/6 de l''ingrés diari.';

-- 4. Permisos: només el servidor (pg_cron i service_role) pot liquidar.
revoke execute on function public.liquida_periode(boolean, numeric) from public, anon, authenticated;
revoke execute on function public.tic_diari(boolean) from public, anon, authenticated;
revoke execute on function public.tic_4h() from public, anon, authenticated;
grant execute on function public.liquida_periode(boolean, numeric) to service_role;
grant execute on function public.tic_diari(boolean) to service_role;
grant execute on function public.tic_4h() to service_role;

-- 5. Planificació amb pg_cron (cada 4 hores, en UTC). Si hi havia un job del tic diari, es treu per no
--    pagar dues vegades. Si pg_cron no està activat (per exemple, al Supabase local), només avisa.
do $cron$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.unschedule(jobid)
        from cron.job
        where command ilike '%tic_diari%' or command ilike '%tic_4h%' or jobname in ('tic_4h', 'tick_4h');

        perform cron.schedule('tic_4h', '0 */4 * * *', 'select public.tic_4h();');
    else
        raise notice 'pg_cron no està activat: activa''l (Database → Extensions) i torna a executar aquesta migració.';
    end if;
end;
$cron$;
