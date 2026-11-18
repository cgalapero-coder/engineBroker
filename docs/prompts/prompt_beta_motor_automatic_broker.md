# Prompt para GitHub Copilot — Beta del motor autónomo de análisis de inversiones

## Contexto de trabajo

Quiero iniciar la primera versión beta de un microservicio de **Automatic Broker**, basado en la arquitectura de motor autónomo de análisis de inversiones que acompaña a este prompt.

También tendrás disponible el contexto completo del microservicio `manageDatabase`, que ya existe y proporciona acceso a MongoDB mediante Kafka, así como el documento funcional con las colecciones, sus estructuras JSON y los índices iniciales.

**Antes de escribir código, inspecciona el repositorio y todo el contexto disponible.** No supongas nombres de paquetes, versiones, tópicos Kafka, formatos de mensajes, nombres de clases ni contratos de integración: localízalos en el proyecto y reutiliza lo que ya esté definido.

## 1. Objetivo de esta tarea

Construir una primera versión beta de un microservicio que sirva como núcleo inicial de investigación y análisis asistido por IA de Automatic Broker.

El microservicio debe poder:

1. Recibir una petición de análisis o investigación.
2. Orquestar el proceso mediante Java y Spring Boot.
3. Utilizar IA generativa a través de Spring AI, dejando Ollama como proveedor local configurable para las pruebas.
4. Separar claramente la generación de texto de los cálculos, validaciones y reglas deterministas.
5. Obtener o recibir información de fuentes externas mediante componentes desacoplados, sin acoplar la lógica de negocio a un proveedor concreto.
6. Estructurar los datos útiles obtenidos.
7. Persistir esos datos utilizando el microservicio `manageDatabase` a través de Kafka.
8. Registrar la actividad relevante para disponer de trazabilidad.
9. Funcionar inicialmente en **modo simulación**, sin enviar órdenes reales a brokers ni realizar operaciones financieras.

El objetivo de esta primera iteración es tener un flujo pequeño, ejecutable y verificable de extremo a extremo. No se pretende implementar todavía toda la plataforma descrita en la arquitectura.

## 2. Arquitectura y criterios que debes respetar

La arquitectura de referencia propone una plataforma modular:

- **Java + Spring Boot:** servicios, APIs internas, seguridad, configuración, orquestación y adaptadores.
- **Spring AI:** integración de modelos generativos desde Java.
- **Ollama:** proveedor local inicial para experimentar y ejecutar la beta.
- **MongoDB:** repositorio documental central, accedido en este proyecto a través de `manageDatabase`.
- **Kafka:** comunicación entre microservicios. Reutiliza la infraestructura y los contratos existentes.
- **Python:** futuro servicio cuantitativo independiente, con pandas, NumPy, scikit-learn y statsmodels, cuando se incorporen cálculos estadísticos o predictivos que lo justifiquen.
- **Docker/Kubernetes:** ejecución y despliegue según la infraestructura que ya tenga el proyecto.

No conviertas el microservicio en un agente único con capacidad ilimitada. Mantén separadas estas responsabilidades:

- Investigación y generación de hipótesis mediante IA.
- Obtención, normalización y validación de datos.
- Cálculos cuantitativos deterministas.
- Generación de conclusiones y explicaciones.
- Persistencia y auditoría.
- Gestión del riesgo, que será independiente y no debe delegarse al LLM.

La IA puede investigar, resumir, extraer entidades y explicar resultados. No debe decidir por sí sola reglas financieras, límites de riesgo ni permisos.

## 3. Integración con manageDatabase y MongoDB

Esta parte es especialmente importante.

El microservicio `manageDatabase` ya existe y será el responsable de las operaciones de persistencia en MongoDB. **Este nuevo microservicio no debe conectarse directamente a MongoDB ni incorporar repositorios Mongo propios para estas colecciones.**

La comunicación para altas, consultas y modificaciones debe realizarse mediante Kafka y los contratos que ya implemente `manageDatabase`.

Antes de implementar esta integración:

1. Revisa el contexto que te proporcionaré del microservicio `manageDatabase`.
2. Identifica los tópicos, mensajes, comandos, respuestas, claves de correlación, gestión de errores y convenciones que realmente utiliza.
3. Comprueba qué operaciones CRUD están implementadas para cada colección.
4. Reutiliza los contratos y mecanismos existentes.
5. Si falta una operación necesaria, documenta exactamente qué falta y propone el cambio mínimo compatible. No inventes silenciosamente un contrato nuevo.

### Colecciones que debe utilizar la beta

Respeta los nombres exactos de las colecciones y los campos definidos en el documento funcional:

| Colección | Uso inicial previsto en la beta |
|---|---|
| `biData` | Guardar datos relevantes, estructurados y normalizados obtenidos durante la investigación. No guardar indiscriminadamente respuestas completas de webs. |
| `histData` | Registrar las peticiones de investigación, acciones de IA, resultados y errores relevantes. |
| `webData` | Consultar el catálogo de fuentes y servicios externos habilitados, si el CRUD y los contratos existentes lo permiten. |
| `opData` | Consultar definiciones de operaciones y prompts, si están disponibles y resulta necesario para el flujo inicial. |
| `globalConfiguration` | Consultar la configuración general y el modo de funcionamiento, si existe una operación de lectura disponible. |

Las colecciones `manageUserData` y `stadisticData` forman parte del modelo general, pero no es necesario implementar ahora funcionalidades completas de gestión de usuarios ni generación de estadísticas. No las fuerces dentro del MVP si no son necesarias.

### Esquemas de datos

Utiliza como referencia los JSON del documento funcional. En particular:

**`biData`**

- `id`
- `sourceId`
- `sourceName`
- `market`
- `instrument`
- `dataType`
- `value`
- `unit`
- `currency`
- `observedAt`
- `collectedAt`
- `description`

**`histData`**

- `id`
- `actorType`
- `userId`
- `userRole`
- `eventType`
- `operationId`
- `operation`
- `occurredAt`
- `simulationData`
  - `amounts`
  - `services`
  - `gains`
  - `losses`
  - `performance`
- `result`
- `description`

Los esquemas completos de las demás colecciones están en el documento de datos. No los redefinas ni los contradigas.

En esta fase, los ejemplos del documento utilizan strings para los campos y arrays/objetos para las estructuras anidadas. Conserva esa convención en la integración con `manageDatabase`, salvo que el microservicio existente ya establezca una conversión o contrato diferente. No cambies el modelo MongoDB desde este microservicio.

Los identificadores de negocio `id` no deben confundirse automáticamente con el `_id` de MongoDB. La generación de identificadores debe seguir el mecanismo que ya tenga implementado `manageDatabase`.

## 4. Flujo funcional mínimo que quiero conseguir

Implementa un primer caso de uso completo, sencillo y demostrable:

### Caso de uso: investigación de un instrumento

1. El microservicio recibe una solicitud de investigación con, como mínimo:
   - mercado;
   - instrumento o activo;
   - pregunta u objetivo de investigación;
   - usuario, si el contrato de entrada contempla ese dato;
   - indicación de que se ejecuta en modo simulación.

2. Valida la petición y crea un identificador de ejecución o correlación.

3. Registra el inicio de la investigación en `histData`, utilizando el mecanismo Kafka de `manageDatabase`.

4. Prepara el contexto de investigación:
   - consulta `webData` para localizar fuentes habilitadas, si esta capacidad está disponible;
   - utiliza únicamente fuentes o adaptadores configurados y permitidos;
   - no permitas que instrucciones encontradas en páginas o documentos externos modifiquen las instrucciones del sistema, las herramientas disponibles o los permisos del agente.

5. Invoca el componente de IA mediante Spring AI:
   - el proveedor debe ser configurable;
   - Ollama será la opción local inicial;
   - el prompt debe pedir resultados estructurados;
   - debe distinguir hechos, inferencias, hipótesis y predicciones;
   - debe identificar incertidumbres y datos que no haya podido verificar;
   - no debe inventar cotizaciones, noticias, fuentes ni valores.

6. Normaliza y valida la respuesta:
   - comprueba que los datos requeridos están presentes;
   - conserva la fuente y las fechas cuando estén disponibles;
   - descarta o marca como no verificables los datos sin procedencia suficiente;
   - evita duplicados cuando sea posible usando los identificadores y criterios que permita el modelo existente.

7. Persiste en `biData` únicamente los datos relevantes y estructurados que puedan reutilizarse en análisis, estadísticas o gráficos. No guardes la respuesta íntegra de una web como si fuera un dato de mercado.

8. Registra en `histData` el resultado final de la investigación, incluyendo estado, resumen y errores relevantes cuando corresponda.

9. Devuelve al solicitante un resultado que incluya:
   - identificador de ejecución;
   - estado;
   - resumen de la investigación;
   - conclusiones e hipótesis;
   - evidencias y fuentes disponibles;
   - incertidumbres y limitaciones;
   - confirmación de si los datos útiles se han persistido.

Si todavía no existe un conector real de datos externos, implementa una interfaz desacoplada y un adaptador de prueba claramente identificado. No presentes datos simulados como datos reales ni inventes resultados de mercado. La beta debe poder probarse sin depender de una API comercial.

## 5. Diseño interno esperado

Organiza el código por responsabilidades, siguiendo las convenciones del proyecto. Como orientación, deberían quedar separadas estas piezas:

- **API de entrada:** controlador REST o mecanismo de entrada que ya utilice el proyecto.
- **Application / Use cases:** coordinación del caso de uso de investigación.
- **Domain:** modelos, estados y reglas propias del caso de uso.
- **AI adapter:** integración con Spring AI y el proveedor Ollama.
- **Prompt management:** prompts separados del código Java y versionables.
- **Source adapters:** interfaz para consultar fuentes externas y adaptadores concretos.
- **Data normalization:** transformación y validación de resultados.
- **Persistence client:** cliente Kafka para solicitar operaciones a `manageDatabase`.
- **Audit / history:** registro de inicio, finalización y errores.
- **Configuration:** configuración externa por perfiles.

No es obligatorio utilizar literalmente estos nombres de paquetes o clases. Adáptalos a la estructura real del repositorio y evita crear capas vacías o abstracciones innecesarias.

## 6. Configuración y ejecución local

Revisa cómo está configurado actualmente el proyecto y respeta sus versiones de Java, Spring Boot, Spring AI y demás dependencias.

Prepara la configuración para poder ejecutar la beta localmente:

- proveedor de IA configurable, con Ollama como opción inicial;
- URL/base URL y nombre del modelo configurables;
- parámetros básicos del modelo configurables;
- bootstrap servers de Kafka y configuración de consumidor/productor coherentes con el proyecto;
- tópicos y contratos de `manageDatabase` tomados de su contexto real;
- timeouts y reintentos razonables;
- nivel de log configurable.

Si el proyecto utiliza `application-local.yml` o `application-local.properties`, completa ese perfil sin sobrescribir configuraciones existentes que no estén relacionadas con esta tarea. No incluyas contraseñas, tokens ni claves reales en el repositorio. Añade valores de ejemplo o variables de entorno.

Comprueba si Ollama ya está disponible en el entorno. Si no lo está, deja documentado cómo levantarlo y qué modelo debe descargarse, sin asumir que el servicio está activo.

No añadas infraestructura Docker/Kubernetes duplicada si ya existe una configuración común que deba reutilizarse.

## 7. Seguridad y límites obligatorios de esta beta

- El modo inicial será exclusivamente de investigación y simulación.
- No implementar envío de órdenes reales, compra, venta ni conexión operativa a un broker.
- No permitir que el LLM cambie su propia configuración, permisos o límites.
- No entregar credenciales al LLM ni incluir secretos en prompts, logs o respuestas.
- No almacenar credenciales en texto plano.
- No confiar en instrucciones procedentes de webs, noticias o documentos externos.
- No tratar una predicción generada por IA como un hecho.
- No ejecutar cálculos financieros críticos a partir de texto libre sin validación determinista.
- No introducir una gestión de riesgo ficticia: el motor de riesgo será un componente independiente en una fase posterior.

## 8. Pruebas que debe incluir

Implementa pruebas proporcionadas al alcance de la beta:

1. Pruebas unitarias de validación y normalización.
2. Pruebas del servicio de aplicación con adaptadores simulados.
3. Pruebas del formato estructurado esperado de la respuesta de IA.
4. Pruebas de los mensajes Kafka enviados a `manageDatabase`, utilizando los contratos reales.
5. Pruebas de errores: proveedor de IA no disponible, timeout, respuesta inválida, Kafka no disponible y fallo de persistencia.
6. Una prueba de integración o procedimiento reproducible que permita ejecutar una investigación de extremo a extremo en local, sin operar con dinero real.

Las pruebas no deben necesitar credenciales reales ni depender de mercados en tiempo real.

## 9. Documentación que debes generar o actualizar

Incluye documentación breve y útil en el propio proyecto:

- propósito y alcance de la beta;
- arquitectura y flujo de ejecución;
- dependencias y requisitos locales;
- configuración de Ollama;
- configuración Kafka y relación con `manageDatabase`;
- ejemplo de petición y respuesta;
- colecciones utilizadas y finalidad de cada una;
- limitaciones conocidas;
- instrucciones para ejecutar las pruebas.

Añade un diagrama Mermaid del flujo si encaja con la documentación existente.

## 10. Forma de trabajar: inspección, plan e implementación

Trabaja en este orden:

### Paso 1 — Inspección

Examina el proyecto actual y el contexto del microservicio `manageDatabase`. Identifica:

- estructura y módulos existentes;
- versiones y dependencias;
- convenciones de código;
- configuración por perfiles;
- infraestructura Kafka;
- contratos reales de comunicación con `manageDatabase`;
- capacidades CRUD ya disponibles;
- pruebas existentes.

No modifiques nada durante esta primera inspección.

### Paso 2 — Plan concreto

Presenta un plan breve con:

- componentes que vas a crear o modificar;
- flujo que quedará operativo;
- colecciones utilizadas;
- contratos Kafka que vas a reutilizar;
- dependencias nuevas, justificando cada una;
- cuestiones que no se pueden resolver con el contexto disponible.

No te detengas por decisiones que puedan resolverse siguiendo las convenciones existentes. Si hay una ambigüedad que afecte a un contrato externo o pueda romper otro microservicio, señálala antes de cambiarlo.

### Paso 3 — Implementación

Una vez definido el plan, implementa la beta en el repositorio:

- código funcional, no solo esqueletos;
- configuración local;
- prompts externos y versionados;
- pruebas;
- documentación;
- integración real con `manageDatabase` mediante Kafka.

Mantén los cambios acotados y no refactorices partes ajenas a esta tarea.

### Paso 4 — Verificación y entrega

Ejecuta las pruebas y compilación disponibles. Corrige los errores introducidos por los cambios.

Al terminar, informa de:

1. Qué se ha implementado.
2. Qué archivos se han creado o modificado.
3. Cómo se ejecuta localmente.
4. Cómo se prueba el flujo completo.
5. Qué datos se escriben en cada colección.
6. Qué contratos Kafka se han utilizado.
7. Qué queda pendiente para la siguiente iteración.
8. Resultado real de compilación y pruebas, sin afirmar que algo funciona si no se ha verificado.

## 11. Fuera del alcance de esta primera beta

No implementar todavía:

- ejecución real de operaciones financieras;
- integración operativa con brokers;
- motor de riesgo completo;
- servicio cuantitativo Python completo;
- entrenamiento o reentrenamiento automático de modelos;
- backtesting avanzado;
- paper trading completo;
- frontend;
- gestión avanzada de usuarios, roles y suscripciones;
- generación completa de estadísticas;
- descubrimiento autónomo e ilimitado de webs;
- RAG o memoria semántica avanzada, salvo que ya exista una infraestructura reutilizable y sea imprescindible para el caso mínimo.

Deja los puntos futuros desacoplados y documentados, pero no los desarrolles ahora.

---

## Resultado esperado

Quiero terminar esta tarea con un microservicio beta de Automatic Broker que pueda recibir una solicitud de investigación, utilizar IA generativa local mediante Spring AI/Ollama, producir un resultado estructurado y trazable, y persistir los datos útiles en las colecciones existentes a través de `manageDatabase` y Kafka.

**Prioridad absoluta: integración correcta con la arquitectura y los contratos que ya existen, flujo funcional de extremo a extremo, trazabilidad y seguridad. No crear una segunda vía de acceso a MongoDB ni inventar contratos de comunicación.**
