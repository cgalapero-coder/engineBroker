# Prompt para GitHub Copilot — Desacoplar el proveedor de IA en Automatic Broker

## Contexto

El microservicio de investigación de **Automatic Broker ya está desarrollado y funcionando**. Actualmente utiliza **Java, Spring Boot, Spring AI y Ollama**.

Quiero evolucionar la implementación para evitar que la lógica funcional dependa directamente de Ollama o de un proveedor concreto. En el futuro quiero poder probar distintos motores y modelos —por ejemplo, servidores locales compatibles con API OpenAI o proveedores comerciales— sin modificar la lógica de investigación.

La solución debe encajar con el código, las versiones y las decisiones que ya existen en el repositorio. **No quiero rehacer el microservicio ni sustituir su implementación actual.**

## Objetivo

Introducir una abstracción propia del proyecto, denominada conceptualmente `AIProvider` (el nombre definitivo puede adaptarse a las convenciones existentes), que separe:

- La lógica funcional de Automatic Broker: qué investigación realizar y cómo procesar sus resultados.
- La integración técnica con el motor o proveedor de IA: cómo enviar la petición y recibir la respuesta.

La lógica funcional debe depender de la abstracción, no de clases concretas de Ollama ni de detalles de un proveedor.

**Spring AI debe seguir siendo la tecnología de integración con los modelos**, siempre que la versión y las dependencias actuales permitan hacerlo correctamente. La abstracción propia debe aportar independencia funcional, no duplicar innecesariamente las abstracciones que Spring AI ya ofrece.

## Instrucciones obligatorias: primero inspeccionar, después modificar

Antes de escribir o cambiar código:

1. Inspecciona el proyecto completo y localiza:
   - Los componentes que invocan actualmente a Ollama o Spring AI.
   - Los servicios de aplicación y de dominio que realizan investigaciones.
   - Los DTO, modelos de petición y respuesta y posibles formatos estructurados.
   - La configuración de Spring AI y Ollama en `application*.yml` / `application*.properties`.
   - Las dependencias y versiones de Spring Boot y Spring AI.
   - Los mecanismos actuales de gestión de errores, timeouts, logs y disponibilidad.
   - Los tests existentes y la forma de ejecutar el proyecto.
2. Identifica qué partes están acopladas directamente a Ollama y cuáles ya están correctamente desacopladas.
3. Presenta un plan breve de cambios, indicando qué clases se crearían, cuáles se modificarían y cuáles no necesitan tocarse.
4. **No empieces una reestructuración general.** Haz el cambio mínimo que permita introducir la abstracción conservando el comportamiento actual.
5. Si Spring AI ya proporciona una abstracción adecuada para alguna parte, reutilízala en lugar de crear una capa redundante.

No inventes clases, paquetes, propiedades, endpoints ni dependencias sin comprobar antes cómo está organizado el repositorio.

## Diseño funcional esperado

La arquitectura objetivo debe ser equivalente a:

```text
Automatic Broker
       |
       v
Servicio de investigación
       |
       v
Abstracción propia AIProvider
       |
       v
Implementación seleccionada por configuración
       |
       +-------------------+
       |                   |
       v                   v
Ollama / Spring AI    Futuro proveedor compatible
       |                   |
       v                   v
Modelo local          Otro runtime o API
```

El servicio de investigación no debe conocer las particularidades de Ollama. Debe trabajar con un contrato común de petición y respuesta.

### 1. Contrato del proveedor

Diseña una interfaz Java propia, adaptada al proyecto. Como referencia conceptual:

```java
public interface AIProvider {

    AIResponse generate(AIRequest request);

    String getProviderName();

    boolean isAvailable();
}
```

Esto es solo una orientación, no una obligación de copiar literalmente esta firma.

Requisitos:

- El contrato debe cubrir las necesidades reales que ya tiene el microservicio.
- Reutiliza los DTO existentes cuando sea razonable.
- No pierdas información que actualmente devuelve Spring AI o que necesita el flujo de investigación.
- Conserva el soporte de respuestas estructuradas si ya existe.
- Ten en cuenta los timeouts y los errores de comunicación.
- Si el proyecto utiliza programación reactiva, streaming o llamadas asíncronas, respeta el enfoque existente; no introduzcas llamadas bloqueantes sin justificación.
- Evita acoplar el contrato a clases específicas de Ollama.
- No añadas métodos que no tengan una utilidad concreta en esta fase.

### 2. Implementación actual de Ollama

Crea o adapta una implementación, por ejemplo `OllamaAIProvider`, que encapsule la integración actual con Ollama y Spring AI.

Condiciones:

- Debe conservar el comportamiento que tiene hoy el microservicio.
- Debe utilizar la configuración y las dependencias que ya existen, adaptándose a sus versiones reales.
- No dupliques la lógica de prompts, investigación, validación o persistencia dentro del proveedor.
- El proveedor se limita a traducir la petición común al mecanismo de invocación de IA y transformar la respuesta al formato común.
- No elimines ni sustituyas componentes actuales que sigan siendo necesarios.

### 3. Selección mediante configuración

Permite seleccionar el proveedor activo mediante configuración externa, siguiendo preferentemente el patrón que ya utilice el proyecto.

Ejemplo conceptual:

```yaml
automatic-broker:
  ai:
    provider: ollama
```

La propiedad y su ubicación definitivas deben adaptarse a la configuración existente.

Requisitos:

- El proveedor predeterminado debe seguir siendo **Ollama**, para no alterar el despliegue actual.
- La selección debe realizarse mediante configuración, sin modificar código de negocio.
- Utiliza mecanismos propios de Spring Boot adecuados a la versión del proyecto, como configuración tipada y creación condicional de beans si encajan con la implementación.
- Valida al arrancar que el proveedor seleccionado existe y está correctamente configurado.
- Si el proveedor indicado no existe, muestra un error claro. No cambies silenciosamente a otro proveedor.
- Mantén las URLs, nombres de modelo, credenciales y demás parámetros específicos dentro de la configuración correspondiente; nunca los incrustes en la lógica funcional.
- No guardes secretos en el repositorio.

### 4. Preparar la extensión a otros proveedores

Deja preparada la arquitectura para incorporar implementaciones adicionales sin modificar el servicio de investigación.

Como posible evolución se contemplan:

- Servidores locales compatibles con API OpenAI, como llama.cpp o vLLM.
- LocalAI.
- Proveedores comerciales mediante API.

**No es necesario implementar todos estos proveedores ahora.**

En esta tarea:
- Implementa y deja funcionando Ollama.
- Define claramente cómo se añadiría otro proveedor.
- Si la arquitectura permite incorporar de forma sencilla un segundo proveedor compatible sin añadir complejidad innecesaria, puedes dejar preparada esa posibilidad, pero no introduzcas dependencias o configuraciones que no se vayan a utilizar.
- No presentes un modelo (Qwen, DeepSeek, Gemma, etc.) como si fuera necesariamente un proveedor o runtime: proveedor/runtime y modelo deben mantenerse conceptualmente diferenciados.

## Restricciones de Automatic Broker que deben conservarse

Este cambio es exclusivamente una refactorización de la integración con IA. No debe alterar el resto de la arquitectura ni ampliar el alcance funcional.

Conserva expresamente:

1. La integración con `manageDatabase` a través de los mecanismos Kafka que ya utiliza el proyecto.
2. La ausencia de conexiones directas a MongoDB desde este microservicio.
3. Los contratos, tópicos, formatos de mensajes y mecanismos de correlación Kafka existentes. No inventes ni cambies contratos.
4. La persistencia actual de los datos útiles de investigación en `biData` y de la actividad en `histData`, según esté implementada.
5. Los prompts y sus ubicaciones actuales. No los mezcles con la implementación concreta de un proveedor.
6. El funcionamiento en modo de simulación.
7. La ausencia de operaciones reales con brokers, órdenes de compra o venta y movimientos de dinero.
8. La validación y normalización de las respuestas antes de utilizarlas o persistirlas.
9. La trazabilidad necesaria para identificar qué proveedor y qué modelo han generado una respuesta, siempre que pueda incorporarse sin romper los contratos existentes.

No cambies el esquema de MongoDB ni los contratos Kafka como parte de esta tarea.

## Gestión de errores y observabilidad

- Diferencia los errores de configuración, proveedor no disponible, timeout, error de comunicación y respuesta inválida.
- Mantén el tratamiento de errores que ya tenga el microservicio y mejóralo solo donde sea necesario.
- Registra el proveedor y modelo utilizados, evitando registrar credenciales, tokens o información sensible.
- No implementes reintentos indiscriminados: respeta la política actual y evita duplicar investigaciones o persistencias.
- La comprobación de disponibilidad no debe provocar llamadas costosas al modelo si basta con comprobar el estado del servicio.
- No introduzcas mecanismos de fallback automáticos que cambien de proveedor sin dejar constancia y sin estar configurados expresamente.

## Pruebas

Añade o adapta pruebas para demostrar que:

1. El servicio de investigación utiliza la abstracción `AIProvider` y no depende directamente de Ollama.
2. La implementación de Ollama conserva el comportamiento actual.
3. La configuración selecciona correctamente el proveedor.
4. Una configuración con un proveedor desconocido falla de forma clara.
5. Los errores y timeouts del proveedor se traducen correctamente.
6. Se pueden probar los servicios de investigación utilizando un proveedor simulado/mock, sin necesitar Ollama en ejecución.
7. Los cambios no rompen los tests ni los flujos existentes de Kafka y persistencia.

No hagas tests que necesiten consumir servicios externos reales durante la ejecución normal de la batería de pruebas.

## Documentación

Actualiza la documentación técnica del microservicio para explicar:

- La nueva abstracción y su responsabilidad.
- Cómo se selecciona el proveedor.
- Cómo configurar Ollama.
- Cómo añadir una nueva implementación de `AIProvider`.
- Qué parámetros son generales y cuáles son específicos de cada proveedor.
- Cómo ejecutar las pruebas sin levantar un modelo local.

Incluye ejemplos basados en las propiedades reales del proyecto, no ejemplos ficticios que no funcionen.

## Criterios de aceptación

La tarea se considerará terminada cuando:

- El microservicio siga funcionando con Ollama como hasta ahora.
- La lógica de investigación dependa de la abstracción y no de Ollama.
- La selección del proveedor se realice mediante configuración.
- El contrato común no esté contaminado con tipos específicos de Ollama.
- Sea posible probar la lógica de investigación con un mock de `AIProvider`.
- No se hayan alterado los contratos Kafka, la persistencia, las colecciones MongoDB ni los flujos funcionales existentes.
- Compilen el proyecto y pasen los tests pertinentes.
- La documentación permita incorporar otro proveedor posteriormente.

## Forma de trabajo y entrega

Trabaja sobre el proyecto existente y realiza los cambios directamente, pero siguiendo este orden:

1. Inspección y diagnóstico del acoplamiento actual.
2. Presentación de un plan breve.
3. Implementación incremental.
4. Ejecución de compilación y pruebas.
5. Revisión de posibles regresiones.
6. Actualización de documentación.

Al finalizar, presenta un resumen con:

- Archivos creados, modificados y eliminados.
- Diseño final de la abstracción.
- Cómo se selecciona Ollama.
- Qué habría que implementar para añadir un segundo proveedor.
- Comandos ejecutados y resultado de compilación y tests.
- Limitaciones o tareas pendientes.

**Importante:** si para cumplir algún requisito fuera necesario modificar un contrato Kafka, un esquema de persistencia o una funcionalidad ajena a la integración de IA, detente y explícalo antes de hacerlo. No amplíes el alcance por iniciativa propia.
