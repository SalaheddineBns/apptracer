import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TPipe } from '../../../core/i18n/t.pipe';
import { EmptyState } from '../../../shared/components/empty-state';
import { ErrorState } from '../../../shared/components/error-state';
import { LoadingState } from '../../../shared/components/loading-state';
import { MyProjectsStore } from '../data/my-projects.store';

/** SFD-10 E1 "My projects": projects of my teams, then shared projects, read-only (RG-10-11). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TPipe, EmptyState, ErrorState, LoadingState],
  providers: [MyProjectsStore],
  selector: 'app-my-projects',
  templateUrl: './my-projects.html',
})
export class MyProjectsPage {
  protected readonly store = inject(MyProjectsStore);

  /** RG-10-11: no active project anywhere, neither in a team nor shared. */
  protected readonly empty = computed(() => {
    const data = this.store.data();
    return data !== null && data.sharedProjects.length === 0 && data.teams.every((group) => group.projects.length === 0);
  });

  constructor() {
    this.store.load();
  }
}
