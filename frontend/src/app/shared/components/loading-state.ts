import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';
import { TPipe } from '../../core/i18n/t.pipe';

/** Loading state: `p-skeleton` rows that reproduce the shape of the content. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonModule, TPipe],
  selector: 'app-loading-state',
  template: `
    <div class="flex flex-col gap-2" data-cy="etat-chargement" aria-busy="true" [attr.aria-label]="'common.loading' | t">
      @for (row of rowIndexes(); track row) {
        <p-skeleton height="2.5rem" />
      }
    </div>
  `,
})
export class LoadingState {
  readonly rows = input(5);
  protected readonly rowIndexes = computed(() => Array.from({ length: this.rows() }, (_, i) => i));
}
