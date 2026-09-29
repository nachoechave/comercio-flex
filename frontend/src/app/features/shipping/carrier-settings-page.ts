import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../core/routing/inherited-route-param';
import {
  CarrierConnectionTestRequest,
  CarrierConnectionTestResult,
  CarrierDocumentType,
  CarrierSettings,
  CarrierSettingsSave,
} from './shipping.models';

@Component({
  selector: 'app-carrier-settings-page',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="carrier-page">
      <header class="hero">
        <div>
          <span class="eyebrow">INTEGRACIÓN DE TRANSPORTE</span>
          <h1>Andreani</h1>
          <p>
            Activá cotización en tiempo real, generación de envíos, etiqueta PDF y actualización de
            seguimiento desde Comercio Flex.
          </p>
        </div>
        <span class="status" [class.active]="draft?.enabled">
          {{ draft?.enabled ? 'Activo' : 'Desactivado' }}
        </span>
      </header>

      @if (error()) {
        <div class="notice error" role="alert">{{ error() }}</div>
      }
      @if (notice()) {
        <div class="notice success" role="status">{{ notice() }}</div>
      }

      @if (draft; as s) {
        <form #form="ngForm" (ngSubmit)="form.valid && save()">
          <section class="panel activation">
            <div>
              <h2>Estado de la integración</h2>
              <p>
                Cuando está activa, el checkout consulta Andreani además de tus métodos de envío
                propios. Si Andreani no responde y existe una alternativa manual, esa alternativa
                sigue disponible.
              </p>
            </div>
            <label class="toggle">
              <input type="checkbox" name="enabled" [(ngModel)]="s.enabled" />
              <span>{{ s.enabled ? 'Integración activa' : 'Integración inactiva' }}</span>
            </label>
          </section>

          <section class="panel">
            <div class="section-heading">
              <div>
                <span class="kicker">CUENTA</span>
                <h2>Credenciales y contrato</h2>
              </div>
              <span class="credentials" [class.ok]="credentialsConfigured()">
                {{ credentialsConfigured() ? 'Credenciales guardadas' : 'Sin credenciales' }}
              </span>
            </div>
            <div class="grid two">
              <label>
                <span>Ambiente</span>
                <select name="environment" [(ngModel)]="s.environment" (ngModelChange)="invalidateTest()">
                  <option value="SANDBOX">QA / Sandbox</option>
                  <option value="PRODUCTION">Producción</option>
                </select>
              </label>
              <label>
                <span>Código de cliente</span>
                <input name="clientCode" maxlength="80" [(ngModel)]="s.clientCode" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Código de contrato</span>
                <input name="contractCode" maxlength="80" [(ngModel)]="s.contractCode" (ngModelChange)="invalidateTest()" />
              </label>
              <div></div>
              <label>
                <span>Usuario Andreani</span>
                <input
                  name="username"
                  autocomplete="off"
                  maxlength="300"
                  [(ngModel)]="username"
                  (ngModelChange)="invalidateTest()"
                  placeholder="Dejalo vacío para conservar el actual"
                />
              </label>
              <label>
                <span>Contraseña Andreani</span>
                <input
                  name="password"
                  type="password"
                  autocomplete="new-password"
                  maxlength="300"
                  [(ngModel)]="password"
                  (ngModelChange)="invalidateTest()"
                  placeholder="Dejala vacía para conservar la actual"
                />
              </label>
            </div>
            @if (credentialsConfigured()) {
              <label class="clear">
                <input type="checkbox" name="clearCredentials" [(ngModel)]="clearCredentials" (ngModelChange)="invalidateTest()" />
                Eliminar las credenciales guardadas al guardar
              </label>
            }

            <div class="connection-test">
              <div>
                <span class="kicker">DIAGNÓSTICO</span>
                <strong>Probar conexión antes de activar</strong>
                <p>
                  Verifica autenticación y una cotización real contra el ambiente elegido. No guarda
                  cambios, no activa Andreani y no genera ningún envío.
                </p>
              </div>
              <label class="test-postal">
                <span>CP destino de prueba</span>
                <input
                  name="testPostalCode"
                  maxlength="20"
                  [(ngModel)]="testPostalCode"
                  (ngModelChange)="invalidateTest()"
                  placeholder="Ej. 1900"
                />
              </label>
              <button
                class="test-button"
                type="button"
                (click)="testConnection()"
                [disabled]="testing() || !canTest()"
              >
                {{ testing() ? 'Probando…' : 'Probar conexión' }}
              </button>
            </div>

            @if (testResult(); as result) {
              <section class="test-result" [class.ok]="result.success" [class.partial]="result.authenticationOk && !result.quoteOk" aria-live="polite">
                <header>
                  <div>
                    <strong>{{ result.success ? 'Conexión lista para piloto' : result.authenticationOk ? 'Conexión parcial' : 'No se pudo conectar' }}</strong>
                    <span>{{ result.environment === 'PRODUCTION' ? 'Producción' : 'QA / Sandbox' }} · CP {{ result.destinationPostalCode }}</span>
                  </div>
                  <span class="result-badge">{{ result.success ? 'OK' : 'REVISAR' }}</span>
                </header>
                <ul>
                  @for (check of result.checks; track check.code) {
                    <li [class.ok]="check.success">
                      <span aria-hidden="true">{{ check.success ? '✓' : '!' }}</span>
                      <div>
                        <strong>{{ check.code === 'AUTH' ? 'Autenticación' : check.code === 'QUOTE' ? 'Cotización' : check.code }}</strong>
                        <small>{{ check.message }}</small>
                      </div>
                    </li>
                  }
                </ul>
                @if (result.quoteOk && result.providerCost) {
                  <p class="quote-result">
                    Tarifa de prueba Andreani: <strong>$ {{ result.providerCost }}</strong>
                    @if (result.serviceCode) { <span>· {{ result.serviceCode }}</span> }
                  </p>
                }
              </section>
            }
          </section>

          <section class="panel">
            <div class="section-heading">
              <div>
                <span class="kicker">ORIGEN</span>
                <h2>Datos del remitente</h2>
                <p>Se utilizan para crear la orden de envío y la etiqueta.</p>
              </div>
            </div>
            <div class="grid three">
              <label class="wide">
                <span>Nombre / Razón social</span>
                <input name="senderName" required maxlength="160" [(ngModel)]="s.origin.senderName" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Email</span>
                <input name="senderEmail" type="email" required maxlength="254" [(ngModel)]="s.origin.senderEmail" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Teléfono</span>
                <input name="senderPhone" required maxlength="40" [(ngModel)]="s.origin.senderPhone" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Documento</span>
                <select name="senderDocumentType" [(ngModel)]="s.origin.senderDocumentType" (ngModelChange)="invalidateTest()">
                  <option value="DNI">DNI</option>
                  <option value="CUIT">CUIT</option>
                  <option value="CUIL">CUIL</option>
                </select>
              </label>
              <label>
                <span>Número de documento</span>
                <input
                  name="senderDocumentNumber"
                  required
                  pattern="[0-9]{7,20}"
                  maxlength="20"
                  [(ngModel)]="s.origin.senderDocumentNumber"
                  (ngModelChange)="invalidateTest()"
                />
              </label>
              <label class="wide">
                <span>Calle</span>
                <input name="originStreet" required maxlength="160" [(ngModel)]="s.origin.street" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Número</span>
                <input name="originNumber" required maxlength="30" [(ngModel)]="s.origin.number" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Código postal</span>
                <input name="originPostal" required maxlength="20" [(ngModel)]="s.origin.postalCode" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Localidad</span>
                <input name="originCity" required maxlength="160" [(ngModel)]="s.origin.city" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Provincia</span>
                <input name="originProvince" required maxlength="160" [(ngModel)]="s.origin.province" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>País</span>
                <input name="originCountry" required maxlength="80" [(ngModel)]="s.origin.country" (ngModelChange)="invalidateTest()" />
              </label>
            </div>
          </section>

          <section class="panel">
            <div class="section-heading">
              <div>
                <span class="kicker">PAQUETE</span>
                <h2>Medidas predeterminadas</h2>
                <p>
                  Fase 2 usa estas medidas como paquete base. El peso se multiplica por la cantidad
                  total de unidades del carrito para la cotización.
                </p>
              </div>
            </div>
            <div class="grid four">
              <label>
                <span>Peso por unidad (g)</span>
                <input name="weight" type="number" required min="1" step="1" [(ngModel)]="s.defaultParcel.weightGrams" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Largo (cm)</span>
                <input name="length" type="number" required min="0.1" step="0.1" [(ngModel)]="s.defaultParcel.lengthCm" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Ancho (cm)</span>
                <input name="width" type="number" required min="0.1" step="0.1" [(ngModel)]="s.defaultParcel.widthCm" (ngModelChange)="invalidateTest()" />
              </label>
              <label>
                <span>Alto (cm)</span>
                <input name="height" type="number" required min="0.1" step="0.1" [(ngModel)]="s.defaultParcel.heightCm" (ngModelChange)="invalidateTest()" />
              </label>
            </div>
            <label class="sync-field">
              <span>Actualizar tracking cada</span>
              <div>
                <input
                  name="syncMinutes"
                  type="number"
                  min="5"
                  max="1440"
                  required
                  [(ngModel)]="s.trackingSyncMinutes"
                />
                <span>minutos</span>
              </div>
            </label>
          </section>

          <footer class="save-bar">
            <a [routerLink]="['/tiendas', slug(), 'admin', 'configuracion', 'envios']">← Envíos propios</a>
            <button type="submit" [disabled]="saving() || form.invalid">
              {{ saving() ? 'Guardando…' : 'Guardar Andreani' }}
            </button>
          </footer>
        </form>
      }
    </section>
  `,
  styles: [
    `
      :host { display: block; }
      .carrier-page { max-width: 1120px; margin: 0 auto; display: grid; gap: 22px; padding-bottom: 32px; }
      .hero, .panel, .save-bar { border: 1px solid #e3e8ef; border-radius: 18px; background: #fff; }
      .hero { display: flex; justify-content: space-between; gap: 24px; padding: 26px; background: linear-gradient(135deg,#fff,#f5f8fb); }
      .hero h1, h2 { margin: 4px 0 0; color: #172033; }
      .hero p, .panel p { color: #667085; line-height: 1.55; margin: 8px 0 0; }
      .eyebrow, .kicker { font-size: .72rem; font-weight: 800; letter-spacing: .12em; color: #5b6b7f; }
      .status, .credentials { height: fit-content; padding: 6px 10px; border-radius: 999px; background: #f2f4f7; color: #667085; font-weight: 750; font-size: .8rem; }
      .status.active, .credentials.ok { background: #e8f7ee; color: #18794e; }
      form { display: grid; gap: 18px; }
      .panel { padding: 22px; display: grid; gap: 18px; }
      .activation { grid-template-columns: minmax(0,1fr) auto; align-items: center; }
      .toggle, .clear { display: flex; align-items: center; gap: 8px; font-weight: 700; color: #344054; }
      .section-heading { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
      .grid { display: grid; gap: 14px; }
      .grid.two { grid-template-columns: repeat(2,minmax(0,1fr)); }
      .grid.three { grid-template-columns: repeat(3,minmax(0,1fr)); }
      .grid.four { grid-template-columns: repeat(4,minmax(0,1fr)); }
      .wide { grid-column: span 2; }
      label { display: grid; gap: 6px; color: #344054; font-size: .88rem; font-weight: 650; }
      input, select { width: 100%; box-sizing: border-box; border: 1px solid #cfd7e3; border-radius: 10px; padding: 11px 12px; font: inherit; background: #fff; }
      .sync-field { max-width: 300px; }
      .sync-field div { display: flex; align-items: center; gap: 10px; }
      .sync-field input { max-width: 120px; }
      .notice { padding: 12px 14px; border-radius: 12px; }
      .notice.error { background: #fff1f2; color: #a21c24; }
      .notice.success { background: #e8f7ee; color: #18794e; }
      .connection-test { display: grid; grid-template-columns: minmax(0,1fr) minmax(180px,220px) auto; gap: 14px; align-items: end; padding: 16px; border: 1px solid #d9e0ea; border-radius: 14px; background: #f8fafc; }
      .connection-test > div { align-self: center; }
      .connection-test strong { display: block; margin-top: 3px; color: #172033; }
      .connection-test p { margin-top: 5px; font-size: .86rem; }
      .test-button { min-height: 42px; border: 0; border-radius: 10px; padding: 10px 15px; background: #24364b; color: #fff; font: inherit; font-weight: 800; cursor: pointer; white-space: nowrap; }
      .test-button:disabled { opacity: .5; cursor: not-allowed; }
      .test-result { display: grid; gap: 12px; padding: 15px; border: 1px solid #efb6b9; border-radius: 14px; background: #fff7f7; }
      .test-result.ok { border-color: #a7dfbd; background: #f2fbf5; }
      .test-result.partial { border-color: #e8cf91; background: #fffaf0; }
      .test-result > header { display: flex; justify-content: space-between; gap: 14px; align-items: flex-start; }
      .test-result > header div { display: grid; gap: 2px; }
      .test-result > header strong { color: #172033; }
      .test-result > header span:not(.result-badge) { color: #667085; font-size: .8rem; }
      .result-badge { padding: 4px 8px; border-radius: 999px; background: #fce8e9; color: #9b252c; font-size: .72rem; font-weight: 850; }
      .test-result.ok .result-badge { background: #def5e7; color: #18794e; }
      .test-result.partial .result-badge { background: #fff0c7; color: #805600; }
      .test-result ul { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
      .test-result li { display: flex; gap: 10px; align-items: flex-start; }
      .test-result li > span { display: grid; width: 22px; height: 22px; place-items: center; flex: 0 0 22px; border-radius: 50%; background: #f6d9db; color: #9b252c; font-weight: 900; }
      .test-result li.ok > span { background: #dff4e7; color: #18794e; }
      .test-result li div { display: grid; gap: 2px; }
      .test-result li small { color: #667085; line-height: 1.4; }
      .quote-result { margin: 0 !important; padding-top: 10px; border-top: 1px solid rgb(23 32 51 / 10%); color: #344054 !important; }
      .save-bar { position: sticky; bottom: 12px; display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 14px 18px; box-shadow: 0 12px 28px rgb(16 24 40 / 10%); }
      .save-bar a { color: #475467; font-weight: 700; text-decoration: none; }
      .save-bar button { border: 0; border-radius: 10px; padding: 12px 18px; background: #172033; color: #fff; font-weight: 800; cursor: pointer; }
      .save-bar button:disabled { opacity: .55; cursor: not-allowed; }
      @media (max-width: 900px) { .connection-test { grid-template-columns: 1fr 1fr; } .connection-test > div { grid-column: 1 / -1; } }
      @media (max-width: 780px) {
        .hero, .activation, .section-heading, .save-bar { display: grid; grid-template-columns: 1fr; }
        .grid.two, .grid.three, .grid.four, .connection-test { grid-template-columns: 1fr; }
        .connection-test > div { grid-column: auto; }
        .wide { grid-column: auto; }
      }
    `,
  ],
})
export class CarrierSettingsPage {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  slug = toSignal(inheritedRouteParam(inject(ActivatedRoute), 'storeSlug'), { initialValue: '' });
  draft: (CarrierSettings & {
    origin: NonNullable<CarrierSettings['origin']>;
    defaultParcel: NonNullable<CarrierSettings['defaultParcel']>;
  }) | null = null;
  username = '';
  password = '';
  clearCredentials = false;
  testPostalCode = '';
  saving = signal(false);
  testing = signal(false);
  error = signal('');
  notice = signal('');
  testResult = signal<CarrierConnectionTestResult | null>(null);

  constructor() {
    effect((onCleanup) => {
      const slug = this.slug();
      if (!slug) return;
      const sub = this.http.get<CarrierSettings>(this.url(slug)).subscribe({
        next: (settings) => this.load(settings),
        error: () => this.error.set('No pudimos cargar la configuración de Andreani.'),
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  credentialsConfigured() {
    return !!this.draft?.credentialsConfigured && !this.clearCredentials;
  }

  canTest() {
    const s = this.draft;
    if (!s) return false;
    const hasEnteredCredentials = !!this.username.trim() && !!this.password;
    return !!(
      s.clientCode?.trim() &&
      s.contractCode?.trim() &&
      s.origin.senderName.trim() &&
      s.origin.senderEmail.trim() &&
      s.origin.senderPhone.trim() &&
      s.origin.senderDocumentNumber.trim() &&
      s.origin.street.trim() &&
      s.origin.number.trim() &&
      s.origin.postalCode.trim() &&
      s.origin.city.trim() &&
      s.origin.province.trim() &&
      s.origin.country.trim() &&
      s.defaultParcel.weightGrams > 0 &&
      s.defaultParcel.lengthCm > 0 &&
      s.defaultParcel.widthCm > 0 &&
      s.defaultParcel.heightCm > 0 &&
      this.testPostalCode.trim() &&
      (hasEnteredCredentials || this.credentialsConfigured())
    );
  }

  invalidateTest() {
    this.testResult.set(null);
  }

  private url(slug = this.slug()) {
    return '/api/v1/stores/' + encodeURIComponent(slug ?? '') + '/admin/shipping/carrier';
  }

  private load(settings: CarrierSettings) {
    this.draft = {
      ...settings,
      origin: settings.origin ?? {
        postalCode: '',
        street: '',
        number: '',
        city: '',
        province: '',
        country: 'Argentina',
        senderName: '',
        senderEmail: '',
        senderPhone: '',
        senderDocumentType: 'CUIT' as CarrierDocumentType,
        senderDocumentNumber: '',
      },
      defaultParcel: settings.defaultParcel ?? {
        weightGrams: 500,
        lengthCm: 20,
        widthCm: 20,
        heightCm: 10,
      },
    };
    this.username = '';
    this.password = '';
    this.clearCredentials = false;
    this.testPostalCode = settings.origin?.postalCode ?? this.testPostalCode;
    this.testResult.set(null);
    this.changeDetector.markForCheck();
  }

  testConnection() {
    if (!this.draft || this.testing() || !this.canTest()) return;
    const payload: CarrierConnectionTestRequest = {
      provider: 'ANDREANI',
      environment: this.draft.environment,
      clientCode: this.draft.clientCode!.trim(),
      contractCode: this.draft.contractCode!.trim(),
      username: this.username.trim() || null,
      password: this.password || null,
      origin: this.draft.origin,
      defaultParcel: this.draft.defaultParcel,
      destinationPostalCode: this.testPostalCode.trim(),
    };
    this.testing.set(true);
    this.error.set('');
    this.notice.set('');
    this.testResult.set(null);
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() =>
          this.http.post<CarrierConnectionTestResult>(this.url() + '/test', payload),
        ),
        finalize(() => this.testing.set(false)),
      )
      .subscribe({
        next: (result) => {
          this.testResult.set(result);
          this.changeDetector.markForCheck();
        },
        error: (response) =>
          this.error.set(
            response.error?.message ??
              'No pudimos ejecutar la prueba de conexión con Andreani.',
          ),
      });
  }

  save() {
    if (!this.draft || this.saving()) return;
    const payload: CarrierSettingsSave = {
      provider: 'ANDREANI',
      enabled: this.draft.enabled,
      environment: this.draft.environment,
      clientCode: this.draft.clientCode,
      contractCode: this.draft.contractCode,
      username: this.username.trim() || null,
      password: this.password || null,
      clearCredentials: this.clearCredentials,
      origin: this.draft.origin,
      defaultParcel: this.draft.defaultParcel,
      trackingSyncMinutes: this.draft.trackingSyncMinutes,
      version: this.draft.version,
    };
    this.saving.set(true);
    this.error.set('');
    this.notice.set('');
    this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.http.put<CarrierSettings>(this.url(), payload)),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: (settings) => {
          this.load(settings);
          this.notice.set('Configuración de Andreani guardada.');
        },
        error: (response) =>
          this.error.set(response.error?.message ?? 'No pudimos guardar la configuración de Andreani.'),
      });
  }
}
