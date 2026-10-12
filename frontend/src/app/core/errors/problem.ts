import { HttpErrorResponse } from '@angular/common/http';

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

/** Lecture normalisée d'un ProblemDetail renvoyé par l'API (champs `code` et `errors` à plat dans le corps). */
export interface Problem {
  readonly status: number;
  readonly detail: string;
  readonly code: string | null;
  /** URI du type de problème (ex. `.../concurrent-modification`). */
  readonly type: string | null;
  readonly fieldErrors: readonly FieldError[];
}

export const NETWORK_ERROR_MESSAGE = 'Le serveur est injoignable. Vérifiez votre connexion et réessayez.';
export const DEFAULT_ERROR_MESSAGE = 'Une erreur est survenue. Veuillez réessayer.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.flatMap((item: unknown) =>
    isRecord(item) && typeof item['field'] === 'string' && typeof item['message'] === 'string'
      ? [{ field: item['field'], message: item['message'] }]
      : [],
  );
}

export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, detail: DEFAULT_ERROR_MESSAGE, code: null, type: null, fieldErrors: [] };
  }
  if (error.status === 0) {
    return { status: 0, detail: NETWORK_ERROR_MESSAGE, code: null, type: null, fieldErrors: [] };
  }
  const body: unknown = error.error;
  if (!isRecord(body)) {
    return { status: error.status, detail: DEFAULT_ERROR_MESSAGE, code: null, type: null, fieldErrors: [] };
  }
  const detail = body['detail'];
  const code = body['code'];
  const type = body['type'];
  return {
    status: error.status,
    detail: typeof detail === 'string' && detail.length > 0 ? detail : DEFAULT_ERROR_MESSAGE,
    code: typeof code === 'string' ? code : null,
    type: typeof type === 'string' ? type : null,
    fieldErrors: readFieldErrors(body['errors']),
  };
}
