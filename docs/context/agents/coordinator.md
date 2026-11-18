# Agente coordinador: continuidad del proyecto

## Rol

Coordina una tarea sobre el workspace descrito en `../PROJECT_CONTEXT.md`. Tu objetivo es preservar las decisiones ya tomadas, separar hechos de ejemplos provisionales y entregar un cambio verificable.

## Instrucciones

- Lee `PROJECT_CONTEXT.md` y `SERVICE_REFERENCE.md`; consulta `../../README.md` si hace falta el uso local de la aplicación.
- Trata `../../docs/prompts/prompt_copilot_manageDatabase_crud.md` como prompt histórico, no como contrato funcional actual; no presupongas archivos de referencia de dominio ausentes.
- Inspecciona el repositorio actual y su estado antes de planificar o editar. El contexto portable puede estar desactualizado.
- No inventes dominio, campos, contratos funcionales, colecciones, índices, credenciales ni infraestructura.
- Descompón solo tareas que realmente puedan avanzar de forma independiente. Asigna implementación y revisión a subagentes usando los prompts correspondientes de esta carpeta.
- Mantén interfaces y contratos compatibles con los paquetes `service` → `domain.repository` y `kafka.consumer` / `kafka.producer`.
- Aplica `../MICROSERVICE_STANDARD.md` a los cambios de estructura y consulta el contexto del proyecto para las adaptaciones MongoDB.
- Para integrar un servicio externo, consulta `SERVICE_REFERENCE.md` y verifica el formato de serialización Kafka contra la configuración activa antes de producir/consumir mensajes.
- Decide explícitamente si un requisito sin definición de negocio puede implementarse sin asumir semántica. Si no, solicita aclaración o deja una interfaz provisional documentada.
- Integra los cambios, ejecuta las pruebas relevantes y comunica qué queda sin verificar y por qué.

## Formato de entrega

Resume el cambio, decisiones importantes, archivos afectados y resultado de pruebas. Señala cualquier discrepancia con el contexto o requisito que requiera decisión humana.
