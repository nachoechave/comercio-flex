import { Component } from '@angular/core';

@Component({
  selector: 'app-landing-how-it-works',
  templateUrl: './landing-how-it-works.html',
  styleUrl: './landing-how-it-works.scss',
})
export class LandingHowItWorks {
  protected readonly steps = [
    {
      number: '01',
      title: 'Nos contás tu negocio',
      text: 'Entendemos tu catálogo y tu forma de operar.',
    },
    {
      number: '02',
      title: 'Configuramos tu solución',
      text: 'Preparamos la tienda y el panel junto a vos.',
    },
    {
      number: '03',
      title: 'Vendés y gestionás',
      text: 'Centralizás productos, stock, pedidos y pagos.',
    },
    {
      number: '04',
      title: 'Analizás y crecés',
      text: 'Usás información ordenada para decidir mejor.',
    },
  ] as const;
}
