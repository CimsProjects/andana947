-- Fase 7, la part social (GDD 4.3 i abast.md, secció 3.7). Valors al bloc «social» de dades/configuracio_joc.json.
--
--  * Mercat d'accions: un jugador posa a la venda accions d'una gran estació a un preu (dins d'una forquilla al voltant
--    del preu de referència, perquè no es puguin passar diners entre comptes) i un altre les compra. El venedor paga
--    una comissió.
--  * Juntes: els accionistes d'una gran estació (el president és qui en té més) proposen millores per a l'estació
--    (les del catàleg de millores). Es voten amb les accions: s'aproven quan els vots a favor passen de la meitat de
--    les accions venudes, i les paguen els qui hi han votat a favor, segons les accions. La millora puja els
--    dividends de tots els accionistes.
--  * Cooperatives: grups de jugadors d'una comarca (cal tenir-hi alguna andana), amb rànquing de temporada.

-- 1. Configuració -------------------------------------------------------------------------------------------------------

create or replace function public.config_social()
returns jsonb
language sql
immutable
set search_path = ''
as $$
    select jsonb_build_object(
        'comissio_venda', 0.02,
        'preu_minim_relatiu', 0.5,
        'preu_maxim_relatiu', 3.0,
        'dies_oferta', 7,
        'hores_proposta', 72,
        'majoria', 0.5,
        'maxim_membres', 20
    );
$$;

-- Els noms de les comarques (dades/territori/comarques.json), per a les cooperatives.
create table if not exists public.comarques (
    codi char(2) primary key,
    nom text not null
);
alter table public.comarques enable row level security;
create policy "Tothom pot llegir les comarques" on public.comarques for select to anon, authenticated using (true);
revoke insert, update, delete, truncate on public.comarques from anon, authenticated;

insert into public.comarques (codi, nom) values
    ('01', 'Alt Camp'),
    ('02', 'Alt Empordà'),
    ('03', 'Alt Penedès'),
    ('04', 'Alt Urgell'),
    ('05', 'Alta Ribagorça'),
    ('06', 'Anoia'),
    ('07', 'Bages'),
    ('08', 'Baix Camp'),
    ('09', 'Baix Ebre'),
    ('10', 'Baix Empordà'),
    ('11', 'Baix Llobregat'),
    ('12', 'Baix Penedès'),
    ('13', 'Barcelonès'),
    ('14', 'Berguedà'),
    ('15', 'Cerdanya'),
    ('16', 'Conca de Barberà'),
    ('17', 'Garraf'),
    ('18', 'Garrigues'),
    ('19', 'Garrotxa'),
    ('20', 'Gironès'),
    ('21', 'Maresme'),
    ('22', 'Montsià'),
    ('23', 'Noguera'),
    ('24', 'Osona'),
    ('25', 'Pallars Jussà'),
    ('26', 'Pallars Sobirà'),
    ('27', 'Pla d''Urgell'),
    ('28', 'Pla de l''Estany'),
    ('29', 'Priorat'),
    ('30', 'Ribera d''Ebre'),
    ('31', 'Ripollès'),
    ('32', 'Segarra'),
    ('33', 'Segrià'),
    ('34', 'Selva'),
    ('35', 'Solsonès'),
    ('36', 'Tarragonès'),
    ('37', 'Terra Alta'),
    ('38', 'Urgell'),
    ('39', 'Val d''Aran'),
    ('40', 'Vallès Occidental'),
    ('41', 'Vallès Oriental'),
    ('42', 'Moianès'),
    ('43', 'Lluçanès')
on conflict (codi) do update set nom = excluded.nom;

-- 2. Mercat d'accions ---------------------------------------------------------------------------------------------------

create table if not exists public.ofertes_accions (
    id bigserial primary key,
    venedor_id uuid not null references auth.users (id) on delete cascade,
    node_id text not null references public.nodes (id) on delete cascade,
    quantitat integer not null check (quantitat > 0 and quantitat <= 20),
    preu numeric(12, 2) not null check (preu > 0),
    creada_el timestamptz not null default now(),
    caduca_el timestamptz not null,
    activa boolean not null default true
);
comment on table public.ofertes_accions is 'Accions de grans estacions a la venda entre jugadors (preu per acció).';
create index if not exists ofertes_accions_node_idx on public.ofertes_accions (node_id) where activa;

alter table public.ofertes_accions enable row level security;
create policy "Tothom veu les ofertes" on public.ofertes_accions for select to authenticated using (true);
revoke insert, update, delete, truncate on public.ofertes_accions from anon, authenticated;

-- Les accions que un jugador té i no ha posat a la venda.
create or replace function public.accions_lliures(p_jugador uuid, p_node text)
returns integer
language sql
stable
set search_path = ''
as $$
    select coalesce((select a.quantitat from public.accions a where a.jugador_id = p_jugador and a.node_id = p_node), 0)
         - coalesce((select sum(o.quantitat) from public.ofertes_accions o
                     where o.venedor_id = p_jugador and o.node_id = p_node and o.activa and o.caduca_el > now()), 0)::int;
$$;

create or replace function public.posa_venda(p_node text, p_quantitat integer, p_preu numeric)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_node record;
    v_referencia numeric;
    v_cfg jsonb := public.config_social();
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select n.id, n.nom, n.viatgers_feiner into v_node from public.nodes n where n.id = p_node;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta estació no existeix.');
    end if;
    if p_quantitat is null or p_quantitat < 1 or p_quantitat > public.accions_lliures(v_user_id, p_node) then
        return jsonb_build_object('status', 'error', 'missatge', 'No tens tantes accions de ' || v_node.nom || ' per vendre.');
    end if;
    v_referencia := public.preu_accio(v_node.viatgers_feiner);
    if p_preu is null or p_preu < round(v_referencia * (v_cfg ->> 'preu_minim_relatiu')::numeric, 2)
       or p_preu > round(v_referencia * (v_cfg ->> 'preu_maxim_relatiu')::numeric, 2) then
        return jsonb_build_object('status', 'preu_fora', 'missatge',
            'El preu ha de ser entre ' || round(v_referencia * (v_cfg ->> 'preu_minim_relatiu')::numeric, 2) || ' i '
            || round(v_referencia * (v_cfg ->> 'preu_maxim_relatiu')::numeric, 2) || ' € per acció.');
    end if;
    insert into public.ofertes_accions (venedor_id, node_id, quantitat, preu, caduca_el)
    values (v_user_id, p_node, p_quantitat, round(p_preu, 2), now() + make_interval(days => (v_cfg ->> 'dies_oferta')::int));
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'mercat',
        'missatge', p_quantitat || ' accions de ' || v_node.nom || ' a la venda a ' || round(p_preu, 2) || ' € cadascuna.');
end;
$$;

create or replace function public.cancela_oferta(p_oferta bigint)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.ofertes_accions set activa = false
    where id = p_oferta and venedor_id = auth.uid() and activa;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta oferta no és teva o ja no hi és.');
    end if;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'mercat', 'missatge', 'Oferta retirada.');
end;
$$;

create or replace function public.compra_oferta(p_oferta bigint, p_quantitat integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_oferta record;
    v_meves integer;
    v_te integer;
    v_cost numeric(12, 2);
    v_comissio numeric(12, 2);
    v_saldo numeric(12, 2);
    v_nom text;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    select o.* into v_oferta from public.ofertes_accions o where o.id = p_oferta for update;
    if not found or not v_oferta.activa or v_oferta.caduca_el <= now() then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta oferta ja no hi és.');
    end if;
    if v_oferta.venedor_id = v_user_id then
        return jsonb_build_object('status', 'error', 'missatge', 'No pots comprar les teves pròpies accions.');
    end if;
    if p_quantitat is null or p_quantitat < 1 or p_quantitat > v_oferta.quantitat then
        return jsonb_build_object('status', 'error', 'missatge', 'Quantitat no vàlida.');
    end if;
    select n.nom into v_nom from public.nodes n where n.id = v_oferta.node_id;
    -- Els dos perfils, sempre en el mateix ordre (que no s'esperin l'un a l'altre).
    perform 1 from public.perfils p where p.id in (v_user_id, v_oferta.venedor_id) order by p.id for update;
    v_meves := coalesce((select a.quantitat from public.accions a where a.jugador_id = v_user_id and a.node_id = v_oferta.node_id), 0);
    if v_meves + p_quantitat > 20 then
        return jsonb_build_object('status', 'limit_accions', 'missatge', 'Ningú no pot tenir més de 20 accions d''una estació.');
    end if;
    v_te := coalesce((select a.quantitat from public.accions a where a.jugador_id = v_oferta.venedor_id and a.node_id = v_oferta.node_id), 0);
    if v_te < p_quantitat then
        update public.ofertes_accions set activa = false where id = p_oferta;
        return jsonb_build_object('status', 'error', 'missatge', 'El venedor ja no té aquestes accions.');
    end if;
    v_cost := round(v_oferta.preu * p_quantitat, 2);
    if (select p.saldo from public.perfils p where p.id = v_user_id) < v_cost then
        return jsonb_build_object('status', 'sense_saldo', 'missatge', 'No tens prou catalins: calen ' || v_cost || ' €.');
    end if;
    v_comissio := round(v_cost * (public.config_social() ->> 'comissio_venda')::numeric, 2);

    -- Abans de canviar els dividends, es cobra el que s'ha guanyat fins ara (tots dos).
    perform public.liquida_jugador(v_oferta.venedor_id);
    perform public.liquida_jugador(v_user_id);

    if v_te = p_quantitat then
        delete from public.accions where jugador_id = v_oferta.venedor_id and node_id = v_oferta.node_id;
    else
        update public.accions set quantitat = quantitat - p_quantitat
        where jugador_id = v_oferta.venedor_id and node_id = v_oferta.node_id;
    end if;
    insert into public.accions (node_id, jugador_id, quantitat, darrera_visita_el)
    values (v_oferta.node_id, v_user_id, p_quantitat, now())
    on conflict (node_id, jugador_id) do update set quantitat = public.accions.quantitat + excluded.quantitat;

    update public.ofertes_accions
    set quantitat = case when quantitat > p_quantitat then quantitat - p_quantitat else quantitat end,
        activa = quantitat > p_quantitat
    where id = p_oferta;

    update public.perfils set saldo = saldo - v_cost, actualitzat_el = now() where id = v_user_id returning saldo into v_saldo;
    update public.perfils set saldo = saldo + v_cost - v_comissio, actualitzat_el = now() where id = v_oferta.venedor_id;
    insert into public.moviments (jugador_id, concepte, import, node_id, tipus) values
        (v_user_id, 'Compra de ' || p_quantitat || ' accions a un jugador: ' || v_nom, -v_cost, v_oferta.node_id, 'mercat'),
        (v_oferta.venedor_id, 'Venda de ' || p_quantitat || ' accions: ' || v_nom, v_cost - v_comissio, v_oferta.node_id, 'mercat');

    return jsonb_build_object('status', 'valid', 'tipus_accio', 'mercat',
        'missatge', '+' || p_quantitat || ' accions de ' || v_nom || ' per ' || v_cost || ' €.',
        'cost', v_cost, 'saldo', v_saldo, 'fites', public.revisa_fites(v_user_id));
end;
$$;

-- El mercat: les ofertes actives (les d'una estació, o totes), primer les més barates respecte del preu de referència.
create or replace function public.mercat_accions(p_node text default null)
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
        select jsonb_agg(jsonb_build_object(
            'id', o.id,
            'node_id', o.node_id,
            'nom', n.nom,
            'venedor', public.nom_public(o.venedor_id),
            'meva', o.venedor_id = v_user_id,
            'quantitat', o.quantitat,
            'preu', o.preu,
            'preu_referencia', public.preu_accio(n.viatgers_feiner),
            'dividend_accio', round(public.ingres_diari(n.viatgers_feiner) / 100.0, 2),
            'caduca_el', o.caduca_el
        ) order by o.preu / public.preu_accio(n.viatgers_feiner), o.creada_el)
        from public.ofertes_accions o join public.nodes n on n.id = o.node_id
        where o.activa and o.caduca_el > now() and (p_node is null or o.node_id = p_node)
    ), '[]'::jsonb);
end;
$$;

-- 3. Juntes ---------------------------------------------------------------------------------------------------------------

create table if not exists public.millores_estacio (
    node_id text not null references public.nodes (id) on delete cascade,
    millora text not null references public.millores (id) on delete cascade,
    aprovada_el timestamptz not null default now(),
    primary key (node_id, millora)
);
comment on table public.millores_estacio is 'Millores d''una gran estació aprovades per la seva junta: pugen els dividends de tots els accionistes.';

create table if not exists public.propostes_junta (
    id bigserial primary key,
    node_id text not null references public.nodes (id) on delete cascade,
    millora text not null references public.millores (id) on delete cascade,
    proposada_per uuid not null references auth.users (id) on delete cascade,
    cost numeric(12, 2) not null check (cost >= 0),
    creada_el timestamptz not null default now(),
    caduca_el timestamptz not null,
    estat text not null default 'oberta' check (estat in ('oberta', 'aprovada', 'caducada'))
);
create unique index if not exists propostes_junta_oberta_idx on public.propostes_junta (node_id, millora) where estat = 'oberta';

create table if not exists public.vots_junta (
    proposta bigint not null references public.propostes_junta (id) on delete cascade,
    jugador_id uuid not null references auth.users (id) on delete cascade,
    a_favor boolean not null,
    votat_el timestamptz not null default now(),
    primary key (proposta, jugador_id)
);

alter table public.millores_estacio enable row level security;
alter table public.propostes_junta enable row level security;
alter table public.vots_junta enable row level security;
create policy "Tothom veu les millores de les estacions" on public.millores_estacio for select to authenticated using (true);
create policy "Tothom veu les propostes" on public.propostes_junta for select to authenticated using (true);
create policy "Tothom veu els vots" on public.vots_junta for select to authenticated using (true);
revoke insert, update, delete, truncate on public.millores_estacio, public.propostes_junta, public.vots_junta from anon, authenticated;

-- El factor de les millores d'una gran estació sobre els dividends.
create or replace function public.factor_millores_estacio(p_node text)
returns numeric
language sql
stable
set search_path = ''
as $$
    select 1 + coalesce(sum(m.bonus), 0)
    from public.millores_estacio me join public.millores m on m.id = me.millora
    where me.node_id = p_node;
$$;

-- Les propostes caducades es tanquen quan algú les mira.
create or replace function public.tanca_propostes_caducades()
returns void
language sql
security definer
set search_path = ''
as $$
    update public.propostes_junta set estat = 'caducada' where estat = 'oberta' and caduca_el <= now();
$$;

-- Si una proposta ja té prou vots a favor, s'aprova: la paguen els qui hi han votat a favor, segons les accions que
-- tenen. Si algun no té prou saldo, s'espera. Torna l'estat de la proposta.
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

create or replace function public.proposa_millora_estacio(p_node text, p_millora text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_node record;
    v_millora record;
    v_id bigint;
    v_estat text;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    if not exists (select 1 from public.accions a where a.jugador_id = v_user_id and a.node_id = p_node) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només els accionistes poden proposar millores.');
    end if;
    perform public.tanca_propostes_caducades();
    select n.id, n.nom, n.mode, n.viatgers_feiner into v_node from public.nodes n where n.id = p_node;
    select m.* into v_millora from public.millores m where m.id = p_millora;
    if not found or not (v_node.mode = any (v_millora.modes)) then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta millora no és per a aquesta estació.');
    end if;
    if exists (select 1 from public.millores_estacio me where me.node_id = p_node and me.millora = p_millora) then
        return jsonb_build_object('status', 'error', 'missatge', 'L''estació ja té aquesta millora.');
    end if;
    if exists (select 1 from public.propostes_junta pj where pj.node_id = p_node and pj.millora = p_millora and pj.estat = 'oberta') then
        return jsonb_build_object('status', 'error', 'missatge', 'Ja hi ha una proposta oberta per a aquesta millora.');
    end if;
    insert into public.propostes_junta (node_id, millora, proposada_per, cost, caduca_el)
    values (p_node, p_millora, v_user_id, round(public.preu_concessio(v_node.viatgers_feiner) * v_millora.cost_relatiu, 2),
            now() + make_interval(hours => (public.config_social() ->> 'hores_proposta')::int))
    returning id into v_id;
    -- Qui la proposa hi vota a favor.
    insert into public.vots_junta (proposta, jugador_id, a_favor) values (v_id, v_user_id, true);
    v_estat := public.resol_proposta(v_id);
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'junta',
        'missatge', case when v_estat = 'aprovada' then 'Millora aprovada: tens la majoria de les accions.'
                         else 'Proposta feta. Els altres accionistes tenen ' || (public.config_social() ->> 'hores_proposta')
                              || ' hores per votar-la.' end);
end;
$$;

create or replace function public.vota_proposta(p_proposta bigint, p_a_favor boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_p record;
    v_estat text;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    perform public.tanca_propostes_caducades();
    select * into v_p from public.propostes_junta where id = p_proposta;
    if not found or v_p.estat <> 'oberta' then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta proposta ja està tancada.');
    end if;
    if not exists (select 1 from public.accions a where a.jugador_id = v_user_id and a.node_id = v_p.node_id) then
        return jsonb_build_object('status', 'error', 'missatge', 'Només voten els accionistes de l''estació.');
    end if;
    insert into public.vots_junta (proposta, jugador_id, a_favor) values (p_proposta, v_user_id, coalesce(p_a_favor, false))
    on conflict (proposta, jugador_id) do update set a_favor = excluded.a_favor, votat_el = now();
    v_estat := public.resol_proposta(p_proposta);
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'junta',
        'missatge', case when v_estat = 'aprovada' then 'Millora aprovada! Els dividends de l''estació pugen.'
                         when p_a_favor then 'Vot a favor desat.' else 'Vot en contra desat.' end);
end;
$$;

-- La junta d'una gran estació: accionistes (el president, el que en té més), millores i propostes.
create or replace function public.estat_junta(p_node text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_node record;
    v_venudes integer;
begin
    if v_user_id is null then
        return null;
    end if;
    perform public.tanca_propostes_caducades();
    select n.id, n.nom, n.mode, n.viatgers_feiner into v_node from public.nodes n where n.id = p_node;
    if not found then
        return null;
    end if;
    select coalesce(sum(a.quantitat), 0) into v_venudes from public.accions a where a.node_id = p_node;
    return jsonb_build_object(
        'node_id', v_node.id,
        'nom', v_node.nom,
        'venudes', v_venudes,
        'meves', coalesce((select a.quantitat from public.accions a where a.node_id = p_node and a.jugador_id = v_user_id), 0),
        'preu_referencia', public.preu_accio(v_node.viatgers_feiner),
        'dividend_accio', round(public.ingres_diari(v_node.viatgers_feiner) / 100.0 * public.factor_millores_estacio(p_node), 2),
        'factor_millores', public.factor_millores_estacio(p_node),
        'accionistes', coalesce((
            select jsonb_agg(jsonb_build_object(
                'nom', public.nom_public(x.jugador_id),
                'quantitat', x.quantitat,
                'jo', x.jugador_id = v_user_id,
                'president', x.ordre = 1
            ) order by x.ordre)
            from (select a.jugador_id, a.quantitat, row_number() over (order by a.quantitat desc, a.darrera_visita_el) as ordre
                  from public.accions a where a.node_id = p_node) x
        ), '[]'::jsonb),
        'millores', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', m.id,
                'bonus', m.bonus,
                'cost', round(public.preu_concessio(v_node.viatgers_feiner) * m.cost_relatiu, 2),
                'feta', me.millora is not null,
                'proposta', (select pj.id from public.propostes_junta pj where pj.node_id = p_node and pj.millora = m.id and pj.estat = 'oberta')
            ) order by m.ordre)
            from public.millores m
            left join public.millores_estacio me on me.node_id = p_node and me.millora = m.id
            where v_node.mode = any (m.modes)
        ), '[]'::jsonb),
        'propostes', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', pj.id,
                'millora', pj.millora,
                'cost', pj.cost,
                'proposada_per', public.nom_public(pj.proposada_per),
                'caduca_el', pj.caduca_el,
                'a_favor', (select coalesce(sum(a.quantitat), 0) from public.vots_junta v
                            join public.accions a on a.jugador_id = v.jugador_id and a.node_id = p_node
                            where v.proposta = pj.id and v.a_favor),
                'en_contra', (select coalesce(sum(a.quantitat), 0) from public.vots_junta v
                              join public.accions a on a.jugador_id = v.jugador_id and a.node_id = p_node
                              where v.proposta = pj.id and not v.a_favor),
                'el_meu_vot', (select v.a_favor from public.vots_junta v where v.proposta = pj.id and v.jugador_id = v_user_id)
            ) order by pj.creada_el)
            from public.propostes_junta pj where pj.node_id = p_node and pj.estat = 'oberta'
        ), '[]'::jsonb)
    );
end;
$$;

-- Les grans estacions on el jugador té accions (les seves juntes).
create or replace function public.juntes_meves()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        return null;
    end if;
    perform public.tanca_propostes_caducades();
    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'node_id', a.node_id,
            'nom', n.nom,
            'meves', a.quantitat,
            'venudes', (select sum(a2.quantitat) from public.accions a2 where a2.node_id = a.node_id),
            'president', not exists (select 1 from public.accions a2 where a2.node_id = a.node_id
                                     and (a2.quantitat > a.quantitat
                                          or (a2.quantitat = a.quantitat and a2.darrera_visita_el < a.darrera_visita_el))),
            'propostes_obertes', (select count(*) from public.propostes_junta pj where pj.node_id = a.node_id and pj.estat = 'oberta'),
            'per_votar', (select count(*) from public.propostes_junta pj where pj.node_id = a.node_id and pj.estat = 'oberta'
                          and not exists (select 1 from public.vots_junta v where v.proposta = pj.id and v.jugador_id = v_user_id))
        ) order by a.quantitat desc, n.nom)
        from public.accions a join public.nodes n on n.id = a.node_id
        where a.jugador_id = v_user_id
    ), '[]'::jsonb);
end;
$$;

-- 4. Cooperatives --------------------------------------------------------------------------------------------------------

create table if not exists public.cooperatives (
    id bigserial primary key,
    nom text not null check (char_length(nom) between 3 and 30),
    comarca char(2) not null references public.comarques (codi),
    creada_el timestamptz not null default now()
);
create unique index if not exists cooperatives_nom_idx on public.cooperatives (lower(nom));

create table if not exists public.membres_cooperativa (
    jugador_id uuid primary key references auth.users (id) on delete cascade,
    cooperativa bigint not null references public.cooperatives (id) on delete cascade,
    rol text not null default 'membre' check (rol in ('fundador', 'membre')),
    unit_el timestamptz not null default now()
);
create index if not exists membres_cooperativa_idx on public.membres_cooperativa (cooperativa);

alter table public.cooperatives enable row level security;
alter table public.membres_cooperativa enable row level security;
create policy "Tothom veu les cooperatives" on public.cooperatives for select to authenticated using (true);
create policy "Tothom veu els membres" on public.membres_cooperativa for select to authenticated using (true);
revoke insert, update, delete, truncate on public.cooperatives, public.membres_cooperativa from anon, authenticated;

-- Les comarques on el jugador té alguna andana (on pot fundar una cooperativa o unir-s'hi).
create or replace function public.comarques_jugador(p_jugador uuid)
returns table (comarca char(2))
language sql
stable
set search_path = ''
as $$
    select distinct n.comarca from public.concessions c join public.nodes n on n.id = c.node_id
    where c.jugador_id = p_jugador and c.activa and c.caduca_el >= now() and n.comarca is not null;
$$;

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
    if char_length(v_nom) not between 3 and 30 then
        return jsonb_build_object('status', 'error', 'missatge', 'El nom ha de tenir entre 3 i 30 lletres.');
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

create or replace function public.uneix_cooperativa(p_cooperativa bigint)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_coop record;
begin
    if v_user_id is null then
        return jsonb_build_object('status', 'error', 'missatge', 'Cal iniciar sessió.');
    end if;
    if exists (select 1 from public.membres_cooperativa m where m.jugador_id = v_user_id) then
        return jsonb_build_object('status', 'error', 'missatge', 'Ja ets d''una cooperativa: surt-ne primer.');
    end if;
    select c.* into v_coop from public.cooperatives c where c.id = p_cooperativa for update;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta cooperativa no existeix.');
    end if;
    if v_coop.comarca not in (select c.comarca from public.comarques_jugador(v_user_id) c) then
        return jsonb_build_object('status', 'error', 'missatge', 'Per unir-t''hi, cal tenir alguna andana a la seva comarca.');
    end if;
    if (select count(*) from public.membres_cooperativa m where m.cooperativa = p_cooperativa)
       >= (public.config_social() ->> 'maxim_membres')::int then
        return jsonb_build_object('status', 'error', 'missatge', 'Aquesta cooperativa ja és plena.');
    end if;
    insert into public.membres_cooperativa (jugador_id, cooperativa) values (v_user_id, p_cooperativa);
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'cooperativa', 'missatge', 'Benvingut a ' || v_coop.nom || '!');
end;
$$;

create or replace function public.surt_cooperativa()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_m record;
begin
    select m.* into v_m from public.membres_cooperativa m where m.jugador_id = v_user_id;
    if not found then
        return jsonb_build_object('status', 'error', 'missatge', 'No ets de cap cooperativa.');
    end if;
    delete from public.membres_cooperativa where jugador_id = v_user_id;
    if not exists (select 1 from public.membres_cooperativa m where m.cooperativa = v_m.cooperativa) then
        -- L'últim membre tanca la cooperativa.
        delete from public.cooperatives where id = v_m.cooperativa;
    elsif v_m.rol = 'fundador' then
        -- El fundador passa al membre més antic.
        update public.membres_cooperativa set rol = 'fundador'
        where jugador_id = (select m.jugador_id from public.membres_cooperativa m where m.cooperativa = v_m.cooperativa
                            order by m.unit_el limit 1);
    end if;
    return jsonb_build_object('status', 'valid', 'tipus_accio', 'cooperativa', 'missatge', 'Has sortit de la cooperativa.');
end;
$$;

-- La cooperativa del jugador (membres amb els punts de temporada), el rànquing de cooperatives de la temporada i les
-- cooperatives de les seves comarques on es pot unir.
create or replace function public.estat_cooperatives()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_temporada integer := (public.temporada_actual() ->> 'numero')::int;
    v_meva bigint;
begin
    if v_user_id is null then
        return null;
    end if;
    select m.cooperativa into v_meva from public.membres_cooperativa m where m.jugador_id = v_user_id;
    return (
        with punts as (select r.jugador_id, r.punts from public.ranking_de(v_temporada) r),
        coops as (
            select c.id, c.nom, c.comarca, co.nom as nom_comarca,
                   (select count(*) from public.membres_cooperativa m where m.cooperativa = c.id)::int as membres,
                   (select coalesce(sum(p.punts), 0) from public.membres_cooperativa m join punts p on p.jugador_id = m.jugador_id
                    where m.cooperativa = c.id)::int as punts
            from public.cooperatives c join public.comarques co on co.codi = c.comarca
        )
        select jsonb_build_object(
            'meva', (
                select jsonb_build_object(
                    'id', c.id, 'nom', c.nom, 'comarca', c.nom_comarca, 'punts', c.punts, 'membres_total', c.membres,
                    'posicio', (select count(*) + 1 from coops c2 where c2.punts > c.punts),
                    'membres', (
                        select jsonb_agg(jsonb_build_object(
                            'nom', public.nom_public(m.jugador_id),
                            'rol', m.rol,
                            'jo', m.jugador_id = v_user_id,
                            'punts', coalesce((select p.punts from punts p where p.jugador_id = m.jugador_id), 0)
                        ) order by coalesce((select p.punts from punts p where p.jugador_id = m.jugador_id), 0) desc)
                        from public.membres_cooperativa m where m.cooperativa = c.id
                    )
                )
                from coops c where c.id = v_meva
            ),
            'ranking', coalesce((
                select jsonb_agg(jsonb_build_object('id', x.id, 'nom', x.nom, 'comarca', x.nom_comarca, 'membres', x.membres,
                                                    'punts', x.punts) order by x.punts desc, x.nom)
                from (select * from coops order by punts desc, nom limit 20) x
            ), '[]'::jsonb),
            'a_prop', coalesce((
                select jsonb_agg(jsonb_build_object('id', c.id, 'nom', c.nom, 'comarca', c.nom_comarca, 'membres', c.membres,
                                                    'punts', c.punts) order by c.punts desc, c.nom)
                from coops c
                where c.comarca in (select cj.comarca from public.comarques_jugador(v_user_id) cj)
                  and c.membres < (public.config_social() ->> 'maxim_membres')::int
                  and (v_meva is null or c.id <> v_meva)
            ), '[]'::jsonb),
            'comarques', coalesce((
                select jsonb_agg(jsonb_build_object('codi', co.codi, 'nom', co.nom) order by co.nom)
                from public.comarques co where co.codi in (select cj.comarca from public.comarques_jugador(v_user_id) cj)
            ), '[]'::jsonb),
            'maxim_membres', (public.config_social() ->> 'maxim_membres')::int
        )
    );
end;
$$;

-- 5. Els dividends compten les millores de la junta i el caos ------------------------------------------------------------

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
                * case when a.presencial_fins >= now() then public.multiplicador_concessio() else 1 end
                * public.factor_millores_estacio(a.node_id)
                * public.factor_caos(a.node_id, p_moment)), 0) as total
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

-- 6. Permisos ----------------------------------------------------------------------------------------------------------
revoke execute on function public.accions_lliures(uuid, text) from public, anon, authenticated;
revoke execute on function public.resol_proposta(bigint) from public, anon, authenticated;
revoke execute on function public.tanca_propostes_caducades() from public, anon, authenticated;
revoke execute on function public.comarques_jugador(uuid) from public, anon, authenticated;
revoke execute on function public.ingres_jugador_exacte(uuid, boolean, numeric, timestamptz) from public, anon, authenticated;

revoke execute on function public.posa_venda(text, integer, numeric) from public, anon;
revoke execute on function public.cancela_oferta(bigint) from public, anon;
revoke execute on function public.compra_oferta(bigint, integer) from public, anon;
revoke execute on function public.mercat_accions(text) from public, anon;
revoke execute on function public.proposa_millora_estacio(text, text) from public, anon;
revoke execute on function public.vota_proposta(bigint, boolean) from public, anon;
revoke execute on function public.estat_junta(text) from public, anon;
revoke execute on function public.juntes_meves() from public, anon;
revoke execute on function public.crea_cooperativa(text, text) from public, anon;
revoke execute on function public.uneix_cooperativa(bigint) from public, anon;
revoke execute on function public.surt_cooperativa() from public, anon;
revoke execute on function public.estat_cooperatives() from public, anon;
grant execute on function public.posa_venda(text, integer, numeric) to authenticated;
grant execute on function public.cancela_oferta(bigint) to authenticated;
grant execute on function public.compra_oferta(bigint, integer) to authenticated;
grant execute on function public.mercat_accions(text) to authenticated;
grant execute on function public.proposa_millora_estacio(text, text) to authenticated;
grant execute on function public.vota_proposta(bigint, boolean) to authenticated;
grant execute on function public.estat_junta(text) to authenticated;
grant execute on function public.juntes_meves() to authenticated;
grant execute on function public.crea_cooperativa(text, text) to authenticated;
grant execute on function public.uneix_cooperativa(bigint) to authenticated;
grant execute on function public.surt_cooperativa() to authenticated;
grant execute on function public.estat_cooperatives() to authenticated;
