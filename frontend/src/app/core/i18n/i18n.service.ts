import { computed, inject, Injectable, signal } from '@angular/core';
import { PrimeNG } from 'primeng/config';
import { PRIMENG_EN } from '../theme/primeng-en';
import { PRIMENG_FR } from '../theme/primeng-fr';
import { en } from './en';
import { Dictionary, fr } from './fr';
import { PluralKey, TranslationKey, TranslationParams } from './keys';
import { DEFAULT_LANG, detectLang, INTL_LOCALE, isLang, Lang, LANG_STORAGE_KEY } from './lang';

export const DICTIONARIES: Record<Lang, Dictionary> = { fr, en };
const PRIMENG_TRANSLATIONS = { fr: PRIMENG_FR, en: PRIMENG_EN };

function storedLang(): Lang | null {
  try {
    const value = localStorage.getItem(LANG_STORAGE_KEY);
    return isLang(value) ? value : null;
  } catch {
    return null;
  }
}

function browserLang(): Lang {
  if (typeof navigator === 'undefined') {
    return DEFAULT_LANG;
  }
  return detectLang(navigator.languages?.[0] ?? navigator.language);
}

function lookup(dictionary: Dictionary, key: TranslationKey): string {
  return key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], dictionary) as string;
}

function interpolate(text: string, params?: TranslationParams): string {
  return params ? text.replace(/\{(\w+)\}/g, (match, name: string) => String(params[name] ?? match)) : text;
}

/**
 * Translation outside injection (pure functions such as `toProblem`): the language in force is the one in `localStorage`
 * (written by `setLang`), otherwise the browser's, exactly as when the service starts.
 */
export function translateNow(key: TranslationKey, params?: TranslationParams): string {
  return interpolate(lookup(DICTIONARIES[storedLang() ?? browserLang()], key), params);
}

/**
 * Interface language (SFD-08). Text in a template: the `t` pipe. Text in code: `inject(I18n).t('key')`.
 * Everything that reads `lang()` (computed, template) is recomputed on change, without reloading (RG-08-04).
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly primeng = inject(PrimeNG);
  private readonly current = signal<Lang>(storedLang() ?? browserLang());

  /** RG-08-02, RG-08-03: `localStorage`, otherwise the browser's language. */
  readonly lang = this.current.asReadonly();
  readonly dictionary = computed(() => DICTIONARIES[this.current()]);

  constructor() {
    this.apply(this.current());
  }

  /** RG-08-03: the choice is kept in the browser; RG-08-04: it applies immediately. */
  setLang(lang: Lang): void {
    try {
      localStorage.setItem(LANG_STORAGE_KEY, lang);
    } catch {
      // Storage unavailable: the language applies to the session only.
    }
    this.current.set(lang);
    this.apply(lang);
  }

  /** Translates a key into the current language; `{name}` is replaced by the parameter of the same name. */
  t(key: TranslationKey, params?: TranslationParams): string {
    return interpolate(lookup(this.dictionary(), key), params);
  }

  /**
   * Plural following the language's rule (RG-08-08): `plural('teams.memberCount', 0)` gives '0 membre' in French
   * (0 is singular) and '0 members' in English. `{count}` is provided.
   */
  plural(key: PluralKey, count: number, params: TranslationParams = {}): string {
    const category = new Intl.PluralRules(INTL_LOCALE[this.current()]).select(count) === 'one' ? 'one' : 'other';
    return this.t(`${key}.${category}` as TranslationKey, { ...params, count });
  }

  /** RG-08-05: declared language of the page, tab title, texts of the PrimeNG components. */
  private apply(lang: Lang): void {
    document.documentElement.lang = lang;
    document.title = DICTIONARIES[lang].app.title;
    this.primeng.setTranslation(PRIMENG_TRANSLATIONS[lang]);
  }
}
