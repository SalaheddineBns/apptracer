import { inject, Pipe, PipeTransform } from '@angular/core';
import { I18n } from './i18n.service';
import { TranslationKey, TranslationParams } from './keys';

/**
 * `{{ 'language.label' | t }}` or `{{ 'key' | t: { name: value } }}`.
 * Impure: the language is a signal, and the text must follow its change even when the key does not change.
 */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18n);

  transform(key: TranslationKey, params?: TranslationParams): string {
    return this.i18n.t(key, params);
  }
}
