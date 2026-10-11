import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

/** État d'erreur : message compréhensible et bouton « Réessayer ». */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule],
  selector: 'app-error-state',
  template: `
    <div class="flex flex-col items-center gap-4 py-12 text-center" data-cy="etat-erreur" role="alert">
      <i class="pi pi-exclamation-triangle text-4xl text-muted-color" aria-hidden="true"></i>
      <p class="text-sm text-muted-color">{{ message() }}</p>
      <p-button label="Réessayer" icon="pi pi-refresh" severity="secondary" data-cy="btn-reessayer" (onClick)="retry.emit()" />
    </div>
  `,
})
export class ErrorState {
  readonly message = input('Impossible de charger les données.');
  readonly retry = output<void>();
}
