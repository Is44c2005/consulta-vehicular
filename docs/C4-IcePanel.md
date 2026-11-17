# Modelo C4 – Consulta Vehicular (para IcePanel)

Guía para cargar el modelo en [IcePanel](https://icepanel.io). Cada sección indica qué objeto crear, su tipo, descripción, tecnologías y relaciones. Los nombres coinciden con el código para facilitar la trazabilidad.

> Convención IcePanel: **Actor** = persona; **System** = sistema de software (interno o externo); **App / Store** = contenedor C4 (nivel 2); **Component** = componente (nivel 3). Las relaciones son *connections* con una descripción y la tecnología como *tag*.

---

## Nivel 1 – Contexto del sistema

### Objetos

| Nombre | Tipo IcePanel | Interno/Externo | Descripción |
|---|---|---|---|
| **Ciudadano** | Actor | — | Persona natural que consulta sus datos de contribuyente, un vehículo y los puntos de su licencia. |
| **Consulta Vehicular** | System | Interno | Sistema que integra SRI y ANT en un solo flujo y mantiene la disponibilidad ante caídas de la ANT. |
| **SRI** | System | Externo | Servicio de Rentas Internas. Servicios REST públicos de catastro de contribuyentes y matriculación vehicular. |
| **ANT** | System | Externo | Agencia Nacional de Tránsito. Portal web (HTML) con puntos de licencia y citaciones. Baja disponibilidad. |

### Relaciones

| Origen | Destino | Descripción | Tecnología |
|---|---|---|---|
| Ciudadano | Consulta Vehicular | Ingresa correo, RUC y placa; ve contribuyente, vehículo y licencia | HTTPS / navegador |
| Consulta Vehicular | SRI | Verifica contribuyente, obtiene sus datos y los del vehículo | HTTPS / REST JSON |
| Consulta Vehicular | ANT | Consulta puntos y citaciones por cédula | HTTPS / HTML |

---

## Nivel 2 – Contenedores (dentro de *Consulta Vehicular*)

### Objetos

| Nombre | Tipo IcePanel | Tecnologías | Descripción |
|---|---|---|---|
| **Frontend Web** | App | React 18, Vite, JavaScript · Vercel | SPA con un flujo de 3 tarjetas: contribuyente, vehículo y licencia. Muestra el origen del dato de la ANT (en vivo / caché). |
| **API Gateway** | App | Java 21, Spring Boot 3.3.5, RestClient · Render | BFF. Valida correo y RUC (módulo 10), enmascara el correo, aplica CORS y unifica errores como ProblemDetail. |
| **SRI Service** | App | Java 21, Spring Boot 3.3.5, Spring Cache · Render | Verifica contribuyente / persona natural y consulta vehículos en el SRI. Cachea las respuestas 1 h. |
| **ANT Service** | App | Java 21, Spring Boot 3.3.5, Resilience4j, Jsoup · Render | Consulta puntos de licencia en la ANT con Cache-Aside + Circuit Breaker + fallback a caché desactualizada. |
| **Caché Cloud Redis** | Store | Redis (Upstash), TLS `rediss://` | Caché compartida: respuestas del SRI (TTL 1 h) y último dato válido de la ANT (TTL 7 días). |

### Relaciones

| Origen | Destino | Descripción | Tecnología |
|---|---|---|---|
| Ciudadano | Frontend Web | Usa la aplicación | HTTPS |
| Frontend Web | API Gateway | `POST /api/consultas/contribuyente`, `GET /api/consultas/vehiculos/{placa}`, `GET /api/consultas/licencias/{cedula}` | HTTPS / REST JSON |
| API Gateway | SRI Service | `GET /api/sri/contribuyentes/{ruc}`, `GET /api/sri/vehiculos/{placa}` | HTTP / REST JSON (RestClient) |
| API Gateway | ANT Service | `GET /api/ant/licencias/{cedula}` | HTTP / REST JSON (RestClient) |
| SRI Service | SRI | `existePorNumeroRuc`, `obtenerPorNumerosRuc`, `BaseVehiculo/obtenerPorNumeroPlaca...` | HTTPS / REST JSON, timeout 8 s |
| SRI Service | Caché Cloud Redis | Lee/escribe respuestas del SRI (`sri:*`, TTL 1 h) | Redis protocol (Lettuce) / TLS |
| ANT Service | ANT | `clp_grid_citaciones.jsp?ps_tipo_identificacion=CED&ps_identificacion={cedula}` | HTTPS / HTML, timeout 8 s |
| ANT Service | Caché Cloud Redis | Lee/escribe `ant:licencia:{cedula}` con `guardadoEn` (TTL 7 días) | Redis protocol (Lettuce) / TLS |

---

## Nivel 3 – Componentes de *ANT Service*

### Objetos

| Nombre | Tipo IcePanel | Tecnologías | Descripción |
|---|---|---|---|
| **AntController** | Component | Spring MVC `@RestController` | Expone `GET /api/ant/licencias/{cedula}` y devuelve `ConsultaLicenciaResponse`. |
| **ConsultaLicenciaService** | Component | Spring `@Service`, `Clock` inyectable | Orquesta el patrón: ventana fresca (5 min) → llamada protegida → guardar solo respuestas válidas → fallback a caché desactualizada o 503. |
| **Circuit Breaker** | Component | Resilience4j (programático) | Ventana de 10 llamadas, mínimo 5, 50 % de fallos, 30 s abierto, 2 llamadas half-open. Envuelve HTTP **y** parseo. |
| **AntHttpClient** | Component | Spring `RestClient`, timeouts 8 s | Implementa el puerto `AntClient`. Descarga el HTML de la ANT; los errores se propagan al breaker. |
| **AntHtmlParser** | Component | Jsoup | Parser heurístico y aislado. Extrae puntos y tabla de citaciones (`th`/`td`) o reconoce "no registra citaciones". Si no reconoce nada, lanza `AntRespuestaInvalidaException`. |
| **RedisLicenciaCache** | Component | Spring Data Redis, `StringRedisTemplate`, Jackson | Implementa el puerto `LicenciaCache`. Guarda `{datos, guardadoEn}` en JSON. Best-effort: si Redis falla, registra y continúa. |

### Relaciones

| Origen | Destino | Descripción | Tecnología |
|---|---|---|---|
| API Gateway | AntController | Solicita la licencia de una cédula | HTTP / JSON |
| AntController | ConsultaLicenciaService | `consultar(cedula)` | Llamada Java |
| ConsultaLicenciaService | RedisLicenciaCache | `obtener(cedula)` (antes) y `guardar(cedula, datos, ahora)` (tras éxito) | Llamada Java (puerto `LicenciaCache`) |
| ConsultaLicenciaService | Circuit Breaker | `executeSupplier(...)` | Resilience4j |
| Circuit Breaker | AntHttpClient | `consultarCitaciones(cedula)` | Llamada Java (puerto `AntClient`) |
| Circuit Breaker | AntHtmlParser | `parse(cedula, html)` (dentro del breaker) | Llamada Java |
| AntHttpClient | ANT | GET del HTML de citaciones | HTTPS / HTML |
| RedisLicenciaCache | Caché Cloud Redis | `GET` / `SET ... EX 604800` de `ant:licencia:{cedula}` | Redis protocol / TLS |

---

## Flow: "Consulta con ANT caída"

Crear un *Flow* en IcePanel sobre el diagrama de Nivel 3 (o de Nivel 2, colapsando los componentes) con estos pasos:

| # | Origen → Destino | Descripción del paso |
|---|---|---|
| 1 | Ciudadano → Frontend Web | Ingresa correo y RUC y pulsa *Verificar en el SRI*. |
| 2 | Frontend Web → API Gateway | Tras validar el RUC, solicita `GET /api/consultas/licencias/{cedula}` automáticamente. |
| 3 | API Gateway → AntController | Reenvía la consulta al ANT Service. |
| 4 | AntController → ConsultaLicenciaService | `consultar(cedula)`. |
| 5 | ConsultaLicenciaService → RedisLicenciaCache | Lee la caché: existe un dato de hace 2 días (fuera de la ventana fresca de 5 min). |
| 6 | ConsultaLicenciaService → Circuit Breaker | Ejecuta la llamada protegida. |
| 7 | Circuit Breaker → AntHttpClient | Llama a la ANT. |
| 8 | AntHttpClient → ANT | La ANT no responde en 8 s (timeout) o devuelve 5xx / página de mantenimiento. |
| 9 | Circuit Breaker → ConsultaLicenciaService | Registra el fallo (si ya hay ≥ 50 % de fallos en las últimas llamadas, el circuito se abre y las siguientes consultas ni siquiera intentan llamar a la ANT durante 30 s). |
| 10 | ConsultaLicenciaService → AntController | Devuelve el dato de caché con `fuente=CACHE`, `desactualizado=true` y un aviso con la fecha del dato. *(Alternativa: si no hay caché → 503 ProblemDetail.)* |
| 11 | API Gateway → Frontend Web | Propaga la respuesta. |
| 12 | Frontend Web → Ciudadano | Muestra los puntos con el badge **"Caché desactualizada"** y el aviso. |

---

## Enlazar objetos con GitHub

En IcePanel, cada objeto admite **Links** (pestaña *Links* → *Add link* → *URL*, o la integración de GitHub para enlazar carpetas del repositorio). Sustituya `<usuario>/<repo>` por su repositorio y `main` por su rama:

| Objeto | Enlace |
|---|---|
| Consulta Vehicular | `https://github.com/<usuario>/<repo>` |
| Frontend Web | `https://github.com/<usuario>/<repo>/tree/main/frontend` |
| API Gateway | `https://github.com/<usuario>/<repo>/tree/main/backend/api-gateway` |
| SRI Service | `https://github.com/<usuario>/<repo>/tree/main/backend/sri-service` |
| ANT Service | `https://github.com/<usuario>/<repo>/tree/main/backend/ant-service` |
| Caché Cloud Redis | `https://github.com/<usuario>/<repo>/blob/main/docker-compose.yml` (y la consola de Upstash) |
| AntController | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/controller` |
| ConsultaLicenciaService | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/service` |
| Circuit Breaker | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/config` (`AntConfig`) |
| AntHttpClient | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/adapter` (`AntHttpClient.java`) |
| AntHtmlParser | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/parser` |
| RedisLicenciaCache | `.../backend/ant-service/src/main/java/ec/udla/consulta/ant/adapter` (`RedisLicenciaCache.java`) |
| SRI | `https://srienlinea.sri.gob.ec` |
| ANT | `https://consultaweb.ant.gob.ec` |

Pasos sugeridos en IcePanel:

1. Crear el *landscape* y la vista de **Contexto** con Ciudadano, Consulta Vehicular, SRI y ANT.
2. Hacer *zoom in* en **Consulta Vehicular** y crear los cinco contenedores con sus *tech tags* (React, Spring Boot, Redis…).
3. Hacer *zoom in* en **ANT Service** y crear los seis componentes y sus conexiones.
4. Crear el *Flow* "Consulta con ANT caída" sobre la vista de componentes.
5. Añadir los enlaces de GitHub de la tabla anterior a cada objeto.
