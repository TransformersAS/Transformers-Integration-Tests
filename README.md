# Marketplace Transformers - Integration Tests

Este repositorio contiene las pruebas de integracion contra el backend real de
Marketplace Transformers. El backend es el sistema bajo prueba y vive en el
repositorio [Transformers-AS](https://github.com/TransformersAS/Transformers-AS).

## Que va aqui

- Pruebas ejecutables que llaman al backend mediante HTTP.
- Configuracion minima compartida por las pruebas.
- Evidencia de las ejecuciones de integracion.

## Que no va aqui

No se copian controllers, services, repositories, entidades ni otra logica del
backend. Tampoco se implementan login, carrito, productos u otros casos de uso
en este repositorio. No hay `src/main/java` porque el harness no necesita codigo
de produccion propio.

Las pruebas unitarias del repositorio principal verifican clases internas del
backend de forma aislada. Estas pruebas viven separadas fisicamente y verifican
el sistema desplegado a traves de sus interfaces observables.

## Requisitos

- Java 21.
- Docker con Compose v2 para levantar el backend local.
- No hace falta instalar Maven: se usa Maven Wrapper.

## Ejecucion local

Primero, en el repositorio principal:

```bash
cd /ruta/Transformers-AS
cp .env.example .env
# Reemplazar los valores de contrasena de .env por valores locales.
docker compose up -d --build --wait
```

Despues, en este repositorio:

```bash
cd /ruta/Transformers-Integration-Tests
./mvnw verify
```

Por defecto las pruebas llaman a `http://localhost:8080`. Para otro entorno,
configurar `BACKEND_BASE_URL` sin cambiar el codigo:

```bash
BACKEND_BASE_URL=https://backend.example.test ./mvnw verify
```

`HealthIntegrationTest` es una prueba del harness: comprueba que puede
comunicarse con el backend mediante `GET /actuator/health`, esperando HTTP 200
y `status == "UP"`. No representa cobertura funcional de un caso de uso.

### Variables

| Variable | Valor por defecto | Para que sirve |
| --- | --- | --- |
| `BACKEND_BASE_URL` | `http://localhost:8080` | Backend contra el que se ejecutan las pruebas. |
| `E2E_EMAIL` | `demo@marketplace.local` | Cuenta del perfil local con la que se inicia sesion. |
| `E2E_PASSWORD` | `MarketplaceDemo123!` | Contrasena de esa cuenta. |
| `LOGISTICS_WEBHOOK_SECRET` | sin valor | El mismo con el que arranco el backend. Las pruebas de seguimiento logistico firman con el las novedades del proveedor; sin el, el webhook queda cerrado y esas pruebas fallan al arrancar. |

### Casos de uso cubiertos

- CU-23 preparar y despachar pedidos recibidos: `orders/SellerFulfillmentIntegrationTest`.
- CU-24 seguimiento logistico de pedidos: `logistics/OrderTrackingIntegrationTest`.
- CU-25 seguimiento logistico de devoluciones: `logistics/ReturnTrackingIntegrationTest`.
- Firma y contrato del webhook logistico: `logistics/LogisticsWebhookIntegrationTest`.

El detalle de cada prueba esta en [docs/pruebas-cu23-cu24-cu25.md](docs/pruebas-cu23-cu24-cu25.md).
La configuracion compartida vive en `support/`: `MarketplaceClient` (sesion y CSRF),
`MarketplaceScenario` (las dos sesiones y el proveedor), `Purchases`, `Returns` y
`LogisticsProvider` (firma HMAC de las novedades).

## E2E con Playwright

Con el Marketplace levantado externamente en `http://localhost:4300`:

```bash
npm ci
npx playwright install chromium
LOGISTICS_WEBHOOK_SECRET='el-mismo-del-backend' npm run test:e2e
```

Para apuntar a otro frontend, configurar `FRONTEND_BASE_URL`:

```bash
FRONTEND_BASE_URL=https://frontend.example.test npm run test:e2e
```

Las pruebas corren con un solo trabajador (`workers: 1`): el backend comparte un
unico carrito y una unica cuenta demo entre compradores, asi que dos recorridos
de compra a la vez se pisarian.

Si el frontend se sirve en un puerto que no sea 4300, el backend tiene que
aceptarlo como origen (`APP_CORS_ALLOWED_ORIGINS`): el proxy reenvia el `Origin`
del navegador y el backend rechaza con 403 `Invalid CORS request` los que no
estan en su lista.

Recorridos E2E existentes:

- Sofia: disponibilidad inicial, compra con solicitud de cancelacion y seguridad
  de cuenta ([docs/pruebas-e2e-sofia.md](docs/pruebas-e2e-sofia.md)).
- Pacho: despacho y seguimiento del envio, y seguimiento del retorno de una
  devolucion ([docs/pruebas-cu23-cu24-cu25.md](docs/pruebas-cu23-cu24-cu25.md)).

## Agregar un caso de uso futuro

Agregar cada prueba cuando exista el primer caso de uso real del dominio y
organizar los paquetes por contexto, por ejemplo:

```text
src/test/java/com/transformersas/integration/
  auth/
  catalog/
  orders/
  payments/
```

Cada prueba debe comprobar lo que corresponda del contrato externo: HTTP,
resultado de negocio, persistencia observable e integraciones externas
simuladas. No debe duplicar la logica interna del backend.

## CI

El workflow de GitHub Actions hace checkout de este repositorio y del repositorio
publico `TransformersAS/Transformers-AS` en una carpeta separada. Usa Java 21,
reutiliza el `compose.yaml` del backend para construir y levantar MySQL y la
aplicacion, espera `/actuator/health`, ejecuta `./mvnw verify` y conserva los
reportes de Surefire como artifact. El backend queda fijado a un commit concreto
de `main`, no a una rama movil: al agregar pruebas de un caso de uso nuevo hay
que mover ese `ref` a un commit que ya lo incluya. El job define
`LOGISTICS_WEBHOOK_SECRET` para ese entorno efimero y lo agrega al `.env` del
Compose, porque el webhook logistico queda cerrado sin secreto. Al terminar, baja los servicios y elimina
los volumenes temporales. No duplica Dockerfiles ni publica imagenes.

## Cobertura

Este repositorio no mide automaticamente cobertura del codigo de produccion con
JaCoCo. Las pruebas estan separadas del proceso del backend. Queda pendiente
definir una estrategia real para instrumentar el proceso backend con el agente
de JaCoCo y generar el reporte usando sus class files y fuentes. No se agrega
una configuracion que produzca un porcentaje enganoso.

Los casos de uso se agregaran uno por uno cuando exista su implementacion y su
contrato verificable en el backend.
