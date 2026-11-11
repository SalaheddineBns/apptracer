import { TagColor } from '../core/api/generated';
import { TranslationKey } from '../core/i18n/keys';

export type TagSeverity = 'info' | 'warn' | 'secondary' | 'contrast' | 'success';

/** RG-02-04: a type's colour → `p-tag` severity (design system palette, never a free colour). */
const SEVERITY_BY_COLOR: Record<TagColor, TagSeverity> = {
  [TagColor.Info]: 'info',
  [TagColor.Warn]: 'warn',
  [TagColor.Secondary]: 'secondary',
  [TagColor.Contrast]: 'contrast',
  [TagColor.Success]: 'success',
};

/** Design system icons, linked to the six initial codes (RG-02-02). A type created by the ADMIN has no icon. */
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

/** PrimeIcons icon of the code, or `undefined` for a code outside the six initial ones. */
export function iconForCode(code: string): string | undefined {
  return Object.hasOwn(ICON_BY_CODE, code) ? ICON_BY_CODE[code] : undefined;
}

/** RG-02-04: key of the name of each palette colour (colour is never the only carrier of information). */
const LABEL_KEY_BY_COLOR: Record<TagColor, TranslationKey> = {
  [TagColor.Info]: 'colors.info',
  [TagColor.Warn]: 'colors.warn',
  [TagColor.Secondary]: 'colors.secondary',
  [TagColor.Contrast]: 'colors.contrast',
  [TagColor.Success]: 'colors.success',
};

export function labelKeyForColor(color: TagColor): TranslationKey {
  return LABEL_KEY_BY_COLOR[color];
}

/** The five palette colours, in the order of the contract's enum; their name is translated with `labelKeyForColor`. */
export const TAG_COLORS: readonly TagColor[] = Object.values(TagColor);
