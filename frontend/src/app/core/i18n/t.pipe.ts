import { inject, Pipe, PipeTransform } from '@angular/core';
import { I18n } from './i18n.service';
import { TranslationKey, TranslationParams } from './keys';

/**
 * `{{ 'language.label' | t }}` ou `{{ 'cle' | t: { nom: valeur } }}`.
 * Impur : la langue est un signal, le texte doit suivre son changement même si la clé ne change pas.
 */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18n);

  transform(key: TranslationKey, params?: TranslationParams): string {
    return this.i18n.t(key, params);
  }
}
