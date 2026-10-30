-- Resum del perfil del jugador (pantalla Perfil): progrés, xarxa i trajectòria. Tot surt de les taules del joc.
-- La flota (trens, vagons i busos) arriba a la fase 5; de moment l'app la mostra buida.

create or replace function public.resum_perfil()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_perfil record;
    v_inici_nivell integer;
    v_seguent_nivell integer;
begin
    if v_user_id is null then
        return null;
    end if;

    select p.saldo, p.prestigi, p.nivell, p.creat_el
    into v_perfil
    from public.perfils p
    where p.id = v_user_id;

    if not found then
        return null;
    end if;

    -- Punts de prestigi on comença el nivell actual i on comença el següent (mateixa fórmula que calcular_nivell).
    v_inici_nivell := ceil(100.0 * power(v_perfil.nivell - 1, 1.5))::int;
    v_seguent_nivell := ceil(100.0 * power(v_perfil.nivell, 1.5))::int;

    return jsonb_build_object(
        'saldo', v_perfil.saldo,
        'prestigi', v_perfil.prestigi,
        'nivell', v_perfil.nivell,
        'prestigi_inici_nivell', v_inici_nivell,
        'prestigi_seguent_nivell', v_seguent_nivell,
        'membre_des_de', v_perfil.creat_el,
        'concessions', (
            select count(*) from public.concessions c
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now() and not c.regal
        ),
        'regals', (
            select count(*) from public.concessions c
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now() and c.regal
        ),
        'maxim_concessions', 10 + 2 * v_perfil.nivell,
        'accions', (select coalesce(sum(a.quantitat), 0) from public.accions a where a.jugador_id = v_user_id),
        'estacions_amb_accions', (select count(*) from public.accions a where a.jugador_id = v_user_id),
        -- On és present: municipis i comarques de les concessions actives i de les estacions on té accions.
        'municipis', (
            select count(distinct n.municipi)
            from public.nodes n
            where n.municipi is not null and n.id in (
                select c.node_id from public.concessions c
                where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
                union
                select a.node_id from public.accions a where a.jugador_id = v_user_id
            )
        ),
        'comarques', (
            select count(distinct n.comarca)
            from public.nodes n
            where n.comarca is not null and n.id in (
                select c.node_id from public.concessions c
                where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
                union
                select a.node_id from public.accions a where a.jugador_id = v_user_id
            )
        ),
        'checkins', (select count(*) from public.checkins k where k.jugador_id = v_user_id),
        'nodes_visitats', (select count(distinct k.node_id) from public.checkins k where k.jugador_id = v_user_id),
        'total_cobrat', (
            select coalesce(sum(m.import), 0) from public.moviments m
            where m.jugador_id = v_user_id and m.tipus = 'cobrament'
        )
    );
end;
$$;

comment on function public.resum_perfil is 'Resum del perfil del jugador autenticat: nivell i prestigi, xarxa, presència i trajectòria.';

revoke execute on function public.resum_perfil() from public, anon;
grant execute on function public.resum_perfil() to authenticated;
