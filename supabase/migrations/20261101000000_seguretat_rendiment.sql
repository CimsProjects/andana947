-- Revisió de seguretat i rendiment abans de la beta (fase 8, docs/seguretat.md).
--
-- Seguretat:
--  * Cap client anònim no pot cridar funcions de joc, ni ningú la funció del trigger de registre.
--  * Les funcions que encara no fixaven el search_path el fixen.
--  * Es treu la política que deixava actualitzar el perfil (l'app no ho fa; si algun dia es donés permís d'escriptura,
--    hauria permès canviar el saldo).
--  * Els noms de les cooperatives, com els dels jugadors: lletres, xifres i espais.
--  * Juntes: en aprovar una millora, es bloquegen els perfils dels qui la paguen.
--  * El rànquing no compta els comptes amb el mode desenvolupador.
--  * cobra_arribades: com a molt un cobrament cada 20 segons per jugador.
-- Rendiment (500 jugadors simulats amb dades reals):
--  * L'experiència de maestria d'un cobrament es suma d'un sol cop (abans, fila a fila).
--  * Sense el compilador JIT a les funcions del joc: en consultes petites tarda més a compilar que a executar-les
--    (estat_flota: de 830 a 25 ms).

-- 1. Permisos ------------------------------------------------------------------------------------------------------------
revoke execute on function public.estat_imperi() from public, anon;
revoke execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) from public, anon;
revoke execute on function public.handle_nou_usuari() from public, anon, authenticated;
grant execute on function public.estat_imperi() to authenticated;
grant execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) to authenticated;

alter function public.calcular_nivell(integer) set search_path = '';
alter function public.ingres_diari(numeric) set search_path = '';
alter function public.preu_accio(numeric) set search_path = '';
alter function public.preu_concessio(numeric) set search_path = '';

drop policy if exists "jugador actualitza el seu perfil (sense mode_dev)" on public.perfils;
revoke insert, update, delete, truncate on public.perfils from anon, authenticated;

-- 2. Cooperatives, juntes, rànquing i cobrament ------------------------------------------------------------------------

create or replace function public.crea_cooperativa(p_nom text, p_comarca text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_nom text := btrim(coalesce(p_nom, ''));
    v_id bigint;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    if exists (select 1 from public.membres_cooperativa m where m.jugador_id = v_user_id) then
        return jsonb_build_object('status', 'error', 'missatge', 'Ja ets d''una cooperativa: surt-ne primer.');
    end if;
    v_nom := regexp_replace(v_nom, '\s+', ' ', 'g');
    if char_length(v_nom) not between 3 and 30 or v_nom !~ '^[[:alnum:] ._·''-]+$' then
        return jsonb_build_object('status', 'error', 'missatge', 'El nom ha de tenir entre 3 i 30 lletres, xifres o espais.');
    end if;
    if p_comarca is null or p_comarca not in (select c.comarca from public.comarques_jugador(v_user_id) c) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només pots fundar una cooperativa en una comarca on tinguis andanes.');
    end if;
    if exists (select 1 from public.cooperatives c where lower(c.nom) = lower(v_nom)) then
        return jsonb_build_object('status', 'error', 'missatge', 'Ja hi ha una cooperativa amb aquest nom.');
    end if;
    insert into public.cooperatives (nom, comarca) values (v_nom, p_comarca) returning id into v_id;
    insert into public.membres_cooperativa (jugador_id, cooperativa, rol) values (v_user_id, v_id, 'fundador');
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'cooperativa', 'missatge', 'Has fundat ' || v_nom || '!');
end;
$$;

create or replace function public.resol_proposta(p_proposta bigint)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_p record;
    v_venudes integer;
    v_favor integer;
    v_vot record;
    v_part numeric(12, 2);
    v_nom text;
begin
    select * into v_p from public.propostes_junta where id = p_proposta for update;
    if not found or v_p.estat <> 'oberta' then
        return coalesce(v_p.estat, 'error');
    end if;
    -- Els perfils dels qui hi voten a favor, bloquejats en ordre (que no canviïn mentre es paga, ni s'esperin entre ells).
    perform 1 from public.perfils p
    where p.id in (select v.jugador_id from public.vots_junta v where v.proposta = p_proposta and v.a_favor)
    order by p.id
    for update;
    select coalesce(sum(a.quantitat), 0) into v_venudes from public.accions a where a.node_id = v_p.node_id;
    select coalesce(sum(a.quantitat), 0) into v_favor
    from public.vots_junta v join public.accions a on a.jugador_id = v.jugador_id and a.node_id = v_p.node_id
    where v.proposta = p_proposta and v.a_favor;
    if v_venudes = 0 or v_favor <= v_venudes * (public.config_social() ->> 'majoria')::numeric then
        return 'oberta';
    end if;
    -- Tots els qui hi voten a favor han de poder pagar la seva part.
    if exists (
        select 1 from public.vots_junta v
        join public.accions a on a.jugador_id = v.jugador_id and a.node_id = v_p.node_id
        join public.perfils p on p.id = v.jugador_id
        where v.proposta = p_proposta and v.a_favor and p.saldo < round(v_p.cost * a.quantitat / v_favor, 2)
    ) then
        return 'oberta';
    end if;
    select n.nom into v_nom from public.nodes n where n.id = v_p.node_id;
    for v_vot in
        select v.jugador_id, a.quantitat from public.vots_junta v
        join public.accions a on a.jugador_id = v.jugador_id and a.node_id = v_p.node_id
        where v.proposta = p_proposta and v.a_favor
    loop
        v_part := round(v_p.cost * v_vot.quantitat / v_favor, 2);
        perform public.liquida_jugador(v_vot.jugador_id);
        update public.perfils set saldo = saldo - v_part, actualitzat_el = now() where id = v_vot.jugador_id;
        insert into public.moviments (jugador_id, concepte, import, node_id, tipus)
        values (v_vot.jugador_id, 'Junta de ' || v_nom || ': millora aprovada', -v_part, v_p.node_id, 'junta');
    end loop;
    insert into public.millores_estacio (node_id, millora) values (v_p.node_id, v_p.millora) on conflict do nothing;
    update public.propostes_junta set estat = 'aprovada' where id = p_proposta;
    return 'aprovada';
end;
$$;

create or replace function public.ranking_de(p_temporada integer)
returns table (jugador_id uuid, punts integer, posicio integer)
language sql
stable
set search_path = ''
as $$
    with cfg as (
        select (public.config_temporades() ->> 'punts_per_catali')::int as catali,
               (public.config_temporades() ->> 'punts_per_andana')::int as andana,
               (public.config_temporades() ->> 'punts_per_checkin')::int as checkin,
               ((public.config_temporades() ->> 'inici')::date + (p_temporada - 1) * (public.config_temporades() ->> 'durada_dies')::int)
                   ::timestamp at time zone 'Europe/Madrid' as inici,
               ((public.config_temporades() ->> 'inici')::date + p_temporada * (public.config_temporades() ->> 'durada_dies')::int)
                   ::timestamp at time zone 'Europe/Madrid' as fi
    ),
    punts as (
        select m.jugador_id, floor(sum(m.import))::int * max(cfg.catali) as p
        from public.moviments m, cfg
        where m.tipus = 'cobrament' and m.creat_el >= cfg.inici and m.creat_el < cfg.fi
        group by m.jugador_id
        union all
        select m.jugador_id, count(*)::int * max(cfg.andana)
        from public.moviments m, cfg
        where m.tipus = 'compra' and m.creat_el >= cfg.inici and m.creat_el < cfg.fi
        group by m.jugador_id
        union all
        select k.jugador_id, count(*)::int * max(cfg.checkin)
        from public.checkins k, cfg
        where k.resultat in ('conquesta', 'accions', 'visita_estacio', 'concessio', 'vigent')
          and k.creat_el >= cfg.inici and k.creat_el < cfg.fi
        group by k.jugador_id
        union all
        select fj.jugador_id, sum(f.punts)::int
        from public.fites_jugador fj join public.fites f on f.id = fj.fita, cfg
        where fj.aconseguida_el >= cfg.inici and fj.aconseguida_el < cfg.fi
        group by fj.jugador_id
    ),
    -- Els comptes amb el mode desenvolupador (compres gratis) no surten al rànquing.
    total as (
        select p.jugador_id, sum(p.p)::int as punts from punts p
        where not exists (select 1 from public.perfils pf where pf.id = p.jugador_id and pf.mode_dev)
        group by p.jugador_id having sum(p.p) > 0
    )
    select t.jugador_id, t.punts, (rank() over (order by t.punts desc))::int from total t;
$$;

create or replace function public.cobra_arribades()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_vist timestamptz;
begin
    if v_user_id is null then
        return null;
    end if;
    select p.vist_el into v_vist from public.perfils p where p.id = v_user_id;
    if not found then
        return null;
    end if;
    -- Com a molt un cobrament cada 20 segons per jugador (l'app el demana cada minut; si algú el crida en bucle, no
    -- carrega el servidor).
    if not exists (select 1 from public.perfils p where p.id = v_user_id and p.cobrat_fins > now() - interval '20 seconds') then
        perform public.liquida_jugador(v_user_id);
    end if;
    update public.perfils
    set vist_el = now(),
        pendent_benvinguda = case when v_vist >= now() - interval '5 minutes' then 0 else pendent_benvinguda end,
        pendent_arribades = case when v_vist >= now() - interval '5 minutes' then 0 else pendent_arribades end
    where id = v_user_id;
    return public.json_cobrament(v_user_id);
end;
$$;

-- 3. Maestria: l'experiència d'un cobrament, d'un sol cop -----------------------------------------------------------------

-- Si una maestria ha passat de nivell (per l'experiència que ja té), en desa el nivell i cobra el premi i, al nivell
-- màxim, la skin. Torna els nivells guanyats.
create or replace function public.puja_nivells(p_jugador uuid, p_tipus text, p_clau text)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_cfg jsonb := public.config_maestria();
    v_abans integer;
    v_ara integer;
    v_premi numeric(12, 2);
    v_nom text;
begin
    select m.nivell, public.nivell_maestria(m.xp) into v_abans, v_ara from public.maestries m
    where m.jugador_id = p_jugador and m.tipus = p_tipus and m.clau = p_clau
    for update;
    if not found or v_ara <= v_abans then
        return 0;
    end if;
    update public.maestries set nivell = v_ara where jugador_id = p_jugador and tipus = p_tipus and clau = p_clau;

    select coalesce(sum((v_cfg -> 'premi_nivell_andana' ->> (n - 1))::numeric), 0) into v_premi
    from generate_series(v_abans + 1, v_ara) as n;
    if p_tipus = 'linia' then
        v_premi := v_premi * (v_cfg ->> 'multiplicador_premi_linia')::numeric;
        v_nom := split_part(p_clau, ':', 2);
    else
        select n.nom into v_nom from public.nodes n where n.id = p_clau;
    end if;
    if v_premi > 0 then
        update public.perfils set saldo = saldo + v_premi, actualitzat_el = now() where id = p_jugador;
        insert into public.moviments (jugador_id, concepte, import, node_id, tipus)
        values (p_jugador,
                'Maestria ' || case when p_tipus = 'linia' then 'de la línia ' else '' end || coalesce(v_nom, p_clau)
                    || ': nivell ' || v_ara,
                v_premi,
                case when p_tipus = 'andana' then p_clau end,
                'maestria');
    end if;
    if v_ara >= jsonb_array_length(v_cfg -> 'xp_nivells') then
        insert into public.skins_jugador (jugador_id, skin)
        values (p_jugador, case when p_tipus = 'linia' then v_cfg ->> 'skin_linia_mestra' else v_cfg ->> 'skin_andana_mestra' end)
        on conflict do nothing;
    end if;
    return v_ara - v_abans;
end;
$$;

-- Suma experiència a una andana o línia i en puja el nivell si cal (check-ins, mode desenvolupador).
create or replace function public.suma_xp(p_jugador uuid, p_tipus text, p_clau text, p_xp numeric)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_xp is null or p_xp <= 0 then
        return 0;
    end if;
    insert into public.maestries as m (jugador_id, tipus, clau, xp, nivell)
    values (p_jugador, p_tipus, p_clau, round(p_xp, 2), 1)
    on conflict (jugador_id, tipus, clau) do update set xp = m.xp + excluded.xp, actualitzada_el = now();
    return public.puja_nivells(p_jugador, p_tipus, p_clau);
end;
$$;

-- L'experiència d'un tros de cobrament: totes les andanes i línies del jugador d'una sola consulta; després, només les
-- que han passat de nivell.
create or replace function public.suma_maestria_jugador(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_factor numeric := case when p_feiner then 1.0 else 0.6 end
        * (public.config_maestria() ->> 'xp_per_arribada')::numeric * p_fraccio;
    v_fila record;
    v_nivells integer := 0;
begin
    if p_fraccio is null or p_fraccio <= 0 then
        return 0;
    end if;
    insert into public.maestries as m (jugador_id, tipus, clau, xp, nivell)
    select p_jugador, x.tipus, x.clau, round(x.arribades * v_factor, 2), 1
    from (
        select 'andana' as tipus, c.node_id as clau, n.expedicions_feiner::numeric as arribades
        from public.concessions c join public.nodes n on n.id = c.node_id
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now() and n.expedicions_feiner > 0
        union all
        select 'linia', l.mode::text || ':' || l.codi, sum(ln.expedicions_feiner)::numeric
        from public.concessions c
        join public.linia_nodes ln on ln.node_id = c.node_id
        join public.linies l on l.id = ln.linia_id
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
        group by l.mode, l.codi
        having sum(ln.expedicions_feiner) > 0
    ) x
    where round(x.arribades * v_factor, 2) > 0
    on conflict (jugador_id, tipus, clau) do update set xp = m.xp + excluded.xp, actualitzada_el = now();

    for v_fila in
        select m.tipus, m.clau from public.maestries m
        where m.jugador_id = p_jugador and m.nivell < public.nivell_maestria(m.xp)
    loop
        v_nivells := v_nivells + public.puja_nivells(p_jugador, v_fila.tipus, v_fila.clau);
    end loop;
    return v_nivells;
end;
$$;

revoke execute on function public.puja_nivells(uuid, text, text) from public, anon, authenticated;
revoke execute on function public.suma_xp(uuid, text, text, numeric) from public, anon, authenticated;
revoke execute on function public.suma_maestria_jugador(uuid, boolean, numeric) from public, anon, authenticated;

-- 4. Sense JIT a les funcions del joc -------------------------------------------------------------------------------------
do $jit$
declare
    v_f record;
begin
    for v_f in
        select p.oid::regprocedure as f
        from pg_proc p join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and p.prokind = 'f' and p.prolang in (
            select l.oid from pg_language l where l.lanname in ('sql', 'plpgsql'))
          -- Les SQL senzilles (sense SET) es poden incrustar a la consulta que les crida: es deixen com estan.
          and (p.prosecdef or p.prolang = (select l.oid from pg_language l where l.lanname = 'plpgsql') or p.proconfig is not null)
          and p.prorettype <> 'trigger'::regtype
    loop
        execute format('alter function %s set jit = off', v_f.f);
    end loop;
end;
$jit$;
