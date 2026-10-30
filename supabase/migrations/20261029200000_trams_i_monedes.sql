-- Fase 4, MVP: trams i monedes (GDD 4.0).
-- Un tram és una línia real; el jugador en compra de petits i de mitjans, que vénen amb una andana, i els millora amb
-- una andana més cada vegada, fins a totes les parades del tram. Guanya monedes cada cop que un vehicle d'un tram seu
-- arriba a una de les seves andanes. L'economia és del servidor: l'app només demana l'estat i les compres.
-- Totes les compres, els saldos i els moviments porten la temporada, perquè les temporades de 2 setmanes (més
-- endavant) siguin començar-ne una de nova sense esborrar res. La quantitat de cada compra deixa lloc a les
-- fraccions dels trams grans.

-- La configuració del joc (dades/configuracio_joc.json), que carrega scripts/carrega_postgis.py: és l'únic lloc on
-- hi ha els valors, i les funcions la llegeixen d'aquí.
create table public.configuracio_joc (
    id smallint primary key default 1 check (id = 1),
    valors jsonb not null,
    carregat_el timestamptz not null default now()
);

comment on table public.configuracio_joc is 'Còpia de dades/configuracio_joc.json (scripts/carrega_postgis.py).';

create type public.mida_tram as enum ('petit', 'mitja', 'gran');

create table public.trams (
    id text primary key,
    operador text not null,
    mode public.mode_transport not null,
    codi text not null,
    nom text not null,
    color char(6) check (color ~ '^[0-9A-Fa-f]{6}$'),
    parades integer not null check (parades > 0),
    arribades_feiner integer not null check (arribades_feiner > 0),
    mida public.mida_tram not null,
    -- El preu de compra, que dona la primera andana.
    preu integer check (preu > 0),
    linies text[] not null default '{}',
    -- Els grans no tenen preu: es compraran per fraccions.
    constraint trams_preu_segons_mida check ((mida = 'gran') = (preu is null))
);

comment on table public.trams is 'Els trams del joc (GDD 4.0): cada línia real amb tots els seus sentits, amb la mida i el preu (dades/generades/trams.csv).';

create table public.temporades (
    id serial primary key,
    nom text not null,
    inici timestamptz not null,
    fi timestamptz,
    check (fi is null or fi > inici)
);

comment on table public.temporades is 'Temporades del joc. De moment n''hi ha una d''oberta; les de 2 setmanes, amb rànquing i reinici, vindran més endavant (GDD 4.0).';

insert into public.temporades (nom, inici) values ('Temporada 1', '2026-10-29 00:00:00+01');

create table public.partides (
    jugador_id uuid not null references auth.users (id) on delete cascade,
    temporada_id integer not null references public.temporades (id),
    saldo bigint not null check (saldo >= 0),
    creat_el timestamptz not null default now(),
    primary key (jugador_id, temporada_id)
);

comment on table public.partides is 'El saldo de monedes (catalins) de cada jugador en una temporada. Sempre és la suma dels seus moviments.';

create table public.trams_jugador (
    jugador_id uuid not null,
    temporada_id integer not null,
    tram_id text not null references public.trams (id) on delete cascade,
    quantitat integer not null default 1 check (quantitat > 0),
    -- Les andanes (parades) del tram que té el jugador: una en comprar-lo, i una més a cada millora.
    andanes integer not null default 1 check (andanes >= 1),
    comprat_el timestamptz not null default now(),
    liquidat_fins timestamptz not null default now(),
    -- Les arribades estimades que encara no fan una arribada sencera (GDD 4.0, quan es cobra).
    fraccio_arribada double precision not null default 0 check (fraccio_arribada >= 0 and fraccio_arribada < 1),
    primary key (jugador_id, temporada_id, tram_id),
    foreign key (jugador_id, temporada_id) references public.partides (jugador_id, temporada_id) on delete cascade
);

comment on table public.trams_jugador is 'Els trams de cada jugador. Un tram no és exclusiu: el poden tenir molts jugadors. quantitat és 1 per a un tram sencer.';

create index trams_jugador_tram_idx on public.trams_jugador (tram_id);

create type public.concepte_moviment as enum ('capital_inicial', 'compra_tram', 'millora_tram', 'arribades');

create table public.moviments (
    id bigserial primary key,
    jugador_id uuid not null,
    temporada_id integer not null,
    concepte public.concepte_moviment not null,
    monedes bigint not null,
    tram_id text references public.trams (id) on delete set null,
    arribades integer check (arribades >= 0),
    creat_el timestamptz not null default now(),
    foreign key (jugador_id, temporada_id) references public.partides (jugador_id, temporada_id) on delete cascade
);

comment on table public.moviments is 'Llibre d''apunts (GDD 4.8): el saldo és la suma de les monedes de cada temporada.';

create index moviments_jugador_idx on public.moviments (jugador_id, temporada_id, creat_el desc);

-- RLS: les dades del joc les llegeix tothom; cada jugador llegeix només les seves partides. Ningú escriu des de l'app:
-- tot passa per les funcions de sota.
alter table public.configuracio_joc enable row level security;
alter table public.trams enable row level security;
alter table public.temporades enable row level security;
alter table public.partides enable row level security;
alter table public.trams_jugador enable row level security;
alter table public.moviments enable row level security;

create policy "Tothom pot llegir la configuració" on public.configuracio_joc for select to anon, authenticated using (true);
create policy "Tothom pot llegir els trams" on public.trams for select to anon, authenticated using (true);
create policy "Tothom pot llegir les temporades" on public.temporades for select to anon, authenticated using (true);
create policy "El jugador llegeix les seves partides" on public.partides for select to authenticated
    using (jugador_id = (select auth.uid()));
create policy "El jugador llegeix els seus trams" on public.trams_jugador for select to authenticated
    using (jugador_id = (select auth.uid()));
create policy "El jugador llegeix els seus moviments" on public.moviments for select to authenticated
    using (jugador_id = (select auth.uid()));

revoke insert, update, delete, truncate on public.configuracio_joc, public.trams, public.temporades, public.partides,
    public.trams_jugador, public.moviments from anon, authenticated;

-- Un valor de la configuració del joc: config_joc('trams', 'capitalInicial').
create function public.config_joc(bloc text, clau text)
returns double precision
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    valor double precision;
begin
    select (c.valors -> bloc ->> clau)::double precision into valor from public.configuracio_joc c where c.id = 1;
    if valor is null then
        raise exception 'Falta la configuració del joc: %.%', bloc, clau
            using hint = 'Carrega-la amb scripts/carrega_postgis.py';
    end if;
    return valor;
end;
$$;

-- La temporada oberta ara.
create function public.temporada_actual()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
    select t.id from public.temporades t
    where t.inici <= now() and (t.fi is null or t.fi > now())
    order by t.inici desc
    limit 1;
$$;

-- Arribades estimades d'un tram entre dos instants (GDD 4.0): les d'un feiner, repartides uniformement entre l'inici i
-- la fi del servei (hora de Barcelona), i multiplicades per factorNoFeiner els dissabtes i diumenges [estimat].
create function public.arribades_entre(arribades_feiner integer, des_de timestamptz, fins_a timestamptz)
returns double precision
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    zona constant text := 'Europe/Madrid';
    hora_inici double precision := public.config_joc('trams', 'horaIniciServei');
    hora_fi double precision := public.config_joc('trams', 'horaFiServei');
    factor_no_feiner double precision := public.config_joc('calendari', 'factorNoFeiner');
    dia date;
    inici_servei timestamptz;
    fi_servei timestamptz;
    solapament double precision;
    total double precision := 0;
begin
    if fins_a <= des_de then
        return 0;
    end if;
    -- Com a molt, una temporada llarga: si fa més temps que no es liquida, es compta des d'aquí.
    des_de := greatest(des_de, fins_a - interval '31 days');
    dia := (des_de at time zone zona)::date;
    while dia <= (fins_a at time zone zona)::date loop
        inici_servei := (dia + make_interval(secs => hora_inici * 3600)) at time zone zona;
        fi_servei := (dia + make_interval(secs => hora_fi * 3600)) at time zone zona;
        solapament := extract(epoch from least(fi_servei, fins_a) - greatest(inici_servei, des_de));
        if solapament > 0 then
            total := total + arribades_feiner * solapament / extract(epoch from fi_servei - inici_servei)
                * (case when extract(isodow from dia) in (6, 7) then factor_no_feiner else 1 end);
        end if;
        dia := dia + 1;
    end loop;
    return total;
end;
$$;

-- El cost d'afegir una andana a un tram que en té `andanes` (GDD 4.0): preu de compra × creixementMillora^andanes.
create function public.cost_millora(preu integer, andanes integer)
returns integer
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    pas integer := public.config_joc('trams', 'arrodonimentPreu')::integer;
begin
    return greatest(pas,
        floor(preu * power(public.config_joc('trams', 'creixementMillora'), andanes) / pas + 0.5)::integer * pas);
end;
$$;

-- Cobra les arribades a andana dels trams d'un jugador fins ara. Cada andana rep les arribades d'una parada mitjana
-- del tram (les d'un feiner repartides entre les seves parades). Cada arribada dona un nombre enter de monedes a l'atzar
-- entre el mínim i el màxim de la mida del tram. Torna les monedes guanyades.
create function public.liquida_partida(jugador uuid, temporada integer)
returns bigint
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    tram record;
    estimades double precision;
    arribades integer;
    minim integer;
    maxim integer;
    monedes bigint;
    guanyat bigint := 0;
    ara constant timestamptz := now();
begin
    perform 1 from public.partides p where p.jugador_id = jugador and p.temporada_id = temporada for update;
    for tram in
        select tj.tram_id, tj.quantitat, tj.andanes, tj.liquidat_fins, tj.fraccio_arribada, t.arribades_feiner,
               t.parades, t.mida
        from public.trams_jugador tj join public.trams t on t.id = tj.tram_id
        where tj.jugador_id = jugador and tj.temporada_id = temporada and t.mida <> 'gran'
    loop
        estimades := tram.fraccio_arribada
            + public.arribades_entre(tram.arribades_feiner, tram.liquidat_fins, ara)
                * tram.andanes / tram.parades * tram.quantitat;
        arribades := floor(estimades)::integer;
        if tram.mida = 'petit' then
            minim := public.config_joc('trams', 'recompensaPetitMinima')::integer;
            maxim := public.config_joc('trams', 'recompensaPetitMaxima')::integer;
        else
            minim := public.config_joc('trams', 'recompensaMitjaMinima')::integer;
            maxim := public.config_joc('trams', 'recompensaMitjaMaxima')::integer;
        end if;
        select coalesce(sum(minim + floor(random() * (maxim - minim + 1))), 0)::bigint into monedes
        from generate_series(1, arribades);
        update public.trams_jugador tj
        set liquidat_fins = ara, fraccio_arribada = least(greatest(estimades - arribades, 0), 0.999999)
        where tj.jugador_id = jugador and tj.temporada_id = temporada and tj.tram_id = tram.tram_id;
        if arribades > 0 then
            insert into public.moviments (jugador_id, temporada_id, concepte, monedes, tram_id, arribades)
            values (jugador, temporada, 'arribades', monedes, tram.tram_id, arribades);
            guanyat := guanyat + monedes;
        end if;
    end loop;
    if guanyat > 0 then
        update public.partides p set saldo = p.saldo + guanyat
        where p.jugador_id = jugador and p.temporada_id = temporada;
    end if;
    return guanyat;
end;
$$;

-- Cobra les arribades de tots els jugadors de la temporada. La llança pg_cron cada hora.
create function public.liquida_tots()
returns integer
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    temporada integer := public.temporada_actual();
    partida record;
    n integer := 0;
begin
    for partida in select p.jugador_id from public.partides p where p.temporada_id = temporada loop
        perform public.liquida_partida(partida.jugador_id, temporada);
        n := n + 1;
    end loop;
    return n;
end;
$$;

-- L'estat del jugador que la crida: si encara no juga aquesta temporada, comença la partida amb el capital inicial.
-- Abans de respondre, cobra les arribades pendents. És el que demana l'app en obrir-se.
create function public.estat_partida()
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    jugador uuid := auth.uid();
    temporada integer := public.temporada_actual();
    capital bigint;
    guanyat bigint;
    saldo bigint;
begin
    if jugador is null then
        raise exception 'Cal haver entrat' using errcode = '42501';
    end if;
    if temporada is null then
        raise exception 'No hi ha cap temporada oberta';
    end if;
    capital := public.config_joc('trams', 'capitalInicial')::bigint;
    insert into public.partides (jugador_id, temporada_id, saldo) values (jugador, temporada, capital)
    on conflict do nothing;
    if found then
        insert into public.moviments (jugador_id, temporada_id, concepte, monedes)
        values (jugador, temporada, 'capital_inicial', capital);
    end if;
    guanyat := public.liquida_partida(jugador, temporada);
    select p.saldo into saldo from public.partides p where p.jugador_id = jugador and p.temporada_id = temporada;
    return jsonb_build_object(
        'temporada', temporada,
        'saldo', saldo,
        'guanyat', guanyat,
        'trams', coalesce((
            select jsonb_agg(jsonb_build_object('id', tj.tram_id, 'andanes', tj.andanes) order by tj.comprat_el)
            from public.trams_jugador tj where tj.jugador_id = jugador and tj.temporada_id = temporada
        ), '[]'::jsonb)
    );
end;
$$;

-- Compra un tram petit o mitjà per al jugador que la crida. El client només diu quin; el preu és el del servidor.
-- Torna {resultat, saldo}: resultat és comprat, no_existeix, gran, ja_es_teu o sense_monedes.
create function public.compra_tram(tram_id text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    jugador uuid := auth.uid();
    temporada integer;
    tram public.trams%rowtype;
    saldo bigint;
    resultat text;
begin
    perform public.estat_partida();
    temporada := public.temporada_actual();
    select p.saldo into saldo from public.partides p
    where p.jugador_id = jugador and p.temporada_id = temporada for update;
    select * into tram from public.trams t where t.id = compra_tram.tram_id;
    if not found then
        resultat := 'no_existeix';
    elsif tram.mida = 'gran' then
        resultat := 'gran';
    elsif exists (select 1 from public.trams_jugador tj where tj.jugador_id = jugador
                  and tj.temporada_id = temporada and tj.tram_id = tram.id) then
        resultat := 'ja_es_teu';
    elsif saldo < tram.preu then
        resultat := 'sense_monedes';
    else
        insert into public.trams_jugador (jugador_id, temporada_id, tram_id) values (jugador, temporada, tram.id);
        insert into public.moviments (jugador_id, temporada_id, concepte, monedes, tram_id)
        values (jugador, temporada, 'compra_tram', -tram.preu, tram.id);
        update public.partides p set saldo = p.saldo - tram.preu
        where p.jugador_id = jugador and p.temporada_id = temporada
        returning p.saldo into saldo;
        resultat := 'comprat';
    end if;
    return jsonb_build_object('resultat', resultat, 'saldo', saldo);
end;
$$;

-- Afegeix una andana a un tram del jugador que la crida, fins a totes les parades del tram.
-- Torna {resultat, saldo, andanes}: resultat és millorat, no_es_teu, maxim o sense_monedes.
create function public.millora_tram(tram_id text)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    jugador uuid := auth.uid();
    temporada integer;
    saldo bigint;
    andanes integer;
    parades integer;
    preu integer;
    cost integer;
    resultat text;
begin
    perform public.estat_partida();
    temporada := public.temporada_actual();
    select p.saldo into saldo from public.partides p
    where p.jugador_id = jugador and p.temporada_id = temporada for update;
    select tj.andanes, t.parades, t.preu into andanes, parades, preu
    from public.trams_jugador tj join public.trams t on t.id = tj.tram_id
    where tj.jugador_id = jugador and tj.temporada_id = temporada and tj.tram_id = millora_tram.tram_id
    for update of tj;
    if not found then
        resultat := 'no_es_teu';
    elsif andanes >= parades then
        resultat := 'maxim';
    else
        cost := public.cost_millora(preu, andanes);
        if saldo < cost then
            resultat := 'sense_monedes';
        else
            update public.trams_jugador tj set andanes = tj.andanes + 1
            where tj.jugador_id = jugador and tj.temporada_id = temporada and tj.tram_id = millora_tram.tram_id
            returning tj.andanes into andanes;
            insert into public.moviments (jugador_id, temporada_id, concepte, monedes, tram_id)
            values (jugador, temporada, 'millora_tram', -cost, millora_tram.tram_id);
            update public.partides p set saldo = p.saldo - cost
            where p.jugador_id = jugador and p.temporada_id = temporada
            returning p.saldo into saldo;
            resultat := 'millorat';
        end if;
    end if;
    return jsonb_build_object('resultat', resultat, 'saldo', saldo, 'andanes', andanes);
end;
$$;

-- Les funcions internes no es poden cridar des de l'app; les de l'app, només amb compte.
revoke execute on function public.config_joc(text, text), public.temporada_actual(),
    public.arribades_entre(integer, timestamptz, timestamptz), public.cost_millora(integer, integer),
    public.liquida_partida(uuid, integer), public.liquida_tots(), public.estat_partida(), public.compra_tram(text),
    public.millora_tram(text) from public, anon, authenticated;
grant execute on function public.estat_partida(), public.compra_tram(text), public.millora_tram(text) to authenticated;

-- Cobra les arribades cada hora, encara que el jugador no obri l'app.
create extension if not exists pg_cron;
select cron.schedule('liquida-arribades', '0 * * * *', 'select public.liquida_tots()');
