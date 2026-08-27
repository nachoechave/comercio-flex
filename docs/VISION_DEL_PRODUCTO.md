# Visión del producto

> Estado: visión inicial aprobada; producto desplegado en etapa piloto/precomercial
> Fecha: 2026-07-23

## Visión

Comercio Flex es una plataforma de ecommerce multi-tenant para pequeños y
medianos comercios. Un único producto permite operar varias tiendas sin copiar
el código ni mezclar sus datos. Cada comercio tiene catálogo, usuarios, pedidos,
configuración visual y medios de pago propios.

## Problema que resuelve

Muchos comercios necesitan vender en línea, pero las soluciones genéricas resultan
complejas o no contemplan particularidades como cantidades por peso, variantes
genéricas o retiro en el local. El producto cubre un núcleo común y activa
capacidades acotadas por configuración.

## Propuesta de valor

- Puesta en marcha simple para el comerciante.
- Tienda móvil, catálogo, checkout y operación de pedidos en un mismo producto.
- Configuración por rubro sin mantener versiones separadas.
- Aislamiento estricto de los datos de cada comercio.
- Base técnica que pueda crecer sin microservicios prematuros.

## Usuario objetivo inicial

El vertical piloto aprobado es **indumentaria**. La aplicación soporta talle,
color y opciones genéricas; la validación comercial con un primer cliente real
continúa siendo el próximo paso del piloto.

## Principios

1. Seguridad multiempresa antes que personalización avanzada.
2. Flujo completo de venta antes que muchos reportes.
3. Configuración tipada antes que un motor genérico de reglas.
4. Experiencia móvil y accesible desde el inicio.
5. Documentación, pruebas y aprendizaje forman parte del producto.

## Indicadores de validación del piloto

- El comercio puede publicar un catálogo sin asistencia técnica cotidiana.
- Un cliente completa catálogo → carrito → pedido → pago.
- El administrador opera pedidos y stock.
- Las pruebas demuestran que un comercio no accede a datos de otro.
- El entorno productivo puede respaldarse y restaurarse de forma documentada.
