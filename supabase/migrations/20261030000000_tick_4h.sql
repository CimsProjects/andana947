-- Tick econòmic cada 4 hores.
--
-- En lloc d'un únic tic diari, els ingressos s'acrediten en 6 tocs al dia
-- (cada 4 hores). Cada toc abona 1/6 de l'ingrés diari de cada concessió i
-- dividends d'accionariat. Això fa que el joc sigui més dinàmic: l'usuari
-- veu el saldo créixer durant el dia en lloc d'esperar fins a la mitjanit.
--
-- Prerequisit: l'extensió pg_cron ha d'estar habilitada al projecte de
-- Supabase (Extensions → pg_cron). Habiliteu-la des del tauler si no ho
-- està; aquesta migració fallarà altrament.

-- -----------------------------------------------------------------------
-- 1. Funció tic_4h
--    Calcula els ingressos de 4h (= ingrés_diari / 6) per a cada jugador.
--    S'executa com a funció de manteniment (security definer, sense RLS).
-- -----------------------------------------------------------------------
create or replace function public.tic_4h()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    r_jugador   record;
    r_concessio record;
    r_accio     record;
    v_factor    double precision;
    v_hora      integer;
    v_ingres    numeric(12, 2);
    v_dividend  numeric(12, 2);
    v_total     numeric(12, 2);
    v_concepte  text;
begin
    -- Factor de dia feiner vs. festiu.
    -- Dilluns (1) … Divendres (5) → 1.0 ; Dissabte/Diumenge → 0.6
    if extract(isodow from now() at time zone 'Europe/Madrid') between 1 and 5 then
        v_factor := 1.0;
    else
        v_factor := 0.6;
    end if;

    v_hora := extract(hour from now() at time zone 'Europe/Madrid');

    -- Recorrem tots els jugadors que tinguin concessions actives o accions.
    for r_jugador in
        select distinct jugador_id
        from public.concessions
        where activa = true
          and caduca_el >= now()
        union
        select distinct jugador_id
        from public.accionariat
        where quantitat > 0
    loop
        v_total := 0;

        -- 1a. Ingressos de concessions actives (1/6 de l'ingrés diari).
        for r_concessio in
            select c.id, c.node_id, c.ingres_diari,
                   n.nom
            from public.concessions c
            join public.nodes n on n.id = c.node_id
            where c.jugador_id = r_jugador.jugador_id
              and c.activa = true
              and c.caduca_el >= now()
        loop
            v_ingres := round(r_concessio.ingres_diari * v_factor / 6.0, 2);
            v_total  := v_total + v_ingres;

            insert into public.moviments (jugador_id, concepte, import, node_id)
            values (
                r_jugador.jugador_id,
                'Ingrés 4h: ' || r_concessio.nom,
                v_ingres,
                r_concessio.node_id
            );
        end loop;

        -- 1b. Dividends d'accionariat (1/6 del dividend diari).
        for r_accio in
            select a.node_id, a.quantitat,
                   n.nom,
                   -- Dividend diari total: ingrés_diari del node * 0.02 * quantitat.
                   -- El 2% per acció és el mateix que fa tic_diari.
                   round(n.viatgers_feiner::numeric * 0.00002 * a.quantitat, 2) as dividend_diari
            from public.accionariat a
            join public.nodes n on n.id = a.node_id
            where a.jugador_id = r_jugador.jugador_id
              and a.quantitat > 0
        loop
            v_dividend := round(r_accio.dividend_diari * v_factor / 6.0, 2);
            if v_dividend > 0 then
                v_total := v_total + v_dividend;

                insert into public.moviments (jugador_id, concepte, import, node_id)
                values (
                    r_jugador.jugador_id,
                    'Dividend 4h: ' || r_accio.nom,
                    v_dividend,
                    r_accio.node_id
                );
            end if;
        end loop;

        -- Actualitzem el saldo del jugador si hi ha algun ingrés.
        if v_total > 0 then
            update public.perfils
            set saldo = saldo + v_total
            where id = r_jugador.jugador_id;
        end if;

    end loop;

    -- 2. Marcar concessions caducades que han expirat des de l'últim tic.
    update public.concessions
    set activa = false
    where activa = true
      and caduca_el < now();

end;
$$;

comment on function public.tic_4h() is
    'Abona 1/6 dels ingressos diaris de cada concessió activa i dividend '
    'd''accionariat. S''executa cada 4 hores via pg_cron. '
    'Factor 1.0 en dies feiners, 0.6 en caps de setmana.';

-- -----------------------------------------------------------------------
-- 2. Planificació del job pg_cron
--    Cada 4 hores (a les 0h, 4h, 8h, 12h, 16h i 20h, hora de Madrid).
--    pg_cron treballa en UTC; l'Europa/Madrid és UTC+1 (UTC+2 a l'estiu).
--    Usem UTC 23, 3, 7, 11, 15 i 19 per cobrir les 0h, 4h, 8h, 12h, 16h
--    i 20h de Madrid en horari d'hivern.  A l'estiu hi haurà un desfasament
--    d'1h, acceptable per a un joc.
-- -----------------------------------------------------------------------
select cron.schedule(
    'tick_4h',                    -- nom del job (únic)
    '0 23,3,7,11,15,19 * * *',   -- cada 4h, hora UTC (≈ 0/4/8/12/16/20h CET)
    $$select public.tic_4h();$$
);
