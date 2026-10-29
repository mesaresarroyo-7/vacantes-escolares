# Vacantes escolares

Proyecto académico de solicitudes de vacante para un colegio privado peruano, con Trilce como referencia. Incluye registro de apoderados, solicitudes, revisión de admisiones, seguimiento y auditoría. Las solicitudes del prototipo no constituyen una matrícula oficial.

Java 25, Spring Boot MVC, Thymeleaf, Spring Security, Spring Data JPA, MySQL y Flyway. Las contraseñas de las cuentas se guardan con hash Argon2. **Google reCAPTCHA v2 es el modo predeterminado.**

## Ejecutar la entrega preparada

El ZIP de entrega incluye el código fuente, las guías y `vacantes-escolares.jar`. No incluye instaladores ni atajos `.cmd` o `.ps1`.

Para ejecutar el JAR necesitas **Java 25**, **MySQL Server 8.4** y conexión a Internet para Google reCAPTCHA. **Maven e IntelliJ sirven para editar o compilar el código; no hacen falta para ejecutar el JAR preparado.**

La preparación se hace **una sola vez en cada PC**:

1. Extrae el ZIP completo en una carpeta local.
2. Prepara la base MySQL como se explica más abajo.
3. Copia `application-local.properties.example` y cambia el nombre de la copia a **`application-local.properties`**. Comprueba que Windows no añada `.txt` al nombre.
4. Abre la copia con un editor y rellena los campos vacíos: contraseña MySQL, cuatro contraseñas de demostración y dos claves de Google. Conserva el modo `google`.
5. Abre una terminal en esa carpeta y ejecuta:

```text
java -jar vacantes-escolares.jar --spring.config.additional-location=file:./application-local.properties
```

6. Espera `Started VacantesApplication` y abre **http://127.0.0.1:8087**.

MySQL debe permanecer activo mientras usas el portal. Para detenerlo, pulsa **Ctrl+C** en la terminal de la aplicación. En los siguientes inicios solo debes iniciar MySQL, ejecutar el mismo comando y abrir el navegador.

El archivo local contiene la configuración de esa PC y queda excluido de Git. El paquete distribuye únicamente el ejemplo vacío. Si una contraseña contiene una barra invertida, escríbela como `\\` en el archivo `.properties`.

Si descargaste únicamente el código y no aparece el JAR en la carpeta, compílalo siguiendo **Editar o compilar el código**.

## Preparar MySQL una vez

Conéctate a tu servidor desde Workbench con una cuenta administrativa. Cambia el marcador de contraseña y ejecuta el bloque completo:

```sql
CREATE DATABASE vacantes_escolares
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'vacantes_app'@'127.0.0.1'
  IDENTIFIED BY 'REEMPLAZAR_POR_UNA_CLAVE_PROPIA';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON vacantes_escolares.* TO 'vacantes_app'@'127.0.0.1';
```

Esa contraseña es la que debes escribir en `spring.datasource.password`. La aplicación usa `vacantes_app`; no necesita ejecutarse como `root`.

Si la base o el usuario ya existen, utiliza sus datos y revisa sus permisos. **No ejecutes manualmente los SQL de `src/main/resources/db/migration`: Flyway crea y actualiza las tablas al iniciar.** No hace falta importar la base de otra computadora.

La conexión del ejemplo apunta a `127.0.0.1:3306`, base `vacantes_escolares`, usando UTC en la conexión. La interfaz muestra las fechas en `America/Lima`.

## Configuración local y Google reCAPTCHA v2

Completa estos campos en `application-local.properties`:

| Propiedad | Valor |
| --- | --- |
| `spring.datasource.username` | `vacantes_app` o tu cuenta MySQL. |
| `spring.datasource.password` | La contraseña elegida para esa cuenta. |
| `app.seed.admin-password` | Tu contraseña inicial de administrador, mínimo 10 caracteres. |
| `app.seed.admissions-password` | Tu contraseña inicial de admisiones, mínimo 10 caracteres. |
| `app.seed.guardian-password` | Tu contraseña inicial del apoderado, mínimo 10 caracteres. |
| `app.seed.other-password` | Tu contraseña inicial del otro apoderado, mínimo 10 caracteres. |
| `app.captcha.mode` | `google`. |
| `app.captcha.site-key` | Tu clave del sitio de Google reCAPTCHA v2. |
| `app.captcha.secret-key` | Tu clave secreta de Google reCAPTCHA v2. |
| `app.captcha.allowed-hostnames` | `localhost,127.0.0.1`. |

Obtén las claves desde la [consola de reCAPTCHA](https://www.google.com/recaptcha/admin/create): registra el tipo **v2, casilla “No soy un robot”** y autoriza `localhost` y `127.0.0.1`, sin protocolo ni puerto.

Las claves reales pertenecen a la configuración del equipo de ejecución y no vienen en el proyecto. Sin ellas el arranque se detiene con un mensaje de configuración. No se cambia automáticamente al CAPTCHA de números. El modo `local` solo se utiliza si se selecciona expresamente para pruebas o demostraciones separadas.

En una base nueva se necesitan las **cuatro contraseñas de demostración**, además de la contraseña MySQL y las claves de Google. Las propiedades locales corresponden a las mismas opciones que las variables `DB_*`, `DEMO_*` y `RECAPTCHA_*` usadas en la configuración mediante variables de entorno; el comando de arriba carga directamente el archivo local.

## Cuentas y comprobación inicial

| Cuenta | Rol | Contraseña inicial |
| --- | --- | --- |
| `admin@colegio.test` | Administrador | La definida en `app.seed.admin-password`. |
| `admisiones@colegio.test` | Admisiones | La definida en `app.seed.admissions-password`. |
| `apoderado@colegio.test` | Apoderado | La definida en `app.seed.guardian-password`. |
| `otro@colegio.test` | Otro apoderado | La definida en `app.seed.other-password`. |

El primer inicio prepara ofertas por grado y solicitudes ficticias. El inicializador crea únicamente las cuentas que no existen: cambiar una propiedad después **no cambia la contraseña de una cuenta guardada**.

En Workbench, crea una conexión con usuario `vacantes_app`, host `127.0.0.1`, puerto `3306` y esquema `vacantes_escolares`. Usa la contraseña MySQL que elegiste. Despliega **SCHEMAS > vacantes_escolares > Tables**.

## Excepciones, logs y auditoría de la clase 6

- Las excepciones de negocio identifican solicitudes inexistentes, solicitudes duplicadas, falta de vacantes y estados que ya no admiten cambios.
- El manejador global MVC presenta mensajes comprensibles y conserva los controles de permisos y propiedad de las solicitudes.
- Los eventos relevantes se guardan en **`logs/vacantes-escolares.log`**, relativo a la carpeta de ejecución, con rotación de archivos.
- La tabla **`audit_events`** conserva actor, acción, referencia, detalle, fecha, nivel (`level`) e IP de origen (`origin_ip`). La auditoría de rechazos se conserva aunque la operación de negocio no se complete.
- Los registros saneados evitan guardar contraseñas, tokens y documentos completos.

Para una demostración funcional de unos **7 minutos**:

1. Muestra Google reCAPTCHA en el registro y el acceso con roles.
2. Como apoderado, revisa sus solicitudes y corrige una observada.
3. Como admisiones, revisa la solicitud y comunica un resultado.
4. Provoca un rechazo controlado, por ejemplo una solicitud activa duplicada, y muestra el mensaje amigable.
5. Abre la auditoría como administrador y el archivo de logs; relaciona los eventos con la acción realizada.

La indicación de la clase 6 fue mostrar el funcionamiento y dónde se aplican los mecanismos. No exige informe ni PPT; el profesor puede preguntar sobre código.

## Editar o compilar el código

Instala **JDK 25**, **Maven 3.9** e IntelliJ IDEA. Abre la carpeta de `pom.xml`, impórtala como Maven y selecciona JDK 25. Si aparece `[untrusted]`, elige **Trust Project** para este proyecto y deja desmarcadas las exclusiones de Microsoft Defender.

Desde una terminal en la carpeta de `pom.xml`:

```text
mvn test
mvn package
java -jar target/vacantes-escolares.jar --spring.config.additional-location=file:./application-local.properties
```

Las pruebas usan H2 en memoria. El empaquetado genera `target/vacantes-escolares.jar`. Puedes copiar ese JAR a la raíz para sustituir el de la entrega y usar el comando de inicio corto.

Para ejecutar `pe.edu.vacantes.VacantesApplication` desde IntelliJ, crea una configuración **Application**, selecciona JDK 25 y añade en **Program arguments**:

```text
--spring.config.additional-location=file:./application-local.properties
```

Usa como Working directory la carpeta que contiene el archivo local. El proyecto no carga un archivo `.env` por sí mismo.

## Errores de preparación

| Mensaje | Qué comprobar |
| --- | --- |
| Java 25 no compatible / `release 25` | `java -version`, JDK del proyecto y JRE de ejecución. |
| `Connection refused` | MySQL iniciado y puerto correcto. |
| `Access denied` | Usuario MySQL, contraseña y permisos de `vacantes_app@127.0.0.1`. |
| `Unknown database` | Crear la base vacía `vacantes_escolares`. |
| Faltan claves de reCAPTCHA | Rellenar las dos claves del sitio v2 en el archivo local. |
| Faltan claves de demostración | Rellenar las cuatro propiedades `app.seed.*-password`, con mínimo 10 caracteres. |
| No encuentra `application-local.properties` | Ejecutar desde la carpeta correcta y revisar que no termine en `.txt`. |
| Puerto 8087 ocupado | Detener la otra instancia o elegir otro `server.port` en el archivo local. |

Consulta también [la guía en texto](GUIA-INSTALACION.txt) y [la guía PDF](GUIA-INSTALACION.pdf).
