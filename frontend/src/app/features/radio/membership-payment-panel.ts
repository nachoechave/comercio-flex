import { Component, inject, signal, input, output } from '@angular/core';
import { finalize } from 'rxjs';
import { RadioContext } from './radio-context';
import { MembershipPaymentApi, MembershipCheckoutNavigation, CheckoutState } from './membership-payment-api.service';
@Component({selector:'app-membership-payment-panel',styleUrl:'./membership.scss',template:`
 <section aria-label="Pago de cuota">
 @if(error()){<p role="alert">{{error()}}</p>}
 @if(paid() || state()?.status==='APPROVED'){<p class="status">PAGADO · Cuota pagada</p>}
 @else if(state(); as s){
  @if(s.message){<p role="status">{{s.message}}</p>}
  @if(!s.available && !cancelled()){<p>El pago online no está disponible. Consultá a la radio.</p>}
  @if(s.available && !cancelled()){
   <button (click)="pay()" [disabled]="busy()">{{busy() ? 'Procesando…' : s.status==='REJECTED' || s.status==='CANCELLED' ? 'Intentar nuevamente' : s.status==='UNKNOWN' || s.status==='CREATING' ? 'Verificar operación' : 'Pagar con Mercado Pago'}}</button>
  }
 }
 <button class="secondary" (click)="refresh()" [disabled]="busy()">Actualizar estado</button>
 </section>`})
export class MembershipPaymentPanel {
 private readonly api=inject(MembershipPaymentApi);private readonly context=inject(RadioContext);private readonly navigation=inject(MembershipCheckoutNavigation);
 readonly paid=input(false);readonly cancelled=input(false);readonly updated=output<void>();readonly state=signal<CheckoutState|null>(null);readonly busy=signal(false);readonly error=signal('');private lastRefresh=0;
 constructor(){this.load();}
 refresh(){if(this.busy()||Date.now()-this.lastRefresh<10000)return;this.load();this.updated.emit();}
 private load(){this.lastRefresh=Date.now();this.busy.set(true);this.api.state(this.context.slug()!).pipe(finalize(()=>this.busy.set(false))).subscribe({next:s=>this.state.set(s),error:()=>this.error.set('No pudimos consultar el estado del pago.')});}
 pay(){if(this.busy()||this.paid()||this.cancelled()||!this.state()?.available)return;this.busy.set(true);this.error.set('');this.api.checkout(this.context.slug()!).pipe(finalize(()=>this.busy.set(false))).subscribe({next:s=>{this.state.set(s);if(s.checkoutUrl){try{this.navigation.navigate(s.checkoutUrl);}catch{this.error.set('No pudimos abrir el checkout. Consultá a la radio.');}}},error:e=>this.error.set(e.error?.detail||'No pudimos iniciar el pago. Consultá el estado antes de volver a intentar.')});}
}
