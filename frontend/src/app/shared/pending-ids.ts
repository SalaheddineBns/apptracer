import { signal } from '@angular/core';
import { defer, EMPTY, finalize, Observable } from 'rxjs';

/**
 * Identifiers whose action (deactivate, reactivate) is waiting for the API's response.
 * `guard` sends only one request at a time per identifier: a call while waiting is ignored, however many
 * times the button is pressed. The screen also uses it to disable the button (`has`).
 */
export class PendingIds {
  private readonly current = signal<ReadonlySet<number>>(new Set());

  has(id: number): boolean {
    return this.current().has(id);
  }

  /** Lazy: the identifier is reserved only on subscription, and released on response, error or cancellation. */
  guard<T>(id: number, call: () => Observable<T>): Observable<T> {
    return defer(() => {
      if (this.has(id)) {
        return EMPTY;
      }
      this.current.update((ids) => new Set(ids).add(id));
      return call().pipe(
        finalize(() =>
          this.current.update((ids) => {
            const next = new Set(ids);
            next.delete(id);
            return next;
          }),
        ),
      );
    });
  }
}
