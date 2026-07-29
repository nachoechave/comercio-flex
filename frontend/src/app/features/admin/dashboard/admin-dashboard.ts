import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink],
  template: `
    <section class="placeholder">
      <p>Panel administrativo</p>
      <h1>Base preparada</h1>
      <p>
        La autenticación y la gestión de catálogo están disponibles. Ahora también podés
        consultar y ajustar existencias por variante.
      </p>
      <a routerLink="inventario">Ir al inventario</a>
    </section>
  `,
  styles: `
    .placeholder {
      max-width: 52rem;
      margin: 4rem auto;
      padding: 1rem;
    }

    h1 {
      font-size: clamp(2rem, 8vw, 4rem);
      letter-spacing: -0.04em;
    }
  `,
})
export class AdminDashboard {}
