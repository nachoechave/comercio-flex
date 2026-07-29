import { HttpErrorResponse } from '@angular/common/http';

export interface InventoryProblem {
  detail?: string;
}

export function inventoryErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  const problem =
    typeof error.error === 'object' ? (error.error as InventoryProblem) : null;
  if (error.status === 403) return 'No tenés permiso para consultar o ajustar este inventario.';
  if (error.status === 404) return 'La variante o el comercio ya no están disponibles.';
  if (error.status === 409) {
    return problem?.detail ?? 'La existencia cambió. Verificá el inventario antes de intentar nuevamente.';
  }
  return problem?.detail ?? fallback;
}
