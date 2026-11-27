# Paquete de contexto para otras sesiones

Este directorio reúne el contexto estable del workspace y prompts reutilizables para continuar el trabajo con otra IA o sesión.

## Contenido

- [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md): contexto principal de `engineBroker`, arquitectura, configuración efectiva, logging y pruebas.
- [`chatgpt/ENGINEBROKER_CONTEXT.md`](chatgpt/ENGINEBROKER_CONTEXT.md): dossier único y autocontenido para proporcionar a ChatGPT el contexto técnico completo de este proyecto.
- [`SERVICE_REFERENCE.md`](SERVICE_REFERENCE.md): referencia del contrato del servicio externo `manageDatabase` usado por `engineBroker`.
- [`MICROSERVICE_STANDARD.md`](MICROSERVICE_STANDARD.md): estándar reutilizable de paquetes y prácticas para microservicios EDA con persistencia.
- [`agents/coordinator.md`](agents/coordinator.md): instrucciones para la IA que coordina una tarea y conserva el contexto entre sesiones.
- [`agents/implementation.md`](agents/implementation.md): instrucciones para un agente que implemente una tarea concreta en el microservicio.
- [`agents/reviewer.md`](agents/reviewer.md): instrucciones para un subagente que revise cambios de arquitectura, código y pruebas.

## Cómo usarlo

1. Para tareas en este repositorio, comparte `PROJECT_CONTEXT.md`; añade `SERVICE_REFERENCE.md` si afectan la integración Kafka con `manageDatabase`.
2. Para un agente que deba implementar o mantener `manageDatabase`, comparte `SERVICE_REFERENCE.md` y el contexto/documentación de ese repositorio; no trates `PROJECT_CONTEXT.md` como su documentación de implementación.
3. Copia el prompt del rol correspondiente desde `agents/` y añade la tarea concreta.
4. Para tareas con varios frentes, usa `coordinator.md` como prompt principal y asigna los prompts de implementación y revisión como subagentes.
5. Pide a la IA que inspeccione el estado actual del repositorio antes de editar: esta exportación describe la base técnica conocida y puede quedar desactualizada si el código evoluciona.

Los archivos de `agents/` son definiciones portables para copiar en una conversación o en un sistema de agentes; no son una configuración de agente ejecutable o específica de una plataforma.
