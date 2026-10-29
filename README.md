# Vacantes escolares

Proyecto académico de solicitudes de vacante para un colegio privado peruano, con Trilce como referencia. Incluye registro de apoderados, solicitudes, revisión de admisiones, seguimiento y auditoría. Las solicitudes del prototipo no constituyen una matrícula oficial.

Está desarrollado con Java 25, Spring Boot MVC, Thymeleaf, Spring Security, Spring Data JPA, MySQL y Flyway. Las contraseñas de usuarios se guardan con hash Argon2.


## Descargar y abrir

En GitHub: **Code > Download ZIP**, extrae el archivo y abre la carpeta que contiene `pom.xml` en IntelliJ IDEA.

- [Guía de instalación en PDF](GUIA-INSTALACION.pdf)
- [Guía en texto para copiar comandos](GUIA-INSTALACION.txt)

También puedes clonar el repositorio:

    git clone https://github.com/mesaresarroyo-7/vacantes-escolares.git

## Contenido y requisitos

Este paquete contiene el código fuente, las pruebas y `pom.xml`. Las migraciones SQL de `src/main/resources/db/migration` y el JavaScript de la interfaz pertenecen a la aplicación.

Instala por separado **JDK 25**, **Maven 3.9** y **MySQL Server 8.4**. Puedes usar IntelliJ IDEA para abrir y ejecutar el proyecto. La primera descarga de dependencias Maven requiere conexión a Internet.

## Preparar MySQL

En tu servidor MySQL, ejecuta lo siguiente con una cuenta administrativa. Sustituye el marcador de contraseña por una clave propia:

```sql
CREATE DATABASE vacantes_escolares
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'vacantes_app'@'127.0.0.1'
  IDENTIFIED BY 'REEMPLAZAR_POR_UNA_CLAVE_PROPIA';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON vacantes_escolares.* TO 'vacantes_app'@'127.0.0.1';
```

Si ya tienes la base y una cuenta propia con esos permisos, utiliza sus datos. Flyway crea las tablas al iniciar la aplicación; no hace falta importar una copia de otra computadora.

## Configurar y ejecutar

Abre en IntelliJ la carpeta que contiene `pom.xml`, importa el proyecto Maven y selecciona **JDK 25**. En la configuración de ejecución de `pe.edu.vacantes.VacantesApplication`, configura estas variables de entorno:

| Variable | Valor que debes configurar |
| --- | --- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/vacantes_escolares?connectionTimeZone=%2B00:00&forceConnectionTimeZoneToSession=true` |
| `DB_USERNAME` | `vacantes_app`, o tu usuario de MySQL. |
| `DB_PASSWORD` | La contraseña de ese usuario de MySQL. |
| `DEMO_ADMIN_PASSWORD` | Una contraseña propia de al menos 10 caracteres. |
| `DEMO_ADMISSIONS_PASSWORD` | Otra contraseña propia de al menos 10 caracteres. |
| `DEMO_GUARDIAN_PASSWORD` | Otra contraseña propia de al menos 10 caracteres. |
| `DEMO_OTHER_PASSWORD` | Otra contraseña propia de al menos 10 caracteres. |
| `CAPTCHA_MODE` | `google` (valor predeterminado). |
| `RECAPTCHA_SITE_KEY` | Tu clave del sitio Google reCAPTCHA v2. |
| `RECAPTCHA_SECRET_KEY` | Tu clave secreta Google reCAPTCHA v2. |
| `RECAPTCHA_HOSTNAMES` | `localhost,127.0.0.1` |

Ejecuta `VacantesApplication` con **Run**. También puedes configurar las mismas variables en tu terminal y ejecutar desde esta carpeta:

```text
mvn spring-boot:run
```

Abre **http://127.0.0.1:8087**. MySQL debe estar funcionando durante la ejecución del portal. La aplicación escucha en la propia computadora.

En el primer inicio se crean ofertas por grado, solicitudes ficticias y estas cuentas:

| Correo | Rol | Contraseña inicial |
| --- | --- | --- |
| `admin@colegio.test` | Administrador | La definida en `DEMO_ADMIN_PASSWORD`. |
| `admisiones@colegio.test` | Admisiones | La definida en `DEMO_ADMISSIONS_PASSWORD`. |
| `apoderado@colegio.test` | Apoderado | La definida en `DEMO_GUARDIAN_PASSWORD`. |
| `otro@colegio.test` | Otro apoderado | La definida en `DEMO_OTHER_PASSWORD`. |

El inicializador crea únicamente las cuentas que todavía no existen. Cambiar estas variables después no cambia las contraseñas de cuentas ya guardadas.

## Google reCAPTCHA v2

Este proyecto utiliza **Google reCAPTCHA v2 por defecto**. Antes del primer inicio, registra tu sitio en https://www.google.com/recaptcha/admin/create con tipo v2 y casilla "No soy un robot". Autoriza `localhost` y `127.0.0.1`. Configura las siguientes variables en IntelliJ:

| Variable | Valor |
| --- | --- |
| `CAPTCHA_MODE` | `google` |
| `RECAPTCHA_SITE_KEY` | Tu clave del sitio. |
| `RECAPTCHA_SECRET_KEY` | Tu clave secreta. |
| `RECAPTCHA_HOSTNAMES` | `localhost,127.0.0.1` |

Las claves se configuran en el equipo de ejecución y no se incluyen en este paquete. Sin ellas, el inicio se detiene con un mensaje de configuración: **no se sustituye Google por el CAPTCHA de números automáticamente**. El modo `local` queda disponible únicamente si se selecciona de forma explícita para demostraciones o pruebas.

## Pruebas y compilación

```text
mvn test
mvn package
```

Las pruebas usan H2 en memoria. El empaquetado genera `target/vacantes-escolares.jar`. Para ejecutarlo, mantén configuradas las variables de entorno y usa:

```text
java -jar target/vacantes-escolares.jar
```

Si el inicio informa un error de conexión, comprueba que MySQL está activo en `127.0.0.1:3306` y que los datos de conexión corresponden a tu equipo. Si faltan contraseñas de demostración, configura las cuatro variables `DEMO_*` antes del primer inicio.
