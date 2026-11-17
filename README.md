# Consulta Vehicular

Proyecto de la materia **ISWZ2202 – Diseño y Arquitectura de Software**.

Aplicación web en la que un ciudadano ingresa su **correo** y su **RUC de persona natural** y obtiene, en un solo flujo:

1. Sus datos de contribuyente en el **SRI** (solo si es persona natural).
2. Los datos de un **vehículo** por placa (SRI – matriculación vehicular).
3. Los **puntos de su licencia** y sus **citaciones** en la **ANT**.

La ANT tiene muy baja disponibilidad, así que el servicio que la consulta combina una **caché en la nube (Redis)** con un **Circuit Breaker**.

---

## Arquitectura

```
                       ┌──────────────────────┐
  Navegador ──HTTPS──▶ │  Frontend (React)    │  Vercel
                       └──────────┬───────────┘
                                  │ JSON /api/consultas/**
                       ┌──────────▼───────────┐
                       │  api-gateway (BFF)   │  :8080
                       │  validación, CORS    │
                       └─────┬──────────┬─────┘
                    REST/JSON│          │REST/JSON
               ┌─────────────▼──┐   ┌───▼────────────────┐
               │  sri-service   │   │   ant-service      │
               │  :8081         │   │   :8082            │
               │  Spring Cache  │   │  Cache-Aside +     │
               │                │   │  Circuit Breaker   │
               └──┬─────────┬───┘   └───┬───────────┬────┘
        HTTPS/JSON│         │           │           │HTTPS/HTML
                  ▼         └──▶ Redis ◀┘           ▼
              SRI (REST)      (Upstash)        ANT (Portal web)
```

| Módulo | Puerto | Responsabilidad |
|---|---|---|
| `frontend/` | 5173 (dev) | SPA React 18 + Vite. Un solo flujo en 3 tarjetas. |
| `backend/api-gateway` | 8080 | BFF. Valida correo y RUC (`RucValidator`), enmascara el correo en los logs (no se persiste), llama a los microservicios con `RestClient` y devuelve los errores como `ProblemDetail` propagando el estado y el `detail` de cada microservicio. CORS configurable. |
| `backend/sri-service` | 8081 | Consulta el catastro (¿es contribuyente?, ¿persona natural?) y la base vehicular del SRI. `SriClient` con `RestClient` (timeout 8 s) y `@Cacheable` sobre Redis (TTL 1 h, valores `String`). Si Redis cae, un `CacheErrorHandler` solo registra el error. |
| `backend/ant-service` | 8082 | Consulta los puntos de licencia en la ANT (HTML) con el patrón de disponibilidad descrito abajo. |
| Redis (Upstash) | — | Caché compartida en la nube (`REDIS_URL=rediss://...`). |

Tecnologías: Java 21, Spring Boot 3.3.5, Maven multi-módulo (`groupId ec.udla.consulta`), Resilience4j 2.2 (Circuit Breaker programático), Jsoup 1.18, Spring Data Redis (Lettuce), React 18, Vite 5.

### Reglas de negocio

- **RUC** (gateway, `RucValidator`): 13 dígitos, termina en `001`, provincia `01`-`24` o `30`; si el tercer dígito es `< 6` los 10 primeros dígitos deben ser una cédula válida (módulo 10).
- **Persona natural** (sri-service): `personaSociedad = PNL` **o** `tipoContribuyente` contiene `NATURAL`; si el SRI no envía ninguno de los dos campos, se usa el tercer dígito del RUC (`< 6`). Si es sociedad se responde **422**.
- **Cédula** para la ANT: los 10 primeros dígitos del RUC.
- Las respuestas del SRI se manejan como `JsonNode`, por lo que campos nuevos o desconocidos no rompen nada.

---

## Patrón de disponibilidad de la ANT

Implementado en `ConsultaLicenciaService` (ant-service), sobre los puertos `AntClient` y `LicenciaCache`:

```
consultar(cedula)
 │
 ├─ a) Cache-Aside, ventana fresca ── ¿dato guardado hace < 5 min?  ── sí ─▶ fuente=CACHE, desactualizado=false
 │                                                                     (no se llama a la ANT)
 ├─ b) CircuitBreaker.executeSupplier( parser.parse( antClient.consultar() ) )
 │      · timeouts HTTP de 8 s
 │      · el PARSEO va dentro del breaker → HTML inválido cuenta como fallo
 │      · ventana 10 llamadas, mínimo 5, 50 % fallos, 30 s abierto, 2 llamadas half-open
 │
 ├─ c) éxito ─▶ guardar en Redis {datos, guardadoEn} con TTL 7 días ─▶ fuente=ANT
 │      (solo se guarda una respuesta adecuada: puntos, citaciones o "no registra citaciones")
 │
 └─ d) fallo (timeout, 5xx, circuito abierto, HTML inválido)
        ├─ hay caché  ─▶ fuente=CACHE, desactualizado=true, aviso con la fecha del dato
        └─ no hay     ─▶ 503 ProblemDetail
```

- `AntHtmlParser` es **heurístico y aislado**: si la ANT cambia su HTML, solo se modifica esa clase. Busca los puntos en elementos con `id/class/name` que contengan "punto" o en textos tipo "Puntos: 30" / "30 puntos" (fuera de la tabla de citaciones), y arma las citaciones a partir de tablas con encabezados `th` y filas `td`.
- `RedisLicenciaCache` es *best-effort*: si Redis falla se registra un aviso y el servicio continúa como si no hubiera caché.
- El reloj (`Clock`) se inyecta por constructor para poder probar la ventana fresca sin esperas.

Respuesta de `GET /api/ant/licencias/{cedula}`:

```json
{
  "datos": {
    "cedula": "1710034065",
    "puntos": 28.0,
    "citaciones": [ { "Placa": "PBA1234", "Fecha": "10/01/2026", "Valor": "$ 46,00" } ]
  },
  "fuente": "ANT",
  "consultadoEn": "2026-11-17T15:00:00Z",
  "desactualizado": false,
  "aviso": null
}
```

En el frontend se muestra un badge: **Dato en vivo (ANT)** (`fuente=ANT`), **Caché reciente** (`fuente=CACHE`, `desactualizado=false`) o **Caché desactualizada** (`desactualizado=true`).

---

## Ejecución local (Windows / PowerShell)

### Requisitos

- Java 21, Maven 3.9, Node 20+
- Un Redis: local (Docker) **o** en la nube (Upstash). Si no hay Redis, el sistema funciona igual pero sin caché.

### 1. Redis

Opción A – Redis local con Docker:

```powershell
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

Opción B – Upstash (nube): cree una base en <https://console.upstash.com>, copie la URL `rediss://...` y defínala en cada terminal de backend:

```powershell
$env:REDIS_URL = "rediss://default:<token>@<host>.upstash.io:6379"
```

### 2. Backend (tres terminales)

```powershell
cd backend
mvn clean install -DskipTests

# Terminal 1
mvn -pl sri-service spring-boot:run
# Terminal 2
mvn -pl ant-service spring-boot:run
# Terminal 3
mvn -pl api-gateway spring-boot:run
```

Pruebas unitarias (no levantan Spring ni Redis):

```powershell
cd backend
mvn test
```

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Abra <http://localhost:5173>. En desarrollo, Vite redirige `/api` a `http://localhost:8080`.

### Alternativa: todo con Docker Compose

```powershell
docker compose up --build
```

Levanta Redis + los 3 servicios. Para usar Upstash en lugar del contenedor, cree un `.env` en la raíz con `REDIS_URL=rediss://...` (ver `.env.example`).

---

## Variables de entorno

| Variable | Servicio | Valor por defecto | Descripción |
|---|---|---|---|
| `PORT` | todos | 8080 / 8081 / 8082 | Puerto HTTP (Render lo asigna automáticamente). |
| `REDIS_URL` | sri-service, ant-service | `redis://localhost:6379` | URL de Redis. Use `rediss://` (TLS) para Upstash. |
| `SRI_SERVICE_URL` | api-gateway | `http://localhost:8081` | URL interna del sri-service. |
| `ANT_SERVICE_URL` | api-gateway | `http://localhost:8082` | URL interna del ant-service. |
| `CORS_ALLOWED_ORIGINS` | api-gateway | `http://localhost:5173` | Orígenes permitidos, separados por coma (admite patrones como `https://*.vercel.app`). |
| `SRI_CATASTRO_URL` | sri-service | URL pública del SRI | Base del catastro (opcional). |
| `SRI_MATRICULACION_URL` | sri-service | URL pública del SRI | Base de matriculación vehicular (opcional). |
| `ANT_BASE_URL` | ant-service | URL pública de la ANT | Base del portal de la ANT (opcional). |
| `VITE_API_URL` | frontend (build) | vacío | URL pública del API Gateway en producción. |

Parámetros del patrón ANT (`ant-service/src/main/resources/application.yml`): `ant.timeout=8s`, `ant.cache.fresh-ttl=5m`, `ant.cache.stale-ttl=7d`.

---

## Endpoints

### API Gateway (público)

| Método | Ruta | Cuerpo / parámetros | Respuesta |
|---|---|---|---|
| POST | `/api/consultas/contribuyente` | `{"email":"...","ruc":"1710034065001"}` | `{ruc, cedula, contribuyente:{...}}` |
| GET | `/api/consultas/vehiculos/{placa}` | placa, CAMV o CPN | datos del vehículo (JSON del SRI) |
| GET | `/api/consultas/licencias/{cedula}` | cédula de 10 dígitos | `{datos, fuente, consultadoEn, desactualizado, aviso}` |

### Microservicios (internos)

| Servicio | Método | Ruta |
|---|---|---|
| sri-service | GET | `/api/sri/contribuyentes/{ruc}` |
| sri-service | GET | `/api/sri/vehiculos/{placa}` |
| ant-service | GET | `/api/ant/licencias/{cedula}` |

Todos exponen `/actuator/health`.

### Errores

Todos los errores son `ProblemDetail` (RFC 9457) con mensajes en español; el frontend muestra el campo `detail`.

| Estado | Caso |
|---|---|
| 400 | Correo, RUC, cédula o placa con formato inválido. |
| 404 | RUC no registrado en el SRI o vehículo inexistente. |
| 422 | El RUC corresponde a una sociedad. |
| 502 | El SRI respondió con error o con un formato no interpretable. |
| 503 | La ANT no está disponible y no hay caché, o un microservicio no responde. |

---

## Despliegue (Upstash + Render + Vercel)

1. **Upstash (Redis)**: cree una base Redis (región cercana a Render, p. ej. `us-east-1`) y copie la URL `rediss://default:<token>@<host>.upstash.io:6379`.
2. **Render (backend)**: cree tres *Web Services* desde este repositorio, tipo **Docker**:
   - *Root Directory*: `backend` · *Dockerfile Path*: `./Dockerfile`
   - Variable `MODULE=sri-service` (luego `ant-service` y `api-gateway`). Render expone las variables de entorno como *build args* de Docker, y el `Dockerfile` usa `ARG MODULE` para elegir el módulo.
   - Variables:
     - sri-service y ant-service: `REDIS_URL=rediss://...`
     - api-gateway: `SRI_SERVICE_URL=https://sri-service-xxxx.onrender.com`, `ANT_SERVICE_URL=https://ant-service-xxxx.onrender.com`, `CORS_ALLOWED_ORIGINS=https://<su-app>.vercel.app`
   - *Health Check Path*: `/actuator/health`
3. **Vercel (frontend)**: importe el repositorio con *Root Directory* `frontend`, *Framework* Vite, y defina `VITE_API_URL=https://api-gateway-xxxx.onrender.com`.

> En el plan gratuito de Render los servicios se duermen tras un periodo de inactividad; la primera consulta puede tardar ~1 minuto.

---

## Pruebas

`mvn test` en `backend/` ejecuta pruebas JUnit 5 que **no** levantan Spring ni Redis:

- **sri-service**: formato de RUC (400), no contribuyente (404), sociedad (422), persona natural OK (por `PNL`, por `tipoContribuyente`, por tercer dígito), placa inválida (400), vehículo OK / inexistente (404). Mockito sobre `SriClient`.
- **ant-service**: ANT OK guarda en caché; dato fresco no llama a la ANT; dato viejo se refresca; ANT caída → caché desactualizada; HTML inválido no pisa la caché; sin ANT ni caché → 503; circuito abierto no llama a la ANT; 5 fallos abren el circuito; parser (puntos, tabla `th/td`, "no registra citaciones", HTML vacío y página de mantenimiento inválidos).
- **api-gateway**: `RucValidator` (RUC válido, sufijo, provincia, provincia 30, cédula inválida, sociedad que pasa) y enmascarado del correo.

## Documentación de arquitectura

El modelo C4 para IcePanel está en [docs/C4-IcePanel.md](docs/C4-IcePanel.md).
