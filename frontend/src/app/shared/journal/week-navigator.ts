import { ChangeDetectionStrategy, Component, computed, inject, model } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { I18n } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { addDays, formatDate } from '../dates/week';

/** Week-to-week navigation (SFD-01 E1, SFD-04 E1 and E2). `weekStart` is a Monday, `YYYY-MM-DD`. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, TPipe],
  selector: 'app-week-navigator',
  template: `
    <div class="flex flex-wrap items-center justify-between gap-2" data-cy="navigation-semaine">
      <p-button
        type="button"
        [label]="'weekNavigator.previous' | t"
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
        [label]="'weekNavigator.next' | t"
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

  private readonly i18n = inject(I18n);
  protected readonly label = computed(() =>
    this.i18n.t('weekNavigator.label', {
      start: formatDate(this.weekStart()),
      end: formatDate(addDays(this.weekStart(), 6)),
    }),
  );

  protected shift(days: number): void {
    this.weekStart.set(addDays(this.weekStart(), days));
  }
}
