# Contexto completo para ChatGPT: `engineBroker`

Documento autocontenido para dar a ChatGPT el contexto funcional y técnico
vigente de este repositorio. Describe lo comprobado en el código y la
configuración; si algo difiere, prevalecen los fuentes de `src/main` y
`pom.xml`. Este proyecto es `engineBroker`, no la implementación del servicio
externo `manageDatabase`.

## 1. Resumen y límites funcionales

`engineBroker` es una beta de investigación asistida de instrumentos para
Automatic Broker. Expone una API REST que ejecuta investigación en modo
simulación. No conecta con brokers, no envía órdenes ni compra/vende activos, no
implementa gestión del riesgo y no tiene autenticación/autorización de usuarios.

La aplicación no habla directamente con MongoDB. Solicita persistencia al
microservicio externo `manageDatabase` mediante Kafka. La fuente de datos
implementada (`NoExternalResearchSourceAdapter`) no obtiene datos de mercado
externos; por lo tanto, la aplicación puede completar investigación con
evidencia vacía, pero debe reflejar incertidumbre y no presentar conclusiones
del modelo como hechos verificados.

No inventar reglas de inversión, datos de mercado, conectores, colecciones,
campos de negocio, credenciales ni infraestructura que no estén implementados
o expresamente solicitados. El material de `docs/context/SERVICE_REFERENCE.md`
describe el contrato externo conocido de `manageDatabase`; verificarlo contra
el repositorio de ese servicio antes de hacer cambios incompatibles.

## 2. Stack y ejecución

- Java 25; Maven Wrapper.
- Spring Boot 4.1.2 y Spring AI 2.0.2 (versiones declaradas en `pom.xml`).
- Spring Web MVC, Bean Validation, Spring Kafka, Actuator y Spring AI Ollama.
- JSON mediante Jackson.
- No hay cliente MongoDB en este servicio.

Comandos:

```bash
# macOS/Linux
bash ./mvnw test
bash ./mvnw spring-boot:run

# Windows
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Las unit tests están diseñadas para correr sin Kafka, MongoDB u Ollama externos.
La prueba del contexto usa Kafka embebido. En este checkout `mvnw` puede no
tener permiso de ejecución; `bash ./mvnw ...` evita depender de ese bit en
macOS/Linux.

## 3. Arquitectura y flujo de extremo a extremo

```text
Cliente HTTP
  -> ResearchController
  -> InvestmentResearchService / InvestmentResearchServiceImpl
       -> ManageDatabaseClient -- Kafka request --> manageDatabase
       -> ResearchSourceAdapter (actualmente vacío)
       -> ResearchPromptBuilder
       -> ResearchAiProvider (Ollama por defecto)
       -> ResearchResultNormalizer
       -> ManageDatabaseClient -- Kafka request --> manageDatabase
  <- ResearchResult / ApiExceptionHandler

Kafka manageDatabase.responses
  -> ManageDatabaseResponseListener
  -> ManageDatabaseClient.acceptResponse
  -> CompletableFuture asociado a correlationId
```

Orden del flujo de investigación:

1. Valida que el body no sea nulo y que `simulation=true`.
2. Genera un `executionId` y crea el registro inicial `histData` (`CREATE`).
   Si ese `CREATE` falla, la excepción ocurre antes del bloque de fallo y no se
   puede registrar un nuevo estado fallido.
3. Recoge evidencia; filtra evidencia verificada y comprueba su procedencia y
   que mercado/instrumento coincidan con la solicitud.
4. Construye el prompt del sistema desde
   `src/main/resources/prompts/instrument-research-system.md` y el prompt de
   usuario estructurado con la solicitud/evidencia validada.
5. Llama al proveedor IA seleccionado y normaliza la respuesta estructurada.
   Los hechos (`FACT`) sin referencias a evidencia aceptada se descartan y se
   añaden incertidumbres; también se quitan referencias inválidas.
6. Persiste en `biData` únicamente evidencia verificada disponible (un `CREATE`
   por evidencia; puede ser cero).
7. Actualiza el registro `histData` a completado (`UPDATE`) y devuelve el
   resultado.
8. Si ocurre un `RuntimeException` dentro del bloque principal, intenta
   actualizar `histData` a fallido. Si falla también esa actualización, agrega
   ese error como excepción suprimida y lo registra.

No hay transacción distribuida ni garantía exactly-once entre HTTP, Kafka y la
base del servicio externo. Puede haber persistencia parcial y duplicados ante
reintentos/fallos. El cliente Kafka correlaciona respuestas en memoria; no es
idempotencia durable.

## 4. Endpoint REST y modelos

### `POST /api/v1/research`

Body (`ResearchRequest`):

```json
{
  "market": "EU",
  "instrument": "SAP",
  "objective": "Resume las hipótesis que puedan investigarse para este instrumento.",
  "userId": "opcional",
  "userRole": "opcional",
  "simulation": true
}
```

Validaciones: `market` obligatorio hasta 100 caracteres; `instrument`
obligatorio hasta 200; `objective` obligatorio hasta 2000; `userId` hasta 200;
`userRole` hasta 100; `simulation` obligatorio y debe ser `true`. El controller
responde `200` al completar la investigación.

`ResearchResult` incluye: `executionId`, `status`, `summary`, `conclusions`,
`evidence`, `uncertainties`, `simulation`, `historyPersisted`, `dataPersisted`
y `persistedDataCount`. `ResearchConclusion.Category` representa
`FACT`, `INFERENCE`, `HYPOTHESIS` o `PREDICTION`. `ResearchEvidence` transporta
procedencia, mercado/instrumento, tipo/valor, timestamps, metadatos opcionales
y flag de verificación.

## 5. Clases principales

Todas bajo `com.gtech.automaticbroker.enginebroker`.

| Paquete/clase | Responsabilidad |
|---|---|
| `Application` | Arranque Spring Boot. |
| `api/ResearchController` | Publica `POST /api/v1/research`, valida body y delega al servicio. |
| `api/ApiExceptionHandler` | Convierte excepciones conocidas a respuestas HTTP seguras; registra `DatabaseServiceException`. |
| `service/InvestmentResearchService` | Contrato del caso de uso de investigación. |
| `service/ResearchAiProvider` | Puerto de proveedor IA: `research`, `providerName`, `modelName`. |
| `service/ResearchSourceAdapter` | Puerto para fuentes de evidencia. |
| `service/impl/InvestmentResearchServiceImpl` | Orquesta validación funcional, persistencia de historial/datos, investigación, errores y resultado. |
| `service/impl/NoExternalResearchSourceAdapter` | Implementación actual sin datos externos; no simula cotizaciones. |
| `service/impl/ResearchPromptBuilder` | Carga prompt de sistema y construye petición IA estructurada. |
| `service/impl/ResearchResultNormalizer` | Valida respuesta del modelo, evidencia, referencias y categorías; retira `FACT` no sustentados. |
| `service/impl/OllamaResearchAiProvider` | Integra Spring AI ChatClient con Ollama; timeout, clasificación de fallos y parseo JSON. |
| `domain/entity/ResearchRequest` | DTO de entrada REST con Bean Validation. |
| `domain/entity/ResearchResult` | DTO de salida REST. |
| `domain/entity/ResearchEvidence` | Evidencia y procedencia usada por el normalizador y persistencia. |
| `domain/entity/ResearchConclusion` | Conclusión, categoría, texto, referencias y confianza. |
| `domain/entity/ResearchAiRequest` | Mensajes de sistema y usuario enviados al proveedor IA. |
| `domain/entity/ResearchAiResponse` | Respuesta estructurada esperada del modelo. |
| `domain/entity/NormalizedResearch` | Resultado normalizado interno antes de persistir/devolver. |
| `kafka/producer/ManageDatabaseClient` | Publica `CREATE`/`UPDATE`, espera confirmación Kafka y respuesta de negocio correlacionada; valida status y campos. |
| `kafka/consumer/ManageDatabaseResponseListener` | Consume el topic de respuestas y entrega el JSON al cliente Kafka. |
| `kafka/entity/DatabaseOperation` | Operaciones de este cliente: `CREATE`, `UPDATE`. |
| `kafka/entity/DatabaseRequestEnvelope` | Envelope request: messageId, correlationId, operación, payload, responseRequired. |
| `kafka/entity/DatabaseResponseEnvelope` | Envelope response y error anidado del servicio externo. |
| `config/DatabaseMessagingProperties` | Topics, timeouts, DLT suffix y backoff; valida duraciones/valores. |
| `config/KafkaConfiguration` | Kafka `AdminClient` de health y `DefaultErrorHandler` con retries/DLT. |
| `config/ResearchProperties` | Timeout del caso de uso IA (`app.research.ai-timeout`). |
| `config/AiProviderProperties` | Nombre de proveedor configurado; default `ollama`, normalizado a minúsculas. |
| `config/ResearchAiProviderConfiguration` | Elige implementación del proveedor por `providerName`; sin fallback. |
| `config/AiExecutorConfiguration` | `ExecutorService` basado en virtual threads para timeout/cancelación de llamada IA. |
| `util/exception/*` | Excepciones tipadas para fallos IA, persistencia, evidencia y solicitud inválida. |

### Excepciones tipadas

- `InvalidResearchRequestException`: petición no válida para modo de operación.
- `AiProviderUnavailableException`: Ollama no reachable/resoluble.
- `AiProviderTimeoutException`: agotado `AI_TIMEOUT`.
- `AiProviderCommunicationException` / `AiProviderException`: fallo de llamada.
- `InvalidAiResponseException`: respuesta vacía, JSON inválido o incompleta.
- `InvalidResearchEvidenceException`: fuente devuelve evidencia inconsistente.
- `DatabaseServiceException`: error de envío/espera/contrato Kafka-persistencia.

## 6. Kafka y persistencia externa

Topics configurables:

- Request: `manageDatabase.requests`
- Response: `manageDatabase.responses`
- Sufijo dead-letter: `.DLT`; el handler de listener se configura para usar
  `<topic de origen><sufijo>`. El listener de respuestas puede fallar al parsear
  un mensaje y su error handler puede publicar en
  `manageDatabase.responses.DLT`.

La operación request envía un `DatabaseRequestEnvelope` como objeto
`KafkaTemplate<String,Object>`; usa `correlationId` como clave y
`responseRequired=true`. Espera confirmación de publicación y luego espera la
respuesta por `correlationId`. Se valida que `requestId`, `correlationId`,
`operation` coincidan, que `status=SUCCESS` y que `result` no sea nulo. Las
respuestas desconocidas (sin request pendiente en esta instancia) se ignoran
con `DEBUG`.

Datos de negocio que se escriben:

- `histData`: `CREATE` al comienzo; `UPDATE` a completado o fallido.
- `biData`: `CREATE` por cada evidencia verificada, si la hubiera.
- No se consultan `webData`, `opData` ni `globalConfiguration`.

La semántica y esquema de esas colecciones pertenecen al servicio externo. No
cambiar payloads o contratos sin validar ambos lados.

## 7. Configuración de aplicación

El perfil predeterminado está definido como `local` en `application.yml`.
Spring combina `application.yml` con `application-local.yml`; este último cambia
la configuración Kafka local. Valores de contraseña/usuario no se copian aquí:
usar variables de entorno/gestor de secretos.

| Propiedad efectiva / entorno | Default o valor configurado | Notas |
|---|---|---|
| `spring.profiles.default` | `local` | Perfil por defecto. |
| `server.address` | `127.0.0.1` | Local; cambiar `SERVER_ADDRESS` solo si el despliegue lo requiere. |
| `server.port` | `8092` | `SERVER_PORT`. |
| `spring.kafka.bootstrap-servers` | local `localhost:9092`; base `localhost:9094` | `KAFKA_BOOTSTRAP_SERVERS` se puede establecer en el entorno; si no, se aplica el default del perfil activo. |
| Kafka protocol | `PLAINTEXT` en local | El perfil local sobrescribe el protocolo SASL del YAML base; el broker local documentado no requiere autenticación. |
| Kafka SASL credentials | No usadas en local | La configuración base permite variables `KAFKA_USERNAME` / `KAFKA_PASSWORD`; no registrarlas ni usarlas en el broker PLAINTEXT local. |
| `spring.kafka.consumer.group-id` | `engineBroker-local` | Valor fijo en `application.yml`, no hay `KAFKA_CONSUMER_GROUP` referenciado en los YAML actuales. |
| `auto-offset-reset` | `earliest` | Consumer. |
| `enable-auto-commit` | `false` | Consumer. |
| Key deserializer / serializer | String | Consumer / producer. |
| Value deserializer | String | Consumer recibe el JSON como texto. |
| Value serializer | Spring Kafka `JsonSerializer` | Producer serializa el envelope objeto. |
| `spring.kafka.listener.ack-mode` | `record` | Confirmación por registro. |
| `app.manage-database.request-topic` | `manageDatabase.requests` | `MANAGEDATABASE_REQUEST_TOPIC`. |
| `app.manage-database.response-topic` | `manageDatabase.responses` | `MANAGEDATABASE_RESPONSE_TOPIC`. |
| `app.manage-database.send-timeout` | `5s` | `MANAGEDATABASE_SEND_TIMEOUT`. |
| `app.manage-database.response-timeout` | `15s` | `MANAGEDATABASE_RESPONSE_TIMEOUT`. |
| `app.manage-database.dead-letter-suffix` | `.DLT` | `KAFKA_DLT_SUFFIX`. |
| `app.manage-database.retry-interval-ms` | `1000` | `KAFKA_RETRY_INTERVAL_MS`. |
| `app.manage-database.max-retries` | `2` | `KAFKA_MAX_RETRIES`; según `FixedBackOff`, retries se interpretan con la semántica de Spring Kafka. |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | `OLLAMA_BASE_URL`. |
| `spring.ai.ollama.chat.options.model` | `qwen3:8b` | `OLLAMA_MODEL`; se registra el nombre del modelo, no las respuestas. |
| `spring.ai.ollama.chat.options.temperature` | `0.2` | `OLLAMA_TEMPERATURE`. |
| `app.research.ai.provider` | `ollama` | `AI_PROVIDER`; debe coincidir con `providerName()` de un bean registrado. |
| `app.research.ai-timeout` | `90s` | `AI_TIMEOUT`. |
| Nivel logger de la aplicación | `INFO` | `APP_LOG_LEVEL` para el paquete `com.gtech.automaticbroker.enginebroker`. |
| Actuator web endpoints | `health,info,metrics` | Health sin detalles; probes liveness/readiness activas. |

`KAFKA_BOOTSTRAP_SERVERS` se referencia en ambos YAML: el default base es
`localhost:9094` y el default local `localhost:9092`. El perfil local coloca
bootstrap y `properties.security.protocol` directamente bajo `spring.kafka`;
no anidar otro bloque `kafka` debajo de esa clave, porque Spring Boot ignoraría
esas propiedades.

El perfil `test` en `ApplicationContextTest` se activa por anotación; no hay
`application-test.yml` visible entre los resources. `@EmbeddedKafka` sustituye
el bootstrap de Kafka y configura PLAINTEXT para el broker embebido.
No quitar `@ActiveProfiles("test")` sin configurar explícitamente el contexto
de Kafka embebido.

## 8. Errores HTTP y observabilidad

`ApiExceptionHandler` devuelve cuerpos genéricos; la causa interna no se incluye
en el JSON HTTP:

| Excepción | HTTP | Código JSON |
|---|---:|---|
| Bean validation / `InvalidResearchRequestException` | 400 | `INVALID_REQUEST` |
| `AiProviderUnavailableException` | 503 | `AI_PROVIDER_UNAVAILABLE` |
| `AiProviderTimeoutException` | 504 | `AI_PROVIDER_TIMEOUT` |
| `AiProviderCommunicationException` / `AiProviderException` | 502 | `AI_PROVIDER_COMMUNICATION_FAILED` |
| `InvalidAiResponseException` | 502 | `INVALID_AI_RESPONSE` |
| `InvalidResearchEvidenceException` | 502 | `INVALID_RESEARCH_EVIDENCE` |
| `DatabaseServiceException` | 502 | `PERSISTENCE_UNAVAILABLE` |

Solo el handler de `DatabaseServiceException` registra actualmente un `WARN`
con mensaje y stack trace (`manageDatabase request failed`). Es la pista para
timeouts Kafka, envío fallido, respuesta inválida, respuesta negativa o resultado
faltante. El texto exacto del mensaje orienta a la fase:

- `Could not send request...`: fallo del productor, autenticación, broker,
  metadata/topic o timeout de envío.
- `Timed out waiting...`: request publicado pero no llegó respuesta correlativa
  antes del response timeout; revisar consumer externo, topic/grupo/correlación.
- `manageDatabase returned...` / `does not match...`: parseo o contrato de
  respuesta.
- `manageDatabase rejected...`: el servicio externo respondió status no
  SUCCESS; el code puede aparecer en el mensaje de excepción.

El patrón de logs añade `corr:%X{correlationId:-}`; puede quedar vacío porque
no se garantiza el MDC en todos los flujos. El servicio de investigación
registra `executionId`, proveedor/modelo al invocar IA, y resultado con
`persistedDataCount`. El manejo de fallo registra `executionId` y tipo de
excepción, no necesariamente el stack trace. No registrar contraseñas, prompts,
respuestas completas de IA ni payloads sensibles.

## 9. Pruebas existentes

- `ApplicationContextTest`: carga el contexto, usa `@ActiveProfiles("test")` y
  `@EmbeddedKafka`, verifica listener running.
- `ResearchAiProviderConfigurationTest`: selección/configuración del proveedor.
- `ManageDatabaseClientTest`: envío, correlación/timeout y errores de persistencia.
- `InvestmentResearchServiceImplTest`: orquestación, persistencia y fallos del
  caso de uso con dependencias simuladas.
- `OllamaResearchAiProviderTest`: timeout/conversión/fallos sin conexión real a
  Ollama.
- `ResearchPromptBuilderTest`: construcción del prompt.
- `ResearchResultNormalizerTest`: filtro de evidencia, claims y referencias.

No confundir las pruebas unitarias con una verificación de conectividad real de
Kafka, Ollama o `manageDatabase`. Una prueba local de integración requiere
infraestructura y configuración externa compatibles.

## 10. Cómo continuar cambios con ChatGPT

1. Antes de editar, revisar estado git, fuentes, `pom.xml` y YAML del perfil
   efectivo. Este documento es contexto, no sustituto de la implementación.
2. Implementar la petición concreta con cambios acotados, sin alterar límites de
   simulación ni asumir requisitos de negocio.
3. Si toca contrato Kafka, comprobar el producer y consumer de
   `manageDatabase`; validar serializer/deserializer en ambos lados.
4. No exponer detalles internos en respuestas HTTP, pero mantener logging
   diagnóstico seguro; nunca añadir secretos a código, prompts o logs.
5. Añadir/actualizar pruebas y ejecutar la menor suite que cubra el cambio.
6. Conservar cambios preexistentes del usuario y no revertir artefactos del
   worktree que no pertenezcan a la tarea.
