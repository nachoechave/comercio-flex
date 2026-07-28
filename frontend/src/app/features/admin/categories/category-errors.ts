import { HttpErrorResponse } from '@angular/common/http';

import { ApiProblem } from './category.models';

export function apiProblem(error: unknown): ApiProblem | null {
  if (!(error instanceof HttpErrorResponse) || typeof error.error !== 'object') {
    return null;
  }
  return error.error as ApiProblem;
}

export function categoryErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  const problem = apiProblem(error);
  if (error.status === 403) {
    return 'No tenés permiso para realizar esta acción.';
  }
  if (error.status === 404) {
    return 'La categoría o el comercio ya no están disponibles.';
  }
  if (error.status === 409) {
    return problem?.detail ?? 'Ya existe una categoría con ese nombre.';
  }
  return problem?.detail ?? fallback;
}

export function fieldProblem(error: unknown, field: string): string | null {
  const problem = apiProblem(error);
  if (!problem) {
    return null;
  }

  if (Array.isArray(problem.fieldErrors)) {
    return problem.fieldErrors.find((item) => item.field === field)?.message ?? null;
  }

  const candidates = problem.fieldErrors ?? problem.errors;
  const value = candidates?.[field];
  return Array.isArray(value) ? (value[0] ?? null) : (value ?? null);
}
