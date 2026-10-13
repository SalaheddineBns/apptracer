-- SFD-01 RG-01-06 : une saisie ne peut pas chevaucher une autre saisie du même utilisateur
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Intervalle semi-ouvert [début, fin) d'horodatages locaux (début inclus, fin exclue) :
--   HOUR  : [start_date + start_time, start_date + end_time)
--   DAY / RANGE : jours entiers de start_date à end_date inclus, week-ends compris
ALTER TABLE time_entries
    ADD COLUMN occupied tsrange GENERATED ALWAYS AS (
        CASE WHEN granularity = 'HOUR'
            THEN tsrange(start_date + start_time, start_date + end_time, '[)')
            ELSE tsrange(start_date::timestamp, (end_date + 1)::timestamp, '[)')
        END
    ) STORED;

-- Filet de sécurité contre deux requêtes simultanées ; une saisie supprimée logiquement ne bloque
-- plus rien (RG-01-11). PostgreSQL ne compare jamais une ligne à elle-même (modification, RG-01-12).
ALTER TABLE time_entries
    ADD CONSTRAINT ex_time_entries_no_overlap
    EXCLUDE USING gist (user_id WITH =, occupied WITH &&)
    WHERE (deleted_at IS NULL);
