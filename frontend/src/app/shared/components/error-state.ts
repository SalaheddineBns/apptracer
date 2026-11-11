import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, untracked } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { I18n } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';

/** Error state: an understandable message and a Retry button. */
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
  /** Message to show; by default, the generic loading message in the language in force. */
  readonly message = input<string | null>(null);
  private readonly i18n = inject(I18n);
  // RG-08-04 : le message du serveur est dans la langue de la requête. Si la langue change tant qu'il est affiché, on le
  // remplace par le message générique traduit ; « Réessayer » relance la requête, dont la réponse sera dans la nouvelle langue.
  private readonly received = linkedSignal(() => ({ message: this.message(), lang: untracked(this.i18n.lang) }));
  protected readonly text = computed(() => {
    const { message, lang } = this.received();
    return message !== null && lang === this.i18n.lang() ? message : this.i18n.t('common.loadError');
  });
  readonly retry = output<void>();
}
