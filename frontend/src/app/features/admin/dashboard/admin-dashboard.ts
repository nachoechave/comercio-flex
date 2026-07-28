import { Component } from '@angular/core';

@Component({
  selector: 'app-admin-dashboard',
  template: `
    <section class="placeholder">
      <p>Panel administrativo</p>
      <h1>Base preparada</h1>
      <p>
        La autenticación y la gestión de categorías ya están disponibles. Productos, inventario y
        métricas se incorporarán en las próximas historias aprobadas.
      </p>
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
