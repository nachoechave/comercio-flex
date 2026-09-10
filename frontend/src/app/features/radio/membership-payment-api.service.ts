import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';
export interface CheckoutState { available: boolean; status: string; checkoutUrl: string | null; message: string; }
export interface MembershipPayment { publicId: string; periodPublicId: string; providerStatus: string; paidAt: string | null; appliedAt: string | null; reviewReason: string | null; providerPaymentId?: string; periodYear: number; periodMonth: number; planName: string; amount: number; currency: string; periodStatus: string; }
export interface PaymentAttempt { publicId: string; periodPublicId: string; status: string; lastErrorCode: string | null; preferenceId: string | null; externalReference: string; }
export interface MembershipPaymentSettings { enabled: boolean; credentialAvailable: boolean; available: boolean; }
@Injectable({ providedIn: 'root' })
export class MembershipPaymentApi {
 private readonly http=inject(HttpClient); private readonly csrf=inject(CsrfService);
 private base(slug: string) {return '/api/v1/stores/'+encodeURIComponent(slug);}
 state(slug: string) {return this.http.get<CheckoutState>(this.base(slug)+'/me/membership/current-period/payment');}
 checkout(slug: string) {return this.csrf.ensureToken().pipe(switchMap(()=>this.http.post<CheckoutState>(this.base(slug)+'/me/membership/current-period/checkout-pro',{})));}
 history(slug: string,offset=0) {return this.http.get<MembershipPayment[]>(this.base(slug)+'/me/membership/payments',{params:{offset}});}
 adminHistory(slug: string,id: string,offset=0) {return this.http.get<{payments:MembershipPayment[];attempts:PaymentAttempt[]}>(this.base(slug)+'/admin/paid-memberships/'+encodeURIComponent(id)+'/payments',{params:{offset}});}
 settings(slug: string) {return this.http.get<MembershipPaymentSettings>(this.base(slug)+'/admin/membership-payments/settings');}
 enable(slug: string,enabled: boolean) {return this.csrf.ensureToken().pipe(switchMap(()=>this.http.put<MembershipPaymentSettings>(this.base(slug)+'/admin/membership-payments/settings',{enabled})));}
}
@Injectable({providedIn:'root'})
export class MembershipCheckoutNavigation {
 navigate(url: string) {const u=new URL(url);if(u.protocol!=='https:'||u.username||u.password||u.port||!['mercadopago.com','mercadopago.com.ar'].some(h=>u.hostname===h||u.hostname.endsWith('.'+h)))throw new Error('Destino de pago inválido.');globalThis.location.assign(u.href);}
}
