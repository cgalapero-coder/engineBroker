# Referencia de integración: `manageDatabase`

Referencia portátil para agentes que necesiten integrar otro microservicio con esta aplicación. Describe lo que implementa el código actual; no convierte el CRUD técnico en un contrato de negocio estable.

## Identidad y dependencias

| Aspecto | Estado actual |
|---|---|
| Nombre Spring | `manageDatabase` |
| Paquete raíz | `com.gtech.automaticbroker.managedatabase` |
| Java / Spring Boot | Java 25 (LTS) / Spring Boot 4.1.2 |
| Build | Maven Wrapper 3.9.15 (`.\mvnw.cmd` en Windows) |
| Persistencia | MongoDB con Spring Data MongoDB y `MongoTemplate` |
| Mensajería | Kafka con Spring Kafka |
| Configuración de secretos | Externa por variables de entorno; no copiar valores de desarrollo a otros entornos |
| Transporte REST | Actuator técnico, sin API REST de negocio |

Las versiones de dependencias transitivas las gestiona el parent/BOM de Spring Boot; consulta `pom.xml` antes de cambiar versiones.

## Componentes y dirección de dependencias

```text
Kafka request topic
  -> KafkaRequestListener (decode + correlación)
  -> OperationService / OperationServiceImpl (validación y routing)
  -> servicios CRUD de service.impl
  -> domain.repository (MongoTemplate / MongoDB)
  -> respuesta opcional: KafkaResponsePublisher -> Kafka response topic
```

Paquetes:

| Paquete | Responsabilidad |
|---|---|
| `config` | `KafkaConfiguration`, `MessagingProperties` y ajustes de infraestructura |
| `service` | Contratos/tipos de servicio, comandos y resultados |
| `service.impl` | Implementaciones CRUD y dispatcher `OperationServiceImpl` |
| `domain.entity` | `CrudDocument` y documentos persistidos |
| `domain.repository` | Contratos/adaptadores de almacenamiento MongoDB y puerto no implementado `IdempotencyStore` |
| `kafka.entity` | Sobres request/response, enums y codec JSON |
| `kafka.consumer` | Listener Kafka que valida/adapta e invoca el servicio |
| `kafka.producer` | Publicador de respuestas |
| `util.exception` | Errores tipados |
| `util.observability` | Health indicator Kafka y métricas |

Usa inyección por constructor. Mantén la lógica de negocio en servicios, los adaptadores de mensajería fuera de persistencia y el acceso a datos detrás de `domain.repository`.

## Configuración Kafka

### Topics y variables de aplicación

Se leen desde `app.messaging` en `application.yml` y se pueden sustituir por entorno:

| Propiedad | Variable | Predeterminado |
|---|---|---|
| `request-topic` | `KAFKA_REQUEST_TOPIC` | `manageDatabase.requests` |
| `response-topic` | `KAFKA_RESPONSE_TOPIC` | `manageDatabase.responses` |
| `dead-letter-suffix` | `KAFKA_DLT_SUFFIX` | `.DLT` |
| `max-retries` | `KAFKA_MAX_RETRIES` | `2` |
| `retry-interval-ms` | `KAFKA_RETRY_INTERVAL_MS` | `1000` |
| `response-delivery-timeout` | `KAFKA_RESPONSE_DELIVERY_TIMEOUT` | `5s` |

`spring.kafka.bootstrap-servers` viene de `KAFKA_BOOTSTRAP_SERVERS` y por defecto es `localhost:9092`. El grupo base es `KAFKA_CONSUMER_GROUP` / `manageDatabase`. El listener se enlaza al topic de petición. En la configuración base, consumidor y productor usan `StringDeserializer` / `StringSerializer`; el listener recibe el mensaje como `String` y `KafkaMessageCodec` lo convierte desde JSON.

`application-local.yml` cambia el bootstrap a `localhost:9092`, grupo a `${spring.application.name}-local` y usa `JsonSerializer` / `JsonDeserializer` para valores, con `acks: all` en el productor. El consumidor limita los paquetes JSON de confianza al paquete de entidades Kafka. Esta diferencia está presente en la configuración actual; el formato en el cable debe verificarse con el productor externo antes de acoplar servicios: consultar [`../configuracion-kafka-local.md`](../configuracion-kafka-local.md).

La configuración común usa `enable-auto-commit=false` y `ack-mode=record`. El error handler configura backoff fijo (`retry-interval-ms`, `max-retries`) y envía el registro que agota reintentos a `<topic de origen><dead-letter-suffix>`, conservando el número de partición. Por ejemplo, `manageDatabase.requests.DLT`. Asegurar que el topic DLT externo tiene particiones compatibles con el topic de entrada; el repositorio no crea ni aprovisiona topics.

### Contrato temporal de petición

El mensaje es un JSON con campos requeridos:

```json
{
  "messageId": "id-unico-de-la-solicitud",
  "correlationId": "id-de-correlacion",
  "operation": "GET",
  "payload": {
    "collection": "biData",
    "id": "bi-123"
  },
  "responseRequired": true
}
```

- `messageId` y `correlationId` deben ser strings no vacíos; `operation`, `payload` y `responseRequired` deben existir.
- Valores reconocidos por el enum: `CREATE`, `GET`, `UPDATE`, `DELETE`, `GET_MANY`. El dispatcher implementa `CREATE`, `GET`, `UPDATE` y `DELETE`; `GET_MANY` se rechaza como no soportado.
- Todos los payloads del dispatcher deben ser objetos JSON e incluir `collection` con un nombre exacto de la tabla de colecciones de abajo.
- `CREATE`: `{"collection":"biData","document":{"sourceName":"fuente"}}`. `id` puede venir en el documento; si falta o está en blanco, el servicio genera un UUID.
- `GET`: `{"collection":"biData","id":"bi-123"}`.
- `UPDATE`: `{"collection":"biData","id":"bi-123","changes":{"sourceName":"actualizado"}}`. `changes` debe ser objeto, solo puede contener campos existentes de la entidad y no puede actualizar `id` ni `_id`; los campos no enviados permanecen sin cambios.
- `DELETE`: `{"collection":"biData","id":"bi-123"}`. El resultado es `true` si borró y `false` si no había documento.
- Las operaciones CRUD no aceptan claves `password`; se rechazan al crear/actualizar y las claves de ese nombre se limpian recursivamente de los documentos devueltos por los servicios.

### Respuesta

Solo se publica si `responseRequired` es `true`. El publicador usa `correlationId` como clave Kafka y espera la confirmación hasta `response-delivery-timeout`.

```json
{
  "messageId": "id-nuevo-de-respuesta",
  "requestId": "id-unico-de-la-solicitud",
  "correlationId": "id-de-correlacion",
  "operation": "GET",
  "status": "SUCCESS",
  "result": {"id": "bi-123", "sourceName": "fuente"},
  "error": null,
  "occurredAt": "2026-01-01T00:00:00Z"
}
```

Los estados de respuesta son `SUCCESS`, `NOT_IMPLEMENTED`, `REJECTED` y `FAILED`. El rechazo funcional usa `error.category=FUNCTIONAL`, `code` y `message`; errores de validación, colección/operación no soportada, duplicado o documento no encontrado se convierten en rechazo. Las excepciones técnicas que escapan del servicio se propagan para que el handler Kafka reintente y, al agotar reintentos, las enrute al DLT. La opción sin respuesta no genera sobre de error al emisor.

**Garantía:** entrega al menos una vez, no exactly-once. El `messageId` es identificador del mensaje, no prueba de deduplicación. `IdempotencyStore` declara un contrato para reservar/completar/liberar IDs, pero solo existe como interfaz: no hay adaptador ni llamada desde el flujo actual. No hay transacción distribuida MongoDB-Kafka implementada.

## Configuración MongoDB

`application.yml` da estos valores base:

| Ajuste | Variable | Predeterminado |
|---|---|---|
| Host | `MONGODB_HOST` | `localhost` |
| Puerto | `MONGODB_PORT` | `27017` |
| Base de datos | `MONGODB_DATABASE` | `microservices` |
| Usuario | `MONGODB_USERNAME` | No definido en base |
| Contraseña | `MONGODB_PASSWORD` | No definida en base |
| Base de autenticación | `MONGODB_AUTH_DATABASE` | Configurada por perfil |
| Colección provisional | `MONGODB_EXAMPLE_COLLECTION` | `provisional_records` |

El perfil predeterminado es `local`. `application-local.yml` configura usuario `devuser`, base de autenticación `automaticBroker` y un fallback de contraseña local de desarrollo en YAML; `MONGODB_PASSWORD` lo sustituye. No reutilizar ni propagar ese fallback. La base sigue siendo `microservices`, salvo override. `application-prod.yml` requiere `MONGODB_USERNAME` y `MONGODB_PASSWORD`, y usa `MONGODB_AUTH_DATABASE` con fallback `admin`. En Docker, las direcciones deben ser nombres DNS de red/servicio y no `localhost`.

`application-test.yml` configura base `microservices_test`, topic `test.requests` / `test.responses`, sufijo `.DLT`, cero reintentos y tiempo de entrega `1s`. Las pruebas con Testcontainers sobrescriben host, puerto y algunas bases de datos.

La aplicación no declara creación de índices funcionales ni scripts de migración de esquema. MongoDB conserva el índice inherente de `_id`; no se deben asumir unicidades funcionales en email, URL u otros campos.

## Colecciones y campos Java actuales

Todas las entidades extienden `CrudDocument`, que declara `String id` con `@Id`; Spring Data Mongo lo mapea a `_id`. Las propiedades escalares listadas son `String`; las marcadas `Object` aceptan datos flexibles (por ejemplo, objetos o arrays). Esta tabla describe el modelo Java presente, no un esquema de dominio validado:

| Nombre exacto en Mongo/Kafka | Clase | Campos escalares `String` | Campos flexibles `Object` |
|---|---|---|---|
| `manageUserData` | `ManageUserData` | `dni`, `firstName`, `lastName`, `email`, `phone`, `postalAddress`, `country` | `appRoles`, `values`, `access`, `operationsSummary`, `preferences`, `decisions`, `bankAccounts`, `brokers`, `contractedServices` |
| `biData` | `BiData` | `sourceId`, `sourceName`, `market`, `instrument`, `dataType`, `value`, `unit`, `currency`, `observedAt`, `collectedAt`, `description` | — |
| `histData` | `HistData` | `actorType`, `userId`, `userRole`, `eventType`, `operationId`, `operation`, `occurredAt`, `result`, `description` | `simulationData` |
| `stadisticData` | `StadisticData` | `scope`, `userId`, `metric`, `value`, `unit`, `period`, `periodStart`, `periodEnd`, `calculatedAt`, `description` | — |
| `webData` | `WebData` | `url`, `date`, `type`, `description`, `enabled` | `auth`, `operations`, `roles` |
| `opData` | `OpData` | `description`, `prompt` | `limitations`, `roles` |
| `globalConfiguration` | `GlobalConfiguration` | `mode`, `logLevel`, `language` | `selectedOptions`, `dataToStore`, `operationalLimits`, `otherParameters` |

`ProvisionalRecord` es un record separado, documentado como temporal y mapeado a `${app.persistence.example-collection:provisional_records}`. No está en el mapa de colecciones de `OperationServiceImpl`; no tratarlo como parte de las siete colecciones Automatic Broker.

## Salud, métricas e imagen

- Actuator web expone en base `health`, `info`, `metrics`; `management.endpoint.health.show-details=never` y probes liveness/readiness están activados. El perfil `prod` restringe la exposición a `health`.
- Spring Boot aporta la comprobación MongoDB. `KafkaHealthIndicator` consulta el clúster con timeout `management.health.kafka.timeout` (3 segundos por defecto).
- `ProcessingMetrics` registra `microservice.kafka.requests` con `outcome=consumed|processed|failed` y `microservice.kafka.responses` con `outcome=published`.
- El `Dockerfile` tiene etapa Maven + JDK 21, empaqueta el JAR y ejecuta con JRE 21 como usuario no privilegiado. No incluye Docker Compose ni aprovisionamiento de dependencias externas.

## Pruebas y ejecución

```powershell
# Unitarias; no dependen de Kafka/Mongo locales
.\mvnw.cmd test

# Mongo con Testcontainers + workflow Kafka CRUD con Embedded Kafka
.\mvnw.cmd -Dintegration.tests.enabled=true test

# Conectividad explícita con broker local existente; topic dev.test.events debe existir
.\mvnw.cmd -Dkafka.integration.enabled=true -Dtest=KafkaConnectivityIntegrationTest test

# Incluye chequeo de cobertura JaCoCo (líneas del bundle >= 85 %)
.\mvnw.cmd verify
```

Las pruebas Testcontainers se omiten cuando Docker no está disponible. Las pruebas relevantes incluyen servicios/repositorios CRUD sobre las siete colecciones y un flujo Kafka `CREATE` → `UPDATE` → `GET` → `DELETE` por cada colección.

## Decisiones aún pendientes antes de ampliar el contrato

1. Definir los casos de uso/reglas de negocio y si otros servicios deben invocar este CRUD genérico o si se deben añadir comandos/eventos de dominio.
2. Versionar y formalizar el contrato de envelopes, el formato de serialización y compatibilidad entre productores, consumer y perfiles.
3. Definir validaciones/tipos de campos, esquema de objetos anidados, privacidad y tratamiento de credenciales.
4. Acordar idempotencia durable, reintentos para errores funcionales/técnicos, retención/operación de DLT y requisitos de entrega.
5. Especificar índices, consultas, retención y migraciones de Mongo solo a partir de cargas de trabajo confirmadas.
