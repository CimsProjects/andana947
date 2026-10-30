-- Maestria, millores i objectius d'«unir» (docs/abast.md, 30/10/2026).
--  * Trams de la xarxa: parells d'andanes seguides de cada línia (scripts/genera_trams.py).
--  * Maestria d'andanes i línies: cada arribada a una andana pròpia dona experiència a l'andana i a la línia; cada
--    check-in, més. En pujar de nivell es cobra un premi i, al nivell 10, una skin. No dona ingressos.
--  * Millores d'una andana (marquesina, ascensor…): sumen un percentatge a cada arribada. Es desbloquegen amb la
--    maestria de l'andana.
--  * Objectius d'«unir»: municipis, comarques, províncies i línies units per andanes pròpies seguides.
-- Els valors són a dades/configuracio_joc.json (blocs «maestria», «millores» i «unir») i han de coincidir amb les
-- funcions config_* i la taula millores (tests a supabase/tests/maestria_millores_unir.test.sql).

-- 1. Trams de la xarxa ------------------------------------------------------------------------------------------------

create table if not exists public.trams (
    linia_id text not null references public.linies (id) on delete cascade,
    node_a text not null references public.nodes (id) on delete cascade,
    node_b text not null references public.nodes (id) on delete cascade,
    primary key (linia_id, node_a, node_b),
    check (node_a < node_b)
);
comment on table public.trams is 'Parells d''andanes seguides d''una línia, segons els recorreguts dels horaris (scripts/genera_trams.py).';
create index if not exists trams_node_a_idx on public.trams (node_a);
create index if not exists trams_node_b_idx on public.trams (node_b);

alter table public.trams enable row level security;
create policy "Tothom pot llegir els trams" on public.trams for select to anon, authenticated using (true);
revoke insert, update, delete, truncate on public.trams from anon, authenticated;

-- 2. Configuració -----------------------------------------------------------------------------------------------------

create or replace function public.config_maestria()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'xp_nivells', jsonb_build_array(0, 50, 150, 400, 1000, 2500, 6000, 15000, 35000, 80000),
        'xp_per_arribada', 1,
        'xp_per_checkin', 100,
        'premi_nivell_andana', jsonb_build_array(0, 5, 10, 20, 40, 80, 150, 300, 500, 1000),
        'multiplicador_premi_linia', 2,
        'skin_andana_mestra', 'mestre',
        'skin_linia_mestra', 'llegenda'
    );
$$;

create or replace function public.config_unir()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object('radi_transbord_metres', 200);
$$;

-- El catàleg de millores (bloc «millores»).
create table if not exists public.millores (
    id text primary key,
    bonus numeric(4, 2) not null check (bonus > 0 and bonus < 1),
    cost_relatiu numeric(4, 2) not null check (cost_relatiu > 0),
    nivell_minim integer not null check (nivell_minim between 1 and 10),
    modes public.mode_transport[] not null,
    ordre integer not null
);
comment on table public.millores is 'Millores d''una andana: +bonus a cada arribada; costen cost_relatiu × el preu de l''andana.';

alter table public.millores enable row level security;
create policy "Tothom pot llegir les millores" on public.millores for select to authenticated using (true);
revoke insert, update, delete, truncate on public.millores from anon, authenticated;

insert into public.millores (id, bonus, cost_relatiu, nivell_minim, modes, ordre) values
    ('marquesina', 0.10, 0.4, 1, '{bus,tram}', 1),
    ('aparcament_bicis', 0.05, 0.2, 2, '{metro,rodalies,regional,fgc,tram,bus}', 2),
    ('pantalles', 0.10, 0.5, 3, '{metro,rodalies,regional,fgc,tram,bus}', 3),
    ('maquines_bitllets', 0.10, 0.5, 4, '{metro,rodalies,regional,fgc,tram}', 4),
    ('ascensor', 0.20, 1.2, 5, '{metro,rodalies,regional,fgc}', 5),
    ('cafeteria', 0.15, 0.8, 7, '{rodalies,regional,fgc}', 6)
on conflict (id) do update set bonus = excluded.bonus, cost_relatiu = excluded.cost_relatiu,
    nivell_minim = excluded.nivell_minim, modes = excluded.modes, ordre = excluded.ordre;

-- Les skins de la maestria màxima.
insert into public.skins (id, tipus, color, ordre) values
    ('mestre', 'andana', '#00A19A', 9),
    ('llegenda', 'andana', '#C2185B', 10),
    ('grana', 'andana', '#8E1B3A', 11)
on conflict (id) do nothing;

-- 3. Maestria ----------------------------------------------------------------------------------------------------------

create table if not exists public.maestries (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    tipus text not null check (tipus in ('andana', 'linia')),
    clau text not null,
    xp numeric(14, 2) not null default 0 check (xp >= 0),
    nivell integer not null default 1 check (nivell between 1 and 10),
    actualitzada_el timestamptz not null default now(),
    primary key (jugador_id, tipus, clau)
);
comment on table public.maestries is 'Experiència i nivell de maestria de cada andana (clau = node) i línia (clau = mode:codi, perquè un GTFS té diverses rutes per a la mateixa línia) del jugador.';

alter table public.maestries enable row level security;
create policy "El jugador llegeix les seves maestries" on public.maestries for select to authenticated
    using (jugador_id = auth.uid());
revoke insert, update, delete, truncate on public.maestries from anon, authenticated;

-- El nivell (1–10) d'una experiència acumulada.
create or replace function public.nivell_maestria(p_xp numeric)
returns integer
language sql
immutable
set search_path = ''
as $$
    select greatest(1, count(*)::int)
    from jsonb_array_elements_text(public.config_maestria() -> 'xp_nivells') as x(llindar)
    where x.llindar::numeric <= coalesce(p_xp, 0);
$$;

-- L'experiència que cal per arribar a un nivell (null si no existeix).
create or replace function public.xp_nivell(p_nivell integer)
returns numeric
language sql
immutable
set search_path = ''
as $$
    select (public.config_maestria() -> 'xp_nivells' ->> (p_nivell - 1))::numeric
    where p_nivell between 1 and jsonb_array_length(public.config_maestria() -> 'xp_nivells');
$$;

-- Suma experiència a una andana o línia del jugador. Si puja de nivell, cobra el premi (i la skin al nivell màxim).
-- Torna els nivells guanyats.
create or replace function public.suma_xp(p_jugador uuid, p_tipus text, p_clau text, p_xp numeric)
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
    if p_xp is null or p_xp <= 0 then
        return 0;
    end if;

    insert into public.maestries as m (jugador_id, tipus, clau, xp, nivell)
    values (p_jugador, p_tipus, p_clau, 0, 1)
    on conflict (jugador_id, tipus, clau) do nothing;

    select m.nivell into v_abans from public.maestries m
    where m.jugador_id = p_jugador and m.tipus = p_tipus and m.clau = p_clau
    for update;

    update public.maestries m
    set xp = m.xp + round(p_xp, 2),
        nivell = public.nivell_maestria(m.xp + round(p_xp, 2)),
        actualitzada_el = now()
    where m.jugador_id = p_jugador and m.tipus = p_tipus and m.clau = p_clau
    returning m.nivell into v_ara;

    if v_ara <= v_abans then
        return 0;
    end if;

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

-- L'experiència d'un període: les arribades de cada andana pròpia (i de cada línia, a les andanes pròpies).
create or replace function public.suma_maestria_periode(p_feiner boolean, p_fraccio numeric)
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
        select c.jugador_id, c.node_id, n.expedicions_feiner
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.activa and c.caduca_el >= now() and n.expedicions_feiner > 0
    loop
        v_nivells := v_nivells + public.suma_xp(v_fila.jugador_id, 'andana', v_fila.node_id, v_fila.expedicions_feiner * v_factor);
    end loop;
    for v_fila in
        select c.jugador_id, l.mode::text || ':' || l.codi as clau, sum(ln.expedicions_feiner) as arribades
        from public.concessions c
        join public.linia_nodes ln on ln.node_id = c.node_id
        join public.linies l on l.id = ln.linia_id
        where c.activa and c.caduca_el >= now()
        group by c.jugador_id, l.mode, l.codi
        having sum(ln.expedicions_feiner) > 0
    loop
        v_nivells := v_nivells + public.suma_xp(v_fila.jugador_id, 'linia', v_fila.clau, v_fila.arribades * v_factor);
    end loop;
    return v_nivells;
end;
$$;

-- Cada check-in vàlid a una andana pròpia hi suma experiència.
create or replace function public.maestria_checkin()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.resultat in ('conquesta', 'vigent', 'concessio', 'renovacio', 'reactivacio')
       and exists (select 1 from public.concessions c
                   where c.jugador_id = new.jugador_id and c.node_id = new.node_id and c.activa and c.caduca_el >= now()) then
        perform public.suma_xp(new.jugador_id, 'andana', new.node_id,
            (public.config_maestria() ->> 'xp_per_checkin')::numeric);
    end if;
    return new;
end;
$$;

drop trigger if exists checkins_maestria on public.checkins;
create trigger checkins_maestria after insert on public.checkins
    for each row execute function public.maestria_checkin();

-- 4. Millores -----------------------------------------------------------------------------------------------------------

create table if not exists public.millores_andana (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    node_id text not null references public.nodes (id) on delete cascade,
    millora text not null references public.millores (id) on delete cascade,
    comprada_el timestamptz not null default now(),
    primary key (jugador_id, node_id, millora)
);

alter table public.millores_andana enable row level security;
create policy "El jugador llegeix les seves millores" on public.millores_andana for select to authenticated
    using (jugador_id = auth.uid());
revoke insert, update, delete, truncate on public.millores_andana from anon, authenticated;

-- El factor de les millores d'una andana del jugador: 1 + la suma dels bonus.
create or replace function public.factor_millores(p_jugador uuid, p_node text)
returns numeric
language sql
stable
set search_path = ''
as $$
    select 1 + coalesce(sum(m.bonus), 0)
    from public.millores_andana ma
    join public.millores m on m.id = ma.millora
    where ma.jugador_id = p_jugador and ma.node_id = p_node;
$$;

-- Comprar una millora per a una andana pròpia.
create or replace function public.compra_millora(p_node text, p_millora text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_mode_dev boolean;
    v_saldo numeric(12, 2);
    v_node record;
    v_millora record;
    v_nivell integer;
    v_cost numeric(12, 2);
    v_nou numeric(12, 2);
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select p.saldo, coalesce(p.mode_dev, false) into v_saldo, v_mode_dev
    from public.perfils p where p.id = v_user_id for update;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'El perfil no existeix.');
    end if;

    select n.id, n.nom, n.mode, n.viatgers_feiner into v_node from public.nodes n where n.id = p_node;
    if not found or not exists (
        select 1 from public.concessions c
        where c.jugador_id = v_user_id and c.node_id = p_node and c.activa and c.caduca_el >= now()
    ) then
        return jsonb_build_object('status', 'no_es_teva', 'missatge', 'Només pots millorar les teves andanes.');
    end if;

    select m.id, m.bonus, m.cost_relatiu, m.nivell_minim, m.modes into v_millora from public.millores m where m.id = p_millora;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta millora no existeix.');
    end if;
    if not (v_node.mode = any (v_millora.modes)) then
        return jsonb_build_object('status', 'no_disponible', 'missatge', 'Aquesta millora no és per a aquest tipus d''andana.');
    end if;
    if exists (select 1 from public.millores_andana ma
               where ma.jugador_id = v_user_id and ma.node_id = p_node and ma.millora = p_millora) then
        return jsonb_build_object('status', 'ja_la_tens', 'missatge', 'Aquesta andana ja té aquesta millora.');
    end if;

    select coalesce(max(m.nivell), 1) into v_nivell from public.maestries m
    where m.jugador_id = v_user_id and m.tipus = 'andana' and m.clau = p_node;
    if not v_mode_dev and v_nivell < v_millora.nivell_minim then
        return jsonb_build_object('status', 'nivell_insuficient',
            'missatge', 'Cal la maestria de nivell ' || v_millora.nivell_minim || ' en aquesta andana.');
    end if;

    v_cost := case when v_mode_dev then 0
        else round(public.preu_concessio(v_node.viatgers_feiner) * v_millora.cost_relatiu, 2) end;
    if v_saldo < v_cost then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: calen ' || v_cost || ' €.');
    end if;

    insert into public.millores_andana (jugador_id, node_id, millora) values (v_user_id, p_node, p_millora);
    update public.perfils set saldo = saldo - v_cost, actualitzat_el = now() where id = v_user_id
    returning saldo into v_nou;
    insert into public.moviments (jugador_id, concepte, import, node_id, tipus)
    values (v_user_id, 'Millora: ' || v_node.nom, -v_cost, p_node, 'millora');

    return jsonb_build_object(
        'status', 'valid',
        'tipus_accio', 'millora',
        'missatge', 'Millora feta a ' || v_node.nom || ': cada arribada hi deixa un '
            || round(v_millora.bonus * 100) || ' % més.',
        'cost', v_cost,
        'saldo', v_nou
    );
end;
$$;

-- 5. L'ingrés compta les millores --------------------------------------------------------------------------------------

create or replace function public.ingres_jugador(p_jugador uuid, p_feiner boolean, p_fraccio numeric)
returns numeric
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_factor_dia double precision := case when p_feiner then 1.0 else 0.6 end;
    v_mult numeric := public.multiplicador_concessio();
    v_node record;
    v_total numeric(12, 2) := 0;
    v_ingres numeric(12, 2);
begin
    for v_node in
        select c.node_id, c.presencial_fins >= now() as presencial
        from public.concessions c
        where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    loop
        v_ingres := public.ingres_diari_concessio(v_node.node_id, v_factor_dia)
            * case when v_node.presencial then v_mult else 1 end
            * public.factor_millores(p_jugador, v_node.node_id)
            - public.manteniment_diari(v_node.node_id, p_jugador);
        v_total := v_total + round(v_ingres * p_fraccio, 2);
    end loop;

    -- Dividends d'accions (si s'ha visitat l'estació en els darrers 30 dies), ×1,5 amb visita física a la temporada.
    for v_node in
        select a.quantitat, n.viatgers_feiner, a.presencial_fins >= now() as presencial
        from public.accions a
        join public.nodes n on n.id = a.node_id
        where a.jugador_id = p_jugador and a.darrera_visita_el >= (now() - interval '30 days')
    loop
        v_ingres := round(((public.ingres_diari((v_node.viatgers_feiner::double precision * v_factor_dia)::numeric)::double precision
            / 100.0) * v_node.quantitat)::numeric, 2) * case when v_node.presencial then v_mult else 1 end;
        v_total := v_total + round(v_ingres * p_fraccio, 2);
    end loop;

    return greatest(v_total, 0);
end;
$$;

-- El tic de 4 h també suma l'experiència de les arribades del període.
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
    v_resultat jsonb;
begin
    v_feiner := extract(isodow from ((v_inici + make_interval(secs => v_periode / 2)) at time zone 'Europe/Madrid'))
        between 1 and 5;
    if v_fraccio <= 0 then
        -- De matinada no passa cap tren: només es tanquen les concessions caducades.
        update public.concessions set activa = false where activa = true and caduca_el < now();
        return jsonb_build_object('status', 'ok', 'fraccio', 0, 'jugadors_liquidats', 0, 'catalins_repartits', 0);
    end if;
    v_resultat := public.liquida_periode(v_feiner, least(v_fraccio, 1));
    return v_resultat || jsonb_build_object('nivells_maestria', public.suma_maestria_periode(v_feiner, least(v_fraccio, 1)));
end;
$$;

-- 6. Objectius d'«unir» -----------------------------------------------------------------------------------------------

-- Les xarxes del jugador: grups d'andanes pròpies unides per trams (dues andanes seguides d'una línia) o per un
-- transbord a peu (a menys de radi_transbord_metres). Per a cada xarxa: andanes, municipis, comarques, províncies i
-- línies (les dels trams).
create or replace function public.xarxes_jugador(p_jugador uuid)
returns table (arrel text, andanes integer, municipis integer, comarques integer, provincies integer, linies integer)
language sql
stable
set search_path = ''
set jit = off
as $$
    with recursive meus as (
        select n.id, n.municipi, n.comarca, n.posicio
        from public.nodes n
        where n.id in (select d.node_id from public.nodes_del_jugador(p_jugador) d)
    ),
    arestes as (
        select t.node_a as a, t.node_b as b, t.linia_id
        from public.trams t
        where t.node_a in (select id from meus) and t.node_b in (select id from meus)
        union all
        -- Amb l'índex espacial dels nodes: cada andana pròpia només es compara amb les que té a prop.
        select m1.id, n2.id, null::text
        from meus m1
        join public.nodes n2 on n2.id > m1.id
            and extensions.st_dwithin(m1.posicio, n2.posicio, (public.config_unir() ->> 'radi_transbord_metres')::double precision)
        where n2.id in (select id from meus)
    ),
    dirigides as (
        select a, b from arestes
        union
        select b, a from arestes
    ),
    abast (origen, node) as (
        select id, id from meus
        union
        select ab.origen, d.b from abast ab join dirigides d on d.a = ab.node
    ),
    etiqueta as (
        select origen as node, min(node) as arrel from abast group by origen
    )
    select e.arrel,
           count(*)::int,
           count(distinct m.municipi)::int,
           count(distinct m.comarca)::int,
           count(distinct left(m.municipi, 2))::int,
           -- Una línia és un codi d'un mode (els GTFS tenen diverses rutes per a la mateixa línia).
           (select count(distinct l.mode::text || ':' || l.codi)::int
            from arestes ar
            join etiqueta e2 on e2.node = ar.a
            join public.linies l on l.id = ar.linia_id
            where e2.arrel = e.arrel)
    from etiqueta e
    join meus m on m.id = e.node
    group by e.arrel;
$$;

alter table public.fites drop constraint if exists fites_tipus_check;
alter table public.fites add constraint fites_tipus_check check (tipus in (
    'andanes', 'municipis', 'comarques', 'provincies', 'linies', 'linies_completes', 'checkins',
    'uneix_municipis', 'uneix_comarques', 'uneix_provincies', 'uneix_linies', 'tram_llarg'));

insert into public.fites (id, tipus, objectiu, punts, skin, ordre) values
    ('tram_llarg_3', 'tram_llarg', 3, 30, null, 70),
    ('tram_llarg_10', 'tram_llarg', 10, 200, null, 71),
    ('tram_llarg_30', 'tram_llarg', 30, 1000, null, 72),
    ('uneix_municipis_2', 'uneix_municipis', 2, 50, null, 73),
    ('uneix_municipis_5', 'uneix_municipis', 5, 250, null, 74),
    ('uneix_municipis_20', 'uneix_municipis', 20, 1500, null, 75),
    ('uneix_comarques_2', 'uneix_comarques', 2, 150, null, 76),
    ('uneix_comarques_5', 'uneix_comarques', 5, 1000, 'grana', 77),
    ('uneix_provincies_2', 'uneix_provincies', 2, 500, null, 78),
    ('uneix_provincies_4', 'uneix_provincies', 4, 5000, null, 79),
    ('uneix_linies_2', 'uneix_linies', 2, 100, null, 80),
    ('uneix_linies_5', 'uneix_linies', 5, 600, null, 81)
on conflict (id) do nothing;

-- El progrés del jugador en cada tipus de fita, amb les xarxes.
create or replace function public.progres_fites(p_jugador uuid)
returns jsonb
language sql
stable
set search_path = ''
set jit = off
as $$
    with meus as (select node_id from public.nodes_del_jugador(p_jugador)),
    linies as (
        select ln.linia_id, count(*) as total, count(*) filter (where ln.node_id in (select node_id from meus)) as meves
        from public.linia_nodes ln
        where ln.linia_id in (select l2.linia_id from public.linia_nodes l2 where l2.node_id in (select node_id from meus))
        group by ln.linia_id
    ),
    xarxes as (select * from public.xarxes_jugador(p_jugador))
    select jsonb_build_object(
        'andanes', (select count(*) from meus),
        'municipis', (select count(distinct n.municipi) from public.nodes n where n.id in (select node_id from meus) and n.municipi is not null),
        'comarques', (select count(distinct n.comarca) from public.nodes n where n.id in (select node_id from meus) and n.comarca is not null),
        'provincies', (select count(distinct left(n.municipi, 2)) from public.nodes n where n.id in (select node_id from meus) and n.municipi is not null),
        'linies', (select count(distinct l.mode::text || ':' || l.codi) from linies x join public.linies l on l.id = x.linia_id),
        'linies_completes', (select count(*) from linies where meves = total),
        'checkins', (
            select count(*) from public.checkins k where k.jugador_id = p_jugador
              and k.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent', 'concessio')
        ),
        'tram_llarg', (select coalesce(max(x.andanes), 0) from xarxes x),
        'uneix_municipis', (select coalesce(max(x.municipis), 0) from xarxes x),
        'uneix_comarques', (select coalesce(max(x.comarques), 0) from xarxes x),
        'uneix_provincies', (select coalesce(max(x.provincies), 0) from xarxes x),
        'uneix_linies', (select coalesce(max(x.linies), 0) from xarxes x)
    );
$$;

-- 7. Consultes per a l'app ------------------------------------------------------------------------------------------

-- Una maestria en JSON: nivell, experiència i els llindars del nivell actual i del següent.
create or replace function public.json_maestria(p_xp numeric)
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'xp', round(coalesce(p_xp, 0)),
        'nivell', public.nivell_maestria(p_xp),
        'xp_nivell', public.xp_nivell(public.nivell_maestria(p_xp)),
        'xp_seguent', public.xp_nivell(public.nivell_maestria(p_xp) + 1)
    );
$$;

-- La fitxa d'una andana pròpia: maestria, línies, millores i el que dona cada arribada.
create or replace function public.estat_andana(p_node text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_node record;
    v_concessio record;
    v_factor double precision;
    v_nivell integer;
    v_millores numeric;
    v_mult numeric;
begin
    if v_user_id is null then
        return null;
    end if;
    select n.id, n.nom, n.mode, n.operador, n.viatgers_feiner, n.expedicions_feiner, m.nom as municipi
    into v_node
    from public.nodes n left join public.municipis m on m.codi = n.municipi
    where n.id = p_node;
    if not found then
        return null;
    end if;
    select c.regal, c.presencial_fins into v_concessio from public.concessions c
    where c.jugador_id = v_user_id and c.node_id = p_node and c.activa and c.caduca_el >= now();
    if not found then
        return null;
    end if;

    v_factor := case when extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5 then 1.0 else 0.6 end;
    select public.nivell_maestria(m.xp) into v_nivell from public.maestries m
    where m.jugador_id = v_user_id and m.tipus = 'andana' and m.clau = p_node;
    v_nivell := coalesce(v_nivell, 1);
    v_millores := public.factor_millores(v_user_id, p_node);
    v_mult := case when v_concessio.presencial_fins >= now() then public.multiplicador_concessio() else 1 end;

    return jsonb_build_object(
        'id', v_node.id,
        'nom', v_node.nom,
        'mode', v_node.mode,
        'operador', v_node.operador,
        'municipi', v_node.municipi,
        'regal', v_concessio.regal,
        'presencial_fins', case when v_concessio.presencial_fins >= now() then v_concessio.presencial_fins end,
        'multiplicador', v_mult,
        'factor_millores', v_millores,
        'arribades_dia', round(v_node.expedicions_feiner * v_factor),
        'ingres_diari', round(coalesce(public.ingres_diari_concessio(p_node, v_factor), 0) * v_mult * v_millores, 2),
        'manteniment_diari', public.manteniment_diari(p_node, v_user_id),
        'maestria', public.json_maestria((select m.xp from public.maestries m
            where m.jugador_id = v_user_id and m.tipus = 'andana' and m.clau = p_node)),
        'linies', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', x.clau, 'codi', x.codi, 'mode', x.mode, 'color', x.color,
                'arribades_dia', round(x.arribades * v_factor)
            ) || jsonb_build_object('maestria', public.json_maestria(mj.xp)) order by x.codi)
            from (
                select l.mode::text || ':' || l.codi as clau, l.codi, l.mode, max(l.color) as color,
                       sum(ln.expedicions_feiner) as arribades
                from public.linia_nodes ln
                join public.linies l on l.id = ln.linia_id
                where ln.node_id = p_node
                group by l.mode, l.codi
            ) x
            left join public.maestries mj on mj.jugador_id = v_user_id and mj.tipus = 'linia' and mj.clau = x.clau
        ), '[]'::jsonb),
        'millores', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', m.id,
                'bonus', m.bonus,
                'cost', round(public.preu_concessio(v_node.viatgers_feiner) * m.cost_relatiu, 2),
                'nivell_minim', m.nivell_minim,
                'comprada', ma.millora is not null,
                'desbloquejada', v_nivell >= m.nivell_minim
            ) order by m.ordre)
            from public.millores m
            left join public.millores_andana ma on ma.jugador_id = v_user_id and ma.node_id = p_node and ma.millora = m.id
            where v_node.mode = any (m.modes)
        ), '[]'::jsonb)
    );
end;
$$;

-- Les maestries del jugador (línies i andanes, de més a menys experiència) i les seves xarxes.
create or replace function public.estat_maestries()
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
    return jsonb_build_object(
        'linies', coalesce((
            select jsonb_agg(jsonb_build_object('id', m.clau, 'codi', l.codi, 'nom', l.nom, 'mode', l.mode, 'color', l.color)
                || jsonb_build_object('maestria', public.json_maestria(m.xp)) order by m.xp desc, l.codi)
            from public.maestries m
            -- La ruta amb més servei d'aquesta línia en dona el nom i el color.
            cross join lateral (
                select l2.codi, l2.nom, l2.mode, l2.color from public.linies l2
                where l2.mode::text || ':' || l2.codi = m.clau
                order by l2.expedicions_feiner desc limit 1
            ) l
            where m.jugador_id = v_user_id and m.tipus = 'linia'
        ), '[]'::jsonb),
        'andanes', coalesce((
            select jsonb_agg(jsonb_build_object('id', n.id, 'nom', n.nom, 'mode', n.mode)
                || jsonb_build_object('maestria', public.json_maestria(m.xp)) order by m.xp desc, n.nom)
            from public.maestries m join public.nodes n on n.id = m.clau
            where m.jugador_id = v_user_id and m.tipus = 'andana'
              and exists (select 1 from public.concessions c where c.jugador_id = v_user_id and c.node_id = n.id
                          and c.activa and c.caduca_el >= now())
        ), '[]'::jsonb),
        'xarxa_mes_gran', coalesce((
            select jsonb_build_object('andanes', x.andanes, 'municipis', x.municipis, 'comarques', x.comarques,
                                      'provincies', x.provincies, 'linies', x.linies)
            from public.xarxes_jugador(v_user_id) x
            order by x.andanes desc, x.municipis desc
            limit 1
        ), jsonb_build_object('andanes', 0, 'municipis', 0, 'comarques', 0, 'provincies', 0, 'linies', 0))
    );
end;
$$;

-- Les andanes del jugador: el valor de cada arribada compta les millores.
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
    v_skin text;
begin
    if v_user_id is null then
        return null;
    end if;
    v_factor := case when extract(isodow from (now() at time zone 'Europe/Madrid')) between 1 and 5 then 1.0 else 0.6 end;
    select s.color into v_skin from public.perfils p join public.skins s on s.id = p.skin_andana where p.id = v_user_id;
    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', n.id,
            'nom', n.nom,
            'mode', n.mode,
            'lat', extensions.st_y(n.posicio::extensions.geometry),
            'lon', extensions.st_x(n.posicio::extensions.geometry),
            'linies', n.linies,
            'regal', c.regal,
            'presencial', coalesce(c.presencial_fins >= now(), false),
            'color', coalesce(v_skin, '#FF3D7F'),
            'pujades', case when n.expedicions_feiner > 0
                then round((n.viatgers_feiner::double precision * v_factor / n.expedicions_feiner)::numeric) else 0 end,
            'valor', case when n.expedicions_feiner > 0
                then round(public.ingres_diari_concessio(n.id, v_factor)
                    * case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
                    * public.factor_millores(v_user_id, n.id)
                    / n.expedicions_feiner, 2) else 0 end
        ) order by n.nom)
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
    ), '[]'::jsonb);
end;
$$;

-- estat_imperi: cada andana porta el nivell de maestria, les millores i l'ingrés amb els multiplicadors.
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
    v_fi_temporada timestamptz := (public.temporada_actual() ->> 'fi')::timestamptz;
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
        'ingres_diari', round(coalesce(public.ingres_diari_concessio(n.id, 1.0), 0)
            * case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
            * public.factor_millores(v_user_id, n.id), 2),
        'caduca_el', c.caduca_el,
        'dies_restants', case when c.presencial_fins >= now()
            then greatest(0, ceil(extract(epoch from (c.presencial_fins - now())) / 86400.0)::int) end,
        'regal', c.regal,
        'presencial_fins', c.presencial_fins,
        'multiplicador', case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end,
        'manteniment_diari', public.manteniment_diari(c.node_id, v_user_id),
        'nivell_maestria', coalesce((select public.nivell_maestria(m.xp) from public.maestries m
            where m.jugador_id = v_user_id and m.tipus = 'andana' and m.clau = n.id), 1),
        'millores', (select count(*) from public.millores_andana ma where ma.jugador_id = v_user_id and ma.node_id = n.id)
    ) order by c.regal, n.nom), '[]'::jsonb)
    into v_concessions_actives
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
        'ingres_estimat_dia', public.ingres_jugador(v_user_id, true, 1),
        'manteniment_dia', (
            select coalesce(sum(public.manteniment_diari(c.node_id, v_user_id)), 0) from public.concessions c
            where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
        ),
        'temporada_fi', v_fi_temporada
    );
end;
$$;

-- 8. Les fites d'«unir» que ja s'han aconseguit -----------------------------------------------------------------------
do $fites$
begin
    perform public.revisa_fites(p.id) from public.perfils p;
end;
$fites$;

-- 9. Permisos ----------------------------------------------------------------------------------------------------------
revoke execute on function public.suma_xp(uuid, text, text, numeric) from public, anon, authenticated;
revoke execute on function public.suma_maestria_periode(boolean, numeric) from public, anon, authenticated;
revoke execute on function public.maestria_checkin() from public, anon, authenticated;
revoke execute on function public.factor_millores(uuid, text) from public, anon, authenticated;
revoke execute on function public.xarxes_jugador(uuid) from public, anon, authenticated;
revoke execute on function public.tic_4h() from public, anon, authenticated;
revoke execute on function public.ingres_jugador(uuid, boolean, numeric) from public, anon, authenticated;
grant execute on function public.suma_maestria_periode(boolean, numeric) to service_role;
grant execute on function public.tic_4h() to service_role;

revoke execute on function public.compra_millora(text, text) from public, anon;
revoke execute on function public.estat_andana(text) from public, anon;
revoke execute on function public.estat_maestries() from public, anon;
grant execute on function public.compra_millora(text, text) to authenticated;
grant execute on function public.estat_andana(text) to authenticated;
grant execute on function public.estat_maestries() to authenticated;
