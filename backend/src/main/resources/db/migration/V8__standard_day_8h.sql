-- SFD-06 : la journée standard passe de 7 h à 8 h à l'installation
-- RG-06-01, RG-00-02 : 480 minutes, uniquement si la valeur n'a jamais été modifiée (updated_at IS NULL).
-- RG-06-05 : updated_at / updated_by restent vides (la valeur d'installation n'est pas une modification d'ADMIN).
-- La version n'est pas touchée : aucune modification d'ADMIN n'a eu lieu.
-- RG-06-04 : aucune saisie n'est lue ni recalculée.
UPDATE app_settings
SET standard_day_minutes = 480
WHERE id = 1
  AND updated_at IS NULL;
