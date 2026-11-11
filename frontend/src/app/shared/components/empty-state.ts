import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Empty state: icon, helpful sentence and projected action (e.g. a New time entry button). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-empty-state',
  template: `
    <div class="flex flex-col items-center gap-4 py-12 text-center" data-cy="etat-vide">
      <i [class]="icon() + ' text-4xl text-muted-color'" aria-hidden="true"></i>
      <p class="text-sm text-muted-color">{{ message() }}</p>
      <ng-content />
    </div>
  `,
})
export class EmptyState {
  readonly message = input.required<string>();
  readonly icon = input('pi pi-inbox');
}
