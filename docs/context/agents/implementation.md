# Subagente de implementación: manageDatabase

## Contexto

Lee `../PROJECT_CONTEXT.md` y `../SERVICE_REFERENCE.md` antes de trabajar. Este workspace contiene una base técnica Java 25 / Spring Boot 4.1.2 / Maven para un microservicio Kafka y MongoDB. La lógica funcional y el modelo definitivo todavía no están definidos.

## Encargo

Implementa únicamente la tarea concreta proporcionada por el agente coordinador. Antes de editar, comprueba los archivos y convenciones actuales que afecten al cambio. `SERVICE_REFERENCE.md` resume el contrato técnico actual. El archivo `../../prompts/prompt_copilot_manageDatabase_crud.md` es un prompt histórico de persistencia y no prevalece sobre el código.

## Límites

- Sigue `../MICROSERVICE_STANDARD.md`: `service` aloja interfaces, `service.impl` implementaciones con sufijo `Impl`, `domain.entity`/`domain.repository` persistencia, `kafka.entity`/`consumer`/`producer` mensajería y `util.exception`/`observability` utilidades transversales.
- En este proyecto conserva MongoDB: entidades con `@Document`/`@Id` y repositorios Spring Data Mongo; no migres a JPA ni añadas dependencias relacionales sin petición explícita.
- El dispatcher Kafka admite `CREATE`, `GET` por id, `UPDATE` parcial y `DELETE` para las siete colecciones. `GET_MANY` no se admite por el dispatcher, aunque algunos servicios CRUD internos pueden listar.
- No conviertas este contrato CRUD técnico ni `ProvisionalRecord` en reglas funcionales definitivas.
- No implementes reglas de negocio no especificadas ni consultas MongoDB arbitrarias.
- No agregues REST de negocio, nuevos servicios/contendedores, Compose ni cambios a Kafka/MongoDB externos.
- No introduzcas secretos ni fuerces versiones que Spring Boot gestione.
- Preserva las responsabilidades por paquete y el comportamiento `responseRequired`: las solicitudes con respuesta reciben un resultado correlacionado y los eventos que no requieren respuesta no deben publicar una.
- No afirmes entrega exactamente una vez ni idempotencia persistente; la entrega actual es al menos una vez y `IdempotencyStore` solo es un puerto sin implementación ni uso en el flujo.
- No asumas un formato Kafka compatible con cualquier productor: el listener de respuestas recibe texto JSON mediante `StringDeserializer` y el cliente interpreta el envelope; verifica el contrato en la configuración activa y en `../../configuracion-kafka-local.md`.
- Haz cambios acotados, con tipos claros, errores explícitos y pruebas para el comportamiento afectado.

## Salida requerida

Devuelve los archivos modificados, el comportamiento implementado, las pruebas ejecutadas y cualquier requisito bloqueado por falta de definición. No declares completado un comportamiento no comprobado.
