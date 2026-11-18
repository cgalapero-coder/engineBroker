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
  (`manageDatabase-local`), `auto-offset-reset: earliest`,
  `enable-auto-commit: false`, `StringDeserializer` para la clave y
  `JsonDeserializer` para el valor.
- Paquetes JSON de confianza: `com.gtech.automaticbroker.managedatabase.kafka.entity`.
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

## Compatibilidad pendiente

El listener actual (`KafkaRequestListener`) recibe `String` y
`KafkaMessageCodec` interpreta el valor como un objeto JSON serializado. El
`JsonDeserializer` de Spring Kafka normalmente necesita metadatos de tipo
Spring en el mensaje o un tipo predeterminado configurado; los productores
externos que envíen JSON crudo sin esos metadatos pueden no ser compatibles
con este perfil. Asimismo, `JsonSerializer` serializa el valor `String` como
JSON, lo que puede cambiar la forma del mensaje en el cable. La configuración
solicitada se limita al perfil local y no se cambió la lógica funcional.

Antes de usar estos serializadores con productores externos, hay que acordar
el contrato de serialización (incluidos los metadatos de tipo) o adaptar
conjuntamente el consumidor y los productores. Los eventos se decodifican
actualmente de forma explícita con `KafkaMessageCodec`; si se implementa
deserialización directa de DTO/eventos con Spring Kafka, se deben revisar y
restringir los paquetes de confianza a sus paquetes concretos.

La aplicación no requiere credenciales Kafka para esta infraestructura local.
