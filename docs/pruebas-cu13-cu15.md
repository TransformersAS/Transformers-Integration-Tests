# Pruebas de integración de CU-13 y CU-15 (Alejandro)

Este documento describe las pruebas que Alejandro implementó en este repositorio para los dos casos de uso que
trabajó, ambos de complejidad Alta: CU-13 «Tramitar una reclamación de compra» y CU-15 «Controlar el inventario y el
reabastecimiento» (incluida la carga masiva con plantillas de Excel). El alcance corresponde a las tres clases Java
que agregó y no representa la totalidad de los casos de uso del equipo.

## 1. Objetivo

Verificar sobre el sistema desplegado —backend real, MySQL real— el contrato externo de estos dos casos de uso:

- que el comprador pueda abrir una reclamación sobre una compra real, que el vendedor de la tienda pueda pedir
  información o proponer una solución, y que el comprador pueda aceptarla o escalarla sin acuerdo;
- que un reembolso propuesto nunca pueda superar lo pagado por el producto;
- que el vendedor pueda consultar sus existencias, registrar entradas y ajustes de inventario (con su historial),
  configurar un nivel mínimo y ver la alerta de stock bajo encenderse y apagarse;
- que las dos plantillas de Excel se descarguen como archivos reales y que una carga con una fila inválida rechace
  el archivo completo, indicando la fila con el error (contrato «todo o nada»).

Las pruebas usan JUnit 5 y REST Assured, igual que el resto de este repositorio.

## 2. Arquitectura de pruebas

```text
Transformers-AS (sistema bajo prueba)
  MySQL ← backend Spring Boot

Transformers-Integration-Tests (pruebas)
  REST Assured → /api → backend real → MySQL
```

Todo pasa por la API pública, con las mismas llamadas que haría el frontend: sesión, carrito, pago, reclamaciones e
inventario. Ninguna prueba toca la base de datos ni usa clases del backend.

## 3. Configuración

Usa las mismas variables que el resto del repositorio (`BACKEND_BASE_URL`, `E2E_EMAIL`, `E2E_PASSWORD`; ver
[README.md](../README.md)). No agrega ninguna variable nueva.

## 4. Configuración compartida agregada

| Clase | Qué aporta |
| --- | --- |
| `support/MinimalXlsx` | Construye un `.xlsx` mínimo (encabezados + filas de texto) con `java.util.zip`, sin depender de ninguna librería de Excel: alcanza para las cargas que se suben en las pruebas. |
| `MarketplaceClient.postFile(...)` | Envía un `POST` multipart (archivo adjunto) con la cookie de sesión y el token CSRF de la sesión, igual que hace el panel al subir un Excel. |

Reutiliza `MarketplaceScenario` (sesiones de comprador y vendedor) y `Purchases` (compra del producto demo) ya
existentes.

## 5. Pruebas Java

Son 16 pruebas en tres clases, además de las que ya existían.

### 5.1 CU-13 — `claims/ClaimIntegrationTest` (5 pruebas)

- El comprador abre una reclamación sobre una compra real, el vendedor pide información, el comprador responde, el
  vendedor propone una solución con reembolso y el comprador la acepta: la reclamación termina `RESOLVED` /
  `SOLUTION_ACCEPTED`.
- Sin acuerdo, el comprador escala la reclamación: queda `ESCALATED`.
- Un reembolso propuesto por encima de lo pagado por el producto se rechaza con 400.
- El vendedor solo atiende las reclamaciones de su propia tienda (403 `STORE_NOT_AUTHORIZED` con otra tienda; 404
  con una reclamación inexistente).
- Solo un comprador abre reclamaciones (403 con el rol vendedor activo) y solo soporte entra a su cola (403 con el
  rol comprador).

**Límite conocido:** el perfil local no aprovisiona ninguna cuenta con el rol SOPORTE (solo la cuenta demo, con
COMPRADOR y VENDEDOR), así que la decisión de una reclamación escalada por un agente de soporte
(`POST /api/support/claims/{id}/resolve`) no se puede ejercer por HTTP con este harness. Solo se comprueba que la
cola de soporte rechaza a quien no tiene ese rol.

### 5.2 CU-15 — `stock/SellerInventoryIntegrationTest` (5 pruebas)

- Las existencias del producto demo muestran físico, reservado y disponible.
- Una entrada suma unidades y queda como el primer movimiento del historial.
- Un ajuste fija el conteo real, exige un motivo y el intento sin motivo se rechaza con 400.
- Un mínimo por encima del stock enciende `lowStock`; puesto en 0, se apaga.
- Solo un vendedor con el rol activo, dueño de su tienda, usa el inventario (403 sin el rol, 403 con otra tienda,
  401 sin sesión).

Todas las mutaciones sobre el producto demo son **aditivas** o se **revierten al final de su propio método**: otras
clases de este repositorio lo compran repetidamente, así que ninguna prueba puede dejarlo con menos stock del que
tenía al empezar, ni con un mínimo distinto de 0.

### 5.3 CU-15 — `stock/SellerExcelIntegrationTest` (6 pruebas)

- Las dos plantillas (productos e inventario) se descargan con el tipo de contenido y el nombre de archivo
  correctos, y el cuerpo es un `.zip` real (firma `PK`), como todo `.xlsx`.
- Una fila con una categoría inexistente rechaza el archivo completo, con el número de fila y el motivo.
- Un archivo que no es un Excel real, uno con los encabezados equivocados y uno sin ninguna fila de datos se
  rechazan, cada uno con su propio código de error.
- Solo un vendedor con el rol activo usa las plantillas y las cargas.

**Límite conocido:** el perfil local no aprovisiona ninguna categoría activa (las crea el rol ADMIN, y este entorno
no tiene una cuenta demo con ese rol), así que la creación **exitosa** de un producto nuevo por Excel no se prueba
aquí: cualquier fila válida en todo lo demás fallaría igual por falta de una categoría real. Se prueba en cambio el
contrato de rechazo «todo o nada», que no depende de ningún dato sembrado y es, de hecho, la parte más importante de
verificar externamente: que un archivo con un solo error no deje nada guardado.

## 6. Ejecución local

```bash
# Primero, en el repositorio principal
cd /ruta/Transformers-AS
docker compose up -d --build --wait

# Después, en este repositorio
cd /ruta/Transformers-Integration-Tests
./mvnw test -Dtest=ClaimIntegrationTest,SellerInventoryIntegrationTest,SellerExcelIntegrationTest
```

## 7. Alcance

Estas pruebas cubren el contrato HTTP de CU-13 y CU-15 tal como quedó en `main` de `Transformers-AS`. No sustituyen
las pruebas unitarias y de integración del propio backend (`ClaimTests`, `StockControlTests`, `SellerExcelTests`),
que verifican las reglas internas con datos que aquí no se pueden sembrar (una segunda tienda, categorías activas, un
agente de soporte). Los límites de esta capa están anotados en cada sección anterior, en vez de fingir una cobertura
que el entorno local no permite ejercer.
