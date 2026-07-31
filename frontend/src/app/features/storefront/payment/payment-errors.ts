import { HttpErrorResponse } from '@angular/common/http';

export function paymentErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  if (error.status === 0) return 'No pudimos conectarnos. Revisá tu conexión e intentá nuevamente.';
  if (error.status === 409) {
    return 'El pedido cambió mientras intentábamos cobrarlo. Actualizá su estado antes de continuar.';
  }
  if (error.status >= 500) {
    return 'El servicio de pagos no está disponible en este momento. Tu pedido sigue guardado.';
  }
  const detail = error.error?.detail;
  return typeof detail === 'string' && detail.trim() ? detail : fallback;
}
