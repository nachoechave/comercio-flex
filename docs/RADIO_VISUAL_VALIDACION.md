# Validación visual RADIO — 10 de septiembre de 2026

## Revisión a partir de las dos referencias adjuntas

Las imágenes `Landing page de radio xeneize.png` y `Panel del Socio A Todo Boca.png` son ahora la referencia visual directa. Se reemplazó la composición anterior, mucho más espaciada, por:

- Home con hero rectangular, tipografía deportiva, YouTube como CTA principal, programas en tarjetas compactas y recuadro editorial lateral.
- Invitación a socios junto a tres planes con beneficios reales, precio y CTA; plan central oscuro con borde amarillo y rótulo «Destacado» (no se afirma popularidad sin datos).
- Sponsors en franja oscura, navbar con indicador activo, login destacado y footer compacto.
- Panel con sidebar continua, cabecera fotográfica según branding, tarjeta de membresía, resúmenes de cuota/beneficios/pagos, beneficios y estado de pago, historial real en portada y gestión del plan en su sección.
- Nuevos templates y estilos separados para home/panel, sin cambios de contratos, autenticación, backend ni pagos.

Diferencias deliberadas: programas en lugar de noticias; no hay noticias exclusivas, sorteos, carnet QR, notificaciones ficticias, fecha de débito automático ni tarjeta de crédito inventada. La marca, fotografía y logos finales deben provenir de la configuración del tenant; las imágenes de referencia son capturas completas, no assets originales. La variante editorial usa una fotografía de estadio de muestra, no la hinchada exacta de Boca.

Capturas de esta revisión: `home-editorial-{390,768,1366,1920}.png` y `member-editorial-{390,768,1366,1920}.png` en `backend/target/radio-visual`. El panel editorial mantiene el socio y su cuota reales, interceptando sólo el branding. La captura espera explícitamente la carga del branding y la imagen. Logs finales: `radio-reference-tests.log`, `radio-reference-build.log`, `radio-reference-e2e.log` en `backend/target`.

Resultados: 351 tests frontend aprobados en 65 archivos; 24 pruebas de `RadioMembershipIntegrationTests` aprobadas, incluido E2E Chrome/MySQL; production build aprobado. Se inspeccionaron las capturas de ambos diseños en los cuatro anchos. El historial de pagos personal conserva tabla desktop y usa filas con etiquetas en móvil; la consulta y paginación no cambian. El E2E se repite después de este último ajuste, con log `radio-reference-browser-final.log`.

El fallback de programas ahora se dibuja con CSS y colores del tenant. Reemplaza al SVG mencionado en la primera revisión siguiente.

## Videos de YouTube

La configuración RADIO ahora admite `youtubeChannelId`. El backend consulta el feed público del canal, limita la respuesta a seis videos, cachea durante diez minutos y devuelve una lista vacía si YouTube no responde. La home muestra hasta cuatro cards de “Últimos videos”; cada card enlaza al video en YouTube. El admin conserva también `youtubeUrl` para el CTA y el enlace al canal. No se expone una API key ni se modifican pagos.

## Primera revisión, previa a recibir las imágenes

Branch: `codex/radio-visual-redesign`. Base `f11b5831b8f3aabd65cfbc9326d8a9ef6eee6d23`, contenida en `origin/main` (`32dd58969723047045950de37130b073efe406b3`) según `git merge-base --is-ancestor HEAD origin/main`.

## Alcance y correcciones

Cambios exclusivamente frontend: menú colapsable también en tablet, ancho desktop, tamaño del título del hero, contraste de descripciones de planes, fallback de programas servido desde `public`, footer con navegación, navegación del socio arriba en móvil, estados con colores diferenciados y CTA de membresía que distingue usuarios sin plan. No se modificó backend ni lógica de pagos.

## Evidencia

Chrome real mediante Playwright, con MySQL 8.4.10 en Testcontainers. El E2E cubre creación administrativa de plan, registro, login, elección y confirmación, persistencia tras recarga, panel, cierre/reingreso y consulta administrativa de socio/cuotas. La suite `RadioMembershipIntegrationTests` también verifica aislamiento, permisos, snapshots y concurrencia.

Se inspeccionaron capturas de home y panel a 390, 768, 1366 y 1920 px; las comprobaciones automáticas no detectaron desbordamiento horizontal.

Capturas en `backend/target/radio-visual/`:

- `home-{ancho}.png`: tenant real aislado, con su marca violeta y sin contenido editorial configurado.
- `member-{ancho}.png`: socio real del E2E, plan PLUS, cuota pendiente y pago no disponible.
- `home-editorial-{ancho}.png`: variante visual con respuestas de settings/contenido/planes de muestra interceptadas sólo en otro contexto de navegador. Navbar azul oscuro, acento amarillo, fotografía dominante, tres programas con fallback, tres planes y sponsors de muestra. No prueba persistencia de esos fixtures. La fotografía se carga de Unsplash y esta parte requiere acceso a internet. No se incorpora esa imagen ni esa marca al producto.

Comparación con el objetivo: hero fotográfico dominante, contenido ancho, tres columnas desktop y una mobile, planes destacados, footer con navegación y redes. Programas y sponsors usan contenido de muestra; para el sitio definitivo deben configurarse sus imágenes y logos reales. Los colores siguen la configuración de cada tenant.

## Reproducción

Desde `frontend`: `npm run build -- --configuration production` y `npm test -- --watch=false`.

Desde `backend`: `.\mvnw.cmd -Dtest=RadioMembershipIntegrationTests -Dradio.browser=true test` con Docker Desktop disponible.

Logs de esta revisión en `backend/target/`: `radio-visual-validation.log`, `radio-membership-browser.log`, `radio-frontend-validation.log`, `radio-production-build.log`. Son artefactos locales ignorados por Git. No se ejecutó commit, PR, merge ni deploy.
