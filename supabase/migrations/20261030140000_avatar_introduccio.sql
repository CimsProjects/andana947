-- Avatar del jugador i introducció del director (docs/lore.md).
--
-- L'avatar és un bust fet de peces vectorials (scripts/avatar/cataleg.py). El servidor desa quines peces porta cada
-- jugador i comprova que existeixin i que es puguin triar: les del director (nomes_npc) no.

-- 1. Peces i colors que existeixen. Es generen amb `python3 scripts/genera_avatar.py --sql`.
create table public.peces_avatar (
    id text primary key,
    ranura text not null,
    nomes_npc boolean not null default false
);

comment on table public.peces_avatar is 'Peces i colors de l''avatar (scripts/avatar/cataleg.py). nomes_npc: només per al director.';

alter table public.peces_avatar enable row level security;
create policy "Tothom pot llegir les peces de l'avatar" on public.peces_avatar for select to authenticated using (true);
revoke insert, update, delete, truncate on public.peces_avatar from anon, authenticated;

insert into public.peces_avatar (id, ranura, nomes_npc) values
    ('cara_noi', 'cara', true),
    ('cara_noia', 'cara', true),
    ('cara_director', 'cara', true),
    ('cara_director_ullet', 'cara', true),
    ('cabell_curt', 'cabell', false),
    ('cabell_rapat', 'cabell', false),
    ('cabell_arrissat', 'cabell', false),
    ('cabell_llarg', 'cabell', false),
    ('cabell_cua', 'cabell', false),
    ('cabell_monyo', 'cabell', false),
    ('cabell_calb', 'cabell', true),
    ('cap_revisor', 'cap', false),
    ('cap_beisbol', 'cap', false),
    ('cap_llana', 'cap', false),
    ('cap_casc', 'cap', false),
    ('cap_barretina', 'cap', false),
    ('cap_cap_estacio', 'cap', true),
    ('tronc_samarreta', 'tronc', false),
    ('tronc_texana', 'tronc', false),
    ('tronc_armilla', 'tronc', false),
    ('tronc_abric', 'tronc', false),
    ('tronc_dessuadora', 'tronc', false),
    ('tronc_uniforme', 'tronc', false),
    ('coll_bufanda', 'coll', false),
    ('ulls_rodones', 'ulls', false),
    ('ulls_sol', 'ulls', false),
    ('orelles_auriculars', 'orelles', false),
    ('bigoti', 'bigoti', true),
    ('pell_1', 'pell', false),
    ('pell_2', 'pell', false),
    ('pell_3', 'pell', false),
    ('pell_4', 'pell', false),
    ('pell_5', 'pell', false),
    ('cabell_negre', 'color_cabell', false),
    ('cabell_castany', 'color_cabell', false),
    ('cabell_ros', 'color_cabell', false),
    ('cabell_pelroig', 'color_cabell', false),
    ('cabell_gris', 'color_cabell', false),
    ('cabell_magenta', 'color_cabell', false);

-- 2. L'avatar i si el jugador ja ha vist la introducció.
alter table public.perfils
    add column if not exists avatar jsonb,
    add column if not exists introduccio_feta boolean not null default false;

comment on column public.perfils.avatar is 'Peces de l''avatar: cos, pell, cabell, color_cabell, tronc i, opcionals, cap, coll, ulls i orelles.';
comment on column public.perfils.introduccio_feta is 'El jugador ja ha fet la introducció del director.';

-- 3. Desar l'avatar, validant cada peça.
create or replace function public.desa_avatar(p_avatar jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_clau text;
    v_valor text;
    v_net jsonb := '{}'::jsonb;
begin
    if v_user_id is null then
        raise exception 'Cal iniciar sessió.';
    end if;
    if p_avatar is null or jsonb_typeof(p_avatar) <> 'object' then
        raise exception 'L''avatar ha de ser un objecte.';
    end if;

    for v_clau in select jsonb_object_keys(p_avatar) loop
        if v_clau not in ('cos', 'pell', 'cabell', 'color_cabell', 'cap', 'tronc', 'coll', 'ulls', 'orelles') then
            raise exception 'L''avatar no admet «%».', v_clau;
        end if;
    end loop;

    if coalesce(p_avatar ->> 'cos', '') not in ('noi', 'noia') then
        raise exception 'El cos ha de ser noi o noia.';
    end if;
    v_net := jsonb_build_object('cos', p_avatar ->> 'cos');

    foreach v_clau in array array['pell', 'cabell', 'color_cabell', 'tronc', 'cap', 'coll', 'ulls', 'orelles'] loop
        v_valor := p_avatar ->> v_clau;
        if v_valor is null then
            if v_clau in ('pell', 'cabell', 'color_cabell', 'tronc') then
                raise exception 'Falta «%» a l''avatar.', v_clau;
            end if;
            continue;
        end if;
        if not exists (
            select 1 from public.peces_avatar p
            where p.id = v_valor and p.ranura = v_clau and not p.nomes_npc
        ) then
            raise exception 'La peça «%» no existeix o no es pot posar a «%».', v_valor, v_clau;
        end if;
        v_net := v_net || jsonb_build_object(v_clau, v_valor);
    end loop;

    update public.perfils set avatar = v_net, actualitzat_el = now() where id = v_user_id;
    if not found then
        raise exception 'El perfil no existeix.';
    end if;
    return v_net;
end;
$$;

comment on function public.desa_avatar is 'Desa l''avatar del jugador autenticat, si totes les peces existeixen i es poden triar.';

-- 4. Acabar la introducció (cal tenir avatar).
create or replace function public.completa_introduccio()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Cal iniciar sessió.';
    end if;
    update public.perfils
    set introduccio_feta = true, actualitzat_el = now()
    where id = auth.uid() and avatar is not null;
    if not found then
        raise exception 'Abans de començar, fes-te l''avatar.';
    end if;
end;
$$;

comment on function public.completa_introduccio is 'Marca que el jugador autenticat ja ha fet la introducció del director.';

-- 5. Permisos.
revoke execute on function public.desa_avatar(jsonb) from public, anon;
revoke execute on function public.completa_introduccio() from public, anon;
grant execute on function public.desa_avatar(jsonb) to authenticated;
grant execute on function public.completa_introduccio() to authenticated;
