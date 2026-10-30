-- Economia nova (docs/abast.md, 30/10/2026): andanes que es compren des de qualsevol lloc, concessió física ×1,5 fins al
-- final de la temporada, temporades de 30 dies sense reinici, preu i manteniment que creixen amb la mida, rànquing de
-- temporada, objectius (fites), insígnies i skins. Els valors són a dades/configuracio_joc.json (blocs «temporades» i
-- «compra») i han de coincidir amb les funcions config_* (tests a supabase/tests).

-- 1. Columnes noves -------------------------------------------------------------------------------------------------

alter table public.concessions add column if not exists presencial_fins timestamptz;
comment on column public.concessions.presencial_fins is
    'Concessió física: fins quan el jugador cobra ×1,5 per arribada (el final de la temporada del check-in).';

alter table public.accions add column if not exists presencial_fins timestamptz;
comment on column public.accions.presencial_fins is 'Visita física: fins quan els dividends van ×1,5.';

alter table public.perfils
    add column if not exists nom text check (nom is null or char_length(nom) between 3 and 20),
    add column if not exists skin_andana text not null default 'magenta';
comment on column public.perfils.nom is 'El nom públic del jugador al rànquing.';

-- 2. Configuració ---------------------------------------------------------------------------------------------------

create or replace function public.config_temporades()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'durada_dies', 30, 'inici', '2026-10-29', 'multiplicador_concessio', 1.5,
        'punts_per_catali', 1, 'punts_per_andana', 20, 'punts_per_checkin', 10,
        'premi_or', 3, 'premi_plata', 10, 'premi_bronze', 100
    );
$$;

create or replace function public.config_compra()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'creixement_preu_per_andana', 0.02, 'quota_manteniment', 0.05, 'creixement_manteniment_per_andana', 0.01
    );
$$;

create or replace function public.multiplicador_concessio()
returns numeric
language sql
immutable
set search_path = ''
as $$
    select (public.config_temporades() ->> 'multiplicador_concessio')::numeric;
$$;

-- 3. Temporades ------------------------------------------------------------------------------------------------------

-- La temporada d'un moment: número, inici i fi (a la mitjanit de Catalunya).
create or replace function public.temporada_de(p_moment timestamptz)
returns jsonb
language sql
stable
set search_path = ''
as $$
    with cfg as (
        select (public.config_temporades() ->> 'inici')::date as inici,
               (public.config_temporades() ->> 'durada_dies')::int as dies
    ),
    t as (
        select greatest(1, floor(((p_moment at time zone 'Europe/Madrid')::date - cfg.inici)::numeric / cfg.dies)::int + 1) as numero,
               cfg.inici, cfg.dies
        from cfg
    )
    select jsonb_build_object(
        'numero', t.numero,
        'inici', ((t.inici + (t.numero - 1) * t.dies)::timestamp at time zone 'Europe/Madrid'),
        'fi', ((t.inici + t.numero * t.dies)::timestamp at time zone 'Europe/Madrid')
    )
    from t;
$$;

create or replace function public.temporada_actual()
returns jsonb
language sql
stable
set search_path = ''
as $$
    select public.temporada_de(now());
$$;

-- 4. Preu i manteniment -----------------------------------------------------------------------------------------------

-- Les andanes comprades del jugador que compten per al preu i la llicència (els regals, no).
create or replace function public.andanes_comprades(p_jugador uuid)
returns integer
language sql
stable
set search_path = ''
as $$
    select count(*)::int from public.concessions c
    where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now() and not c.regal;
$$;

-- El preu d'una andana per a un jugador: el de la concessió, més un 2 % per cada andana que ja té.
create or replace function public.preu_andana(p_viatgers_feiner numeric, p_jugador uuid)
returns numeric
language sql
stable
set search_path = ''
as $$
    select round(public.preu_concessio(p_viatgers_feiner)
        * (1 + (public.config_compra() ->> 'creixement_preu_per_andana')::numeric
               * coalesce(public.andanes_comprades(p_jugador), 0)), 2);
$$;

-- El manteniment diari d'una andana: un 5 % del que dona, més un 1 % per cada andana que té el jugador.
create or replace function public.manteniment_diari(p_node text, p_jugador uuid)
returns numeric
language sql
stable
set search_path = ''
as $$
    select round(coalesce(public.ingres_diari_concessio(p_node, 1.0), 0)
        * (public.config_compra() ->> 'quota_manteniment')::numeric
        * (1 + (public.config_compra() ->> 'creixement_manteniment_per_andana')::numeric * (
            select count(*) from public.concessions c
            where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
        )), 2);
$$;

-- 5. L'ingrés del jugador: les arribades ×1,5 amb concessió física, menys el manteniment ------------------------------

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

-- 6. Objectius (fites), insígnies i skins ------------------------------------------------------------------------------

create table if not exists public.fites (
    id text primary key,
    tipus text not null check (tipus in ('andanes', 'municipis', 'comarques', 'provincies', 'linies', 'linies_completes', 'checkins')),
    objectiu integer not null check (objectiu > 0),
    punts integer not null check (punts >= 0),
    skin text,
    ordre integer not null
);
comment on table public.fites is 'Objectius del joc: arribar a tantes andanes, municipis, comarques, províncies o línies.';

create table if not exists public.fites_jugador (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    fita text not null references public.fites (id) on delete cascade,
    aconseguida_el timestamptz not null default now(),
    primary key (jugador_id, fita)
);

create table if not exists public.skins (
    id text primary key,
    tipus text not null check (tipus in ('andana')),
    color char(7) not null check (color ~ '^#[0-9A-F]{6}$'),
    ordre integer not null
);
comment on table public.skins is 'Aparences de les andanes del jugador al mapa. Es guanyen amb fites i premis de temporada.';

create table if not exists public.skins_jugador (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    skin text not null references public.skins (id) on delete cascade,
    obtinguda_el timestamptz not null default now(),
    primary key (jugador_id, skin)
);

create table if not exists public.premis_temporada (
    temporada integer not null,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    posicio integer not null,
    punts integer not null,
    skin text references public.skins (id),
    primary key (temporada, jugador_id)
);
comment on table public.premis_temporada is 'La posició de cada jugador en acabar una temporada, i la skin que va guanyar.';

create table if not exists public.temporades_tancades (
    temporada integer primary key,
    tancada_el timestamptz not null default now()
);

alter table public.fites enable row level security;
alter table public.fites_jugador enable row level security;
alter table public.skins enable row level security;
alter table public.skins_jugador enable row level security;
alter table public.premis_temporada enable row level security;
alter table public.temporades_tancades enable row level security;
create policy "Tothom pot llegir les fites" on public.fites for select to authenticated using (true);
create policy "El jugador llegeix les seves fites" on public.fites_jugador for select to authenticated using (jugador_id = auth.uid());
create policy "Tothom pot llegir les skins" on public.skins for select to authenticated using (true);
create policy "El jugador llegeix les seves skins" on public.skins_jugador for select to authenticated using (jugador_id = auth.uid());
create policy "Tothom pot llegir els premis" on public.premis_temporada for select to authenticated using (true);
revoke insert, update, delete, truncate on public.fites, public.fites_jugador, public.skins, public.skins_jugador,
    public.premis_temporada, public.temporades_tancades from anon, authenticated;

insert into public.skins (id, tipus, color, ordre) values
    ('magenta', 'andana', '#FF3D7F', 1),
    ('blau_nit', 'andana', '#2E4A9E', 2),
    ('violeta', 'andana', '#7B3FE4', 3),
    ('taronja', 'andana', '#F28C28', 4),
    ('verd_via', 'andana', '#1F9D55', 5),
    ('bronze', 'andana', '#B0743B', 6),
    ('plata', 'andana', '#A7ADB5', 7),
    ('or', 'andana', '#E0A526', 8)
on conflict (id) do nothing;

insert into public.fites (id, tipus, objectiu, punts, skin, ordre) values
    ('andanes_1', 'andanes', 1, 10, null, 1),
    ('andanes_10', 'andanes', 10, 50, 'blau_nit', 2),
    ('andanes_50', 'andanes', 50, 200, null, 3),
    ('andanes_200', 'andanes', 200, 800, null, 4),
    ('municipis_5', 'municipis', 5, 50, null, 10),
    ('municipis_25', 'municipis', 25, 200, 'violeta', 11),
    ('municipis_100', 'municipis', 100, 800, null, 12),
    ('municipis_947', 'municipis', 947, 5000, null, 13),
    ('comarques_3', 'comarques', 3, 50, null, 20),
    ('comarques_10', 'comarques', 10, 300, 'taronja', 21),
    ('provincies_2', 'provincies', 2, 50, null, 30),
    ('provincies_4', 'provincies', 4, 300, null, 31),
    ('linies_5', 'linies', 5, 50, null, 40),
    ('linies_20', 'linies', 20, 300, null, 41),
    ('linia_completa_1', 'linies_completes', 1, 300, 'verd_via', 50),
    ('linia_completa_5', 'linies_completes', 5, 1500, null, 51),
    ('checkins_10', 'checkins', 10, 50, null, 60),
    ('checkins_100', 'checkins', 100, 500, null, 61)
on conflict (id) do nothing;

-- Totes les comarques: tantes com n'hi ha a les dades (municipis.csv).
insert into public.fites (id, tipus, objectiu, punts, skin, ordre)
select 'comarques_totes', 'comarques', count(distinct m.comarca), 3000, null, 22 from public.municipis m
having count(distinct m.comarca) > 0
on conflict (id) do nothing;

-- On és present el jugador: andanes comprades o de regal i estacions on té accions.
create or replace function public.nodes_del_jugador(p_jugador uuid)
returns table (node_id text)
language sql
stable
set search_path = ''
as $$
    select c.node_id from public.concessions c
    where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now()
    union
    select a.node_id from public.accions a where a.jugador_id = p_jugador and a.quantitat > 0;
$$;

-- El progrés del jugador en cada tipus de fita.
create or replace function public.progres_fites(p_jugador uuid)
returns jsonb
language sql
stable
set search_path = ''
as $$
    with meus as (select node_id from public.nodes_del_jugador(p_jugador)),
    linies as (
        select ln.linia_id, count(*) as total, count(*) filter (where ln.node_id in (select node_id from meus)) as meves
        from public.linia_nodes ln
        where ln.linia_id in (select l2.linia_id from public.linia_nodes l2 where l2.node_id in (select node_id from meus))
        group by ln.linia_id
    )
    select jsonb_build_object(
        'andanes', (select count(*) from meus),
        'municipis', (select count(distinct n.municipi) from public.nodes n where n.id in (select node_id from meus) and n.municipi is not null),
        'comarques', (select count(distinct n.comarca) from public.nodes n where n.id in (select node_id from meus) and n.comarca is not null),
        'provincies', (select count(distinct left(n.municipi, 2)) from public.nodes n where n.id in (select node_id from meus) and n.municipi is not null),
        'linies', (select count(*) from linies),
        'linies_completes', (select count(*) from linies where meves = total),
        'checkins', (
            select count(*) from public.checkins k where k.jugador_id = p_jugador
              and k.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent', 'concessio')
        )
    );
$$;

-- Dona al jugador les fites que ja ha aconseguit i encara no tenia (i la seva skin). Torna les noves.
create or replace function public.revisa_fites(p_jugador uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_progres jsonb := public.progres_fites(p_jugador);
    v_noves jsonb;
begin
    with noves as (
        insert into public.fites_jugador (jugador_id, fita)
        select p_jugador, f.id
        from public.fites f
        where (v_progres ->> f.tipus)::int >= f.objectiu
          and not exists (select 1 from public.fites_jugador fj where fj.jugador_id = p_jugador and fj.fita = f.id)
        on conflict do nothing
        returning fita
    )
    select coalesce(jsonb_agg(f.id order by f.ordre), '[]'::jsonb) into v_noves
    from noves join public.fites f on f.id = noves.fita;

    insert into public.skins_jugador (jugador_id, skin)
    select p_jugador, f.skin
    from public.fites f
    where f.id in (select jsonb_array_elements_text(v_noves)) and f.skin is not null
    on conflict do nothing;

    return v_noves;
end;
$$;

-- 7. Comprar ----------------------------------------------------------------------------------------------------------

-- Dona una andana al jugador, cobrant-la. [p_presencial]: ve d'un check-in (concessió fins al final de la temporada).
create or replace function public.adquireix_andana(p_jugador uuid, p_node text, p_presencial boolean, p_mode_dev boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_node record;
    v_perfil record;
    v_cost numeric(12, 2);
    v_maxim integer;
    v_prestigi integer := 30;
    v_fi timestamptz := (public.temporada_actual() ->> 'fi')::timestamptz;
    v_nou record;
begin
    select n.id, n.nom, n.viatgers_feiner, n.municipi, n.comarca into v_node from public.nodes n where n.id = p_node;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta andana no existeix.');
    end if;
    if v_node.viatgers_feiner >= 20000 then
        return jsonb_build_object('status', 'gran_estacio', 'missatge', v_node.nom || ' és una gran estació: s''hi compren accions.');
    end if;
    if exists (select 1 from public.concessions c where c.jugador_id = p_jugador and c.node_id = p_node and c.activa and c.caduca_el >= now()) then
        return jsonb_build_object('status', 'ja_es_teva', 'missatge', v_node.nom || ' ja és teva.');
    end if;

    select p.saldo, p.nivell into v_perfil from public.perfils p where p.id = p_jugador for update;
    v_maxim := 10 + 2 * v_perfil.nivell;
    if not p_mode_dev and public.andanes_comprades(p_jugador) >= v_maxim then
        return jsonb_build_object('status', 'limit_llicencia',
            'missatge', 'Has assolit el límit de la llicència (' || v_maxim || ' andanes). Puja de nivell!');
    end if;

    v_cost := case when p_mode_dev then 0 else public.preu_andana(v_node.viatgers_feiner, p_jugador) end;
    if v_perfil.saldo < v_cost then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: calen ' || v_cost || ' €.');
    end if;

    -- El prestigi de municipi i comarca nous es guanya anant-hi (fer_checkin), no comprant des de casa.

    insert into public.concessions (jugador_id, node_id, adquirida_el, caduca_el, activa, regal, presencial_fins)
    values (p_jugador, p_node, now(), now() + interval '100 years', true, false, case when p_presencial then v_fi end)
    on conflict (jugador_id, node_id) do update
        set adquirida_el = now(), caduca_el = excluded.caduca_el, activa = true, presencial_fins = excluded.presencial_fins;

    update public.perfils
    set saldo = saldo - v_cost,
        prestigi = prestigi + v_prestigi,
        nivell = public.calcular_nivell(prestigi + v_prestigi),
        actualitzat_el = now()
    where id = p_jugador
    returning saldo, prestigi, nivell into v_nou;

    insert into public.moviments (jugador_id, concepte, import, node_id, tipus)
    values (p_jugador, 'Compra: ' || v_node.nom, -v_cost, p_node, 'compra');

    return jsonb_build_object(
        'status', 'valid',
        'tipus_accio', case when p_presencial then 'conquesta' else 'compra' end,
        'missatge', case when p_presencial
            then 'Teva! ' || v_node.nom || ' amb concessió fins al final de la temporada: cada arribada ×1,5.'
            else 'Teva! Has comprat ' || v_node.nom || '. Fes-hi check-in per cobrar ×1,5.' end,
        'cost', v_cost,
        'saldo', v_nou.saldo,
        'prestigi', v_nou.prestigi,
        'nivell', v_nou.nivell,
        'fites', public.revisa_fites(p_jugador)
    );
end;
$$;

-- Comprar una andana des de qualsevol lloc.
create or replace function public.compra_andana(p_node text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_mode_dev boolean;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select coalesce(p.mode_dev, false) into v_mode_dev from public.perfils p where p.id = v_user_id;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'El perfil no existeix.');
    end if;
    return public.adquireix_andana(v_user_id, p_node, false, v_mode_dev);
end;
$$;

-- Comprar accions d'una gran estació des de qualsevol lloc (com a màxim 20 per jugador i 100 per estació).
create or replace function public.compra_accions(p_node text, p_quantitat integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_node record;
    v_perfil record;
    v_meves integer;
    v_venudes integer;
    v_comprar integer;
    v_cost numeric(12, 2);
    v_nou record;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select n.id, n.nom, n.viatgers_feiner into v_node from public.nodes n where n.id = p_node;
    if not found or v_node.viatgers_feiner < 20000 then
        return jsonb_build_object('status', 'error', 'missatge', 'Només es compren accions de les grans estacions.');
    end if;
    select p.saldo, coalesce(p.mode_dev, false) as mode_dev into v_perfil from public.perfils p where p.id = v_user_id for update;
    select coalesce(sum(a.quantitat) filter (where a.jugador_id = v_user_id), 0), coalesce(sum(a.quantitat), 0)
    into v_meves, v_venudes
    from public.accions a where a.node_id = p_node;
    v_comprar := least(greatest(coalesce(p_quantitat, 0), 0), 20 - v_meves, 100 - v_venudes);
    if v_comprar <= 0 then
        return jsonb_build_object('status', 'sense_accions', 'missatge', 'No pots comprar més accions de ' || v_node.nom || '.');
    end if;
    v_cost := case when v_perfil.mode_dev then 0 else v_comprar * public.preu_accio(v_node.viatgers_feiner) end;
    if v_perfil.saldo < v_cost then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: calen ' || v_cost || ' €.');
    end if;

    insert into public.accions (node_id, jugador_id, quantitat, darrera_visita_el)
    values (p_node, v_user_id, v_comprar, now())
    on conflict (node_id, jugador_id) do update set quantitat = public.accions.quantitat + v_comprar;

    update public.perfils
    set saldo = saldo - v_cost, prestigi = prestigi + 2 * v_comprar,
        nivell = public.calcular_nivell(prestigi + 2 * v_comprar), actualitzat_el = now()
    where id = v_user_id
    returning saldo, prestigi, nivell into v_nou;

    insert into public.moviments (jugador_id, concepte, import, node_id, tipus)
    values (v_user_id, 'Compra de ' || v_comprar || ' accions: ' || v_node.nom, -v_cost, p_node, 'compra_accions');

    return jsonb_build_object(
        'status', 'valid', 'tipus_accio', 'accions',
        'missatge', '+' || v_comprar || ' accions de ' || v_node.nom || '. Fes-hi check-in per cobrar els dividends ×1,5.',
        'cost', v_cost, 'saldo', v_nou.saldo, 'prestigi', v_nou.prestigi, 'nivell', v_nou.nivell,
        'accions_comprades', v_comprar, 'accions_totals', v_meves + v_comprar,
        'fites', public.revisa_fites(v_user_id)
    );
end;
$$;

-- Buscar andanes per comprar: pel nom o el municipi, o les més properes si no s'escriu res.
create or replace function public.cerca_andanes(p_text text, p_lat double precision, p_lon double precision)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
    with punt as (
        select case when p_lat is null or p_lon is null then null
            else extensions.st_setsrid(extensions.st_makepoint(p_lon, p_lat), 4326)::extensions.geography end as g
    ),
    text_net as (select nullif(trim(coalesce(p_text, '')), '') as t)
    select coalesce(jsonb_agg(y.x order by y.ordre), '[]'::jsonb)
    from (
        select jsonb_build_object(
            'id', n.id, 'nom', n.nom, 'mode', n.mode, 'operador', n.operador, 'linies', n.linies,
            'municipi', m.nom, 'viatgers_feiner', n.viatgers_feiner, 'font_afluencia', n.font_afluencia,
            'gran_estacio', n.viatgers_feiner >= 20000,
            'preu', case when n.viatgers_feiner >= 20000 then public.preu_accio(n.viatgers_feiner)
                else public.preu_andana(n.viatgers_feiner, auth.uid()) end,
            'ingres_diari', public.ingres_diari_concessio(n.id, 1.0),
            'teva', exists (select 1 from public.nodes_del_jugador(auth.uid()) d where d.node_id = n.id),
            'distancia_metres', case when punt.g is null then null else round(extensions.st_distance(n.posicio, punt.g)) end
        ) as x,
        row_number() over (order by
            -- Amb text: primer el nom exacte, després el que hi comença i les estacions abans que les parades de bus.
            case when text_net.t is null then 0
                 when lower(n.nom) = lower(text_net.t) then 0
                 when n.nom ilike text_net.t || '%' then 1
                 when n.nom ilike '%' || text_net.t || '%' then 2
                 else 3 end,
            case when text_net.t is not null and n.mode = 'bus' then 1 else 0 end,
            case when punt.g is null then 0 else extensions.st_distance(n.posicio, punt.g) end,
            n.viatgers_feiner desc) as ordre
        from public.nodes n
        left join public.municipis m on m.codi = n.municipi
        cross join punt
        cross join text_net
        where (text_net.t is null and punt.g is not null and extensions.st_dwithin(n.posicio, punt.g, 3000))
           or (text_net.t is not null and (n.nom ilike '%' || text_net.t || '%' or m.nom ilike '%' || text_net.t || '%'))
        order by ordre
        limit 40
    ) y;
$$;

-- 8. Check-in: la concessió física fins al final de la temporada ------------------------------------------------------

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
    v_punt                extensions.geography;
    v_dist_metres         double precision;
    v_radi                double precision;
    v_sobrant             double precision;
    v_darrer_checkin      record;
    v_hores               double precision;
    v_dist_km             double precision;
    v_prestigi_afegir     integer := 0;
    v_fi                  timestamptz := (public.temporada_actual() ->> 'fi')::timestamptz;
    v_concessio           record;
    v_accions             integer;
    v_resultat            text;
    v_resposta            jsonb;
    v_nou                 record;
-- Les validacions (GPS, velocitat, distància i mode desenvolupador) són les de la fase 4.
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

    perform 1 from public.perfils where id = v_user_id for update;

    -- Validació: màxim 1 acció/check-in per node i dia.
    if exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id
          and c.node_id = p_node_id
          and c.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent', 'concessio')
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

    -- Primer check-in en aquesta andana (+20), i en un municipi (+50) o una comarca (+200) on no hi havia anat mai.
    if not exists (
        select 1 from public.checkins c
        where c.jugador_id = v_user_id and c.node_id = p_node_id
          and c.resultat not in ('massa_lluny', 'precisio_insuficient', 'en_moviment', 'velocitat_impossible')
    ) then
        v_prestigi_afegir := 20;
    end if;
    if v_node.municipi is not null and not exists (
        select 1 from public.checkins c join public.nodes n2 on n2.id = c.node_id
        where c.jugador_id = v_user_id and n2.municipi = v_node.municipi
          and c.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent', 'concessio')
    ) then
        v_prestigi_afegir := v_prestigi_afegir + 50;
    end if;
    if v_node.comarca is not null and not exists (
        select 1 from public.checkins c join public.nodes n2 on n2.id = c.node_id
        where c.jugador_id = v_user_id and n2.comarca = v_node.comarca
          and c.resultat in ('conquesta', 'renovacio', 'reactivacio', 'accions', 'visita_estacio', 'vigent', 'concessio')
    ) then
        v_prestigi_afegir := v_prestigi_afegir + 200;
    end if;

    if v_node.viatgers_feiner >= 20000 then
        -- Gran estació: la visita fa que els dividends vagin ×1,5 fins al final de la temporada.
        select coalesce(sum(a.quantitat), 0) into v_accions
        from public.accions a where a.node_id = p_node_id and a.jugador_id = v_user_id;
        if v_accions = 0 then
            return jsonb_build_object('status', 'sense_accions',
                'missatge', v_node.nom || ' és una gran estació: compra''n accions a la pestanya Compra i torna-hi.');
        end if;
        update public.accions set darrera_visita_el = now(), presencial_fins = v_fi
        where node_id = p_node_id and jugador_id = v_user_id;
        v_resultat := 'visita_estacio';
        v_prestigi_afegir := v_prestigi_afegir + 2;
        v_resposta := jsonb_build_object('status', 'valid', 'tipus_accio', 'visita_estacio',
            'missatge', 'Visita a ' || v_node.nom || ': els dividends van ×1,5 fins al final de la temporada.',
            'accions_posseides', v_accions);
    else
        select c.id, c.presencial_fins into v_concessio
        from public.concessions c
        where c.jugador_id = v_user_id and c.node_id = p_node_id and c.activa and c.caduca_el >= now();
        if found then
            if v_concessio.presencial_fins >= v_fi then
                v_resultat := 'vigent';
                v_resposta := jsonb_build_object('status', 'valid', 'tipus_accio', 'vigent',
                    'missatge', 'Ja tens la concessió de ' || v_node.nom || ' fins al final de la temporada.');
            else
                update public.concessions set presencial_fins = v_fi where id = v_concessio.id;
                v_resultat := 'concessio';
                v_prestigi_afegir := v_prestigi_afegir + 10;
                v_resposta := jsonb_build_object('status', 'valid', 'tipus_accio', 'concessio',
                    'missatge', 'Concessió de ' || v_node.nom || ' fins al final de la temporada: cada arribada ×1,5!');
            end if;
        else
            -- No és seva: el check-in la compra amb la concessió física.
            v_resposta := public.adquireix_andana(v_user_id, p_node_id, true, v_mode_dev);
            if v_resposta ->> 'status' <> 'valid' then
                return v_resposta;
            end if;
            v_resultat := 'conquesta';
        end if;
    end if;

    insert into public.checkins (jugador_id, node_id, posicio, precisio_metres, velocitat_kmh, resultat)
    values (v_user_id, p_node_id, v_punt, p_precisio, p_velocitat, v_resultat);

    update public.perfils
    set prestigi = prestigi + v_prestigi_afegir,
        nivell = public.calcular_nivell(prestigi + v_prestigi_afegir),
        actualitzat_el = now()
    where id = v_user_id
    returning saldo, prestigi, nivell into v_nou;

    return v_resposta || jsonb_build_object(
        'saldo', v_nou.saldo,
        'prestigi', v_nou.prestigi,
        'nivell', v_nou.nivell,
        'dies_restants', greatest(0, ceil(extract(epoch from (v_fi - now())) / 86400.0))::int,
        'fites', coalesce(v_resposta -> 'fites', '[]'::jsonb) || public.revisa_fites(v_user_id)
    );
end;
$$;

-- 9. Nom públic i skin ------------------------------------------------------------------------------------------------

create or replace function public.nom_public(p_jugador uuid)
returns text
language sql
stable
set search_path = ''
as $$
    select coalesce(p.nom, 'Operador ' || upper(left(replace(p.id::text, '-', ''), 4)))
    from public.perfils p where p.id = p_jugador;
$$;

create or replace function public.desa_nom(p_nom text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_nom text := regexp_replace(trim(coalesce(p_nom, '')), '\s+', ' ', 'g');
begin
    if auth.uid() is null then
        raise exception 'Cal iniciar sessió.';
    end if;
    if char_length(v_nom) not between 3 and 20 or v_nom !~ '^[[:alnum:] ._·''-]+$' then
        raise exception 'El nom ha de tenir entre 3 i 20 lletres, xifres o espais.';
    end if;
    update public.perfils set nom = v_nom, actualitzat_el = now() where id = auth.uid();
    return v_nom;
end;
$$;

create or replace function public.desa_skin(p_skin text)
returns text
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Cal iniciar sessió.';
    end if;
    if p_skin <> 'magenta' and not exists (
        select 1 from public.skins_jugador s where s.jugador_id = auth.uid() and s.skin = p_skin
    ) then
        raise exception 'Encara no tens aquesta skin.';
    end if;
    update public.perfils set skin_andana = p_skin, actualitzat_el = now() where id = auth.uid();
    return p_skin;
end;
$$;

-- 10. Punts i rànquing de temporada: compta el que es fa a la temporada, no el que es té ------------------------------

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
    total as (select p.jugador_id, sum(p.p)::int as punts from punts p group by p.jugador_id having sum(p.p) > 0)
    select t.jugador_id, t.punts, (rank() over (order by t.punts desc))::int from total t;
$$;

-- L'estat de la temporada per a la pantalla del jugador: rànquing, objectius, insígnies i skins.
create or replace function public.estat_temporada()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_temporada jsonb := public.temporada_actual();
    v_numero integer := (v_temporada ->> 'numero')::int;
    v_progres jsonb;
begin
    if v_user_id is null then
        return null;
    end if;
    v_progres := public.progres_fites(v_user_id);
    return jsonb_build_object(
        'temporada', v_temporada,
        'nom', public.nom_public(v_user_id),
        'jo', (select jsonb_build_object('punts', r.punts, 'posicio', r.posicio)
               from public.ranking_de(v_numero) r where r.jugador_id = v_user_id),
        'jugadors', (select count(*) from public.ranking_de(v_numero)),
        'ranking', (
            select coalesce(jsonb_agg(jsonb_build_object(
                'posicio', r.posicio, 'nom', public.nom_public(r.jugador_id), 'punts', r.punts,
                'avatar', p.avatar, 'jo', r.jugador_id = v_user_id
            ) order by r.posicio, r.punts desc), '[]'::jsonb)
            from (select * from public.ranking_de(v_numero) order by posicio limit 20) r
            join public.perfils p on p.id = r.jugador_id
        ),
        'fites', (
            select jsonb_agg(jsonb_build_object(
                'id', f.id, 'tipus', f.tipus, 'objectiu', f.objectiu, 'punts', f.punts, 'skin', f.skin,
                'progres', least((v_progres ->> f.tipus)::int, f.objectiu),
                'aconseguida_el', fj.aconseguida_el
            ) order by f.ordre)
            from public.fites f
            left join public.fites_jugador fj on fj.fita = f.id and fj.jugador_id = v_user_id
        ),
        'premis', (
            select coalesce(jsonb_agg(jsonb_build_object('temporada', pt.temporada, 'posicio', pt.posicio,
                'punts', pt.punts, 'skin', pt.skin) order by pt.temporada desc), '[]'::jsonb)
            from public.premis_temporada pt where pt.jugador_id = v_user_id
        ),
        'skins', (
            select jsonb_agg(jsonb_build_object(
                'id', s.id, 'color', s.color,
                'teva', s.id = 'magenta' or exists (select 1 from public.skins_jugador sj where sj.jugador_id = v_user_id and sj.skin = s.id),
                'posada', s.id = (select p.skin_andana from public.perfils p where p.id = v_user_id)
            ) order by s.ordre)
            from public.skins s
        )
    );
end;
$$;

-- Tanca les temporades acabades: guarda la posició de cada jugador i dona les skins d'or, plata i bronze.
create or replace function public.tanca_temporades()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_actual integer := (public.temporada_actual() ->> 'numero')::int;
    v_temporada integer;
    v_tancades integer := 0;
    v_or integer := (public.config_temporades() ->> 'premi_or')::int;
    v_plata integer := (public.config_temporades() ->> 'premi_plata')::int;
    v_bronze integer := (public.config_temporades() ->> 'premi_bronze')::int;
begin
    for v_temporada in
        select t from generate_series(1, v_actual - 1) t
        where not exists (select 1 from public.temporades_tancades tt where tt.temporada = t)
    loop
        insert into public.premis_temporada (temporada, jugador_id, posicio, punts, skin)
        select v_temporada, r.jugador_id, r.posicio, r.punts,
               case when r.posicio <= v_or then 'or' when r.posicio <= v_plata then 'plata'
                    when r.posicio <= v_bronze then 'bronze' end
        from public.ranking_de(v_temporada) r
        on conflict do nothing;

        insert into public.skins_jugador (jugador_id, skin)
        select pt.jugador_id, pt.skin from public.premis_temporada pt
        where pt.temporada = v_temporada and pt.skin is not null
        on conflict do nothing;

        insert into public.temporades_tancades (temporada) values (v_temporada);
        v_tancades := v_tancades + 1;
    end loop;
    return v_tancades;
end;
$$;

-- 11. Les andanes del jugador (avisos i mapa): ×1,5 amb concessió física i el color de la seva skin ------------------

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
                    / n.expedicions_feiner, 2) else 0 end
        ) order by n.nom)
        from public.concessions c
        join public.nodes n on n.id = c.node_id
        where c.jugador_id = v_user_id and c.activa and c.caduca_el >= now()
    ), '[]'::jsonb);
end;
$$;

-- 12. Les concessions que ja hi havia passen al model nou ------------------------------------------------------------
--     Totes queden comprades per sempre (també les caducades). Les de check-in tenen la concessió física fins al final
--     de la temporada; les accions visitades els darrers 30 dies, també.
update public.concessions
set caduca_el = greatest(caduca_el, now() + interval '100 years'),
    activa = true,
    presencial_fins = case when not regal then (public.temporada_actual() ->> 'fi')::timestamptz end
where caduca_el < now() + interval '50 years' or not activa;

update public.accions
set presencial_fins = (public.temporada_actual() ->> 'fi')::timestamptz
where darrera_visita_el >= now() - interval '30 days';

-- Les fites que ja s'havien aconseguit.
do $fites$
begin
    perform public.revisa_fites(p.id) from public.perfils p;
end;
$fites$;

-- 13. estat_imperi i nodes_a_prop_jugador coneixen la concessió física -----------------------------------------------
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
        'ingres_diari', public.ingres_diari(n.viatgers_feiner),
        'caduca_el', c.caduca_el,
        'dies_restants', case when c.presencial_fins >= now()
            then greatest(0, ceil(extract(epoch from (c.presencial_fins - now())) / 86400.0)::int) end,
        'regal', c.regal,
        'presencial_fins', c.presencial_fins,
        'multiplicador', case when c.presencial_fins >= now() then public.multiplicador_concessio() else 1 end,
        'manteniment_diari', public.manteniment_diari(c.node_id, v_user_id)
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

drop function if exists public.nodes_a_prop_jugador(double precision, double precision, double precision, integer);

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
    accions_disponibles integer,
    regal boolean,
    presencial boolean
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
                else public.preu_andana(n.viatgers_feiner, auth.uid()) end as preu,
           public.ingres_diari(n.viatgers_feiner) as ingres_diari,
           (c.id is not null and c.activa and c.caduca_el >= now()) as te_concessio,
           (c.id is not null and (not c.activa or c.caduca_el < now())) as concessio_caducada,
           case when c.presencial_fins >= now()
                then greatest(0, ceil(extract(epoch from (c.presencial_fins - now())) / 86400.0)::int) end as dies_restants,
           coalesce(a.quantitat, 0)::int as accions_jugador,
           case when n.viatgers_feiner >= 20000 then (100 - coalesce(tot.total, 0)) else 0 end as accions_disponibles,
           coalesce(c.regal, false) as regal,
           coalesce(c.presencial_fins >= now(), false) or coalesce(a.presencial_fins >= now(), false) as presencial
    from public.nodes n
    cross join punt
    left join public.concessions c on c.node_id = n.id and c.jugador_id = auth.uid()
    left join public.accions a on a.node_id = n.id and a.jugador_id = auth.uid()
    left join accions_totals tot on tot.node_id = n.id
    where extensions.st_dwithin(n.posicio, punt.g, least(radi_metres, 5000))
    order by extensions.st_distance(n.posicio, punt.g)
    limit least(greatest(maxim, 1), 200);
$$;
-- 14. Tancar les temporades cada dia (pg_cron). Si pg_cron no està activat, només avisa.
do $cron$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.unschedule(jobid) from cron.job where jobname = 'tanca_temporades';
        perform cron.schedule('tanca_temporades', '10 2 * * *', 'select public.tanca_temporades();');
    else
        raise notice 'pg_cron no està activat: les temporades es tancaran quan s''activi.';
    end if;
end;
$cron$;

-- 15. Permisos ----------------------------------------------------------------------------------------------------------
revoke execute on function public.adquireix_andana(uuid, text, boolean, boolean) from public, anon, authenticated;
revoke execute on function public.revisa_fites(uuid) from public, anon, authenticated;
revoke execute on function public.progres_fites(uuid) from public, anon, authenticated;
revoke execute on function public.ranking_de(integer) from public, anon, authenticated;
revoke execute on function public.tanca_temporades() from public, anon, authenticated;
revoke execute on function public.ingres_jugador(uuid, boolean, numeric) from public, anon, authenticated;
revoke execute on function public.nodes_del_jugador(uuid) from public, anon, authenticated;
revoke execute on function public.manteniment_diari(text, uuid) from public, anon, authenticated;
revoke execute on function public.compra_andana(text) from public, anon;
revoke execute on function public.compra_accions(text, integer) from public, anon;
revoke execute on function public.cerca_andanes(text, double precision, double precision) from public, anon;
revoke execute on function public.estat_temporada() from public, anon;
revoke execute on function public.desa_nom(text) from public, anon;
revoke execute on function public.desa_skin(text) from public, anon;
grant execute on function public.adquireix_andana(uuid, text, boolean, boolean) to service_role;
grant execute on function public.tanca_temporades() to service_role;
grant execute on function public.compra_andana(text) to authenticated;
grant execute on function public.compra_accions(text, integer) to authenticated;
grant execute on function public.cerca_andanes(text, double precision, double precision) to authenticated;
grant execute on function public.estat_temporada() to authenticated;
grant execute on function public.desa_nom(text) to authenticated;
grant execute on function public.desa_skin(text) to authenticated;
grant execute on function public.fer_checkin(text, double precision, double precision, double precision, double precision) to authenticated;
grant execute on function public.estat_imperi() to authenticated;
grant execute on function public.nodes_a_prop_jugador(double precision, double precision, double precision, integer) to anon, authenticated;
grant execute on function public.andanes_jugador() to authenticated;
