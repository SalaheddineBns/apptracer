import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { I18n } from './i18n.service';
import { LANGS, Lang } from './lang';
import { TPipe } from './t.pipe';

/**
 * SFD-08 E1 Sélecteur de langue (page de connexion). « Français » et « English » sont écrits dans leur propre langue
 * (RG-08-10 : ouvert à tous, avant la connexion) ; la langue en vigueur est le choix sélectionné.
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
      data-cy="selecteur-langue"
    >
      <ng-template #item let-option>
        <span [attr.data-cy]="'choix-langue-' + option.lang" [attr.lang]="option.lang">{{ option.label }}</span>
      </ng-template>
    </p-selectbutton>
  `,
})
export class LanguageSelector {
  protected readonly i18n = inject(I18n);
  // Chaque libellé est écrit dans sa propre langue : « Français » et « English » sont identiques dans les deux dictionnaires.
  protected readonly options = computed(() => LANGS.map((lang) => ({ lang, label: this.i18n.t(`language.${lang}`) })));

  protected choose(lang: Lang | null): void {
    if (lang) {
      this.i18n.setLang(lang);
    }
  }
}
