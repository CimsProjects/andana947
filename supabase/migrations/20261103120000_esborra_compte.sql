-- Esborrar el compte des de l'app (requisit de Google Play per a les apps que deixen crear comptes).
-- Totes les taules del jugador referencien auth.users amb «on delete cascade»: en esborrar l'usuari, se n'esborren el
-- perfil, les andanes, els moviments, els check-ins, les maestries, la flota, les accions, les ofertes, les propostes,
-- els vots i la pertinença a cooperatives. Les cooperatives que es queden sense cap membre també s'esborren.

create or replace function public.esborra_compte()
returns boolean
language plpgsql
security definer
set search_path = ''
set jit = off
as $$
declare
    v_user_id uuid := auth.uid();
    v_cooperativa bigint;
begin
    if v_user_id is null then
        raise exception 'Cal iniciar la sessió' using errcode = '42501';
    end if;

    select m.cooperativa into v_cooperativa from public.membres_cooperativa m where m.jugador_id = v_user_id;

    delete from auth.users u where u.id = v_user_id;

    if v_cooperativa is not null
       and not exists (select 1 from public.membres_cooperativa m where m.cooperativa = v_cooperativa) then
        delete from public.cooperatives c where c.id = v_cooperativa;
    end if;
    return true;
end;
$$;

comment on function public.esborra_compte() is
    'Esborra el compte del jugador que la crida i totes les seves dades (docs/privacitat.md). No es pot desfer.';

revoke execute on function public.esborra_compte() from public, anon;
grant execute on function public.esborra_compte() to authenticated;
