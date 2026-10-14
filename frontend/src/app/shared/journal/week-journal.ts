import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { TimeEntryResponse, WeeklyTimeEntriesResponse } from '../../core/api/generated';
import { ButtonModule } from 'primeng/button';
import { formatDayMonth, formatWeekdayName, todayInParis, weekDays } from '../dates/week';
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
  imports: [ButtonModule, TimeEntryCard, DecimalHoursPipe],
  selector: 'app-week-journal',
  template: `
    <div class="flex flex-col gap-4" data-cy="journal-semaine">
      <ol class="grid grid-cols-1 md:grid-cols-7 gap-4 md:gap-2 list-none m-0 p-0">
        @for (day of days(); track day.iso) {
          <li class="flex flex-col gap-2 min-w-0" [attr.data-cy]="'jour-' + day.iso">
            <div class="flex items-center justify-between gap-2 min-h-8">
              <h2 class="text-sm font-medium text-color m-0">
                <span class="capitalize">{{ day.name }}</span>
                <span class="text-muted-color tabular-nums"> {{ day.label }}</span>
              </h2>
              @if (editable() && day.addable) {
                <p-button
                  type="button"
                  icon="pi pi-plus"
                  [outlined]="true"
                  size="small"
                  severity="secondary"
                  [ariaLabel]="'Ajouter une saisie le ' + day.name + ' ' + day.label"
                  (onClick)="add.emit(day.iso)"
                  [attr.data-cy]="'btn-ajouter-saisie-' + day.iso"
                />
              }
            </div>
            @for (entry of day.entries; track entry.id) {
              <app-time-entry-card
                [entry]="entry"
                [editable]="editable()"
                [showComment]="showComment()"
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
  /** RG-01-10, RG-04-03 : faux pour le journal d'un autre compte : ni « + », ni « Modifier », ni « Supprimer ». */
  readonly editable = input(true);
  /** SFD-04 E2 : affiche le commentaire de chaque saisie (« Mon journal » ne le liste pas). */
  readonly showComment = input(false);
  readonly edit = output<TimeEntryResponse>();
  readonly remove = output<TimeEntryResponse>();
  /** Date du jour (`YYYY-MM-DD`), à Paris par défaut (RG-00-01) : borne des jours où l'on peut saisir. */
  readonly today = input(todayInParis());
  /** SFD-01 E1 : « + » d'un jour, avec sa date (`YYYY-MM-DD`). */
  readonly add = output<string>();

  protected readonly days = computed(() => {
    const week = this.week();
    const today = this.today();
    return weekDays(week.weekStart).map((iso) => ({
      iso,
      // RG-01-07 : pas de saisie à une date de début future, donc pas de « + » sur un jour à venir.
      addable: iso <= today,
      name: formatWeekdayName(iso),
      label: formatDayMonth(iso),
      // L'ordre de l'API (date puis heure de début) est conservé.
      entries: week.entries.filter((entry) => coversDay(entry, iso)),
    }));
  });
}
