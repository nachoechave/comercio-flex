import { HttpErrorResponse } from '@angular/common/http';

interface ProductProblem {
  detail?: string;
}

export function productErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  const problem =
    typeof error.error === 'object' ? (error.error as ProductProblem) : null;
  if (error.status === 403) return 'No tenés permiso para realizar esta acción.';
  if (error.status === 404) return 'El producto o el comercio ya no están disponibles.';
  if (error.status === 409) {
    return problem?.detail ?? 'Otra persona modificó estos datos. Recargá antes de continuar.';
  }
  return problem?.detail ?? fallback;
}
