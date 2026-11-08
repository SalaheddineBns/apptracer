/** RG-08-01 : l'interface existe en français et en anglais. */
export type Lang = 'fr' | 'en';

export const LANGS: readonly Lang[] = ['fr', 'en'];

export const DEFAULT_LANG: Lang = 'en';

/** Clé de `localStorage` où le choix de l'utilisateur est conservé (RG-08-03). */
export const LANG_STORAGE_KEY = 'lang';

/** Locale `Intl` de chaque langue : jours et mois nommés dans la langue, nombres à la française ou à l'anglaise (RG-08-08). */
export const INTL_LOCALE: Record<Lang, string> = { fr: 'fr-FR', en: 'en-GB' };

export function isLang(value: unknown): value is Lang {
  return value === 'fr' || value === 'en';
}

/** RG-08-02 : français si la langue préférée du navigateur est le français, anglais pour toute autre langue. */
export function detectLang(preferred: string | null | undefined): Lang {
  return preferred?.trim().toLowerCase().startsWith('fr') ? 'fr' : 'en';
}
