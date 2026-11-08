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
 * Traduction hors injection (fonctions pures comme `toProblem`) : la langue en vigueur est celle du `localStorage`
 * (écrite par `setLang`), sinon celle du navigateur, exactement comme au démarrage du service.
 */
export function translateNow(key: TranslationKey, params?: TranslationParams): string {
  return interpolate(lookup(DICTIONARIES[storedLang() ?? browserLang()], key), params);
}

/**
 * Langue de l'interface (SFD-08). Texte dans un gabarit : pipe `t`. Texte dans le code : `inject(I18n).t('cle')`.
 * Tout ce qui lit `lang()` (computed, gabarit) est recalculé au changement, sans rechargement (RG-08-04).
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly primeng = inject(PrimeNG);
  private readonly current = signal<Lang>(storedLang() ?? browserLang());

  /** RG-08-02, RG-08-03 : `localStorage`, sinon langue du navigateur. */
  readonly lang = this.current.asReadonly();
  readonly dictionary = computed(() => DICTIONARIES[this.current()]);

  constructor() {
    this.apply(this.current());
  }

  /** RG-08-03 : le choix est conservé dans le navigateur ; RG-08-04 : il s'applique immédiatement. */
  setLang(lang: Lang): void {
    try {
      localStorage.setItem(LANG_STORAGE_KEY, lang);
    } catch {
      // Stockage indisponible : la langue vaut pour la session seulement.
    }
    this.current.set(lang);
    this.apply(lang);
  }

  /** Traduit une clé dans la langue courante ; `{nom}` est remplacé par le paramètre du même nom. */
  t(key: TranslationKey, params?: TranslationParams): string {
    return interpolate(lookup(this.dictionary(), key), params);
  }

  /**
   * Pluriel selon la règle de la langue (RG-08-08) : `plural('teams.memberCount', 0)` donne « 0 membre » en français
   * (0 est au singulier) et « 0 members » en anglais. `{count}` est fourni.
   */
  plural(key: PluralKey, count: number, params: TranslationParams = {}): string {
    const category = new Intl.PluralRules(INTL_LOCALE[this.current()]).select(count) === 'one' ? 'one' : 'other';
    return this.t(`${key}.${category}` as TranslationKey, { ...params, count });
  }

  /** RG-08-05 : langue déclarée de la page, titre de l'onglet, textes des composants PrimeNG. */
  private apply(lang: Lang): void {
    document.documentElement.lang = lang;
    document.title = DICTIONARIES[lang].app.title;
    this.primeng.setTranslation(PRIMENG_TRANSLATIONS[lang]);
  }
}
