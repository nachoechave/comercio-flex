import { Component } from '@angular/core';

interface LandingFeature {
  readonly marker: string;
  readonly title: string;
  readonly description: string;
}

@Component({
  selector: 'app-landing-features',
  templateUrl: './landing-features.html',
  styleUrl: './landing-features.scss',
})
export class LandingFeatures {
  protected readonly features: readonly LandingFeature[] = [
    {
      marker: '01',
      title: 'Tienda online',
      description: 'Tu catálogo disponible para vender desde cualquier dispositivo.',
    },
    {
      marker: '02',
      title: 'Productos y variantes',
      description: 'Gestioná talles, colores, precios y stock por variante.',
    },
    {
      marker: '03',
      title: 'Inventario',
      description: 'Registrá movimientos auditables y trabajá con alertas de stock.',
    },
    {
      marker: '04',
      title: 'Pedidos',
      description: 'Centralizá tus ventas y seguí el estado de cada pedido.',
    },
    {
      marker: '05',
      title: 'Pagos',
      description: 'Ofrecé Mercado Pago y transferencia bancaria según tu configuración.',
    },
    {
      marker: '06',
      title: 'Gestión del comercio',
      description: 'Configurá tu operación diaria desde un panel simple.',
    },
  ];
}
