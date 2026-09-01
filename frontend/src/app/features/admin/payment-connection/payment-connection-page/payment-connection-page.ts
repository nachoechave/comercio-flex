import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, finalize, forkJoin, of, Subscription, switchMap } from 'rxjs';

import { CsrfService } from '../../../../core/auth/csrf.service';
import { CommerceDatePipe, formatCommerceDate } from '../../../../shared/pipes/commerce-date.pipe';
import { StatusPill } from '../../../../shared/ui/status-pill/status-pill';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { PaymentAuthorizationNavigationService } from '../payment-authorization-navigation.service';
import { PaymentConnectionApiService } from '../payment-connection-api.service';
import { paymentConnectionErrorMessage } from '../payment-connection-errors';
import {
  PaymentConnection,
  PaymentWebhookEventSummary,
  QrSetup,
} from '../payment-connection.models';

type OAuthResult = 'connected' | 'cancelled' | 'failed';

@Component({
  selector: 'app-payment-connection-page',
  imports: [CommerceDatePipe, ReactiveFormsModule, RouterLink, StatusPill],
  templateUrl: './payment-connection-page.html',
  styleUrl: './payment-connection-page.scss',
})
export class PaymentConnectionPage {
  private readonly api = inject(PaymentConnectionApiService);
  private readonly csrf = inject(CsrfService);
  private readonly navigation = inject(PaymentAuthorizationNavigationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly retryCancelButton = viewChild<ElementRef<HTMLButtonElement>>('retryCancelButton');
  private readonly operationsRefreshButton =
    viewChild<ElementRef<HTMLButtonElement>>('operationsRefreshButton');
  private readonly retryTriggerButtons =
    viewChildren<ElementRef<HTMLButtonElement>>('retryTriggerButton');
  private webhooksSubscription?: Subscription;
  private retrySubscription?: Subscription;
  private retryTriggerEventId: string | null = null;
  private qrSubscription?: Subscription;

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
  readonly webhookEvents = signal<PaymentWebhookEventSummary[]>([]);
  readonly webhooksLoading = signal(true);
  readonly webhooksErrorMessage = signal<string | null>(null);
  readonly webhooksNoticeMessage = signal<string | null>(null);
  readonly storeTimezone = signal('UTC');
  readonly retryingEventId = signal<string | null>(null);
  readonly confirmingRetryEventId = signal<string | null>(null);
  readonly qrSetup = signal<QrSetup | null>(null);
  readonly qrLoading = signal(false);
  readonly qrAction = signal<'DISCOVERY' | 'CONFIGURATION' | null>(null);
  readonly qrFormVisible = signal(false);
  readonly qrErrorMessage = signal<string | null>(null);
  readonly qrNoticeMessage = signal<string | null>(null);
  readonly qrForm = new FormGroup({
    storeName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    streetName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    streetNumber: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(40)] }),
    cityName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    stateName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
    latitude: new FormControl<number | null>(null, [Validators.required, Validators.min(-90), Validators.max(90)]),
    longitude: new FormControl<number | null>(null, [Validators.required, Validators.min(-180), Validators.max(180)]),
    reference: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
  });
  readonly busy = computed(
    () => this.startingAuthorization() || this.disconnecting() || this.qrAction() !== null,
  );
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
      this.webhooksNoticeMessage.set(null);
      this.storeTimezone.set('UTC');
      this.qrSetup.set(null);
      this.qrErrorMessage.set(null);
      this.qrNoticeMessage.set(null);
      this.qrFormVisible.set(false);
      this.confirmingDisconnect.set(false);
      this.loading.set(true);
      this.cancelOperationalRequests();
      this.loadFailedWebhooks(storeSlug);
      const subscription = this.api.get(storeSlug).subscribe({
        next: (connection) => {
          this.connection.set(connection);
          this.loading.set(false);
          this.applyOAuthResult(connection);
          if (connection.status === 'CONNECTED') this.loadQrSetup(storeSlug);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(
            paymentConnectionErrorMessage(error, 'No pudimos cargar la conexión de pagos.'),
          );
        },
      });
      onCleanup(() => {
        subscription.unsubscribe();
        this.cancelOperationalRequests();
      });
    });
  }

  discoverQrSetup(): void {
    this.runQrAction('DISCOVERY');
  }

  showQrConfiguration(): void {
    if (!this.busy()) this.qrFormVisible.set(true);
  }

  cancelQrConfiguration(): void {
    if (!this.busy()) this.qrFormVisible.set(false);
  }

  configureQr(): void {
    if (this.qrForm.invalid || this.busy()) {
      this.qrForm.markAllAsTouched();
      return;
    }
    this.runQrAction('CONFIGURATION');
  }

  askToRetry(event: PaymentWebhookEventSummary): void {
    if (!event.retryAllowed || this.retryingEventId()) return;
    this.webhooksNoticeMessage.set(null);
    this.retryTriggerEventId = event.eventId;
    this.confirmingRetryEventId.set(event.eventId);
    queueMicrotask(() => this.retryCancelButton()?.nativeElement.focus());
  }

  cancelRetry(): void {
    if (this.retryingEventId()) return;
    this.confirmingRetryEventId.set(null);
    setTimeout(() => this.restoreRetryFocus());
  }

  handleRetryDialogEscape(event: Event): void {
    event.stopPropagation();
    this.cancelRetry();
  }

  retryWebhook(event: PaymentWebhookEventSummary): void {
    if (
      !event.retryAllowed ||
      this.retryingEventId() ||
      this.confirmingRetryEventId() !== event.eventId
    ) {
      return;
    }
    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.webhooksErrorMessage.set('No pudimos identificar el comercio.');
      return;
    }
    this.retryingEventId.set(event.eventId);
    this.webhooksErrorMessage.set(null);
    this.retrySubscription = this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.retryWebhook(storeSlug, event.eventId)),
        switchMap(() => this.api.getFailedWebhooks(storeSlug)),
        finalize(() => this.retryingEventId.set(null)),
      )
      .subscribe({
        next: (events) => {
          this.webhookEvents.set(events);
          this.confirmingRetryEventId.set(null);
          this.webhooksNoticeMessage.set('El webhook fue programado para reintento.');
          queueMicrotask(() => this.operationsRefreshButton()?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.webhooksErrorMessage.set(
            paymentConnectionErrorMessage(error, 'No pudimos reintentar el webhook.'),
          );
        },
      });
  }

  reloadFailedWebhooks(): void {
    const storeSlug = this.storeSlug();
    if (storeSlug && !this.webhooksLoading()) {
      this.webhooksNoticeMessage.set(null);
      this.loadFailedWebhooks(storeSlug);
    }
  }

  formatEventDate(occurredAt: string): string {
    try {
      return formatCommerceDate(occurredAt, 'dateTime', this.storeTimezone());
    } catch {
      return occurredAt;
    }
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

  private loadFailedWebhooks(storeSlug: string): void {
    this.webhooksSubscription?.unsubscribe();
    this.webhooksLoading.set(true);
    this.webhooksErrorMessage.set(null);
    this.webhooksSubscription = forkJoin({
      events: this.api.getFailedWebhooks(storeSlug),
      settings: this.api.getStoreSettings(storeSlug).pipe(
        catchError(() => of({ timezone: 'UTC' })),
      ),
    })
      .pipe(finalize(() => this.webhooksLoading.set(false)))
      .subscribe({
        next: ({ events, settings }) => {
          this.storeTimezone.set(settings.timezone || 'UTC');
          this.webhookEvents.set(events);
        },
        error: (error: unknown) => {
          this.webhookEvents.set([]);
          this.webhooksErrorMessage.set(
            paymentConnectionErrorMessage(
              error,
              'No pudimos cargar los eventos que requieren atención.',
            ),
          );
        },
      });
  }

  private cancelOperationalRequests(): void {
    this.webhooksSubscription?.unsubscribe();
    this.retrySubscription?.unsubscribe();
    this.webhooksSubscription = undefined;
    this.retrySubscription = undefined;
    this.qrSubscription?.unsubscribe();
    this.qrSubscription = undefined;
    this.retryingEventId.set(null);
    this.confirmingRetryEventId.set(null);
  }

  private loadQrSetup(storeSlug: string): void {
    this.qrSubscription?.unsubscribe();
    this.qrLoading.set(true);
    this.qrErrorMessage.set(null);
    this.qrSubscription = this.api
      .getQrSetup(storeSlug)
      .pipe(finalize(() => this.qrLoading.set(false)))
      .subscribe({
        next: (setup) => this.qrSetup.set(setup),
        error: (error: unknown) => {
          this.qrErrorMessage.set(
            paymentConnectionErrorMessage(error, 'No pudimos cargar la configuración QR.'),
          );
        },
      });
  }

  private runQrAction(action: 'DISCOVERY' | 'CONFIGURATION'): void {
    const storeSlug = this.storeSlug();
    if (!storeSlug || this.busy()) return;
    this.qrAction.set(action);
    this.qrErrorMessage.set(null);
    this.qrNoticeMessage.set(null);
    const request$ = action === 'DISCOVERY'
      ? this.api.discoverQrSetup(storeSlug)
      : this.api.configureQr(storeSlug, {
          storeName: this.qrForm.controls.storeName.value.trim(),
          streetName: this.qrForm.controls.streetName.value.trim(),
          streetNumber: this.qrForm.controls.streetNumber.value.trim(),
          cityName: this.qrForm.controls.cityName.value.trim(),
          stateName: this.qrForm.controls.stateName.value.trim(),
          latitude: this.qrForm.controls.latitude.value!,
          longitude: this.qrForm.controls.longitude.value!,
          reference: this.qrForm.controls.reference.value.trim() || null,
        });
    this.qrSubscription = this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => request$),
        finalize(() => this.qrAction.set(null)),
      )
      .subscribe({
        next: (setup) => {
          this.qrSetup.set(setup);
          this.qrFormVisible.set(false);
          this.qrNoticeMessage.set(
            setup.qrOrdersReady
              ? 'La sucursal y la caja QR quedaron verificadas.'
              : 'Verificamos Mercado Pago. Todavía falta configurar la sucursal o la caja.',
          );
        },
        error: (error: unknown) => {
          this.qrErrorMessage.set(
            paymentConnectionErrorMessage(error, 'No pudimos configurar Código QR.'),
          );
        },
      });
  }

  private restoreRetryFocus(): void {
    const eventId = this.retryTriggerEventId;
    const trigger = this.retryTriggerButtons().find(
      (button) => button.nativeElement.dataset['eventId'] === eventId,
    );
    trigger?.nativeElement.focus();
    this.retryTriggerEventId = null;
  }

  private oauthResult(): OAuthResult | null {
    const result = this.route.snapshot.queryParamMap.get('oauth');
    return result === 'connected' || result === 'cancelled' || result === 'failed' ? result : null;
  }
}
