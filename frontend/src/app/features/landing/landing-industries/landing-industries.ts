import { Component } from '@angular/core';

@Component({
  selector: 'app-landing-industries',
  templateUrl: './landing-industries.html',
  styleUrl: './landing-industries.scss',
})
export class LandingIndustries {
  protected readonly industries = [
    'Indumentaria',
    'Calzado',
    'Accesorios',
    'Carnicerías',
    'Almacenes',
    'Y muchos más',
  ] as const;
}
