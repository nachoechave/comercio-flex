# Visión del producto

> Estado: visión inicial aprobada; alcance detallado sujeto al comercio piloto
> Fecha: 2026-07-23

## Visión

Comercio Flex será una plataforma de ecommerce multiempresa para pequeños y
medianos comercios. Un único producto permitirá operar varias tiendas sin copiar
el código ni mezclar sus datos. Cada comercio tendrá catálogo, usuarios, pedidos,
configuración visual y conexión de pagos propios.

## Problema que resuelve

Muchos comercios necesitan vender en línea, pero las soluciones genéricas resultan
complejas o no contemplan particularidades como cantidades por peso, variantes de
talle/color o retiro en el local. El producto buscará cubrir un núcleo común y
activar capacidades acotadas por configuración.

## Propuesta de valor

- Puesta en marcha simple para el comerciante.
- Tienda móvil, catálogo, checkout y operación de pedidos en un mismo producto.
- Configuración por rubro sin mantener versiones separadas.
- Aislamiento estricto de los datos de cada comercio.
- Base técnica que pueda crecer sin microservicios prematuros.

## Usuario objetivo inicial

El vertical piloto aprobado es **indumentaria**. La primera experiencia validará
productos con variantes de talle y color. Aún debe identificarse el comercio piloto
concreto y entrevistarse a sus responsables para validar el flujo operativo.

## Principios

1. Seguridad multiempresa antes que personalización avanzada.
2. Flujo completo de venta antes que muchos reportes.
3. Configuración tipada antes que un motor genérico de reglas.
4. Experiencia móvil y accesible desde el inicio.
5. Documentación, pruebas y aprendizaje forman parte del producto.

## Indicadores de validación del piloto

- El comercio puede publicar un catálogo sin asistencia técnica cotidiana.
- Un cliente completa catálogo → carrito → pedido → pago de prueba.
- El administrador opera pedidos y stock.
- Las pruebas demuestran que un comercio no accede a datos de otro.
- El piloto puede desplegarse, respaldarse y restaurarse de forma documentada.
