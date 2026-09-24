# Envíos externos — Fase 2

Base: `feature/shipping-core` (Fase 1). Rama: `feature/shipping-phase-2`.

## Objetivo

Agregar un primer transportista real sin acoplar el checkout ni el ciclo de pedidos a un proveedor específico. La implementación conserva retiro, tarifa fija, localidad y código postal de Fase 1, y suma Andreani como adaptador externo.

## Alcance implementado

- Configuración Andreani por tenant, desactivada por defecto.
- Ambientes SANDBOX/PRODUCTION separados.
- Credenciales cifradas con el `CredentialCipher` AES-GCM ya usado por pagos. La API administrativa nunca devuelve usuario ni contraseña.
- Cotización Andreani en tiempo real desde el checkout.
- Cotización temporal persistida por 15 minutos. El frontend recibe un `quoteToken`; la creación del pedido valida token, CP, total, subtotal, descuento y vencimiento antes de consumirlo.
- Snapshot del pedido con proveedor, servicio, costo al cliente, costo real del transportista, paquete, documento y dirección.
- Envío gratis sigue afectando el importe cobrado al cliente; `provider_cost` conserva el costo logístico real.
- Provisionado idempotente de la orden externa después de confirmar el pedido.
- Descarga protegida de la etiqueta PDF desde el panel del pedido.
- Tracking con refresco automático al consultar el shipment cuando el último sync está vencido. El intervalo es configurable por tenant y nunca baja de 5 minutos.
- Estados remotos pueden adelantar el shipment local a PREPARING, SHIPPED o DELIVERED. No completan automáticamente el estado comercial del pedido.
- Email `ORDER_SHIPPED` se reutiliza cuando el tracking externo adelanta por primera vez a SHIPPED.
- Los errores transitorios del proveedor quedan visibles en el shipment sin borrar tracking previo ni romper métodos manuales.

## Endpoints

Bajo `/api/v1/stores/{storeSlug}`:

- `POST /shipping/quote`: ahora puede devolver una opción `type=CARRIER` con `provider=ANDREANI` y `quoteToken`.
- `GET /admin/shipping/carrier`: lee configuración sin exponer secretos.
- `PUT /admin/shipping/carrier`: guarda configuración/credenciales con versión optimista.
- `POST /admin/orders/{id}/shipment/carrier`: crea el envío remoto si el pedido está confirmado. Es idempotente si ya existe `external_reference`.
- `GET /admin/orders/{id}/shipment/label`: proxy autenticado de la etiqueta PDF.
- `GET /admin/orders/{id}/shipment`: conserva Fase 1 y además refresca tracking externo cuando corresponde.

## Modelo de paquete

Fase 2 utiliza un paquete predeterminado por comercio:

- peso por unidad;
- largo;
- ancho;
- alto.

Para cotizar, el peso base se multiplica por la cantidad total de unidades y las dimensiones se conservan. Esto permite integrar el transportista sin modificar todo el catálogo. La evolución recomendada es agregar peso/dimensiones por producto o variante y un algoritmo de packing.

## Seguridad

- Escrituras administrativas conservan autenticación, permisos tenant y CSRF.
- Las credenciales no viajan en respuestas GET.
- Para persistir credenciales es obligatorio configurar `PAYMENT_TOKEN_ENCRYPTION_KEY_V1`; se reutiliza la infraestructura de cifrado existente y no se guardan secretos en texto plano.
- Cambiar SANDBOX/PRODUCTION exige volver a ingresar credenciales para evitar reutilizarlas con otro contexto criptográfico.
- El quote token es UUID aleatorio, expira y se consume una sola vez.
- La creación remota ocurre fuera de la transacción que reserva/consume inventario: el checkout nunca depende de una llamada externa para confirmar sus invariantes locales.

## Andreani

El adaptador implementa las operaciones de login, tarifas, orden de envío, etiqueta y tracking contra los hosts QA/Producción del contrato actual de API. La activación real requiere credenciales habilitadas por Andreani para el comercio.

No se incluyeron credenciales reales en el repositorio ni se realizaron llamadas reales durante la implementación. Las pruebas del core usan un `CarrierGateway` simulado; la verificación contra QA debe hacerse cuando se disponga de una cuenta Andreani.

## Compatibilidad

- V032 no se modifica.
- V033 agrega configuración, quotes temporales y metadatos externos del shipment.
- Todos los tenants arrancan con Andreani desactivado.
- FASHION, FRESH y CATALOG siguen compartiendo el mismo checkout.
- Mercado Pago, transferencia bancaria, RADIO, QR y pagos de membresía no cambian de contrato.

## Operación

1. Admin → Andreani.
2. Configurar ambiente, cliente, contrato, usuario/clave, remitente y paquete base.
3. Guardar y activar.
4. El cliente ingresa dirección y cotiza en checkout.
5. Si elige Andreani, informa DNI/CUIT/CUIL; el pedido guarda el snapshot de esa cotización.
6. Después de confirmar el pago, el comercio abre el pedido y genera el envío en Andreani.
7. Descarga la etiqueta PDF desde el mismo panel.
8. Las consultas posteriores del shipment actualizan tracking según el intervalo configurado.

## Pendientes posteriores a Fase 2

- Peso y dimensiones por producto/variante.
- Packing de varios productos en uno o más bultos.
- Creación automática mediante outbox inmediatamente después de confirmar el pago.
- Cancelación/devolución remota y reembolsos.
- Webhooks del transportista si el proveedor habilita un contrato de eventos.
- Segundo adaptador (Correo Argentino/OCA) reutilizando `CarrierGateway`.
