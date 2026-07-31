# Glosario

- **ADR:** registro que explica una decisión arquitectónica, alternativas y consecuencias.
- **Allowlist:** lista explícita de valores permitidos; cualquier valor no listado
  se rechaza.
- **AbstractRoutingDataSource:** componente Spring que selecciona un datasource
  mediante una clave obtenida del contexto actual.
- **API REST:** contrato HTTP mediante el cual Angular se comunica con el backend.
- **Archivado lógico:** retiro reversible de un registro mediante un estado, sin
  eliminar físicamente la fila ni romper referencias históricas.
- **BigDecimal:** tipo Java para cálculos decimales exactos; se usa para dinero
  en vez de `double`.
- **Controller:** entrada HTTP del backend; valida el formato y delega un caso de uso.
- **CORS:** reglas que controlan qué orígenes web pueden llamar a la API.
- **CSR (Client-Side Rendering):** renderizado de una SPA en el navegador. Reduce
  complejidad de despliegue, aunque limita SEO y previews sociales avanzados.
- **Cookie `HttpOnly`:** cookie que el navegador envía, pero JavaScript no puede
  leer directamente; reduce la exposición del identificador de sesión ante XSS.
- **Correo normalizado:** representación canónica usada para comparar correos sin
  diferencias irrelevantes de mayúsculas o espacios.
- **Credential stuffing:** intentos automatizados con credenciales filtradas de
  otros servicios.
- **CSRF:** ataque que intenta ejecutar una acción aprovechando una sesión existente.
- **Dependency Injection:** mecanismo que entrega dependencias sin crearlas manualmente.
- **DTO:** objeto diseñado para transportar datos por una frontera, como la API.
- **Entity:** objeto persistente asociado a datos de la base; no debe exponerse como DTO.
- **Flyway / migración:** cambio versionado y reproducible del esquema de base de datos.
- **Fallo cerrado:** ante una ausencia o inconsistencia, negar la operación en vez
  de usar un valor predeterminado potencialmente inseguro.
- **Guard:** control de navegación Angular; ayuda a la UX, no reemplaza seguridad backend.
- **Hash adaptativo:** transformación unidireccional de contraseña cuyo costo se
  ajusta para dificultar ataques de fuerza bruta.
- **Idempotencia:** procesar repetidamente el mismo evento sin repetir su efecto.
- **Idempotency-Key:** identificador único de un intento de escritura. Permite
  reconocer un reintento después de un timeout y devolver el resultado original.
- **localStorage:** almacenamiento clave/valor del navegador que sobrevive a
  recargas. No es una base confiable ni debe contener secretos.
- **Identificador público:** UUID opaco expuesto por la API; evita revelar la
  clave numérica interna utilizada por índices y relaciones.
- **Interceptor:** pieza Angular que trata solicitudes/respuestas HTTP transversalmente.
- **JWT:** token firmado con información verificable; no implica revocación automática.
- **Membresía (`membership`):** vínculo que autoriza a un usuario global a operar
  en un comercio concreto con un rol determinado.
- **Multi-tenant / multiempresa:** una plataforma atiende varios comercios aislados.
- **`no-store`:** directiva HTTP que indica que una respuesta no debe guardarse
  en cachés; STORE-01 la usa porque la disponibilidad puede cambiar.
- **Paginación:** división de una lista grande en páginas; el servidor devuelve
  sólo un tramo y el total disponible.
- **Ledger:** historial inmutable de movimientos que explica cómo se obtuvo un
  balance.
- **Lock de fila:** bloqueo transaccional temporal que serializa cambios
  concurrentes sobre el mismo registro.
- **Pool de conexiones:** conjunto acotado de conexiones reutilizables a una base
  de datos; evita abrir una conexión nueva por cada consulta.
- **Base de control:** base compartida con el registro necesario para localizar y
  operar las bases separadas de los comercios.
- **Connection routing:** selección segura de una conexión de base a partir del
  comercio resuelto por el servidor.
- **OAuth:** autorización delegada sin pedir al comercio que entregue su contraseña.
- **PKCE:** extensión de OAuth que liga el código de autorización a un secreto
  temporal del cliente, reduciendo el riesgo de usar un código interceptado.
- **Preferencia de pago:** configuración creada en Checkout Pro con importe,
  productos, referencia y URLs que produce el enlace de inicio del pago.
- **PaymentGateway:** puerto del dominio que abstrae al proveedor de pagos; permite
  usar Mercado Pago real o un proveedor falso sin cambiar las reglas de negocio.
- **Intento de pago:** registro interno que vincula un pedido con un intento de
  cobro, su importe esperado, moneda, proveedor, estado e idempotencia.
- **AES-GCM:** cifrado autenticado que protege el contenido y también detecta si
  el ciphertext o su contexto fueron alterados. Cada cifrado usa un nonce nuevo.
- **AAD:** datos adicionales autenticados por AES-GCM. No se cifran, pero impiden
  mover una credencial entre tenant, ambiente, conexión o campo sin detección.
- **External reference:** identificador opaco enviado al proveedor para correlacionar
  el pago verificado con un intento interno.
- **Inbox de webhooks:** tabla durable que deduplica notificaciones externas y
  permite procesarlas o reintentarlas sin perder eventos.
- **Deadlock:** bloqueo circular entre transacciones concurrentes. MySQL aborta
  una de ellas para recuperar el progreso; la aplicación debe traducir ese caso
  a un resultado controlado o reintentable sin duplicar efectos.
- **Rate limiting:** límite temporal de solicitudes para reducir abuso, fuerza
  bruta y agotamiento de recursos.
- **Repository:** abstracción para leer o guardar datos del dominio.
- **Reserva de stock:** compromiso temporal que reduce la cantidad vendible sin
  alterar el balance físico.
- **Service / caso de uso:** coordina reglas y una operación de negocio.
- **Fingerprint:** hash de una representación canónica del comando; permite
  comprobar si una clave idempotente conserva la misma intención.
- **Token de consulta:** secreto URL-safe que, junto al UUID público, permite a
  un invitado consultar su pedido. La base conserva sólo su hash.
- **Snapshot:** copia de datos tomada en un momento. El carrito conserva una
  fotografía visible, pero debe revalidarla porque precio y disponibilidad cambian.
- **Máquina de estados:** conjunto explícito de estados y transiciones permitidas
  que evita saltos ambiguos en el ciclo de vida de un pedido.
- **Movimiento compensatorio:** registro inverso que restaura un efecto anterior
  sin borrar su historial; una cancelación repone stock de esta manera.
- **Sesión:** estado autenticado mantenido por el servidor y asociado a una cookie
  segura del navegador.
- **Spring Session JDBC:** implementación que persiste sesiones HTTP en tablas de
  una base relacional para compartirlas entre instancias y sobrevivir reinicios.
- **Signal:** primitiva reactiva de Angular para representar estado y valores derivados.
- **SKU:** código operativo único que identifica una variante vendible dentro de
  un comercio.
- **Slug:** texto estable y seguro para URLs, por ejemplo `remeras-de-nino`. No es
  un identificador de base de datos ni una autorización.
- **Storefront / tienda pública:** interfaz anónima donde un visitante navega el
  catálogo de un comercio, separada del panel administrativo.
- **SSR:** generación inicial de HTML en el servidor; puede mejorar SEO y primera carga.
- **Tenant:** comercio cuyos datos y configuración deben mantenerse aislados.
- **ThreadLocal:** almacenamiento asociado al hilo actual. Debe limpiarse porque
  los servidores reutilizan hilos entre solicitudes.
- **Variable de entorno:** configuración externa al código, apropiada para secretos.
- **Versión optimista:** número que permite detectar si otro usuario cambió un
  recurso desde que fue leído, evitando sobrescribirlo silenciosamente.
- **Webhook:** solicitud enviada por un sistema externo para notificar un evento.
- **XSS:** inyección de script en una página. Una cookie `HttpOnly` dificulta el
  robo directo de sesión, pero no reemplaza la prevención de contenido inseguro.
- **XSRF token:** valor que la SPA copia desde `XSRF-TOKEN` al header
  `X-XSRF-TOKEN` para demostrar que una operación no proviene de un sitio externo.
- **Access token:** credencial breve que permite llamar APIs autorizadas del
  proveedor; se cifra y nunca se entrega al navegador.
- **Refresh token:** credencial rotativa usada para obtener un access token nuevo
  sin pedir autorización en cada cobro; también se cifra y actualiza de forma
  atómica.
- **State OAuth:** valor aleatorio, vencible y de un uso que vincula el retorno
  del proveedor con el usuario y comercio que iniciaron la autorización.
- **Nickname vendedor:** nombre de usuario público devuelto por `/users/me`. Se
  muestra como referencia visual, pero el `user_id` es la identidad canónica.
