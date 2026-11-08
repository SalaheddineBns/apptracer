import { HttpErrorResponse } from '@angular/common/http';
import { translateNow } from '../i18n/i18n.service';

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

/** Messages génériques (RG-08-05) : dans la langue en vigueur au moment de l'erreur. */
const networkErrorMessage = (): string => translateNow('errors.network');
const defaultErrorMessage = (): string => translateNow('errors.generic');

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
    return { status: 0, detail: defaultErrorMessage(), code: null, type: null, fieldErrors: [] };
  }
  if (error.status === 0) {
    return { status: 0, detail: networkErrorMessage(), code: null, type: null, fieldErrors: [] };
  }
  const body: unknown = error.error;
  if (!isRecord(body)) {
    return { status: error.status, detail: defaultErrorMessage(), code: null, type: null, fieldErrors: [] };
  }
  const detail = body['detail'];
  const code = body['code'];
  const type = body['type'];
  return {
    status: error.status,
    detail: typeof detail === 'string' && detail.length > 0 ? detail : defaultErrorMessage(),
    code: typeof code === 'string' ? code : null,
    type: typeof type === 'string' ? type : null,
    fieldErrors: readFieldErrors(body['errors']),
  };
}
