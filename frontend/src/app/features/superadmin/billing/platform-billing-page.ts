import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface BillingRow {
  companyId: string;
  companyName: string;
  slug: string;
  companyStatus: string;
  status: 'PENDING' | 'PAID' | 'BONIFIED' | 'OVERDUE';
  amount: number | null;
  paymentMethod: string | null;
  paidAt: string | null;
  notes: string | null;
}
interface BillingMonth {
  year: number;
  month: number;
  total: number;
  paid: number;
  bonified: number;
  pending: number;
  overdue: number;
  collected: number;
  rows: BillingRow[];
}

@Component({
  selector: 'app-platform-billing-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="page">
      <header>
        <div><p class="eyebrow">FINANZAS</p><h1>Abonos mensuales</h1><p>Controlá qué comercios pagaron el mes.</p></div>
        <div class="period">
          <select [(ngModel)]="month" (ngModelChange)="load()">
            @for (name of monthNames; track $index) { <option [ngValue]="$index + 1">{{ name }}</option> }
          </select>
          <input type="number" min="2020" max="2100" [(ngModel)]="year" (change)="load()" />
        </div>
      </header>

      @if (error()) { <p class="notice error">{{ error() }}</p> }
      @if (data(); as d) {
        <div class="summary">
          <article><span>Cobrados</span><strong>{{ d.paid }}</strong></article>
          <article><span>Pendientes</span><strong>{{ d.pending }}</strong></article>
          <article><span>Vencidos</span><strong>{{ d.overdue }}</strong></article>
          <article><span>Bonificados</span><strong>{{ d.bonified }}</strong></article>
          <article><span>Total cobrado</span><strong>{{ d.collected | currency:'ARS':'symbol-narrow':'1.0-0' }}</strong></article>
        </div>

        <div class="table-wrap">
          <table>
            <thead><tr><th>Comercio</th><th>Estado</th><th>Monto</th><th>Fecha</th><th>Método</th><th>Notas</th><th></th></tr></thead>
            <tbody>
              @for (row of d.rows; track row.companyId) {
                <tr>
                  <td><strong>{{ row.companyName }}</strong><small>{{ row.slug }}</small></td>
                  <td><span class="badge" [attr.data-status]="row.status">{{ label(row.status) }}</span></td>
                  <td><input type="number" min="0" step="100" [(ngModel)]="draft(row).amount" /></td>
                  <td><input type="date" [(ngModel)]="draft(row).paidDate" /></td>
                  <td>
                    <select [(ngModel)]="draft(row).paymentMethod">
                      <option value="">—</option><option>Transferencia</option><option>Efectivo</option><option>Mercado Pago</option><option>Otro</option>
                    </select>
                  </td>
                  <td><input maxlength="500" [(ngModel)]="draft(row).notes" placeholder="Opcional" /></td>
                  <td class="actions">
                    <button (click)="save(row, 'PAID')" [disabled]="saving()">Marcar pagado</button>
                    <button class="secondary" (click)="save(row, 'PENDING')" [disabled]="saving()">Pendiente</button>
                    <button class="secondary" (click)="save(row, 'BONIFIED')" [disabled]="saving()">Bonificar</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [`
    .page{display:grid;gap:1.25rem}.eyebrow{font-size:.75rem;font-weight:800;letter-spacing:.12em;margin:0 0 .25rem}
    header{display:flex;justify-content:space-between;gap:1rem;align-items:end}h1{margin:.1rem 0}.period{display:flex;gap:.5rem}
    select,input,button{min-height:2.5rem;border:1px solid #cbd5e1;border-radius:.65rem;padding:.5rem .65rem;font:inherit;background:#fff}
    button{background:#111827;color:#fff;font-weight:700;cursor:pointer}.secondary{background:#fff;color:#111827}
    .summary{display:grid;grid-template-columns:repeat(5,minmax(8rem,1fr));gap:.75rem}.summary article{padding:1rem;border:1px solid #e2e8f0;border-radius:.8rem;background:#fff;display:grid;gap:.3rem}.summary strong{font-size:1.35rem}
    .table-wrap{overflow:auto;border:1px solid #e2e8f0;border-radius:.8rem;background:#fff}table{width:100%;border-collapse:collapse;min-width:1100px}th,td{padding:.75rem;border-bottom:1px solid #eef2f7;text-align:left;vertical-align:middle}td:first-child{display:grid}td small{color:#64748b}.actions{display:flex;gap:.4rem;white-space:nowrap}
    .badge{display:inline-block;padding:.25rem .5rem;border-radius:999px;background:#f1f5f9}.badge[data-status="PAID"]{background:#dcfce7;color:#166534}.badge[data-status="OVERDUE"]{background:#fee2e2;color:#991b1b}.badge[data-status="BONIFIED"]{background:#e0e7ff;color:#3730a3}
    .notice{padding:.75rem;border-radius:.6rem}.error{background:#fef2f2;color:#991b1b}@media(max-width:800px){header{align-items:start;flex-direction:column}.summary{grid-template-columns:repeat(2,1fr)}}
  `]
})
export class PlatformBillingPage {
  private readonly http = inject(HttpClient);
  readonly monthNames = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  readonly data = signal<BillingMonth | null>(null);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  private readonly drafts = new Map<string,{amount:number;paidDate:string;paymentMethod:string;notes:string}>();

  constructor(){ this.load(); }

  load(): void {
    this.error.set(null);
    this.http.get<BillingMonth>('/api/v1/superadmin/billing',{params:{year:this.year,month:this.month}}).subscribe({
      next:d=>{this.data.set(d);this.drafts.clear();},
      error:()=>this.error.set('No pudimos cargar los abonos.')
    });
  }

  draft(row: BillingRow) {
    if (!this.drafts.has(row.companyId)) {
      this.drafts.set(row.companyId, {
        amount: row.amount ?? 50000,
        paidDate: row.paidAt ? row.paidAt.slice(0,10) : new Date().toISOString().slice(0,10),
        paymentMethod: row.paymentMethod ?? 'Transferencia',
        notes: row.notes ?? ''
      });
    }
    return this.drafts.get(row.companyId)!;
  }

  save(row: BillingRow, status: 'PAID'|'PENDING'|'BONIFIED'): void {
    const d=this.draft(row); this.saving.set(true); this.error.set(null);
    const body={year:this.year,month:this.month,status,amount:status==='PAID'?d.amount:null,paymentMethod:status==='PAID'?d.paymentMethod:null,paidAt:status==='PAID'&&d.paidDate?new Date(d.paidDate+'T12:00:00Z').toISOString():null,notes:d.notes||null};
    this.http.put('/api/v1/superadmin/billing/'+encodeURIComponent(row.companyId),body).subscribe({
      next:()=>{this.saving.set(false);this.load();},
      error:(e)=>{this.saving.set(false);this.error.set(e?.error?.detail||'No pudimos guardar el pago.');}
    });
  }

  label(status:string):string { return ({PAID:'Pagado',PENDING:'Pendiente',OVERDUE:'Vencido',BONIFIED:'Bonificado'} as Record<string,string>)[status]??status; }
}
