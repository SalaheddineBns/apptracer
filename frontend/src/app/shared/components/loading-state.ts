import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';

/** État de chargement : lignes `p-skeleton` qui reproduisent la forme du contenu. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonModule],
  selector: 'app-loading-state',
  template: `
    <div class="flex flex-col gap-2" data-cy="etat-chargement" aria-busy="true" aria-label="Chargement en cours">
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
