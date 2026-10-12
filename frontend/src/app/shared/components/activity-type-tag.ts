import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import { TagColor } from '../../core/api/generated';
import { iconForCode, severityForColor } from '../activity-type.ui';

/**
 * Tag d'un type d'activité (RG-02-04). Sans dépendance à un store : reçoit les données du type en entrée.
 * Le libellé est toujours affiché, la couleur ne porte jamais l'information seule.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-activity-type-tag',
  imports: [Tag],
  template: `<p-tag
    [value]="label()"
    [severity]="severity()"
    [icon]="icon()"
    data-cy="activity-type-tag"
  />`,
})
export class ActivityTypeTag {
  readonly label = input.required<string>();
  readonly color = input.required<TagColor>();
  readonly code = input.required<string>();

  protected readonly severity = computed(() => severityForColor(this.color()));
  protected readonly icon = computed(() => iconForCode(this.code()));
}
