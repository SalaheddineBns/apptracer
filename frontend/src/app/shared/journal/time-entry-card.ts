import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TimeEntryResponse } from '../../core/api/generated';
import { ActivityTypeTag } from '../components/activity-type-tag';
import { DurationPipe } from '../pipes/duration';
import { datesLabel, periodLabel } from './time-entry.ui';

/**
 * Une saisie dans le journal (SFD-01 E1, réutilisée en lecture seule par SFD-04 E2). Sans dépendance à un store.
 * RG-02-05, CA-02-07 : le type et le projet viennent de la saisie, ils s'affichent même désactivés.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, ActivityTypeTag, DurationPipe],
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
      <span class="text-sm font-medium text-color tabular-nums" data-cy="saisie-duree">{{
        entry().durationMinutes | duration
      }}</span>
      @if (editable()) {
        <div class="flex gap-2">
          <p-button
            type="button"
            icon="pi pi-pencil"
            [text]="true"
            size="small"
            severity="secondary"
            [ariaLabel]="'Modifier la saisie ' + name()"
            (onClick)="edit.emit(entry())"
            [attr.data-cy]="'btn-modifier-saisie-' + entry().id"
          />
          <p-button
            type="button"
            icon="pi pi-trash"
            [text]="true"
            size="small"
            severity="secondary"
            [ariaLabel]="'Supprimer la saisie ' + name()"
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
  /** RG-01-10 : « Modifier » et « Supprimer » ne sont rendus que sur son propre journal. */
  readonly editable = input(true);
  readonly edit = output<TimeEntryResponse>();
  readonly remove = output<TimeEntryResponse>();

  protected readonly period = computed(() => periodLabel(this.entry()));
  protected readonly name = computed(
    () => `${this.entry().activityType.label} ${datesLabel(this.entry())}`,
  );
}
