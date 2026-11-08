/**
 * Dictionnaire français : référence des clés. `en.ts` est typé par `Dictionary` : une clé manquante ou en trop
 * casse la compilation (RG-08-05). Les paramètres s'écrivent `{nom}`.
 */
export const fr = {
  app: {
    title: 'AppTracer',
  },
  language: {
    label: 'Langue de l’interface',
    fr: 'Français',
    en: 'English',
  },
};

export type Dictionary = typeof fr;
