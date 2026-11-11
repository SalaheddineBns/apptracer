import { Dictionary } from './fr';

/** Dotted paths of every leaf of a dictionary: `'language.fr'`, `'app.title'`… */
type Paths<T> = {
  [K in keyof T & string]: T[K] extends object ? `${K}.${Paths<T[K]>}` : K;
}[keyof T & string];

/** Valid translation key: an unknown key is refused at compile time. */
export type TranslationKey = Paths<Dictionary>;

export type TranslationParams = Record<string, string | number>;

/** Base of a plural pair: `'teams.memberCount'` for `teams.memberCount.one` and `teams.memberCount.other`. */
export type PluralKey = { [K in TranslationKey]: K extends `${infer Base}.one` ? Base : never }[TranslationKey];
