import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import type { ToggleButton } from 'primeng/togglebutton';
import { I18n } from './i18n.service';
import { LANGS, Lang } from './lang';
import { TPipe } from './t.pipe';

/**
 * SFD-08 E1 Language selector (sign-in page). 'Français' and 'English' are written in their own language
 * (RG-08-10: open to everyone, before signing in); the language in force is the selected option.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, SelectButtonModule, TPipe],
  selector: 'app-language-selector',
  template: `
    <span id="language-selector-label" class="sr-only">{{ 'language.label' | t }}</span>
    <p-selectbutton
      [options]="options()"
      optionLabel="label"
      optionValue="lang"
      [ngModel]="i18n.lang()"
      (ngModelChange)="choose($event)"
      [allowEmpty]="false"
      size="small"
      ariaLabelledBy="language-selector-label"
      [pt]="pt"
      data-cy="selecteur-langue"
    >
      <ng-template #item let-option>
        <span [attr.data-cy]="'choix-langue-' + option.lang">{{ option.label }}</span>
      </ng-template>
    </p-selectbutton>
  `,
})
export class LanguageSelector {
  protected readonly i18n = inject(I18n);
  // Each label is written in its own language: 'Français' and 'English' are identical in both dictionaries.
  protected readonly options = computed(() => LANGS.map((lang) => ({ lang, label: this.i18n.t(`language.${lang}`) })));

  /**
   * `lang` on the button itself (its name comes from `aria-label`, so from text the inner `<span>` does not cover):
   * the screen reader pronounces 'Français' in French and 'English' in English, whatever the page's language.
   */
  protected readonly pt = {
    pcToggleButton: {
      root: ({ instance }: { instance: ToggleButton }) => ({ lang: LANGS.find((lang) => this.i18n.t(`language.${lang}`) === instance.ariaLabel()) }),
    },
  };

  protected choose(lang: Lang | null): void {
    if (lang) {
      this.i18n.setLang(lang);
    }
  }
}
