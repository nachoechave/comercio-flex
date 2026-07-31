import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { CsrfService } from '../../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { PaymentAuthorizationNavigationService } from '../payment-authorization-navigation.service';
import { PaymentConnectionApiService } from '../payment-connection-api.service';
import { paymentConnectionErrorMessage } from '../payment-connection-errors';
import { PaymentConnection } from '../payment-connection.models';

type OAuthResult = 'connected' | 'cancelled' | 'failed';

@Component({
  selector: 'app-payment-connection-page',
  imports: [DatePipe],
  templateUrl: './payment-connection-page.html',
  styleUrl: './payment-connection-page.scss',
})
export class PaymentConnectionPage {
  private readonly api = inject(PaymentConnectionApiService);
  private readonly csrf = inject(CsrfService);
  private readonly navigation = inject(PaymentAuthorizationNavigationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: '',
  });
  readonly connection = signal<PaymentConnection | null>(null);
  readonly loading = signal(true);
  readonly startingAuthorization = signal(false);
  readonly disconnecting = signal(false);
  readonly confirmingDisconnect = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly busy = computed(() => this.startingAuthorization() || this.disconnecting());
  readonly accountLabel = computed(
    () => this.connection()?.connectedAccountLabel?.trim() || 'Cuenta de Mercado Pago verificada',
  );
  readonly environmentLabel = computed(() =>
    this.connection()?.environment === 'PRODUCTION' ? 'Producción' : 'Prueba',
  );

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      if (!storeSlug) return;
      this.connection.set(null);
      this.errorMessage.set(null);
      this.noticeMessage.set(null);
      this.confirmingDisconnect.set(false);
      this.loading.set(true);
      const subscription = this.api.get(storeSlug).subscribe({
        next: (connection) => {
          this.connection.set(connection);
          this.loading.set(false);
          this.applyOAuthResult(connection);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            paymentConnectionErrorMessage(error, 'No pudimos cargar la conexión de pagos.'),
          );
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  startAuthorization(): void {
    if (this.busy()) return;
    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.errorMessage.set('No pudimos identificar el comercio.');
      return;
    }
    this.startingAuthorization.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.startAuthorization(storeSlug)),
        finalize(() => this.startingAuthorization.set(false)),
      )
      .subscribe({
        next: (authorization) => {
          try {
            this.navigation.navigate(authorization.authorizationUrl);
          } catch {
            this.errorMessage.set(
              'Mercado Pago devolvió un destino de autorización inválido. No hicimos cambios.',
            );
          }
        },
        error: (error: unknown) => this.errorMessage.set(paymentConnectionErrorMessage(error)),
      });
  }

  askToDisconnect(): void {
    if (!this.busy()) this.confirmingDisconnect.set(true);
  }

  cancelDisconnect(): void {
    this.confirmingDisconnect.set(false);
  }

  disconnect(): void {
    if (this.busy()) return;
    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.errorMessage.set('No pudimos identificar el comercio.');
      return;
    }
    this.disconnecting.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.disconnect(storeSlug)),
        switchMap(() => this.api.get(storeSlug)),
        finalize(() => this.disconnecting.set(false)),
      )
      .subscribe({
        next: (connection) => {
          this.connection.set(connection);
          this.confirmingDisconnect.set(false);
          this.noticeMessage.set('La cuenta de Mercado Pago fue desconectada de esta tienda.');
        },
        error: (error: unknown) => this.errorMessage.set(paymentConnectionErrorMessage(error)),
      });
  }

  private applyOAuthResult(connection: PaymentConnection): void {
    const result = this.oauthResult();
    if (result === 'connected') {
      this.noticeMessage.set(
        connection.status === 'CONNECTED'
          ? 'La cuenta de Mercado Pago quedó conectada correctamente.'
          : 'Volvimos de Mercado Pago, pero no pudimos confirmar la conexión.',
      );
    } else if (result === 'cancelled') {
      this.noticeMessage.set('No autorizaste la conexión. No hicimos cambios.');
    } else if (result === 'failed') {
      this.errorMessage.set(
        'No pudimos completar la autorización. La conexión anterior no cambió.',
      );
    }
    if (result) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { oauth: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  private oauthResult(): OAuthResult | null {
    const result = this.route.snapshot.queryParamMap.get('oauth');
    return result === 'connected' || result === 'cancelled' || result === 'failed' ? result : null;
  }
}
