import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TimeEntryResponse } from '../../core/api/generated';
import { I18n } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { ActivityTypeTag } from '../components/activity-type-tag';
import { DurationPipe } from '../pipes/duration';
import { datesLabel, periodLabel } from './time-entry.ui';

/**
 * A time entry in the journal (SFD-01 E1, reused read-only by SFD-04 E2). No dependency on a store.
 * RG-02-05, CA-02-07: the type and the project come from the entry; they are shown even when deactivated.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, ActivityTypeTag, DurationPipe, TPipe],
  selector: 'app-time-entry-card',
  template: `
    <article
      class="flex flex-col items-start gap-2 rounded-lg bg-surface-50 dark:bg-surface-800 p-2 min-w-0"
      [attr.data-cy]="'saisie-' + entry().id"
    >
      <app-activity-type-tag
        [label]="entry().activityType.label"
        [color]="entry().activityType.color"
        [code]="entry().activityType.code"
      />
      @if (entry().project; as project) {
        <span class="text-sm text-color wrap-anywhere" data-cy="saisie-projet">{{
          project.name
        }}</span>
      }
      <span class="text-xs text-muted-color tabular-nums" data-cy="saisie-periode">{{
        period()
      }}</span>
      @if (showDuration()) {
        <span class="text-sm font-medium text-color tabular-nums" data-cy="saisie-duree">{{
          entry().durationMinutes | duration
        }}</span>
      }
      @if (showComment() && entry().comment; as comment) {
        <!-- Limité à 3 lignes si demandé : le texte complet reste dans le DOM (lecteurs d'écran) et au survol.
             Texte collé aux balises : avec whitespace-pre-line, un retour à la ligne du gabarit deviendrait une ligne vide. -->
        <p
          class="m-0 text-xs text-muted-color whitespace-pre-line wrap-anywhere"
          [class.line-clamp-3]="clampComment()"
          [attr.title]="clampComment() ? comment : null"
          data-cy="saisie-commentaire"
          >{{ comment }}</p
        >
      }
      @if (editable()) {
        <div class="flex gap-2">
          <p-button
            type="button"
            icon="pi pi-pencil"
            [text]="true"
            size="small"
            severity="secondary"
            [ariaLabel]="'timeEntry.editAria' | t: { name: name() }"
            (onClick)="edit.emit(entry())"
            [attr.data-cy]="'btn-modifier-saisie-' + entry().id"
          />
          <p-button
            type="button"
            icon="pi pi-trash"
            [text]="true"
            size="small"
            severity="secondary"
            [ariaLabel]="'timeEntry.deleteAria' | t: { name: name() }"
            (onClick)="remove.emit(entry())"
            [attr.data-cy]="'btn-supprimer-saisie-' + entry().id"
          />
        </div>
      }
    </article>
  `,
})
export class TimeEntryCard {
  readonly entry = input.required<TimeEntryResponse>();
  /** RG-01-10: Edit and Delete are rendered only in one's own journal. */
  readonly editable = input(true);
  /** Comment of the time entry: My journal (SFD-01 E1) and a member's journal (SFD-04 E2). */
  readonly showComment = input(false);
  /** SFD-04 E2 shows the recorded duration; My journal (SFD-01 E1) shows the comment instead. */
  readonly showDuration = input(true);
  /** Comment limited to 3 lines (narrow cards of My journal). */
  readonly clampComment = input(false);
  readonly edit = output<TimeEntryResponse>();
  readonly remove = output<TimeEntryResponse>();

  private readonly i18n = inject(I18n);
  protected readonly period = computed(() => periodLabel(this.entry(), this.i18n));
  protected readonly name = computed(
    () => `${this.entry().activityType.label} ${datesLabel(this.entry(), this.i18n)}`,
  );
}
