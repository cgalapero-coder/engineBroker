# Paquete de contexto para otras sesiones

Este directorio reúne el contexto estable del workspace y prompts reutilizables para continuar el trabajo con otra IA o sesión.

## Contenido

- [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md): resumen principal de arquitectura, límites y decisiones actuales.
- [`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md): referencia detallada para integrar otros microservicios: envelopes Kafka, topics, configuración, colecciones/campos Mongo, pruebas y límites conocidos.
- [`MICROSERVICE_STANDARD.md`](MICROSERVICE_STANDARD.md): estándar reutilizable de paquetes y prácticas para microservicios de negocio EDA con persistencia en BBDD.
- [`agents/coordinator.md`](agents/coordinator.md): instrucciones para la IA que coordina una tarea y conserva el contexto entre sesiones.
- [`agents/implementation.md`](agents/implementation.md): instrucciones para un agente que implemente una tarea concreta en el microservicio.
- [`agents/reviewer.md`](agents/reviewer.md): instrucciones para un subagente que revise cambios de arquitectura, código y pruebas.

## Cómo usarlo

1. Para un agente que deba integrar o crear otro microservicio consumidor, comparte `PROJECT_CONTEXT.md` y `SERVICE_REFERENCE.md`; incluye `MICROSERVICE_STANDARD.md` si también debe seguir el estándar estructural.
2. Copia el prompt del rol correspondiente desde `agents/` y añade la tarea concreta.
3. Para tareas con varios frentes, usa `coordinator.md` como prompt principal y asigna los prompts de implementación y revisión como subagentes.
4. Pide a la IA que inspeccione el estado actual del repositorio antes de editar: esta exportación describe la base técnica conocida y puede quedar desactualizada si el código evoluciona.

Los archivos de `agents/` son definiciones portables para copiar en una conversación o en un sistema de agentes; no son una configuración de agente ejecutable o específica de una plataforma.
