import { TagColor } from '../core/api/generated';

export type TagSeverity = 'info' | 'warn' | 'secondary' | 'contrast' | 'success';

/** RG-02-04 : couleur d'un type → severity `p-tag` (palette du design system, jamais de couleur libre). */
const SEVERITY_BY_COLOR: Record<TagColor, TagSeverity> = {
  [TagColor.Info]: 'info',
  [TagColor.Warn]: 'warn',
  [TagColor.Secondary]: 'secondary',
  [TagColor.Contrast]: 'contrast',
  [TagColor.Success]: 'success',
};

/** Icônes du design system, associées aux six codes initiaux (RG-02-02). Un type créé par l'ADMIN n'a pas d'icône. */
const ICON_BY_CODE: Readonly<Record<string, string>> = {
  TRAVAIL_PROJET: 'pi pi-briefcase',
  REUNION: 'pi pi-users',
  LECTURE_SFD: 'pi pi-book',
  REVUE_MR: 'pi pi-code',
  FORMATION: 'pi pi-graduation-cap',
  AUTRE: 'pi pi-ellipsis-h',
};

export function severityForColor(color: TagColor): TagSeverity {
  return SEVERITY_BY_COLOR[color];
}

/** Icône PrimeIcons du code, ou `undefined` pour un code hors des six initiaux. */
export function iconForCode(code: string): string | undefined {
  return Object.hasOwn(ICON_BY_CODE, code) ? ICON_BY_CODE[code] : undefined;
}

/** RG-02-04 : nom français de chaque couleur de la palette (la couleur n'est jamais le seul porteur d'information). */
const LABEL_BY_COLOR: Record<TagColor, string> = {
  [TagColor.Info]: 'Bleu',
  [TagColor.Warn]: 'Orange',
  [TagColor.Secondary]: 'Gris',
  [TagColor.Contrast]: 'Contraste',
  [TagColor.Success]: 'Vert',
};

export function labelForColor(color: TagColor): string {
  return LABEL_BY_COLOR[color];
}

/** Les cinq couleurs de la palette, dans l'ordre de l'enum du contrat. */
export const TAG_COLOR_OPTIONS: { value: TagColor; label: string }[] = Object.values(TagColor).map((value) => ({
  value,
  label: LABEL_BY_COLOR[value],
}));
