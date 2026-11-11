import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import { TagColor } from '../../core/api/generated';
import { iconForCode, severityForColor } from '../activity-type.ui';

/**
 * Tag of an activity type (RG-02-04). No dependency on a store: receives the type's data as input.
 * The label is always shown; colour never carries the information alone.
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
