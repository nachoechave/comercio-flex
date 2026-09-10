import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdminDashboard } from '../admin/dashboard/admin-dashboard';
@Component({ selector: 'app-membership-admin-home', imports: [AdminDashboard, RouterLink], template: `
 @if (radio) { <h1>Administración de la radio</h1><p>Gestioná los planes y consultá las cuotas de tus socios.</p><a [routerLink]="['socios']">Ver socios</a> } @else { <app-admin-dashboard /> }
` })
export class MembershipAdminHome { readonly radio = inject(ActivatedRoute).parent?.snapshot.data['tenantSettings']?.tenantType === 'RADIO'; }
