import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';
import { toProblem } from './problem';

/**
 * À passer à `true` dans le `HttpContext` d'une requête dont l'appelant affiche lui-même l'erreur
 * (ex. message sous un champ, message générique de la connexion).
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/**
 * Affiche le `detail` du ProblemDetail dans le p-toast, puis relaie l'erreur à l'appelant.
 * - 401 : laissé à l'intercepteur JWT (T-03-13), qui doit être déclaré APRÈS celui-ci dans
 *   `withInterceptors([...])` afin de renouveler / rejouer avant que l'erreur ne remonte ici.
 * - réponses avec `errors[]` : destinées aux formulaires (affichage sous les champs), pas de toast.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const messages = inject(MessageService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status !== 401 && !req.context.get(SKIP_ERROR_TOAST)) {
        const problem = toProblem(error);
        if (problem.fieldErrors.length === 0) {
          messages.add({ severity: 'error', summary: 'Erreur', detail: problem.detail, life: 5000 });
        }
      }
      return throwError(() => error);
    }),
  );
};
