# Estándar de microservicios de negocio EDA con persistencia en BBDD

Este estándar se aplica a microservicios Java cuyo flujo de negocio se integra mediante eventos o mensajes y que persisten su estado en una base de datos. Adáptalo al stack de persistencia existente; no migres tecnología ni inventes contratos de negocio como parte de una reorganización de paquetes.

## Estructura requerida

```text
src/main/java/<paquete-base>/
  Application.java
  config/
  service/
    impl/
  domain/
    repository/
    entity/
  kafka/
    entity/
    consumer/
    producer/
  util/
    exception/
    observability/
```

- **Paquete raíz:** `Application.java` aloja el `main` y la configuración de arranque.
- **`config`:** configuración transversal/técnica, propiedades externas, clientes y configuración Kafka/BBDD. Si hacen falta subpaquetes de configuración, cuelgan de aquí.
- **`service`:** interfaces de servicio/casos de uso y la lógica de negocio expresada por contratos. Las implementaciones viven en `service.impl`.
- **`service.impl`:** cada implementación conserva el nombre de su interfaz y añade `Impl` (por ejemplo, `OrderService` → `OrderServiceImpl`). Usa inyección por constructor.
- **`domain.entity`:** entidades del modelo persistido, con identificador explícito que corresponda a la clave primaria/identificador persistente.
- **`domain.repository`:** contratos y repositorios de persistencia; define solo operaciones necesarias, tipadas y acotadas al dominio.
- **`kafka.entity`:** DTOs/sobres de entrada y salida de Kafka. No reutilices automáticamente esos DTOs como entidades persistidas ni como modelo interno de negocio.
- **`kafka.consumer`:** consumidores Kafka, normalmente componentes anotados con `@KafkaListener`, responsables de validar/adaptar el transporte e invocar un servicio; no alojan reglas de negocio.
- **`kafka.producer`:** productores/publicadores Kafka, componentes Spring anotados con `@Component` cuando corresponda. Aíslan la serialización y el envío del resto de la lógica.
- **`util.exception`:** excepciones propias, que extienden `RuntimeException` salvo que exista una razón explícita para otro contrato.
- **`util.observability`:** métricas, health indicators, logging/correlation y otras capacidades de observabilidad.
- **`util`:** no es un cajón de sastre: úsalo solo para utilidades transversales que no pertenezcan a los paquetes anteriores; crea subpaquetes temáticos.

## Repositorios y entidades según la BBDD

La ubicación en paquetes es común, pero las anotaciones e interfaces dependen del motor:

| Persistencia | Entidad | Identificador | Repositorio típico |
|---|---|---|---|
| Relacional/JPA | `@Entity` | `@Id` (clave primaria) | `JpaRepository<Entity, Id>` o `CrudRepository<Entity, Id>` |
| MongoDB | `@Document` | `@Id` | `MongoRepository<Entity, Id>` o `CrudRepository<Entity, Id>` |

No anotes entidades Mongo con `@Entity` ni uses `JpaRepository` sobre MongoDB. Mantén la tecnología actual del proyecto; separa modelo de negocio y representación de almacenamiento cuando la evolución del esquema lo requiera. Evita consultas genéricas sin límites y cambios de esquema/índices no solicitados.

## Reglas EDA y persistencia

- El consumer adapta el mensaje y delega al servicio; el servicio contiene o coordina la lógica de negocio y depende de repositorios/puertos, no de listeners Kafka.
- Define contratos de evento, comando o petición/respuesta explícitos y versionables. Incluye identificadores de mensaje y correlación cuando aplique; valida antes de ejecutar la lógica.
- Distingue eventos fire-and-forget de solicitudes que esperan respuesta. Publica respuesta solo si el contrato la requiere.
- Considera entrega al menos una vez: el procesamiento puede repetirse. Diseña idempotencia con estado durable y claves adecuadas cuando el caso de uso lo necesite; no afirmes exactly-once extremo a extremo sin garantías implementadas y verificadas.
- Define errores recuperables/no recuperables, reintentos limitados y DLT según requisitos. No ocultes errores ni confirmes éxito antes de persistir/publicar lo requerido.
- Para coordinar escritura BBDD y emisión de eventos, analiza outbox/inbox o transacciones soportadas por la infraestructura; no supongas atomicidad entre broker y base de datos.
- Configura endpoints y secretos externamente; no registres credenciales ni payloads sensibles. Añade métricas y comprobaciones de dependencias con exposición restringida.
- Los consumidores y productores no sustituyen pruebas: cubre lógica de servicio, mapeos persistentes, contratos de mensajes, fallos y reentrega; reserva Testcontainers para pruebas de integración que justifiquen servicios reales.

## Aplicación en este workspace

`engineBroker` es una aplicación REST de investigación asistida, no el servicio
CRUD `manageDatabase`. Mantén las responsabilidades actuales: API en `api`,
casos de uso en `service` / `service.impl`, proveedor de IA detrás de
`ResearchAiProvider` y el adaptador Kafka a `manageDatabase` en
`kafka.producer` / `kafka.consumer`. No añadas persistencia Mongo local ni
dependencias directas de base de datos: el almacenamiento se solicita por Kafka.
La beta solo funciona en modo simulación y no debe enviar órdenes ni inferir
reglas de inversión.

La referencia del contrato externo Kafka está en
[`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md). El archivo
`docs/prompts/prompt_copilot_manageDatabase_crud.md` es histórico y no describe
la implementación de este repositorio. Verifica siempre la configuración activa
en `application.yml` y `application-local.yml`; en particular, `local` usa
PLAINTEXT contra el broker local y la prueba de contexto selecciona
explícitamente el perfil `test` para usar Embedded Kafka.
