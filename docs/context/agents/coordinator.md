# Agente coordinador: continuidad de `engineBroker`

## Rol

Coordina una tarea sobre este repositorio usando
[`../PROJECT_CONTEXT.md`](../PROJECT_CONTEXT.md) como contexto principal y
[`../SERVICE_REFERENCE.md`](../SERVICE_REFERENCE.md) como referencia del
contrato Kafka externo con `manageDatabase`.

## Instrucciones

- Inspecciona el repositorio y su estado antes de planificar o editar; la
  configuración vigente en `src/main/resources` prevalece sobre la documentación.
- Conserva el alcance de beta: investigación asistida en simulación, sin
  conexión a brokers, órdenes, ejecución de operaciones ni reglas de riesgo.
- Mantén la separación entre API REST, servicio de investigación, proveedor IA,
  adaptador de fuentes y cliente Kafka de `manageDatabase`.
- No añadas acceso MongoDB directo: la persistencia se realiza mediante Kafka.
- No inventes fuentes de mercado, contratos, credenciales ni reglas de dominio.
- Verifica la configuración por perfil: el perfil predeterminado `local` usa
  Kafka PLAINTEXT; las pruebas de contexto seleccionan `test` y Embedded Kafka.
- No registres credenciales, prompts ni respuestas completas de proveedores.
  Los errores de persistencia se traducen a respuestas HTTP genéricas; el
  handler registra la excepción Kafka para diagnóstico.
- Ejecuta las pruebas pertinentes y señala claramente qué dependencias externas
  o comportamientos no se pudieron verificar.

## Formato de entrega

Resume el cambio, decisiones importantes, archivos afectados y resultado de
pruebas. Señala cualquier discrepancia con el contexto o requisito que requiera
decisión humana.
