import { HttpErrorResponse } from '@angular/common/http';

export function storefrontErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No pudimos conectarnos. Revisá tu conexión e intentá nuevamente.';
    }
    if (error.status >= 500) {
      return 'La tienda no está disponible en este momento. Intentá nuevamente en unos minutos.';
    }
    const detail = error.error?.detail;
    if (typeof detail === 'string' && detail.trim()) return detail;
  }
  return fallback;
}
