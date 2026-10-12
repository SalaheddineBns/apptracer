import { signal } from '@angular/core';
import { defer, EMPTY, finalize, Observable } from 'rxjs';

/**
 * Identifiants dont une action (désactiver, réactiver) attend la réponse de l'API.
 * `guard` n'envoie qu'une requête à la fois par identifiant : un appel pendant l'attente est ignoré, quel que soit
 * le nombre d'appuis. L'écran s'en sert aussi pour désactiver le bouton (`has`).
 */
export class PendingIds {
  private readonly current = signal<ReadonlySet<number>>(new Set());

  has(id: number): boolean {
    return this.current().has(id);
  }

  /** Paresseux : l'identifiant n'est réservé qu'à la souscription, et libéré à la réponse, l'erreur ou l'abandon. */
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
