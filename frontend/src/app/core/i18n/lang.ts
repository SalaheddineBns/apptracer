/** RG-08-01: the interface exists in French and English. */
export type Lang = 'fr' | 'en';

export const LANGS: readonly Lang[] = ['fr', 'en'];

export const DEFAULT_LANG: Lang = 'en';

/** `localStorage` key where the user's choice is kept (RG-08-03). */
export const LANG_STORAGE_KEY = 'lang';

/**
 * `Intl` locale of each language: day and month names in the language, French or English number formatting (RG-08-08).
 */
export const INTL_LOCALE: Record<Lang, string> = { fr: 'fr-FR', en: 'en-GB' };

export function isLang(value: unknown): value is Lang {
  return value === 'fr' || value === 'en';
}

/** RG-08-02: French if the browser's preferred language is French, English for any other language. */
export function detectLang(preferred: string | null | undefined): Lang {
  return preferred?.trim().toLowerCase().startsWith('fr') ? 'fr' : 'en';
}
