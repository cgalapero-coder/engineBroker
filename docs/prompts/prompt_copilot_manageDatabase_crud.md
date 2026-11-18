# GitHub Copilot — Implementación CRUD en `manageDatabase`

> Prompt histórico de la fase inicial de persistencia CRUD. No es el contrato técnico vigente ni la especificación de negocio. Para el estado actual, consulta `docs/context/PROJECT_CONTEXT.md`, `docs/context/SERVICE_REFERENCE.md` y el código/configuración del repositorio.

## Objetivo

Implementar el código de acceso y persistencia necesario para disponer de operaciones CRUD sobre las siete colecciones definidas para Automatic Broker, **dentro del microservicio `manageDatabase` que ya existe**.

El microservicio, su estructura general y el contexto funcional del proyecto ya están disponibles en el repositorio y en el contexto de trabajo de Copilot. No hay que crear un microservicio nuevo ni volver a diseñar el proyecto.

La tarea consiste en **añadir e integrar el código que falta en los paquetes, capas y ubicaciones que correspondan dentro de la arquitectura existente**, respetando sus convenciones.

## Aclaración tecnológica importante

El modelo de datos definido para esta funcionalidad utiliza **MongoDB**. Por tanto, para implementar repositorios sobre MongoDB con Spring se debe utilizar **Spring Data MongoDB** (`MongoRepository`, `MongoTemplate`, anotaciones BSON/MongoDB, etc.).

**No utilizar Spring Data JPA para acceder a MongoDB**: JPA está orientado a bases de datos relacionales y no es el módulo de persistencia adecuado para MongoDB.

Antes de modificar dependencias o configuración, inspecciona el proyecto y confirma qué tecnología de persistencia está configurada actualmente. Si el proyecto ya utiliza MongoDB, reutiliza esa configuración y sus dependencias. No añadas JPA ni cambies la tecnología de base de datos. Si el repositorio revela que la base de datos real es relacional y que JPA ya es la tecnología adoptada, detente y señala la discrepancia antes de implementar, porque supondría cambiar el modelo de persistencia acordado.

## Alcance funcional

Implementar CRUD independiente para estas colecciones MongoDB, respetando exactamente sus nombres:

| Colección | Responsabilidad |
|---|---|
| `manageUserData` | Administración de usuarios |
| `biData` | Información estructurada para Business Intelligence |
| `histData` | Histórico de actividad de usuarios e IA |
| `stadisticData` | Estadísticas y métricas |
| `webData` | Catálogo de webs y servicios externos |
| `opData` | Definición de operaciones disponibles |
| `globalConfiguration` | Configuración general |

Las estructuras JSON de referencia ya forman parte del contexto/documentación del proyecto. Utilízalas como fuente para crear o completar los modelos. No inventes campos ni cambies nombres o estructuras sin una razón técnica imprescindible; si detectas una inconsistencia, documenta la observación.

## Instrucciones de integración en el proyecto existente

1. Inspecciona el código actual antes de realizar cambios.
2. Identifica el paquete raíz, módulos, capas, convenciones de nombres, configuración de MongoDB, gestión de errores y estilo de pruebas.
3. Localiza dónde deben incorporarse los modelos, repositorios, servicios y, si procede, controladores o interfaces de entrada.
4. Reutiliza clases, interfaces, utilidades, configuración y dependencias que ya existan.
5. No dupliques componentes ni crees estructuras paralelas a las existentes.
6. No reemplaces ni refactorices código ajeno a esta funcionalidad.
7. No crees otro proyecto, otro microservicio ni una nueva aplicación Spring Boot.
8. No modifiques `pom.xml` o `build.gradle` salvo que sea imprescindible y después de comprobar que la dependencia no existe ya.
9. Mantén la compatibilidad con las versiones de Java, Spring Boot y librerías que utiliza el repositorio.
10. No sobrescribas configuraciones existentes con valores de ejemplo.

Si alguna capa ya está implementada, complétala o reutilízala en lugar de crear otra.

## Modelos de datos

Crear o completar un modelo por colección, siguiendo las convenciones del proyecto.

Criterios:

- Nombres de propiedades en inglés y `camelCase`.
- Respetar los campos y estructuras de los JSON de referencia.
- Mantener arrays y objetos anidados como estructuras equivalentes en Java.
- En esta primera fase, conservar los tipos de datos definidos en los JSON, mayoritariamente `String`, incluidos los campos que posteriormente podrían convertirse en fechas, importes o booleanos.
- Todos los campos distintos del identificador son opcionales en esta fase.
- No introducir validaciones de negocio ni autorización avanzada que todavía no estén definidas.
- Mapear expresamente cada modelo a su nombre de colección, evitando que una convención automática cambie los nombres.

Las colecciones incluyen, entre otros, estos campos:

- `manageUserData`: `dni`, `firstName`, `lastName`, `email`, `phone`, `postalAddress`, `country`, `appRoles`, `values`, `access`, `operationsSummary`, `preferences`, `decisions`, `bankAccounts`, `brokers`, `contractedServices`.
- `biData`: `sourceId`, `sourceName`, `market`, `instrument`, `dataType`, `value`, `unit`, `currency`, `observedAt`, `collectedAt`, `description`.
- `histData`: `actorType`, `userId`, `userRole`, `eventType`, `operationId`, `operation`, `occurredAt`, `simulationData`, `result`, `description`.
- `stadisticData`: `scope`, `userId`, `metric`, `value`, `unit`, `period`, `periodStart`, `periodEnd`, `calculatedAt`, `description`.
- `webData`: `url`, `auth`, `date`, `operations`, `roles`, `type`, `description`, `enabled`.
- `opData`: `description`, `prompt`, `limitations`, `roles`.
- `globalConfiguration`: `selectedOptions`, `mode`, `dataToStore`, `logLevel`, `language`, `operationalLimits`, `otherParameters`.

No conviertas todavía los campos a tipos numéricos, fechas o booleanos: esa evolución queda fuera de esta tarea.

## Identificadores

Los JSON de referencia incluyen un campo lógico `id`. MongoDB dispone además de su identificador técnico `_id`.

- Inspecciona cómo se identifican actualmente los documentos en el proyecto.
- Mantén una única estrategia coherente para todas las colecciones.
- Si `id` se mantiene como campo separado de `_id`, garantiza su unicidad mediante un índice único.
- Si se decide utilizar `_id` como identificador de aplicación, documenta la equivalencia y evita mantener dos identificadores inconsistentes.
- No implementes un autoincremento ingenuo basado en leer el máximo identificador y sumarle uno: no es seguro con concurrencia.
- Si el proyecto ya dispone de un generador de identificadores, reutilízalo.
- Si no existe, utiliza una solución segura y centralizada, compatible con varias instancias concurrentes. No introduzcas una secuencia autoincremental sin justificarla y documentar sus garantías.
- La generación de identificadores debe quedar desacoplada de los servicios CRUD.

## Repositorios y servicios

Implementar el acceso a datos utilizando el mecanismo de Spring Data adecuado para la tecnología existente (para MongoDB, Spring Data MongoDB).

Cada colección debe disponer de operaciones de persistencia claramente identificables. Se puede reutilizar código común cuando aporte valor, pero sin crear una abstracción genérica compleja que dificulte el mantenimiento.

Implementar una capa de servicio que contenga la lógica de las operaciones y que no dependa de HTTP ni de Kafka.

### Operaciones mínimas por colección

**Create**
- Recibir el modelo correspondiente.
- Generar el identificador si procede.
- Comprobar duplicidades cuando corresponda.
- Persistir y devolver el documento creado.

**Read**
- Consultar por identificador.
- Consultar todos los documentos.
- Mantener el diseño preparado para añadir paginación, ordenación y filtros.
- No es necesario implementar un motor avanzado de filtros dinámicos en esta fase.

**Update**
- Localizar el documento por su identificador.
- Actualizar los datos recibidos.
- No permitir que se modifique el identificador.
- Evitar que una actualización parcial elimine campos existentes que no hayan sido enviados.
- Devolver el documento actualizado.

**Delete**
- Eliminar físicamente el documento identificado.
- Distinguir entre documento eliminado y documento inexistente.
- No implementar borrado lógico.

Si la arquitectura existente ya define DTOs, mappers o convenciones para las respuestas, utilizarlos. No introducir capas adicionales sin necesidad.

## Interfaces de acceso

Implementar las interfaces de entrada que encajen con el diseño actual del microservicio.

- Si el microservicio ya expone API REST para operaciones de administración o pruebas, integrar ahí los endpoints CRUD siguiendo sus convenciones.
- Si no existe esa interfaz, no imponer una API REST nueva sin justificarlo: dejar los servicios invocables desde otros componentes.
- No acoplar los servicios de persistencia a controladores.

La interfaz de acceso deberá permitir identificar claramente la colección y la operación solicitada, según el patrón ya utilizado en el proyecto.

## Kafka: fuera del alcance de la fase inicial de persistencia CRUD

La integración Kafka se desarrollará posteriormente.

En esta tarea:

- No crear productores ni consumidores Kafka.
- No añadir dependencias Kafka.
- No crear topics, eventos, listeners ni configuración Kafka.
- No implementar todavía el enrutamiento de peticiones recibidas por eventos.
- No introducir referencias a Kafka en modelos, repositorios o servicios CRUD.

Los servicios de persistencia deben poder ser invocados en el futuro desde consumidores Kafka sin modificar su lógica. La futura capa Kafka deberá actuar como adaptador de entrada y delegar en los servicios existentes.

## Índices y seguridad

Crear únicamente los índices básicos justificados para el CRUD:

- Índice único de `id` si se mantiene separado de `_id`.
- Índice único de `email` en `manageUserData` solo si el modelo y las reglas actuales confirman que debe ser único.
- Índice único de `url` en `webData` solo si se confirma que no puede haber duplicados.
- Para `histData`, valorar índices sobre `occurredAt` y sobre `userId + occurredAt` si encajan con las consultas previstas.

No crear todos los índices candidatos ni optimizar consultas que todavía no existen.

Seguridad:

- El campo `password` de los brokers no debe almacenarse en texto plano.
- No devolver credenciales en consultas CRUD generales.
- No registrar contraseñas ni datos sensibles en logs.
- No implementar ahora roles, permisos ni autorización avanzada.
- Mantener `appRoles` y `roles` como datos del modelo, sin implementar su semántica de seguridad.

## Gestión de errores

Reutilizar el mecanismo de excepciones y respuestas de error que ya tenga el microservicio.

Cubrir, como mínimo:

- Documento inexistente.
- Identificador duplicado.
- Error de conexión o persistencia.
- Datos de entrada no válidos.
- Error inesperado.

No exponer detalles internos, credenciales ni información sensible en las respuestas.

## Pruebas

Añadir pruebas siguiendo el framework y las convenciones ya utilizadas en el repositorio.

Cubrir las siete colecciones y, como mínimo:

- Alta.
- Consulta por identificador.
- Consulta general.
- Modificación.
- Eliminación.
- Identificador duplicado.
- Documento inexistente.
- Persistencia y recuperación de arrays y objetos anidados.
- Actualización sin pérdida accidental de campos no enviados.

Para pruebas de integración, utilizar una base MongoDB de pruebas aislada (por ejemplo, Testcontainers si ya encaja con las dependencias y versiones del proyecto). Nunca ejecutar pruebas destructivas contra la base de datos de desarrollo o producción.

## Criterios de aceptación

La tarea se considerará terminada cuando:

1. El código esté integrado en el microservicio existente y en los paquetes adecuados.
2. Existan modelos y operaciones CRUD para las siete colecciones.
3. Los nombres físicos de las colecciones y los campos JSON se respeten.
4. La configuración MongoDB existente siga funcionando.
5. Los identificadores no puedan duplicarse por errores de concurrencia.
6. Las actualizaciones no eliminen datos por accidente.
7. Las credenciales no se expongan ni se almacenen en texto plano.
8. Las pruebas compilen y se ejecuten correctamente.
9. El proyecto compile y arranque con su configuración habitual.
10. No se haya incorporado Kafka ni se haya creado un microservicio nuevo.

## Forma de ejecución

Trabaja sobre el repositorio actual y realiza los cambios directamente en los archivos correspondientes.

Antes de editar, presenta brevemente:
- La arquitectura y las capas existentes que has localizado.
- Los archivos que prevés crear o modificar.
- La tecnología de persistencia que has confirmado.

Después implementa la funcionalidad en incrementos coherentes. Compila y ejecuta las pruebas tras cada incremento relevante.

Al finalizar, proporciona un resumen de:
- Archivos creados y modificados.
- CRUD implementados por colección.
- Decisiones técnicas adoptadas.
- Pruebas ejecutadas y resultados.
- Cualquier punto que haya quedado pendiente.

No te limites a proponer código o mostrar ejemplos: **implementa los cambios directamente en el proyecto existente**.
