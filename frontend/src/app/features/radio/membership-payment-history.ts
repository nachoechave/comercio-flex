import { Component, inject, signal, input, effect, untracked } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { MembershipPaymentApi, MembershipPayment, PaymentAttempt } from './membership-payment-api.service';
@Component({selector:'app-membership-payment-history',imports:[CurrencyPipe,DatePipe],styleUrl:'./membership.scss',template:`
 <section><h2>Pagos</h2>@if(error()){<p role="alert">{{error()}}</p>}
 <div class="table-wrap"><table><thead><tr><th>Período</th><th>Plan</th><th>Importe</th><th>Cuota</th><th>Pago</th><th>Fecha de pago</th>@if(membershipId()){<th>ID Mercado Pago</th><th>Revisión</th>}</tr></thead><tbody>
 @for(p of payments();track p.publicId){<tr><td>{{p.periodMonth}}/{{p.periodYear}}</td><td>{{p.planName}}</td><td>{{p.amount|currency:p.currency}}</td><td>{{p.periodStatus==='ACCREDITED'?'PAGADO':'PENDIENTE'}}</td><td>{{p.providerStatus}}</td><td>{{p.paidAt ? (p.paidAt|date:'dd/MM/yyyy HH:mm') : '—'}}</td>@if(membershipId()){<td>{{p.providerPaymentId}}</td><td>{{p.reviewReason || '—'}}</td>}</tr>}
 @empty{<tr><td colspan="8">Todavía no hay pagos registrados.</td></tr>}
 </tbody></table></div>
 @if(membershipId()) {<h3>Intentos</h3><div class="table-wrap"><table><thead><tr><th>Estado</th><th>Referencia</th><th>Preferencia</th><th>Diagnóstico</th></tr></thead><tbody>@for(a of attempts();track a.publicId){<tr><td>{{a.status}}</td><td>{{a.externalReference}}</td><td>{{a.preferenceId || '—'}}</td><td>{{a.lastErrorCode || '—'}}</td></tr>}</tbody></table></div>}
 <div class="actions"><button (click)="page(-100)" [disabled]="busy()||offset()===0">Pagos anteriores</button><button (click)="page(100)" [disabled]="busy()||(payments().length<100 && attempts().length<100)">Más pagos</button></div></section>`})
export class MembershipPaymentHistory {
 private readonly api=inject(MembershipPaymentApi);readonly slug=input.required<string>();readonly membershipId=input<string>();readonly payments=signal<MembershipPayment[]>([]);readonly attempts=signal<PaymentAttempt[]>([]);readonly error=signal('');readonly busy=signal(false);readonly offset=signal(0);
 constructor(){effect(()=>{const slug=this.slug(),id=this.membershipId();untracked(()=>{this.offset.set(0);this.load(slug,id);});});}
 page(delta:number){this.offset.update(n=>Math.max(0,n+delta));this.load(this.slug(),this.membershipId());}
 private load(slug:string,id?:string){this.busy.set(true);this.error.set('');if(id)this.api.adminHistory(slug,id,this.offset()).pipe(finalize(()=>this.busy.set(false))).subscribe({next:r=>{this.payments.set(r.payments);this.attempts.set(r.attempts);},error:()=>this.error.set('No pudimos cargar los pagos.')});else this.api.history(slug,this.offset()).pipe(finalize(()=>this.busy.set(false))).subscribe({next:p=>this.payments.set(p),error:()=>this.error.set('No pudimos cargar los pagos.')});}
}
