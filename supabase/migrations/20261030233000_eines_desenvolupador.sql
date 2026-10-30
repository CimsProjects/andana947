-- Eines del mode desenvolupador (perfils.mode_dev, que només es pot activar des del servidor): provar la maestria, les
-- skins i el cobrament sense esperar. Totes comproven que el jugador té el mode desenvolupador activat.

-- Experiència per a una andana pròpia i per a les línies que hi paren (amb els premis i les skins dels nivells).
create or replace function public.dev_suma_maestria(p_node text, p_xp numeric)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_nivells integer := 0;
    v_linia record;
begin
    if v_user_id is null or not coalesce((select p.mode_dev from public.perfils p where p.id = v_user_id), false) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només amb el mode desenvolupador.');
    end if;
    if p_xp is null or p_xp <= 0 or p_xp > 100000 then
        return jsonb_build_object('status', 'error', 'missatge', 'L''experiència ha de ser entre 1 i 100.000.');
    end if;
    if not exists (select 1 from public.concessions c
                   where c.jugador_id = v_user_id and c.node_id = p_node and c.activa and c.caduca_el >= now()) then
        return jsonb_build_object('status', 'no_es_teva', 'missatge', 'Només a les teves andanes.');
    end if;

    v_nivells := public.suma_xp(v_user_id, 'andana', p_node, p_xp);
    for v_linia in
        select distinct l.mode::text || ':' || l.codi as clau
        from public.linia_nodes ln join public.linies l on l.id = ln.linia_id
        where ln.node_id = p_node
    loop
        v_nivells := v_nivells + public.suma_xp(v_user_id, 'linia', v_linia.clau, p_xp);
    end loop;

    return jsonb_build_object(
        'status', 'valid',
        'tipus_accio', 'maestria',
        'missatge', '+' || round(p_xp) || ' d''experiència a l''andana i a les seves línies: '
            || v_nivells || ' nivells pujats.',
        'saldo', (select p.saldo from public.perfils p where p.id = v_user_id)
    );
end;
$$;

-- Totes les skins, per veure-les al mapa.
create or replace function public.dev_desbloqueja_skins()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_noves integer;
begin
    if v_user_id is null or not coalesce((select p.mode_dev from public.perfils p where p.id = v_user_id), false) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només amb el mode desenvolupador.');
    end if;
    insert into public.skins_jugador (jugador_id, skin)
    select v_user_id, s.id from public.skins s
    on conflict do nothing;
    get diagnostics v_noves = row_count;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'skins',
        'missatge', 'Totes les skins desbloquejades (' || v_noves || ' de noves).');
end;
$$;

-- Cobrar ara les arribades de 4 hores, com el tic (surt la pantalla de benvinguda en tornar a l'app).
create or replace function public.dev_cobra_ara()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_feiner boolean := extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5;
    v_fraccio numeric;
    v_ingres numeric(12, 2);
    v_saldo numeric(12, 2);
begin
    if v_user_id is null or not coalesce((select p.mode_dev from public.perfils p where p.id = v_user_id), false) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només amb el mode desenvolupador.');
    end if;
    -- 4 hores del dia de servei (de 05:00 a 24:00: 4 de 19 hores).
    v_fraccio := round(4.0 / ((public.config_servei() ->> 'fi_hora')::numeric - (public.config_servei() ->> 'inici_hora')::numeric), 6);
    v_ingres := public.ingres_jugador(v_user_id, v_feiner, v_fraccio);
    if v_ingres <= 0 then
        return jsonb_build_object('status', 'error', 'missatge', 'No hi ha res per cobrar: compra alguna andana.');
    end if;
    update public.perfils set saldo = saldo + v_ingres, actualitzat_el = now() where id = v_user_id
    returning saldo into v_saldo;
    insert into public.moviments (jugador_id, concepte, import, tipus)
    values (v_user_id, 'Liquidació de 4 h (prova)', v_ingres, 'cobrament');
    perform public.suma_maestria_jugador(v_user_id, v_feiner, v_fraccio);
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'cobrament',
        'missatge', 'Cobrats ' || v_ingres || ' € de 4 hores d''arribades.', 'saldo', v_saldo);
end;
$$;

-- L'experiència d'un període per a un sol jugador (la de tots, suma_maestria_periode, ara hi delega).
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
    for v_fila in
        select c.node_id, n.expedicions_feiner
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now() and n.expedicions_feiner > 0
    loop
        v_nivells := v_nivells + public.suma_xp(p_jugador, 'andana', v_fila.node_id, v_fila.expedicions_feiner * v_factor);
    end loop;
    for v_fila in
        select l.mode::text || ':' || l.codi as clau, sum(ln.expedicions_feiner) as arribades
        from public.concessions c
        join public.linia_nodes ln on ln.node_id = c.node_id
        join public.linies l on l.id = ln.linia_id
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
        group by l.mode, l.codi
        having sum(ln.expedicions_feiner) > 0
    loop
        v_nivells := v_nivells + public.suma_xp(p_jugador, 'linia', v_fila.clau, v_fila.arribades * v_factor);
    end loop;
    return v_nivells;
end;
$$;

create or replace function public.suma_maestria_periode(p_feiner boolean, p_fraccio numeric)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_jugador uuid;
    v_nivells integer := 0;
begin
    for v_jugador in select distinct c.jugador_id from public.concessions c where c.activa and c.caduca_el >= now() loop
        v_nivells := v_nivells + public.suma_maestria_jugador(v_jugador, p_feiner, p_fraccio);
    end loop;
    return v_nivells;
end;
$$;

revoke execute on function public.suma_maestria_jugador(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.dev_suma_maestria(text, numeric) from public, anon;
revoke execute on function public.dev_desbloqueja_skins() from public, anon;
revoke execute on function public.dev_cobra_ara() from public, anon;
grant execute on function public.dev_suma_maestria(text, numeric) to authenticated;
grant execute on function public.dev_desbloqueja_skins() to authenticated;
grant execute on function public.dev_cobra_ara() to authenticated;
