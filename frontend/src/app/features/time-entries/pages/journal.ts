import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Page PROVISOIRE : destination de la connexion en attendant « Mon journal » (SFD-01, T-01-11). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-journal-placeholder',
  template: `
    <div class="bg-surface-0 dark:bg-surface-900 rounded-xl p-6 flex flex-col gap-2">
      <h1 class="text-2xl font-semibold text-color" data-cy="titre-journal">Mon journal</h1>
      <p class="text-sm text-muted-color">Page provisoire : le journal sera disponible avec la SFD-01.</p>
    </div>
  `,
})
export class JournalPlaceholderPage {}
