# Configuración local de Kafka

## Conexión

El broker local usa `apache/kafka:4.1.0` en modo KRaft, con un broker,
protocolo `PLAINTEXT`, sin autenticación ni TLS. La aplicación ejecutada
directamente en Windows se conecta a `localhost:9092`. `kafka:9092` solo
es resoluble desde contenedores conectados a la red Docker del broker.
La interfaz de administración está disponible en `http://localhost:8080`.
El broker tiene 3 particiones predeterminadas y factor de replicación 1.

Esta configuración no modifica Docker, el broker ni Kafka UI.

## Perfil y propiedades

`src/main/resources/application-local.yml` configura:

- Bootstrap server: `localhost:9092`.
- Productor: `StringSerializer` para la clave, `JsonSerializer` para el
  valor y `acks: all`.
- Consumidor: group ID `${spring.application.name}-local`
  (`engineBroker-local`), `auto-offset-reset: earliest`,
  `enable-auto-commit: false`, `StringDeserializer` para clave y valor. El
  listener interpreta explícitamente el valor JSON como una respuesta de
  `manageDatabase`.
- Confirmación de listeners: `record`.

El perfil predeterminado está definido como `local` en
`application.yml`. También se puede activar explícitamente con
`SPRING_PROFILES_ACTIVE=local` o `--spring.profiles.active=local`.

## Prueba de integración contra el broker local

`KafkaConnectivityIntegrationTest` comprueba producción y consumo reales en
`dev.test.events` con la configuración del perfil `local`. Está deshabilitada
por defecto y requiere el broker disponible en `localhost:9092`, así como que
el tópico exista previamente; la prueba no crea tópicos. Para ejecutarla:

```powershell
.\mvnw.cmd -Dkafka.integration.enabled=true -Dtest=KafkaConnectivityIntegrationTest test
```

En IntelliJ, se puede habilitar agregando
`-Dkafka.integration.enabled=true` a las opciones de la VM de la ejecución.
La prueba usa un grupo de consumidor único por ejecución y no invoca el
listener de negocio. El mensaje de prueba permanece en el tópico de acuerdo
con la política de retención configurada en el broker.

## Dependencia

No se añadió ninguna dependencia: `pom.xml` ya incluye
`spring-boot-starter-kafka`, que aporta Spring Kafka. La versión la gestiona
Spring Boot mediante el parent del proyecto; no se fijó una versión adicional.

## Formato de los mensajes

El consumidor convierte los bytes del valor Kafka a `String` y el cliente de
`manageDatabase` interpreta esa cadena como JSON. Por tanto, las respuestas
deben publicarse como objetos JSON (por ejemplo, `{...}`), no como objetos
JSON serializados como una cadena escapada (`"{...}"`). La deserialización no
depende de cabeceras de tipo de Spring Kafka.

El productor de esta aplicación usa `JsonSerializer` para publicar los
envelopes de petición como objetos JSON. Coordina el formato JSON con los
productores externos y evita configurar `JsonDeserializer` para las respuestas:
ese deserializador espera un valor compatible con el tipo Java configurado y
falla si recibe un objeto JSON cuando el listener espera una cadena.

La aplicación no requiere credenciales Kafka para esta infraestructura local.
