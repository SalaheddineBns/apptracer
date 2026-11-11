import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';
import { I18n } from '../i18n/i18n.service';
import { toProblem } from './problem';

/**
 * Set to `true` in the `HttpContext` of a request whose caller displays the error itself
 * (e.g. a message under a field, the generic sign-in message).
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/**
 * Shows the ProblemDetail `detail` in the p-toast, then passes the error on to the caller.
 * - 401: left to the JWT interceptor (T-03-13), which must be declared AFTER this one in
 *   `withInterceptors([...])` so that it renews / replays before the error comes back here.
 * - responses with `errors[]`: meant for forms (shown under the fields), no toast.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const messages = inject(MessageService);
  const i18n = inject(I18n);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status !== 401 && !req.context.get(SKIP_ERROR_TOAST)) {
        const problem = toProblem(error);
        if (problem.fieldErrors.length === 0) {
          messages.add({ severity: 'error', summary: i18n.t('common.error'), detail: problem.detail, life: 5000 });
        }
      }
      return throwError(() => error);
    }),
  );
};
