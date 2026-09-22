# Galería de productos

Rama: `feature/product-image-gallery`. No requiere otro proveedor de almacenamiento.

## Arquitectura encontrada

El catálogo utiliza records de dominio y repositorios JDBC, no entidades JPA. Ya existían `ProductImage`, `ProductImageReference` y `product_images` (V012), con una restricción única por producto. `products` no tiene `image_url`: los DTOs exponían `image`, con URLs del proxy HTTP del backend y un thumbnail. Se preserva ese contrato.

El editor crea primero un borrador, sube su imagen, registra stock mediante el servicio existente y finalmente publica cuando se solicita. FASHION, FRESH y CATALOG comparten el componente público de detalle. Las cards consumen `image`.

El almacenamiento existente genera versiones display y thumbnail. `S3ProductImageStorage` acepta endpoint, bucket, región, credenciales y path-style mediante `app.media.s3`; producción usa S3 por defecto, compatible con Supabase. La feature conserva esa configuración y el almacenamiento local de desarrollo.

## Modelo y migración

V030 amplía la tabla existente con `position`, `is_primary` y una columna generada para garantizar como máximo una principal mediante un índice único. El índice único `(product_id, position)` y el CHECK 0–5 protegen las posiciones. `position` admite NULL únicamente como estado intermedio usado por el repositorio al permutar posiciones dentro de una transacción bloqueada; ninguna operación de la API confirma posiciones nulas.

Cada fila existente conserva IDs, claves de storage y timestamps, y recibe posición 0 y principal=true. No se copian ni borran archivos. No se alteran productos, variantes, precios, stock ni órdenes. No se modifica ninguna migración aplicada previamente.

`Product` y `PublicProductDetail` incorporan `images`; cada imagen incluye `position` y `primary`. Los detalles y listados mantienen `image` y agregan `imageUrl`, calculado a partir de la principal. Las consultas eligen primero `is_primary`, después `position`, con fallback a la primera. Los listados no multiplican filas por el JOIN de la galería. Los constructores anteriores de los records conservan compatibilidad con consumidores Java existentes.

## API

Base administrativa: `/api/v1/stores/{storeSlug}/admin/products/{productId}`.

| Método | Ruta relativa | Entrada / resultado |
| --- | --- | --- |
| POST | `/images` | multipart: partes repetidas `images`, `altText`; devuelve galería completa |
| DELETE | `/images/{imageId}` | elimina una imagen; devuelve galería completa |
| PUT | `/images/{imageId}/primary` | selecciona principal; devuelve galería completa |
| PUT | `/images/order` | array JSON de todos los IDs, sin duplicados; devuelve galería completa |

Las rutas anteriores `PUT /image` y `DELETE /image` siguen funcionando sobre la principal y conservan el resto de la galería. Las URLs de lectura display/thumbnail no cambian.

Todas las mutaciones bloquean la fila de producto (`FOR UPDATE`) dentro de la transacción del tenant. El backend verifica `existentes + nuevas <= 6` antes de escribir archivos. Los lotes vacíos o que exceden seis responden 400. El lote se confirma íntegramente; si falla almacenamiento, procesamiento o SQL se revierte la base y se intenta limpiar cada objeto nuevo. Las solicitudes concurrentes no pueden superar el límite.

Elegir principal no cambia el orden. Reordenar no cambia la principal. Eliminar compacta posiciones; si se elimina la principal, se promueve la primera restante. Al eliminar la última, la galería queda vacía. La limpieza de los dos objetos de storage ocurre después del commit.

## Angular y storefront

El editor permite selección múltiple, previews con object URLs liberadas al quitar/salir, contador de guardadas más pendientes, máximo seis, eliminación y selección de principal. Usa botones izquierda/derecha, sin agregar CDK ni dependencias. Las selecciones pendientes pueden reordenarse antes del upload; la primera es principal cuando el producto todavía no tiene imágenes. Se conserva el texto alternativo obligatorio; el lote comparte la descripción ingresada.

La creación conserva el flujo borrador → upload de lote → stock → publicación. Si falla el upload, se ofrece continuar editando el borrador existente. En edición se usa el botón explícito de agregar imágenes.

El detalle público compartido por FASHION/FRESH/CATALOG muestra principal grande, thumbnails con botones accesibles, contador y swipe horizontal. Cero imágenes conserva el placeholder; una sola no muestra navegación innecesaria. Si un DTO anterior no trae `images`, Angular usa `image`. Las cards siguen mostrando solo la principal.

## Aislamiento y seguridad

- Las rutas nuevas exigen `MANAGE_CATALOG`; se mantiene CSRF y la seguridad de productos existente.
- Se usa el datasource del tenant resuelto; los IDs de imagen se comprueban contra las imágenes del producto bloqueado. Manipular un ID de otro producto o comercio no habilita escritura.
- El acceso público sigue exigiendo producto publicado. Las imágenes administrativas requieren permisos de catálogo.
- Se mantienen JPEG/PNG reales, firma y decodificación, límite de 5 MiB, 10 millones de píxeles, orientación y recodificación para retirar metadatos/contenido adicional. No se confía en MIME o extensión enviados por el cliente.
- Los nombres originales no forman parte del path: las claves usan tenant, UUID de producto y UUID de imagen. Se conserva `nosniff` y el manejo de caché previo.
- El máximo multipart total sube de 6 MB a 31 MB para admitir seis archivos válidos; el límite individual no cambia. Si una instalación define `MEDIA_MAX_REQUEST_SIZE` o un proxy con límite menor, debe actualizar ese valor antes de habilitar cargas grandes.
- No se modifica lógica RADIO, membresías, sponsors, Checkout Pro ni QR.

## Pruebas y decisiones pendientes

Se amplían los tests de integración de productos con cero/una/varias/seis imágenes, overflow 7 y 5+2, selección principal, orden, eliminación, IDs ajenos, tenant B, permisos, CSRF, publicación, lotes inválidos, concurrencia, DTO y rutas legacy. Un test Flyway migra datos reales de V029 a V030 y comprueba preservación. Los unit tests verifican rechazo antes del storage y compensación de objetos ante fallos. Angular cubre selección múltiple, capacidad, previews, orden, principal, eliminación y galería en los tres templates, además de los tests anteriores de cero/una imagen.

No existe script lint en `frontend/package.json`; se utiliza Prettier sobre archivos modificados y `git diff --check`.

La eliminación de storage mantiene la estrategia existente best-effort: ante fallo registra un warning y puede dejar un objeto huérfano. Una cola durable de limpieza y compensación ante caída del proceso queda fuera de esta feature. El bloqueo se mantiene durante el upload para priorizar consistencia; seis archivos acotan el trabajo, pero conviene observar latencias reales de S3. No se ejecutan pruebas contra el bucket de producción. No se hace deploy, merge ni push.

Resultados finales (22/09/2026):

- Backend: 489 casos registrados en 78 suites; **488 aprobados, 0 fallos, 0 errores y 1 omitido**. El omitido es el test preexistente RADIO `browserEndToEnd`, habilitado únicamente con `-Dradio.browser=true`.
- Se ejecutó la suite completa Maven: 488 casos en ese momento. Detectó una aserción del nuevo test de migración que comparaba arrays binarios por referencia. Tras corregirla y agregar el caso de fallback, se recompilaron y ejecutaron los 27 casos de `ProductImageMigrationTests`, `ProductManagementIntegrationTests` y `ProductImageServiceTests`: todos aprobados. Los reportes finales no contienen fallos.
- Frontend: **359/359 aprobados** en 65 archivos. Tras retirar el código de eliminación singular que ya no usaba el editor, se repitieron sus 41 casos: todos aprobados.
- Total de casos distintos: **848 registrados, 847 aprobados y 1 omitido**. Las repeticiones focalizadas no se cuentan dos veces.
- Compilación backend correcta mediante Maven; build Angular de producción correcto después de los cambios finales.
- Prettier sobre los archivos frontend modificados y `git diff --check`: correctos. No hay tarea lint configurada en Maven ni en package.json.
- Docker se inició para ejecutar MySQL efímero mediante Testcontainers. No se modificaron bases de producción ni se probó con el bucket productivo.

Logs locales de ejecución, ignorados por Git: `backend/gallery-backend-tests.log`, `backend/gallery-focused-tests.log`, `frontend/gallery-frontend-tests.log`, `frontend/gallery-form-final-tests.log` y `frontend/gallery-build.log`. Los XML finales están en `backend/target/surefire-reports`.

## Archivos de esta entrega

- `backend/src/main/java/com/comercioflex/catalog/api/ProductDetailResponse.java`
- `backend/src/main/java/com/comercioflex/catalog/api/ProductSummaryResponse.java`
- `backend/src/main/java/com/comercioflex/catalog/api/PublicProductDetailResponse.java`
- `backend/src/main/java/com/comercioflex/catalog/api/PublicProductSummaryResponse.java`
- `backend/src/main/java/com/comercioflex/catalog/domain/Product.java`
- `backend/src/main/java/com/comercioflex/catalog/domain/PublicProductDetail.java`
- `backend/src/main/java/com/comercioflex/catalog/infrastructure/jdbc/JdbcProductRepository.java`
- `backend/src/main/java/com/comercioflex/catalog/infrastructure/jdbc/JdbcPublicCatalogRepository.java`
- `backend/src/main/java/com/comercioflex/media/api/AdminProductImageController.java`
- `backend/src/main/java/com/comercioflex/media/api/ProductImageResponse.java`
- `backend/src/main/java/com/comercioflex/media/application/ProductImageRepository.java`
- `backend/src/main/java/com/comercioflex/media/application/ProductImageService.java`
- `backend/src/main/java/com/comercioflex/media/domain/ProductImage.java`
- `backend/src/main/java/com/comercioflex/media/domain/ProductImageReference.java`
- `backend/src/main/java/com/comercioflex/media/infrastructure/jdbc/JdbcProductImageRepository.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/tenant/V030__expand_product_image_gallery.sql`
- `backend/src/test/java/com/comercioflex/ProductImageMigrationTests.java`
- `backend/src/test/java/com/comercioflex/ProductManagementIntegrationTests.java`
- `backend/src/test/java/com/comercioflex/media/application/ProductImageServiceTests.java`
- `docs/product-image-gallery.md`
- `frontend/src/app/features/admin/products/product-api.service.spec.ts`
- `frontend/src/app/features/admin/products/product-api.service.ts`
- `frontend/src/app/features/admin/products/product-form/product-form.html`
- `frontend/src/app/features/admin/products/product-form/product-form.scss`
- `frontend/src/app/features/admin/products/product-form/product-form.spec.ts`
- `frontend/src/app/features/admin/products/product-form/product-form.ts`
- `frontend/src/app/features/admin/products/product.models.ts`
- `frontend/src/app/features/storefront/product-detail/public-product-detail.html`
- `frontend/src/app/features/storefront/product-detail/public-product-detail.scss`
- `frontend/src/app/features/storefront/product-detail/public-product-detail.spec.ts`
- `frontend/src/app/features/storefront/product-detail/public-product-detail.ts`
- `frontend/src/app/features/storefront/storefront.models.ts`
