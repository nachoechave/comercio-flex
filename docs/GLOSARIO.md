# Glosario

- **ADR:** registro que explica una decisión arquitectónica, alternativas y consecuencias.
- **Allowlist:** lista explícita de valores permitidos; cualquier valor no listado
  se rechaza.
- **AbstractRoutingDataSource:** componente Spring que selecciona un datasource
  mediante una clave obtenida del contexto actual.
- **API REST:** contrato HTTP mediante el cual Angular se comunica con el backend.
- **Controller:** entrada HTTP del backend; valida el formato y delega un caso de uso.
- **CORS:** reglas que controlan qué orígenes web pueden llamar a la API.
- **CSRF:** ataque que intenta ejecutar una acción aprovechando una sesión existente.
- **Dependency Injection:** mecanismo que entrega dependencias sin crearlas manualmente.
- **DTO:** objeto diseñado para transportar datos por una frontera, como la API.
- **Entity:** objeto persistente asociado a datos de la base; no debe exponerse como DTO.
- **Flyway / migración:** cambio versionado y reproducible del esquema de base de datos.
- **Fallo cerrado:** ante una ausencia o inconsistencia, negar la operación en vez
  de usar un valor predeterminado potencialmente inseguro.
- **Guard:** control de navegación Angular; ayuda a la UX, no reemplaza seguridad backend.
- **Idempotencia:** procesar repetidamente el mismo evento sin repetir su efecto.
- **Interceptor:** pieza Angular que trata solicitudes/respuestas HTTP transversalmente.
- **JWT:** token firmado con información verificable; no implica revocación automática.
- **Membresía (`membership`):** vínculo que autoriza a un usuario global a operar
  en un comercio concreto con un rol determinado.
- **Multi-tenant / multiempresa:** una plataforma atiende varios comercios aislados.
- **Pool de conexiones:** conjunto acotado de conexiones reutilizables a una base
  de datos; evita abrir una conexión nueva por cada consulta.
- **Base de control:** base compartida con el registro necesario para localizar y
  operar las bases separadas de los comercios.
- **Connection routing:** selección segura de una conexión de base a partir del
  comercio resuelto por el servidor.
- **OAuth:** autorización delegada sin pedir al comercio que entregue su contraseña.
- **Repository:** abstracción para leer o guardar datos del dominio.
- **Service / caso de uso:** coordina reglas y una operación de negocio.
- **Sesión:** estado autenticado mantenido por el servidor y asociado a una cookie
  segura del navegador.
- **Signal:** primitiva reactiva de Angular para representar estado y valores derivados.
- **SSR:** generación inicial de HTML en el servidor; puede mejorar SEO y primera carga.
- **Tenant:** comercio cuyos datos y configuración deben mantenerse aislados.
- **ThreadLocal:** almacenamiento asociado al hilo actual. Debe limpiarse porque
  los servidores reutilizan hilos entre solicitudes.
- **Variable de entorno:** configuración externa al código, apropiada para secretos.
- **Webhook:** solicitud enviada por un sistema externo para notificar un evento.
