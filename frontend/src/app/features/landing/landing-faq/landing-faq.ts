import { Component } from '@angular/core';

@Component({
  selector: 'app-landing-faq',
  templateUrl: './landing-faq.html',
  styleUrl: './landing-faq.scss',
})
export class LandingFaq {
  protected readonly questions = [
    {
      question: '¿Necesito conocimientos técnicos?',
      answer:
        'No. El panel está pensado para la gestión cotidiana y la implementación se realiza de forma acompañada.',
    },
    {
      question: '¿Qué medios de pago puedo utilizar?',
      answer:
        'Cada comercio puede configurar Mercado Pago y transferencia bancaria dentro de los medios actualmente soportados.',
    },
    {
      question: '¿Puedo manejar productos con talles y colores?',
      answer:
        'Sí. Los productos admiten variantes y opciones genéricas, por ejemplo talle, color, material, presentación o peso.',
    },
    {
      question: '¿Cómo se administra el stock?',
      answer:
        'El inventario se gestiona por variante mediante movimientos auditables y alertas de stock bajo.',
    },
    {
      question: '¿Puedo recibir pedidos por transferencia?',
      answer:
        'Sí. La transferencia bancaria puede habilitarse como medio de pago y su confirmación se gestiona desde el panel.',
    },
    {
      question: '¿Puedo acceder desde el celular?',
      answer:
        'Sí. Tanto la tienda pública como el panel se adaptan a computadoras, tablets y dispositivos móviles.',
    },
    {
      question: '¿Mis datos están separados de otros comercios?',
      answer:
        'Sí. Comercio Flex aplica aislamiento por comercio y bases de datos separadas para los datos operativos de cada tenant.',
    },
  ] as const;
}
