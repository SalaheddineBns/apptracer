import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { I18n } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';

/** État d'erreur : message compréhensible et bouton « Réessayer ». */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, TPipe],
  selector: 'app-error-state',
  template: `
    <div class="flex flex-col items-center gap-4 py-12 text-center" data-cy="etat-erreur" role="alert">
      <i class="pi pi-exclamation-triangle text-4xl text-muted-color" aria-hidden="true"></i>
      <p class="text-sm text-muted-color">{{ text() }}</p>
      <p-button [label]="'common.retry' | t" icon="pi pi-refresh" severity="secondary" data-cy="btn-reessayer" (onClick)="retry.emit()" />
    </div>
  `,
})
export class ErrorState {
  /** Message à afficher ; par défaut, le message générique de chargement dans la langue en vigueur. */
  readonly message = input<string | null>(null);
  private readonly i18n = inject(I18n);
  protected readonly text = computed(() => this.message() ?? this.i18n.t('common.loadError'));
  readonly retry = output<void>();
}
