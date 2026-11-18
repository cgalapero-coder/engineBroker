# Contexto del proyecto `manageDatabase`

## Para qué sirve este documento

Este archivo resume el estado técnico comprobado del microservicio para agentes que deban mantenerlo o integrarse con él. El detalle del contrato Kafka, la configuración por perfil, las colecciones MongoDB y las pruebas está en [`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md). El código y `src/main/resources` son la fuente de verdad si discrepan con esta exportación.

## Propósito y límites actuales

`manageDatabase` es un microservicio Java/Spring Boot que consume solicitudes Kafka, enruta operaciones CRUD técnicas a servicios MongoDB y puede publicar respuestas correlacionadas. No contiene todavía reglas de negocio confirmadas ni expone API REST de negocio. Sus operaciones sobre las siete colecciones Automatic Broker son scaffolding técnico y no deben interpretarse como el contrato de negocio definitivo de los microservicios que lo usen.

No confundir el documento temporal `ProvisionalRecord` / colección `provisional_records` con las siete colecciones CRUD. Tampoco inferir reglas de negocio, autorizaciones, índices, semántica de campos u obligaciones de privacidad de los nombres de las propiedades. Los campos anidados cuyo tipo Java es `Object` son flexibles y su esquema sigue sin estar definido.

## Stack y dependencias

- Java 25 (LTS), Maven Wrapper (usar `.\mvnw.cmd` en Windows) y Spring Boot 4.1.2 según `pom.xml`.
- Spring Data MongoDB y Spring Kafka, cuyas versiones las gestiona el parent/BOM de Spring Boot; no fijar versiones transitivas manualmente.
- Actuator para salud y métricas.
- Pruebas: JUnit 5, Mockito/AssertJ y Spring Boot Test; integración Mongo con Testcontainers MongoDB 1.21.3. La prueba Kafka de flujo CRUD usa broker Kafka embebido.
- Lombok figura como dependencia opcional; el código actual usa principalmente records e inyección por constructor.
- Kafka y MongoDB son servicios externos al despliegue de aplicación. No crear ni cambiar brokers, bases de datos, contenedores o infraestructura sin petición explícita.

Confirma siempre las versiones vigentes en `pom.xml` antes de modificar el proyecto.

## Arquitectura y paquetes

Paquete raíz: `com.gtech.automaticbroker.managedatabase`.

```text
Application.java                  Arranque Spring Boot y propiedades
config/                            Configuración Kafka y propiedades tipadas
service/                           Contratos CRUD, casos de uso y tipos de aplicación
  impl/                            Servicios CRUD y enrutamiento OperationServiceImpl
domain/
  entity/                          CrudDocument y documentos MongoDB
  repository/                      Puertos/adaptadores MongoDB y repositorio provisional
kafka/
  entity/                          Envelopes, enums y codec JSON
  consumer/                        KafkaRequestListener
  producer/                        KafkaResponsePublisher
util/
  exception/                       Excepciones explícitas de dominio/operación
  observability/                   Indicador de salud Kafka y métricas de proceso
```

El listener adapta transporte y delega en `OperationService`; `OperationServiceImpl` valida y enruta a siete servicios CRUD. Las implementaciones de servicios están desacopladas del listener. Los adaptadores CRUD principales usan `MongoTemplate` a través de `AbstractMongoCrudStore`; `ProvisionalRecord` conserva además un adaptador basado en `MongoRepository`. No añadir JPA ni acoplar los servicios a Kafka o HTTP.

## Decisiones, garantías y asuntos no resueltos

- MongoDB es el almacén implementado. Los documentos CRUD usan `@Document`, comparten `CrudDocument` con `@Id String id` (persistido como `_id`) y el generador central `UuidDocumentIdGenerator` asigna UUID si falta el id.
- El CRUD de servicio permite crear, leer por id, listar, actualizar parcialmente y borrar. El adaptador de aplicación Kafka expone temporalmente `CREATE`, `GET`, `UPDATE` y `DELETE`; `GET_MANY` no está soportado por `OperationServiceImpl`. `DELETE` devuelve booleano, incluso `false` cuando el documento no existía.
- `UPDATE` aplica solo los campos enviados y protege `id` / `_id`; los campos no enviados se conservan. Se rechazan campos desconocidos. No se declaran índices funcionales ni migraciones de esquema en las entidades.
- Se rechazan claves `password` en documentos/cambios y se eliminan recursivamente de los datos devueltos; no se ha acordado un modelo de credenciales para el dominio.
- El listener solo publica una respuesta cuando `responseRequired=true`. La entrega Kafka es al menos una vez, con confirmación por registro, reintentos fijos y enrutamiento a DLT; no hay garantía exactly-once. `IdempotencyStore` solo existe como interfaz/puerto: no hay implementación, uso ni deduplicación persistente activa.
- No afirmar que existen transacciones distribuidas entre Kafka y MongoDB. Si una futura regla de negocio necesita atomicidad entre guardar y emitir eventos, acordar primero el patrón y sus garantías (por ejemplo, outbox/inbox).
- Permanecen abiertos: límites y reglas de negocio, versionado del contrato, tipado de los campos flexibles, índices/consultas de negocio, política de idempotencia, seguridad/autorización y compatibilidad definitiva del formato de serialización Kafka.
- El perfil `local` usa `JsonSerializer`/`JsonDeserializer`, mientras que el listener de negocio recibe `String` y el perfil base configura serializadores/deserializadores de String. Antes de que productores externos dependan de ello, acordar y probar el formato real del mensaje; [`configuracion-kafka-local.md`](../configuracion-kafka-local.md) describe la discrepancia conocida.

## Operación y pruebas

La aplicación arranca con perfil predeterminado `local`. Los valores de base, host, topics, grupo consumidor, reintentos, DLT y exposición Actuator se documentan junto con sus perfiles y variables en [`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md). Actuator publica `health`, `info` y `metrics`; el endpoint health no muestra detalles, y Kafka tiene un `HealthIndicator` propio.

Comandos usuales desde la raíz en PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Dintegration.tests.enabled=true test
.\mvnw.cmd -Dkafka.integration.enabled=true -Dtest=KafkaConnectivityIntegrationTest test
.\mvnw.cmd verify
```

`test` ejecuta pruebas unitarias sin necesitar servicios locales. La propiedad `integration.tests.enabled=true` activa pruebas de integración que requieren Docker: MongoDB 8 mediante Testcontainers y, para el flujo Kafka CRUD, Kafka embebido. La prueba separada `KafkaConnectivityIntegrationTest` usa el broker externo configurado en el perfil `local` y necesita el topic `dev.test.events` existente. `verify` ejecuta la comprobación JaCoCo del mínimo de 85 % de cobertura de líneas del bundle.

## Fuentes y referencias

- [`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md): referencia técnica expandida para integrar el servicio.
- [`MICROSERVICE_STANDARD.md`](MICROSERVICE_STANDARD.md): estándar de paquetes/prácticas reutilizable, con límites para este proyecto.
- [`../configuracion-kafka-local.md`](../configuracion-kafka-local.md): perfil Kafka local y su advertencia de serialización.
- [`../prompts/prompt_copilot_manageDatabase_crud.md`](../prompts/prompt_copilot_manageDatabase_crud.md): prompt histórico de implementación CRUD; no es la fuente de verdad del estado actual.
- [`../../README.md`](../../README.md): instrucciones y ejemplos del repositorio; comprobarlo contra código porque puede evolucionar independientemente.
