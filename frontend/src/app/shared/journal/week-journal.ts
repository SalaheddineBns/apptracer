import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { TimeEntryResponse, WeeklyTimeEntriesResponse } from '../../core/api/generated';
import { ButtonModule } from 'primeng/button';
import { I18n } from '../../core/i18n/i18n.service';
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
            <!--
              En liste (< md) : une ligne « jour date · total · + ».
              En 7 colonnes (≥ md) : ligne 1 « jour · total », ligne 2 « date · + » ; entre md et lg le nom est abrégé
              (« mer. ») pour que le total tienne à sa droite dans une colonne étroite.
            -->
            <div
              class="grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-x-2 min-h-8 md:grid-cols-[minmax(0,1fr)_auto] md:gap-y-1"
            >
              <h2 class="text-sm font-medium text-color m-0 min-w-0 md:col-start-1 md:row-start-1">
                <span class="capitalize md:max-lg:sr-only">{{ day.name }}</span>
                <span class="capitalize hidden md:max-lg:inline" aria-hidden="true">{{ day.shortName }}</span>
                <span class="text-muted-color tabular-nums md:sr-only"> {{ day.label }}</span>
              </h2>
              @if (showDayTotals()) {
                <span
                  class="text-sm tabular-nums text-color whitespace-nowrap justify-self-end md:col-start-2 md:row-start-1"
                >
                  <span aria-hidden="true" [class.text-muted-color]="day.minutes === 0" [attr.data-cy]="'total-jour-' + day.iso">{{
                    day.minutes === 0 ? '–' : (day.minutes | decimalHours)
                  }}</span>
                  <span class="sr-only">Total du {{ day.name }} : {{ day.minutes | decimalHours }}</span>
                </span>
              }
              <span
                class="hidden text-sm text-muted-color tabular-nums md:flex md:items-center md:h-8 md:col-start-1 md:row-start-2"
                aria-hidden="true"
                >{{ day.label }}</span
              >
              @if (editable() && day.addable) {
                <p-button
                  type="button"
                  icon="pi pi-plus"
                  [outlined]="true"
                  size="small"
                  severity="secondary"
                  class="justify-self-end md:col-start-2 md:row-start-2"
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
                [showDuration]="showDuration()"
                [clampComment]="clampComment()"
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
  /** Affiche le commentaire de chaque saisie. */
  readonly showComment = input(false);
  /** Durée enregistrée sur chaque carte (SFD-04 E2) ; « Mon journal » affiche le commentaire à la place. */
  readonly showDuration = input(true);
  /** Commentaire limité à 3 lignes. */
  readonly clampComment = input(false);
  /** SFD-07 E1 : total du jour dans l'en-tête, tiré de `week.days` (RG-07-02, aucun recalcul) ; faux hors « Mon journal ». */
  readonly showDayTotals = input(false);
  readonly edit = output<TimeEntryResponse>();
  readonly remove = output<TimeEntryResponse>();
  /** Date du jour (`YYYY-MM-DD`), à Paris par défaut (RG-00-01) : borne des jours où l'on peut saisir. */
  readonly today = input(todayInParis());
  /** SFD-01 E1 : « + » d'un jour, avec sa date (`YYYY-MM-DD`). */
  readonly add = output<string>();

  private readonly i18n = inject(I18n);

  protected readonly days = computed(() => {
    const week = this.week();
    const lang = this.i18n.lang();
    const today = this.today();
    const totals = new Map(week.days.map((total) => [total.date, total.minutes]));
    return weekDays(week.weekStart).map((iso) => ({
      iso,
      // RG-01-07 : pas de saisie à une date de début future, donc pas de « + » sur un jour à venir.
      addable: iso <= today,
      name: formatWeekdayName(iso, lang),
      // « lun. » … « dim. » : les trois premières lettres suffisent à distinguer les jours (français et anglais).
      shortName: `${formatWeekdayName(iso, lang).slice(0, 3)}.`,
      label: formatDayMonth(iso),
      minutes: totals.get(iso) ?? 0,
      // L'ordre de l'API (date puis heure de début) est conservé.
      entries: week.entries.filter((entry) => coversDay(entry, iso)),
    }));
  });
}
