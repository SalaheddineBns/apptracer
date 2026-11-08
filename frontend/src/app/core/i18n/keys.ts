import { Dictionary } from './fr';

/** Chemins pointés de toutes les feuilles d'un dictionnaire : `'language.fr'`, `'app.title'`… */
type Paths<T> = {
  [K in keyof T & string]: T[K] extends object ? `${K}.${Paths<T[K]>}` : K;
}[keyof T & string];

/** Clé de traduction valide : une clé inconnue est refusée à la compilation. */
export type TranslationKey = Paths<Dictionary>;

export type TranslationParams = Record<string, string | number>;
