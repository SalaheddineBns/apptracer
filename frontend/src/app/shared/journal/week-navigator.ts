import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { addDays, formatFrenchDate } from '../dates/week';

/** Navigation de semaine en semaine (SFD-01 E1, SFD-04 E1 et E2). `weekStart` est un lundi, `YYYY-MM-DD`. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule],
  selector: 'app-week-navigator',
  template: `
    <div class="flex flex-wrap items-center justify-between gap-2" data-cy="navigation-semaine">
      <p-button
        type="button"
        label="Semaine précédente"
        icon="pi pi-chevron-left"
        [text]="true"
        severity="secondary"
        (onClick)="shift(-7)"
        data-cy="btn-semaine-precedente"
      />
      <span
        class="text-sm font-medium text-color tabular-nums text-center max-sm:order-first max-sm:w-full"
        aria-live="polite"
        data-cy="libelle-semaine"
        >{{ label() }}</span
      >
      <p-button
        type="button"
        label="Semaine suivante"
        icon="pi pi-chevron-right"
        iconPos="right"
        [text]="true"
        severity="secondary"
        (onClick)="shift(7)"
        data-cy="btn-semaine-suivante"
      />
    </div>
  `,
})
export class WeekNavigator {
  readonly weekStart = model.required<string>();

  protected readonly label = computed(
    () =>
      `Semaine du ${formatFrenchDate(this.weekStart())} au ${formatFrenchDate(addDays(this.weekStart(), 6))}`,
  );

  protected shift(days: number): void {
    this.weekStart.set(addDays(this.weekStart(), days));
  }
}
