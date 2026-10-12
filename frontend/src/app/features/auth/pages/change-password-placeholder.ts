import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Page PROVISOIRE : cible du menu « Changer mon mot de passe » en attendant l'écran E3 (T-03-16). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-change-password-placeholder',
  template: `
    <div class="bg-surface-0 dark:bg-surface-900 rounded-xl p-6 flex flex-col gap-2">
      <h1 class="text-2xl font-semibold text-color" data-cy="titre-mot-de-passe">Changer mon mot de passe</h1>
      <p class="text-sm text-muted-color">Page provisoire : le formulaire arrive avec la tâche T-03-16.</p>
    </div>
  `,
})
export class ChangePasswordPlaceholderPage {}
