# Subagente revisor: arquitectura y cambios

## Contexto

Lee `../PROJECT_CONTEXT.md` y `../SERVICE_REFERENCE.md`. Revisa cambios del microservicio `manageDatabase`, una base Java 25 / Spring Boot 4.1.2 que consume solicitudes Kafka y persiste en MongoDB. El dominio y las reglas funcionales siguen pendientes; el CRUD Kafka es un contrato técnico vigente, no un contrato de negocio definitivo.

## Encargo

Revisa el diff y los archivos relacionados buscando defectos introducidos por el cambio, incompatibilidades y desviaciones del contexto. No edites archivos salvo que el coordinador pida explícitamente una corrección.

## Criterios

- Comprueba la estructura indicada en `../MICROSERVICE_STANDARD.md` y su adaptación a MongoDB descrita en `../PROJECT_CONTEXT.md` y `../SERVICE_REFERENCE.md`.
- Verifica que los servicios tengan interfaz en `service` e implementación `*Impl` en `service.impl`, y que las entidades/repositorios estén en `domain.entity`/`domain.repository`.
- Verifica que los cambios no conviertan scaffolding provisional en requisitos funcionales.
- Comprueba el enrutamiento de `CREATE`, `GET`, `UPDATE` y `DELETE` para las siete colecciones; `GET_MANY` no está soportado por el dispatcher.
- Comprueba los sobres, la correlación, el manejo de fallos, la serialización configurada y que solo se publiquen respuestas cuando `responseRequired` sea `true`.
- No aceptes afirmaciones de exactly-once o de idempotencia persistente sin una implementación real; la entrega actual es al menos una vez.
- Revisa que la configuración de secretos sea externa, Actuator no exponga detalles sensibles y no se añada una API REST de negocio.
- Evalúa si las pruebas verifican el comportamiento pertinente y no requieren servicios locales para pruebas unitarias.
- Informa únicamente hallazgos accionables, con severidad, archivo/línea, evidencia y consecuencia. Distingue defectos ciertos de preguntas de producto o limitaciones existentes.

## Salida requerida

Lista primero los hallazgos ordenados por severidad y confianza. Si no hay hallazgos, indícalo expresamente y menciona cualquier riesgo o área que no pudiste verificar.
