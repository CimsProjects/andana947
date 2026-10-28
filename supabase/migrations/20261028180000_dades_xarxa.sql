-- Dades de la xarxa (fase 2): municipis, línies, nodes (parades i estacions) i afluència.
-- Les omple scripts/carrega_postgis.py a partir de dades/generades/ (vegeu docs/dades.md).
-- Són dades de referència: tothom les pot llegir i ningú no les pot modificar des de l'app.

create type public.mode_transport as enum ('metro', 'rodalies', 'regional', 'fgc', 'tram', 'bus');

-- D'on surt l'afluència d'un node (GDD 2): dada publicada o model d'estimació.
create type public.font_afluencia as enum ('real', 'estimat');

create table public.fonts_dades (
    id text primary key,
    nom text not null,
    operador text,
    url text not null,
    llicencia text,
    web text,
    carregat_el timestamptz not null default now()
);

comment on table public.fonts_dades is 'Fonts obertes de les dades del joc (dades/fonts.json).';

create table public.municipis (
    codi char(5) primary key check (codi ~ '^[0-9]{5}$'),
    nom text not null,
    comarca char(2) not null,
    poblacio integer not null check (poblacio >= 0),
    superficie_km2 numeric(10, 3) not null check (superficie_km2 > 0),
    densitat numeric(10, 2) not null check (densitat >= 0)
);

comment on table public.municipis is 'Els 947 municipis: població del Cens 2021 (INE), superfície dels límits de l''IGN i comarca.';

create table public.linies (
    id text primary key,
    operador text not null,
    mode public.mode_transport not null,
    codi text not null,
    nom text not null default '',
    color char(6) check (color ~ '^[0-9A-Fa-f]{6}$'),
    color_text char(6) check (color_text ~ '^[0-9A-Fa-f]{6}$'),
    expedicions_feiner integer not null default 0 check (expedicions_feiner >= 0),
    viatgers_feiner numeric(12, 1) check (viatgers_feiner >= 0),
    font_afluencia public.font_afluencia
);

comment on table public.linies is 'Línies del GTFS de cada operador, amb les expedicions i els viatgers d''un feiner.';

create table public.nodes (
    id text primary key,
    nom text not null,
    mode public.mode_transport not null,
    operador text not null,
    posicio extensions.geography(point, 4326) not null,
    municipi char(5) references public.municipis (codi),
    comarca char(2),
    linies text[] not null default '{}',
    expedicions_feiner integer not null default 0 check (expedicions_feiner >= 0),
    poblacio_400m integer check (poblacio_400m >= 0),
    viatgers_feiner numeric(12, 1) not null check (viatgers_feiner >= 0),
    font_afluencia public.font_afluencia not null,
    metode text not null default '',
    any_dada smallint,
    versio_model smallint not null default 1,
    actualitzat_el timestamptz not null default now()
);

comment on table public.nodes is 'Parades i estacions reals on es pot fer check-in (GDD 3). viatgers_feiner és l''afluència d''un dia feiner; font_afluencia diu si és real o estimada.';
comment on column public.nodes.metode is 'Com s''ha obtingut l''afluència (font i model).';

create index nodes_posicio_idx on public.nodes using gist (posicio);
create index nodes_municipi_idx on public.nodes (municipi);

create table public.linia_nodes (
    linia_id text not null references public.linies (id) on delete cascade,
    node_id text not null references public.nodes (id) on delete cascade,
    expedicions_feiner integer not null check (expedicions_feiner >= 0),
    primary key (linia_id, node_id)
);

create index linia_nodes_node_idx on public.linia_nodes (node_id);

-- RLS: lectura per a tothom, escriptura només des del servidor (la clau secreta, que salta l'RLS).
alter table public.fonts_dades enable row level security;
alter table public.municipis enable row level security;
alter table public.linies enable row level security;
alter table public.nodes enable row level security;
alter table public.linia_nodes enable row level security;

create policy "Tothom pot llegir les fonts" on public.fonts_dades for select to anon, authenticated using (true);
create policy "Tothom pot llegir els municipis" on public.municipis for select to anon, authenticated using (true);
create policy "Tothom pot llegir les línies" on public.linies for select to anon, authenticated using (true);
create policy "Tothom pot llegir els nodes" on public.nodes for select to anon, authenticated using (true);
create policy "Tothom pot llegir les parades de cada línia" on public.linia_nodes for select to anon, authenticated using (true);

revoke insert, update, delete, truncate on public.fonts_dades, public.municipis, public.linies, public.nodes,
    public.linia_nodes from anon, authenticated;

-- Nodes a prop d'un punt, del més proper al més llunyà (pantalla «A prop», GDD 7.1).
create function public.nodes_a_prop(lat double precision, lon double precision, radi_metres double precision default 500,
                                    maxim integer default 50)
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
    distancia_metres double precision
)
language sql
stable
security invoker
set search_path = ''
as $$
    with punt as (
        select extensions.st_setsrid(extensions.st_makepoint(lon, lat), 4326)::extensions.geography as g
    )
    select n.id, n.nom, n.mode, n.operador,
           extensions.st_y(n.posicio::extensions.geometry), extensions.st_x(n.posicio::extensions.geometry),
           n.linies, n.viatgers_feiner, n.font_afluencia,
           extensions.st_distance(n.posicio, punt.g)
    from public.nodes n, punt
    where extensions.st_dwithin(n.posicio, punt.g, least(radi_metres, 5000))
    order by extensions.st_distance(n.posicio, punt.g)
    limit least(greatest(maxim, 1), 200);
$$;

comment on function public.nodes_a_prop is 'Nodes a menys de radi_metres (màxim 5 km) d''un punt, ordenats per distància.';

grant execute on function public.nodes_a_prop(double precision, double precision, double precision, integer)
    to anon, authenticated;
