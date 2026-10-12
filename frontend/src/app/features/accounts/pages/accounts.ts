import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Page PROVISOIRE : destination de l'ADMIN en attendant « Comptes » (SFD-03 E4, T-03-17). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-accounts-placeholder',
  template: `
    <div class="bg-surface-0 dark:bg-surface-900 rounded-xl p-6 flex flex-col gap-2">
      <h1 class="text-2xl font-semibold text-color" data-cy="titre-comptes">Comptes</h1>
      <p class="text-sm text-muted-color">Page provisoire : la gestion des comptes arrive avec la SFD-03 (écran E4).</p>
    </div>
  `,
})
export class AccountsPlaceholderPage {}
