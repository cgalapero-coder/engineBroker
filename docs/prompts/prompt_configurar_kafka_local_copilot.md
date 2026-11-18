# Prompt para Copilot: configurar Kafka en el perfil local

Actúa como agente de desarrollo dentro del proyecto actual. Tu tarea es
**aplicar la configuración de Kafka para el entorno local**, comprobar
que encaja con la estructura existente y dejar documentados los
parámetros para poder reutilizarlos más adelante.

No te limites a proponer cambios: inspecciona el proyecto, realiza los
cambios necesarios y verifica el resultado.

## 1. Contexto de la infraestructura local

Kafka ya está desplegado y funcionando mediante Docker Desktop en el
equipo de desarrollo.

  -----------------------------------------------------------------------
  Parámetro                           Valor
  ----------------------------------- -----------------------------------
  Imagen del broker                   `apache/kafka:4.1.0`

  Modo de ejecución                   Docker Desktop, modo KRaft

  Número de brokers                   1

  Bootstrap server desde la           `localhost:9092`
  aplicación ejecutada en Windows     

  Bootstrap server desde otros        `kafka:9092`
  contenedores de la red Docker       

  Protocolo                           `PLAINTEXT`

  Autenticación                       Ninguna

  SSL/TLS                             Desactivado

  Particiones predeterminadas del     3
  broker                              

  Factor de replicación               1

  Interfaz de administración          `http://localhost:8080`
  -----------------------------------------------------------------------

**Importante:** para el perfil `local` de esta aplicación, que se
ejecuta directamente en Windows, utiliza `localhost:9092`. No utilices
`kafka:9092` en ese perfil: ese nombre solo es resoluble desde
contenedores conectados a la red Docker correspondiente.

No cambies la configuración de Docker, del broker ni de Kafka UI.

## 2. Inspección previa obligatoria

Antes de modificar archivos:

1.  Identifica la versión de Spring Boot y la herramienta de
    construcción utilizada (Maven o Gradle).
2.  Comprueba si `spring-kafka` ya está declarado directa o
    transitivamente. Si falta, incorpora la dependencia compatible con
    la versión de Spring Boot del proyecto, aprovechando la gestión de
    dependencias/BOM existente. No fijes una versión arbitraria.
3.  Localiza los archivos de configuración y determina cómo se nombran y
    activan los perfiles (`application-local.yml`,
    `application-local.yaml`, `application.yml`,
    `application.properties`, etc.).
4.  Revisa las convenciones actuales de configuración, nombres de
    aplicación y estructura de paquetes.
5.  Comprueba si ya existe alguna configuración Kafka para evitar
    duplicidades.

Respeta la arquitectura y las convenciones del repositorio. No
sustituyas archivos completos si basta con añadir las propiedades
necesarias.

## 3. Cambios que debes aplicar

### 3.1 Configuración del perfil local

Crea o completa el archivo correspondiente al perfil local,
preferentemente `application-local.yml` si esa es la convención del
proyecto.

Configura como mínimo:

-   `spring.kafka.bootstrap-servers`: `localhost:9092`.
-   Productor con serializador de clave `StringSerializer`.
-   Productor con serializador JSON de Spring Kafka.
-   `acks: all`.
-   Consumidor con un `group-id` derivado de `spring.application.name` y
    del perfil local, siempre que la aplicación tenga definido ese
    nombre.
-   `auto-offset-reset: earliest`.
-   `enable-auto-commit: false`.
-   Consumidor con deserializador de clave `StringDeserializer`.
-   Consumidor con deserializador JSON de Spring Kafka.
-   Confirmación del listener en modo `record`.

Como referencia, la configuración conceptual es:

``` yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
    consumer:
      group-id: ${spring.application.name}-local
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: <paquetes propios que deban deserializarse>
    listener:
      ack-mode: record
```

Adapta esta referencia a la versión de Spring Kafka y a la configuración
existente. No copies literalmente una propiedad si no es compatible con
la versión del proyecto.

**Seguridad de deserialización:** evita configurar
`spring.json.trusted.packages: "*"` salvo que el proyecto ya tenga una
decisión explícita y justificada para hacerlo. Prioriza los paquetes
concretos de los DTO/eventos que realmente vaya a consumir la
aplicación. Si todavía no existen esos paquetes, documenta que habrá que
definirlos al implementar los consumidores; no inventes paquetes.

Si `spring.application.name` no está definido, no introduzcas un nombre
ficticio: utiliza la convención real del proyecto o deja indicado el
dato que falta.

### 3.2 Dependencia

Si no existe, añade `spring-kafka` en el `pom.xml` o `build.gradle`
correspondiente.

-   Utiliza la versión gestionada por Spring Boot cuando proceda.
-   No dupliques dependencias.
-   No actualices versiones generales del proyecto.
-   No añadas librerías de Kafka que no sean necesarias.

### 3.3 Alcance funcional

En esta tarea solo se debe preparar la infraestructura de configuración.

-   No inventes nombres de tópicos.
-   No crees tópicos automáticamente.
-   No implementes productores, consumidores, listeners, DTO ni eventos
    de negocio.
-   No modifiques los CRUD ni la lógica funcional existente.
-   No cambies la configuración de MongoDB ni de otros servicios.
-   No alteres los perfiles distintos de `local`, salvo que sea
    imprescindible para evitar una duplicidad y lo justifiques.

## 4. Guardar la configuración para reutilizarla

Además de aplicar los cambios, guarda en el repositorio una nota breve y
reutilizable, preferentemente en:

`docs/configuracion-kafka-local.md`

Si la carpeta `docs` no existe, créala. La nota debe recoger:

-   Los datos de conexión del broker local.
-   La diferencia entre `localhost:9092` (aplicación ejecutada en
    Windows) y `kafka:9092` (contenedores Docker).
-   Las propiedades Kafka incorporadas al perfil local.
-   La dependencia añadida y su gestión de versión.
-   Cualquier decisión o dato pendiente, especialmente los paquetes de
    deserialización JSON.
-   Cómo activar el perfil local según la configuración real del
    proyecto.

No guardes contraseñas ni secretos. En esta infraestructura local Kafka
no requiere credenciales.

## 5. Verificación

Al terminar:

1.  Ejecuta la compilación o comprobación equivalente disponible en el
    proyecto, sin saltarte los tests existentes.
2.  Comprueba que el archivo de configuración se carga con el perfil
    `local`.
3.  Verifica que no hay errores de sintaxis YAML ni propiedades
    incompatibles con la versión de Spring Boot/Spring Kafka.
4.  No afirmes que has probado una conexión real con Kafka si no has
    ejecutado una prueba efectiva.
5.  No modifiques ni elimines datos, volúmenes o contenedores Docker.

## 6. Informe final

Al finalizar, presenta un resumen con:

-   Archivos creados y modificados.
-   Dependencia incorporada (si era necesaria).
-   Configuración efectiva del perfil local.
-   Ubicación de `docs/configuracion-kafka-local.md`.
-   Comprobaciones ejecutadas y sus resultados.
-   Cualquier punto pendiente para una futura implementación de
    productores y consumidores.

Si encuentras una decisión que no puedes resolver inspeccionando el
proyecto, no inventes la respuesta: conserva el resto de cambios seguros
y explica claramente qué dato falta.
