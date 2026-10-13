import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TimeEntryResponse, WeeklyTimeEntriesResponse } from '../../core/api/generated';
import { formatDayMonth, formatWeekdayName, weekDays } from '../dates/week';
import { DecimalHoursPipe } from '../pipes/duration';
import { TimeEntryCard } from './time-entry-card';
import { coversDay } from './time-entry.ui';

/**
 * Vue semaine du journal, du lundi au dimanche (SFD-01 E1, réutilisée en lecture seule par SFD-04 E2).
 * Sans dépendance à un store. RG-01-13 : une PLAGE est répétée sur chaque jour couvert ; le total affiché est
 * celui de l'API (`totalMinutes`), jamais recalculé ici.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TimeEntryCard, DecimalHoursPipe],
  selector: 'app-week-journal',
  template: `
    <div class="flex flex-col gap-4" data-cy="journal-semaine">
      <ol class="grid grid-cols-1 md:grid-cols-7 gap-4 md:gap-2 list-none m-0 p-0">
        @for (day of days(); track day.iso) {
          <li class="flex flex-col gap-2 min-w-0" [attr.data-cy]="'jour-' + day.iso">
            <h2 class="text-sm font-medium text-color m-0">
              <span class="capitalize">{{ day.name }}</span>
              <span class="text-muted-color tabular-nums"> {{ day.label }}</span>
            </h2>
            @for (entry of day.entries; track entry.id) {
              <app-time-entry-card
                [entry]="entry"
                [editable]="editable()"
                (edit)="edit.emit($event)"
                (remove)="remove.emit($event)"
              />
            } @empty {
              <p class="text-xs text-muted-color m-0">Aucune saisie</p>
            }
          </li>
        }
      </ol>
      <p class="text-sm text-color m-0 md:text-right">
        Total de la semaine :
        <span class="font-semibold tabular-nums" data-cy="total-semaine">{{
          week().totalMinutes | decimalHours
        }}</span>
      </p>
    </div>
  `,
})
export class WeekJournal {
  readonly week = input.required<WeeklyTimeEntriesResponse>();
  readonly editable = input(true);
  readonly edit = output<TimeEntryResponse>();
  readonly remove = output<TimeEntryResponse>();

  protected readonly days = computed(() => {
    const week = this.week();
    return weekDays(week.weekStart).map((iso) => ({
      iso,
      name: formatWeekdayName(iso),
      label: formatDayMonth(iso),
      // L'ordre de l'API (date puis heure de début) est conservé.
      entries: week.entries.filter((entry) => coversDay(entry, iso)),
    }));
  });
}
