# Andreani — checklist de piloto real

Este documento cubre la validación que debe hacerse antes de ofrecer Andreani como transportista activo a comercios de Comercio Flex.

## Requisitos del comercio

Cada tenant utiliza su propia cuenta Andreani. Para el piloto se necesita:

- código de cliente;
- código de contrato;
- usuario y contraseña habilitados para API;
- credenciales del ambiente que corresponda (QA/Sandbox o Producción);
- datos reales del remitente;
- un paquete base con peso y dimensiones razonables.

Las credenciales se guardan cifradas y no vuelven al frontend después de persistirse.

## Prueba desde el panel

Ruta: `Admin → Configuración → Envíos → Andreani`.

1. Elegir `QA / Sandbox` para la primera integración.
2. Completar cliente, contrato y credenciales.
3. Completar remitente y paquete base.
4. Ingresar un código postal de destino de prueba.
5. Pulsar **Probar conexión**.

La prueba no guarda cambios, no activa la integración y no crea un envío.

### Chequeo AUTH

Ejecuta el login contra Andreani. Debe devolver OK antes de continuar.

Si falla, revisar:

- ambiente correcto;
- usuario y contraseña;
- credenciales habilitadas por Andreani para API;
- disponibilidad del servicio Andreani.

### Chequeo QUOTE

Con autenticación válida, se consulta una tarifa usando:

- código de cliente;
- contrato;
- CP destino de prueba;
- peso y dimensiones del paquete base.

Si AUTH pasa pero QUOTE falla, revisar primero cliente, contrato, CP y paquete. Un resultado parcial permite distinguir un problema de credenciales de un problema de cotización.

## Flujo E2E obligatorio antes de Producción

Con una cuenta QA válida, validar en este orden:

1. cotización desde checkout;
2. selección de Andreani y documento del destinatario;
3. creación del pedido;
4. confirmación de pago;
5. botón `Generar envío en Andreani`;
6. número de envío/tracking;
7. descarga de etiqueta PDF;
8. consulta posterior del shipment y refresco de tracking;
9. transición a `SHIPPED` y email correspondiente.

No habilitar Producción sólo porque `Probar conexión` devuelve OK: esa prueba valida autenticación y tarifas, pero no crea una orden real ni valida etiqueta/tracking.

## Pendientes para una etapa posterior

- peso y dimensiones por producto/variante;
- packing de múltiples productos/bultos;
- generación automática del envío después del pago;
- cancelación/devolución remota;
- webhooks/eventos si Andreani los habilita para el contrato;
- soporte multi-carrier simultáneo.
