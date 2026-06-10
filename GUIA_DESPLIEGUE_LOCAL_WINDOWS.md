# Guía de Despliegue Local en Windows — Wattimizer (Entorno de Desarrollo)

**Objetivo:** reproducir el entorno de desarrollo completo en un PC Windows limpio, con hot-reload de backend y frontend, sin VPS, sin dominio, sin HTTPS.

**Tiempo estimado primera vez:** 45–75 minutos.

---

## Índice rápido

1. [Preparación en el PC de origen](#parte-0)
2. [Instalar herramientas en el PC nuevo](#parte-1)
3. [Obtener el código (git clone)](#parte-2)
4. [Crear el fichero `.env`](#parte-3)
5. [Levantar infraestructura Docker](#parte-4)
6. [Inicializar la base de datos (scripts SQL)](#parte-5)
7. [Arrancar el backend en IntelliJ IDEA](#parte-6)
8. [Arrancar el frontend en VS Code](#parte-7)
9. [Configurar el Shelly Plug S Gen 3](#parte-8)
10. [Verificación end-to-end](#parte-9)
11. [Flujo de trabajo diario](#parte-10)
12. [Solución de problemas](#parte-11)

---

## Arquitectura del entorno local

```
[Shelly Plug S]                         [PC Windows (este))
       |                                         |
       | MQTT TCP :1883                          |
       v                                         |
[Mosquitto :1883] <── Docker ──> [TimescaleDB :5432]
       ^                                  ^
       |                                  |
[Spring Boot :8080] ────────────────────────
       ^
       | proxy /api  /ws-iot
       |
[Angular :4200] <──── Navegador
```

**Modo de ejecución elegido: Híbrido**

| Componente | Cómo corre | Motivo |
|---|---|---|
| TimescaleDB | Docker | Requiere extensión nativa TimescaleDB |
| Mosquitto | Docker | Configuración ya lista en el repo |
| Spring Boot | Proceso nativo (IntelliJ) | Debugger, DevTools hot-reload |
| Angular | Proceso nativo (`ng serve`) | Proxy integrado, hot-reload |
| Nginx | **No se usa en local** | Su función la hace el proxy de `ng serve` |

> El `docker-compose.yml` del repo está pensado para el VPS (tiene nginx con SSL y certbot).
> En local **solo arrancamos los servicios `timescaledb` y `mosquitto`** del compose.

---

<a name="parte-0"></a>
## PARTE 0 — Preparación en el PC de origen (antes de cambiar de máquina)

Haz esto antes de cerrar el PC donde ya funciona todo.

### 0.1 Verificar que el código está subido a GitHub

```powershell
git status
git log --oneline -5
```

Si hay cambios sin commitear que quieras llevar al otro PC, hazlo ahora:

```powershell
git add .
git commit -m "feat: estado antes de migración a otro PC"
git push
```

### 0.2 Verificar que `mosquitto/config/password_file` está en el repositorio

```powershell
git ls-files mosquitto/config/password_file
# Debe mostrar la ruta si el fichero está trackeado
```

Si no aparece, el `password_file` está en `.gitignore` y necesitarás copiarlo manualmente (USB, correo propio, etc.) al otro PC. Este fichero binario contiene las credenciales hasheadas del broker MQTT.

### 0.3 Anotar tus credenciales MQTT

Apunta el usuario y contraseña que usas para conectarte al broker (los que pusiste al generar el `password_file`). Los necesitarás al crear el `.env` en el otro PC. Los valores por defecto del proyecto son:

- Usuario: `gateway-service`
- Contraseña: `s3cr3t`

Si los cambiaste, usa los tuyos.

### 0.4 Anotar la MAC del Shelly

La MAC de tu enchufe físico (identificador único en el sistema) es: `9070694d3590`

---

<a name="parte-1"></a>
## PARTE 1 — Instalar herramientas en el PC nuevo

Instala en el orden indicado. Verifica cada paso antes de continuar.

### 1.1 Docker Desktop

**¿Para qué?** Para correr TimescaleDB y Mosquitto en contenedores sin instalarlos nativamente.

**Instalación:**

1. Ve a https://www.docker.com/products/docker-desktop/ y descarga el instalador para Windows.
2. Ejecuta el instalador. Cuando pregunte por el backend de virtualización, **selecciona WSL 2** (recomendado sobre Hyper-V).
3. Si el instalador dice que WSL 2 no está instalado, abre PowerShell como administrador y ejecuta:
   ```powershell
   wsl --install
   # Reinicia el PC si lo pide
   ```
4. Tras instalar y reiniciar, abre Docker Desktop. La primera vez descarga la distribución de Linux WSL y tarda 2-5 minutos.
5. Espera hasta que el icono de la ballena en la barra de tareas esté **verde** y ponga "Docker Desktop is running".

**Configuración recomendada en Docker Desktop:**

- Ve a `Settings → Resources → WSL Integration` y activa la integración con tu distribución de Linux.
- En `Settings → Resources → Advanced`, asigna al menos **2 CPU y 3 GB de RAM** si tu equipo lo permite.

**Verificación:**

```powershell
docker --version
# Docker version 27.x.x o superior
docker compose version
# Docker Compose version v2.x.x
```

> **Problema frecuente:** Si Docker Desktop pide activar la virtualización en BIOS, entra a la BIOS del PC (normalmente F2 o Supr al arrancar) y activa "Intel VT-x" o "AMD-V".

---

### 1.2 Git

**¿Para qué?** Para clonar el repositorio desde GitHub.

1. Ve a https://git-scm.com/downloads y descarga el instalador para Windows 64-bit.
2. Durante la instalación, acepta los valores por defecto excepto en la pantalla **"Adjusting your PATH environment"** donde debes seleccionar **"Git from the command line and also from 3rd-party software"**.
3. En **"Choosing the default behavior of `git pull`"** selecciona **"Rebase"** (más limpio para este proyecto).

**Verificación:**

```powershell
git --version
# git version 2.x.x
```

**Configuración inicial (si es la primera vez en este PC):**

```powershell
git config --global user.name "Tu Nombre"
git config --global user.email "tu@email.com"
```

---

### 1.3 JDK 26 (Eclipse Temurin)

**¿Para qué?** Para compilar y ejecutar el backend Spring Boot con IntelliJ IDEA.

1. Ve a https://adoptium.net/temurin/releases/?version=26
2. Selecciona: **Operating System: Windows**, **Architecture: x64**, **Package Type: JDK**, **Version: 26**.
3. Descarga el instalador `.msi`.
4. Ejecuta el instalador. **IMPORTANTE:** en la pantalla de características, marca las opciones:
   - ✅ **"Set JAVA_HOME variable"**
   - ✅ **"Add to PATH"**
5. Finaliza la instalación.

**Verificación:**

```powershell
java -version
# openjdk version "26" ...
echo $env:JAVA_HOME
# Debe mostrar algo como: C:\Program Files\Eclipse Adoptium\jdk-26.0.x-hotspot
```

Si `JAVA_HOME` no aparece, establécelo manualmente:
```powershell
# En Variables de Entorno del Sistema → Nueva variable de sistema:
# Nombre: JAVA_HOME
# Valor: C:\Program Files\Eclipse Adoptium\jdk-26.0.x-hotspot
```

---

### 1.4 Node.js 22 LTS

**¿Para qué?** Para instalar dependencias npm del frontend Angular y ejecutar `ng serve`.

1. Ve a https://nodejs.org/en/download y descarga la versión **22.x LTS** para Windows 64-bit (`.msi`).
2. Ejecuta el instalador con los valores por defecto. Asegúrate de que la opción **"Add to PATH"** está marcada.
3. Cuando pregunte "Do you want to install the necessary tools for native modules?", **no es necesario marcarlo** para este proyecto.

**Verificación:**

```powershell
node --version
# v22.x.x
npm --version
# 10.x.x
```

---

### 1.5 Angular CLI

**¿Para qué?** Para compilar y servir la aplicación Angular en modo desarrollo.

```powershell
npm install -g @angular/cli@latest
```

**Verificación:**

```powershell
ng version
# Angular CLI: 21.x.x  (o superior)
```

> Si `ng` no se reconoce, cierra y vuelve a abrir PowerShell para que recargue el PATH.

---

### 1.6 IntelliJ IDEA (para el backend)

**¿Para qué?** Para editar, compilar y depurar el backend Spring Boot con soporte nativo de Maven y Spring.

Si lo tienes instalado, salta al paso 6.3. Si no:

1. Ve a https://www.jetbrains.com/idea/download/ y descarga la **Community Edition** (gratuita) o la Ultimate si tienes licencia.
2. Instala con los valores por defecto.
3. En la primera pantalla de bienvenida, **no es necesario** configurar nada todavía.

---

### 1.7 VS Code (para el frontend)

1. Ve a https://code.visualstudio.com/ y descarga el instalador.
2. Instala con los valores por defecto marcando **"Add to PATH"**.
3. Extensiones recomendadas (instálalas dentro de VS Code, en el menú de extensiones):
   - **Angular Language Service** (identificador: `Angular.ng-template`)
   - **Biome** (identificador: `biomejs.biome`) — linter/formateador del proyecto

---

<a name="parte-2"></a>
## PARTE 2 — Obtener el código (git clone)

### 2.1 Autenticación con GitHub

Si el repositorio es **privado**, necesitas autenticarte. La forma más sencilla es con un token de acceso personal (PAT):

1. En GitHub → tu avatar → `Settings → Developer settings → Personal access tokens → Tokens (classic)` → `Generate new token`.
2. Selecciona el scope **`repo`** (acceso completo a repositorios privados).
3. Copia el token generado (solo se muestra una vez).
4. En la siguiente ventana de PowerShell, cuando git te pida contraseña, pega el token en lugar de la contraseña.

Alternativamente, si tienes SSH configurado:
```powershell
# Verificar si ya hay clave SSH:
Test-Path "$env:USERPROFILE\.ssh\id_ed25519.pub"
# Si devuelve False, genera una nueva:
ssh-keygen -t ed25519 -C "tu@email.com"
# Luego añade la clave pública a tu GitHub en Settings → SSH Keys
```

### 2.2 Clonar el repositorio

```powershell
# Elige la carpeta donde quieras el proyecto (ejemplo):
cd C:\Dev

# Con HTTPS (pedirá tu token PAT como contraseña):
git clone https://github.com/<tu-usuario>/wattimizer-app.git

# O con SSH (si tienes clave configurada):
git clone git@github.com:<tu-usuario>/wattimizer-app.git

cd wattimizer-app
```

### 2.3 Verificar la estructura del proyecto

```powershell
dir
# Debes ver: backend/  frontend/  mosquitto/  nginx/  docker-compose.yml  AGENTS.md  ...
```

Comprueba que el `password_file` de Mosquitto está presente:

```powershell
Test-Path "mosquitto\config\password_file"
# True — si devuelve False, cópialo manualmente desde el PC de origen
```

---

<a name="parte-3"></a>
## PARTE 3 — Crear el fichero `.env`

El fichero `.env` **no está en el repositorio** (contiene credenciales). Tienes que crearlo tú en la raíz del proyecto.

### 3.1 Crear el fichero

En VS Code o con el Bloc de notas, crea el fichero `wattimizer-app/.env` con este contenido:

```dotenv
# ── TimescaleDB ──────────────────────────────────────────────────────────────
# Contraseña para el usuario 'postgres' del contenedor Docker de la BD.
# Puedes poner cualquier valor; lo que importa es que sea consistente.
DB_PASSWORD=wattimizer_dev_pass

# ── Mosquitto MQTT ────────────────────────────────────────────────────────────
# Deben coincidir EXACTAMENTE con las credenciales que están en mosquitto/config/password_file.
# Los valores por defecto del proyecto son estos; cámbia los si los modificaste en el password_file.
PROD_MQTT_USER=gateway-service
PROD_MQTT_PASSWORD=s3cr3t

# ── Seguridad del backend ─────────────────────────────────────────────────────
# Clave secreta para firmar los tokens JWT (mínimo 32 caracteres).
PROD_JWT_SECRET=esta_es_una_clave_temporal_de_32_bytes_desarrollo

# Clave que protege el endpoint de creación de administradores.
PROD_ADMIN_KEY=clave_maestra_desarrollo_daw_2026
```

### 3.2 Verificar el fichero

```powershell
Get-Content .env
# Debe mostrar las 5 variables sin errores de codificación
```

> **¡NUNCA subas este fichero a GitHub!** Comprueba que `.env` aparece en el `.gitignore`:
> ```powershell
> Select-String -Path .gitignore -Pattern "^\.env"
> # Debe devolver la línea ".env"
> ```

---

<a name="parte-4"></a>
## PARTE 4 — Levantar la infraestructura Docker

### 4.1 Verificar que Docker Desktop está corriendo

El icono de la ballena en la barra de tareas debe estar **verde**. Si no está arrancado:

```powershell
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
# Espera 30-60 segundos a que arranque completamente
```

### 4.2 Arrancar solo los servicios de infraestructura

```powershell
# Desde la raíz del proyecto (wattimizer-app/)
docker compose --env-file .env up -d timescaledb mosquitto
```

La primera vez descargará las imágenes de Docker Hub (~500 MB para timescaledb-ha, ~30 MB para mosquitto). Puede tardar 2-10 minutos dependiendo de la conexión.

Salida esperada:
```
✔ Container db_iot       Started
✔ Container broker_mqtt  Started
```

### 4.3 Verificar que los contenedores están corriendo

```powershell
docker ps
```

Debes ver exactamente dos contenedores con estado `Up`:

```
CONTAINER ID   IMAGE                          NAMES         STATUS        PORTS
xxxxxxxxxxxx   timescale/timescaledb-ha:pg17  db_iot        Up X seconds  0.0.0.0:5432->5432/tcp
xxxxxxxxxxxx   eclipse-mosquitto:2.1.2-alpine broker_mqtt   Up X seconds  0.0.0.0:1883->1883/tcp
```

### 4.4 Verificar los logs de cada contenedor

```powershell
# Verificar TimescaleDB (espera el mensaje "database system is ready to accept connections")
docker logs db_iot --tail 20
# Línea clave: "database system is ready to accept connections"

# Verificar Mosquitto
docker logs broker_mqtt --tail 10
# Línea clave: "mosquitto version X.X.X running"
```

### 4.5 Verificar la conexión a la base de datos desde PowerShell

```powershell
# Conecta desde dentro del propio contenedor para verificar sin cliente externo
docker exec -it db_iot psql -U postgres -c "\l"
# Debe listar las bases de datos, incluyendo "wattimizer_db"
```

Si ves la lista, el contenedor está listo. Si ves un error de autenticación, revisa que `DB_PASSWORD` en el `.env` es correcto.

### 4.6 Verificar Mosquitto (opcional, requiere cliente MQTT)

Si tienes MQTT Explorer o `mosquitto_pub` instalado:

```powershell
# Desde dentro del contenedor (no requiere instalación extra):
docker exec -it broker_mqtt mosquitto_pub -h localhost -p 1883 -u gateway-service -P s3cr3t -t "test/ping" -m "hola"
# Si no hay error, el broker acepta conexiones autenticadas
```

---

<a name="parte-5"></a>
## PARTE 5 — Inicializar la base de datos

Este es el paso más crítico y tiene un orden estricto que **debes respetar**.

### Orden obligatorio de ejecución

```
1. Arrancar el backend (Hibernate crea las tablas)
2. Parar el backend
3. Script 00: activar extensiones (pgcrypto + timescaledb)
4. Script 01: convertir readings a hypertable
5. Script 02: añadir constraints y índices (tariffs-td-schema.sql)
6. Script 03: cargar calendario regulatorio CNMC (seed-tariff-calendar-slots.sql)
7. Script 04: insertar usuarios de desarrollo
8. Script 05: insertar dispositivo Shelly real
9. Script 06: insertar dispositivo de simulación
10. Arrancar el backend (operación normal)
```

### 5.1 Conectar con un cliente de BD

Usa TablePlus, pgAdmin, DBeaver o el que prefieras. Crea una conexión nueva:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `5432` |
| Base de datos | `wattimizer_db` |
| Usuario | `postgres` |
| Contraseña | El valor que pusiste en `DB_PASSWORD` en el `.env` (ej: `wattimizer_dev_pass`) |

### 5.2 Primer arranque del backend (solo para que Hibernate cree las tablas)

Sigue las instrucciones del [PASO 6](#parte-6) para configurar IntelliJ IDEA y arrancar el backend.

Una vez que veas en la consola de IntelliJ:
```
Started JwtAuthBackendDemoApplication in X.XXX seconds
```
**Para el backend** (botón rojo de Stop en IntelliJ). Ahora Hibernate ha creado todas las tablas. Puedes verificarlo en TablePlus con:

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
```

Debes ver: `alerts`, `devices`, `federated_identities`, `periods`, `readings`, `tariff_calendar_slots`, `tariff_contracted_powers`, `tariffs`, `users`.

### 5.3 Script 00 — Activar extensiones

Ejecuta el fichero `backend/src/main/resources/db/dev-seed/00-extensions.sql` en TablePlus.

Este script activa:
- `timescaledb`: necesaria para poder crear la hypertable en el siguiente paso.
- `pgcrypto`: permite generar hashes bcrypt directamente en SQL (para el seed de usuarios).

### 5.4 Script 01 — Convertir `readings` a hypertable

> **Por qué hace falta:** Hibernate crea `readings` como una tabla PostgreSQL normal. TimescaleDB necesita que la conviertas explícitamente en una hypertable para habilitar las capacidades de series temporales (chunking temporal, compresión, retención automática). Esto debe hacerse mientras la tabla está vacía.

Ejecuta `backend/src/main/resources/db/dev-seed/01-hypertable.sql`.

Resultado esperado en la consola de resultados:
```
create_hypertable
-----------------
(1,public,readings,t)
```

Verifica con:
```sql
SELECT hypertable_name, num_chunks FROM timescaledb_information.hypertables;
-- Debe aparecer: readings | 0
```

### 5.5 Script 02 — Añadir constraints e índices de tarifas

Ejecuta `backend/src/main/resources/db/tariffs-td-schema.sql`.

Este script:
- Elimina columnas legacy si las hubiera
- Añade los CHECK constraints de `access_tariff_code` y `geographic_zone`
- Crea los índices únicos de búsqueda del calendario

Si algún `ALTER TABLE` falla con "column already exists" o "constraint already exists", es normal en una base de datos ya migrada; puedes ignorarlo o revisar el error concreto.

### 5.6 Script 03 — Cargar el calendario regulatorio CNMC

Ejecuta `backend/src/main/resources/db/seed-tariff-calendar-slots.sql`.

Este script carga **336 filas** con el calendario regulatorio de REE/CNMC que cubre:
- Peaje 2.0TD: PENINSULA e ISLAS_BALEARES
- Peaje 3.0TD: PENINSULA e ISLAS_BALEARES

> Sin este seed, los endpoints de analítica de costes (`/api/v1/analytics/cost` y `/api/v1/analytics/ghost-consumption`) devuelven **0 €** para todos los contratos que usen esas combinaciones. El backend no falla (entra en modo degradado), pero los cálculos económicos no son fiables.

Verifica el resultado:
```sql
SELECT access_tariff_code, geographic_zone, COUNT(*) AS filas
FROM tariff_calendar_slots
GROUP BY access_tariff_code, geographic_zone
ORDER BY 1, 2;
```

### 5.7 Script 04 — Insertar usuarios de desarrollo

Ejecuta `backend/src/main/resources/db/dev-seed/03-seed-users-dev.sql`.

Este script crea dos usuarios con contraseñas hasheadas en bcrypt usando pgcrypto:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@wattimizer.dev` | `Admin_Wattimizer1!` | `ROLE_ADMIN` |
| `user@wattimizer.dev` | `User_Wattimizer1!` | `ROLE_USER` |

Verifica:
```sql
SELECT id, username, role, active FROM users;
```

### 5.8 Script 05 — Insertar el dispositivo Shelly real

Ejecuta `backend/src/main/resources/db/dev-seed/04-seed-device-shelly.sql`.

Inserta tu Shelly Plug S Gen 3 (MAC: `9070694d3590`) asignado al usuario administrador de desarrollo.

Verifica:
```sql
SELECT id, name, mac_address, is_simulated, user_id FROM devices;
```

### 5.9 Script 06 — Insertar el dispositivo de simulación

Ejecuta `backend/src/main/resources/db/dev-seed/05-seed-device-simulation.sql`.

Inserta un dispositivo virtual (MAC: `SIM000000001`, `is_simulated=true`) que el backend puede usar para generar lecturas artificiales sin hardware físico conectado.

---

<a name="parte-6"></a>
## PARTE 6 — Arrancar el backend en IntelliJ IDEA

### 6.1 Abrir el proyecto

1. Abre IntelliJ IDEA.
2. En la pantalla de bienvenida → `Open` (o `File → Open` si ya estaba abierto otro proyecto).
3. Navega a `C:\Dev\wattimizer-app\backend` y selecciona esa carpeta. **Ojo: abre la carpeta `backend`, no la raíz del proyecto.**
4. IntelliJ detecta el `pom.xml` automáticamente y pregunta si quieres abrir como proyecto Maven. Acepta.
5. Espera a que termine la barra de progreso inferior ("Indexing..." y "Resolving dependencies..."). Puede tardar 3-8 minutos la primera vez mientras descarga todas las dependencias de Maven.

### 6.2 Configurar el SDK (JDK 26)

1. Ve a `File → Project Structure` (atajo: `Ctrl+Alt+Shift+S`).
2. En la sección `Project`:
   - `Project SDK`: haz clic en el desplegable → `Add SDK → JDK`.
   - Navega a `C:\Program Files\Eclipse Adoptium\jdk-26.0.x-hotspot` y selecciónalo.
   - `Project language level`: elige **26 (Record patterns, pattern matching for switch)**.
3. Haz clic en `Apply` y `OK`.

### 6.3 Sincronizar dependencias Maven

1. Abre el panel de Maven en la parte derecha de IntelliJ (icono de `m` azul).
2. Haz clic en el botón **"Reload All Maven Projects"** (icono de flechas circulares en la barra del panel Maven).
3. Espera a que descargue todas las dependencias. La primera vez puede tardar bastante dependiendo de tu conexión (descarga ~200 MB).
4. Verifica que no hay errores en rojo en el panel Maven ni en la pestaña "Problems".

### 6.4 Crear la Run Configuration

1. Haz clic en el desplegable de configuraciones (arriba a la derecha de la barra de herramientas) → `Edit Configurations...`.
2. Haz clic en `+` (añadir) → `Spring Boot`.
3. Rellena los campos:

| Campo | Valor |
|---|---|
| **Name** | `Wattimizer Backend Dev` |
| **Main class** | `com.joselumartos.jwtauthbackenddemo.JwtAuthBackendDemoApplication` |
| **JRE** | `Project default (JDK 26)` |

4. Expande la sección **"Environment variables"** y haz clic en el icono de carpeta a la derecha del campo de texto.

5. En el editor de variables, añade cada una de estas (botón `+` para cada una):

| Nombre | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/wattimizer_db` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `wattimizer_dev_pass` ← el mismo que pusiste en `.env` |
| `MQTT_URL` | `tcp://localhost:1883` |
| `MQTT_USER` | `gateway-service` |
| `MQTT_PASSWORD` | `s3cr3t` |
| `JWT_SECRET` | `esta_es_una_clave_temporal_de_32_bytes_desarrollo` |
| `ADMIN_KEY` | `clave_maestra_desarrollo_daw_2026` |

> **¿Por qué estas variables en lugar de las del `.env`?**
> El `.env` lo lee `docker compose`, no IntelliJ. Para que el backend arranque nativamente con los valores correctos, debes declararlas en la Run Configuration. Las variables `SPRING_DATASOURCE_*` sobreescriben las propiedades `spring.datasource.*` del `application.properties` siguiendo el mecanismo de externalización de configuración de Spring Boot.

6. Haz clic en `OK` para cerrar el editor de variables y `Apply`+`OK` para guardar la configuración.

### 6.5 Arrancar el backend

Selecciona la configuración `Wattimizer Backend Dev` y pulsa el botón **Run** (triángulo verde) o `Shift+F10`.

**Mensajes esperados en la consola de IntelliJ:**

```
INFO - Starting JwtAuthBackendDemoApplication
INFO - HikariPool-1 - Start completed (conexión a PostgreSQL exitosa)
INFO - Connected to MQTT broker: tcp://localhost:1883
INFO - Started JwtAuthBackendDemoApplication in X.XXX seconds
```

**Mensajes de error que significan que algo falla:**

| Error | Causa | Solución |
|---|---|---|
| `Connection refused: localhost:5432` | El contenedor `db_iot` no está corriendo | `docker compose up -d timescaledb` |
| `Connection refused: localhost:1883` | El contenedor `broker_mqtt` no está corriendo | `docker compose up -d mosquitto` |
| `hostname can't be null` | `MQTT_URL` no está definida en la Run Configuration | Revisar paso 6.4 |
| `Bad credentials` / `Login failed` | `SPRING_DATASOURCE_USERNAME` o `PASSWORD` incorrectos | Verificar que coinciden con el `.env` |
| `Table 'readings' does not exist` | Hibernate aún no ha creado las tablas | Es normal si es el primer arranque |

### 6.6 Test rápido desde PowerShell

Con el backend corriendo, abre una nueva PowerShell y ejecuta:

```powershell
# Login con el usuario admin de desarrollo
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"admin@wattimizer.dev","password":"Admin_Wattimizer1!"}' `
  | Select-Object -ExpandProperty Content
```

Respuesta esperada:
```json
{"statusCode":"200","jwt":"eyJhbGciOiJIUzI1NiJ9..."}
```

Si recibes `401 Unauthorized`, verifica que ejecutaste el script `03-seed-users-dev.sql` y que el backend ha cargado los usuarios (puede necesitar reiniciarse si los insertaste mientras estaba corriendo).

---

<a name="parte-7"></a>
## PARTE 7 — Arrancar el frontend en VS Code

### 7.1 Abrir el proyecto en VS Code

1. Abre VS Code.
2. `File → Open Folder` → selecciona `C:\Dev\wattimizer-app\frontend`. **Ojo: abre la carpeta `frontend`, no la raíz del proyecto.**
3. VS Code abrirá la carpeta y puede preguntar si confías en los autores. Acepta.

### 7.2 Abrir una terminal integrada en VS Code

`Ctrl+ñ` (o `Terminal → New Terminal`). Verifica que la ruta de la terminal es la carpeta `frontend`:

```powershell
pwd
# C:\Dev\wattimizer-app\frontend
```

### 7.3 Instalar dependencias npm

```powershell
npm install --legacy-peer-deps
```

**¿Por qué `--legacy-peer-deps`?** Algunas dependencias de PrimeNG 21 y Angular 21 declaran peer dependencies con restricciones de versión ligeramente incompatibles entre sí. Este flag le dice a npm que resuelva los conflictos de forma más permisiva, como hacía npm v6. Sin él, la instalación aborta con errores ERESOLVE.

La instalación descarga ~600 MB en `node_modules/`. Tardará 2-5 minutos.

Verifica que terminó sin errores fatales:
```powershell
# No debe haber líneas con "npm ERR!" al final de la salida
```

### 7.4 Arrancar el servidor de desarrollo

```powershell
ng serve
```

**Mensajes esperados:**

```
▲ [WARNING] ... (avisos menores del compilador, son normales)
✔ Browser application bundle generation complete.

Initial chunk files | Names    | Raw size
main.js             | main     | 2.xx MB

Application bundle generation complete. [X.XXX seconds]

Watch mode enabled. Watching for file changes...
  ➜  Local:   http://localhost:4200/
  ➜  Network: http://192.168.x.x:4200/
```

> El servidor tarda 15-45 segundos en compilar la primera vez.

### 7.5 Verificar el proxy

El fichero `proxy.conf.json` del proyecto ya está configurado para redirigir:
- `/api/*` → `http://localhost:8080` (llamadas REST)
- `/ws-iot/*` → `http://localhost:8080` (WebSocket STOMP)
- `/oauth2/*` → `http://localhost:8080` (OAuth2 callbacks)

Puedes verificar que el proxy está activo mirando la salida inicial de `ng serve`:
```
Proxy created: /api, /oauth2  →  http://localhost:8080
Proxy created: /ws-iot        →  http://localhost:8080 (WebSocket)
```

### 7.6 Abrir la aplicación en el navegador

Abre Chrome o Firefox y ve a: **http://localhost:4200**

Debes ver la pantalla de login de Wattimizer.

**Prueba de login:**
- Usuario: `admin@wattimizer.dev`
- Contraseña: `Admin_Wattimizer1!`

Si el login funciona y ves el dashboard (aunque esté vacío de datos), el stack completo está funcionando.

### 7.7 Primer acceso: registrar cuenta de administrador (alternativa a los scripts SQL)

Si prefieres crear los usuarios a través de la API en lugar del script SQL, puedes usar Postman o curl. Para crear un administrador necesitas la cabecera secreta:

```powershell
# Crear cuenta admin via API
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/register/admin" `
  -Method Post `
  -Headers @{"X-Wattimizer-Admin-Secret" = "clave_maestra_desarrollo_daw_2026"; "Content-Type" = "application/json"} `
  -Body '{"username":"admin@wattimizer.dev","password":"Admin_Wattimizer1!"}' `
  | Select-Object -ExpandProperty StatusCode
# 201

# Crear cuenta usuario normal
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/register" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"username":"user@wattimizer.dev","password":"User_Wattimizer1!"}' `
  | Select-Object -ExpandProperty StatusCode
# 201
```

---

<a name="parte-8"></a>
## PARTE 8 — Configurar el Shelly Plug S Gen 3

### 8.1 Localizar la IP local del PC nuevo

El Shelly necesita la IP de red local del PC (no `localhost`, porque el Shelly es un dispositivo físico en la misma LAN).

```powershell
ipconfig | Select-String "IPv4"
# Ejemplo de salida:
#    IPv4 Address. . . . . . . . . . . : 192.168.1.105
```

Apunta esa IP. La llamaremos `<IP_PC>` en los pasos siguientes (ejemplo: `192.168.1.105`).

> **Consejo:** Si esta IP cambia cada vez que reinicias el PC (DHCP dinámico), configura una IP estática en el router para esta máquina, o establécela en Windows: `Panel de Control → Centro de redes → Adaptador → IPv4 → Usar la siguiente dirección IP`.

### 8.2 Abrir el puerto 1883 en el Firewall de Windows

Docker Desktop expone el puerto 1883, pero el Firewall de Windows puede bloquearlo para conexiones entrantes desde otros dispositivos de la LAN. Abre PowerShell **como Administrador** y ejecuta:

```powershell
# Crear regla de firewall para el broker MQTT
New-NetFirewallRule `
  -DisplayName "Wattimizer - Mosquitto MQTT" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 1883 `
  -Action Allow `
  -Profile Private,Domain

# Verificar que la regla se creó
Get-NetFirewallRule -DisplayName "Wattimizer - Mosquitto MQTT" | Select-Object DisplayName, Enabled, Action
```

**Verificación desde PowerShell** (el Shelly debe poder llegar al puerto):

```powershell
# Prueba de conectividad TCP al broker desde el propio PC
Test-NetConnection -ComputerName localhost -Port 1883
# TcpTestSucceeded: True
```

### 8.3 Acceder a la interfaz web del Shelly

1. Asegúrate de que el PC nuevo y el Shelly están en **la misma red WiFi o LAN**.
2. Localiza la IP del Shelly. Métodos:
   - Mira en la app **Shelly Cloud** (si lo tienes vinculado allí).
   - Mira en la lista de dispositivos DHCP de tu router (generalmente en `192.168.1.1` o `192.168.0.1`).
   - Usa un escáner de red como **Advanced IP Scanner** (gratuito).
3. Con la IP del Shelly, abre en el navegador: `http://<IP_del_Shelly>/` (por ejemplo: `http://192.168.1.42`)
4. Verás la interfaz web del dispositivo.

### 8.4 Configurar los parámetros MQTT

En la interfaz web del Shelly:

1. Ve a **Settings** (⚙️) → **MQTT**.
2. Activa la opción **Enable MQTT** si no está activa.
3. Rellena los campos:

| Campo | Valor |
|---|---|
| **Server** | `<IP_PC>` (la IP del PC nuevo, ej: `192.168.1.105`) |
| **Port** | `1883` |
| **Username** | `gateway-service` ← el usuario del `password_file` de Mosquitto |
| **Password** | `s3cr3t` ← la contraseña correspondiente |
| **Client ID** | Déjalo vacío (el Shelly genera uno automáticamente) |

4. Haz clic en **Save**.

El Shelly se reconectará al broker. En los logs de Mosquitto deberías ver la conexión:

```powershell
docker logs broker_mqtt --tail 20 -f
# Línea esperada: New client connected from <IP_Shelly> as shellyplugsg3-9070694d3590
```

### 8.5 Verificar que el Shelly está publicando datos

En los logs del backend (consola de IntelliJ), deberías ver:

```
DEBUG - Received MQTT message on topic: shellyplugsg3-9070694d3590/events/rpc
INFO  - Saved reading for device: 9070694d3590
```

Si no ves mensajes, comprueba:
1. Que el Shelly tiene conectividad WiFi (su LED debe estar blanco fijo).
2. Que el Shelly está publicando (en su interfaz web, en el panel de estado, debería verse actividad MQTT).
3. Que el `MqttConfig.java` del backend está suscrito al tópico correcto: `shellyplugsg3-9070694d3590/#`.

> **Nota importante:** El backend solo escucha los tópicos de TU Shelly específico
> (`shellyplugsg3-9070694d3590/#`). Esta suscripción está codificada en
> `backend/src/main/java/.../config/MqttConfig.java` en la línea del
> `MqttPahoMessageDrivenChannelAdapter`. Si en el futuro cambias de dispositivo o añades más
> enchufes, tendrás que actualizar esa configuración.

---

<a name="parte-9"></a>
## PARTE 9 — Verificación end-to-end

Checklist completo antes de dar el entorno por bueno:

### 9.1 Servicios Docker

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Esperado:
```
NAMES         STATUS         PORTS
db_iot        Up X minutes   0.0.0.0:5432->5432/tcp
broker_mqtt   Up X minutes   0.0.0.0:1883->1883/tcp
```

### 9.2 Backend

- ✅ Consola de IntelliJ muestra "Started JwtAuthBackendDemoApplication"
- ✅ `curl http://localhost:8080/api/v1/auth/login` con credenciales válidas devuelve JWT
- ✅ Logs muestran conexión MQTT exitosa

### 9.3 Frontend

- ✅ `http://localhost:4200` carga la pantalla de login
- ✅ Login con `admin@wattimizer.dev` / `Admin_Wattimizer1!` funciona
- ✅ El dashboard muestra la lista de dispositivos

### 9.4 Flujo de datos en tiempo real (si el Shelly está conectado)

- ✅ En la página de dispositivos aparece el Shelly Plug S (`9070694d3590`)
- ✅ Al seleccionarlo en el dashboard, la gráfica se actualiza con datos
- ✅ Los logs de IntelliJ muestran mensajes de `Saved reading for device: 9070694d3590`

### 9.5 Base de datos

```sql
-- Verificar hypertable activa
SELECT hypertable_name, num_chunks FROM timescaledb_information.hypertables;
-- readings | 1 (habrá un chunk si ya llegaron datos)

-- Verificar usuarios
SELECT username, role FROM users;

-- Verificar dispositivos
SELECT name, mac_address, is_simulated FROM devices;

-- Verificar seed del calendario
SELECT COUNT(*) FROM tariff_calendar_slots;
-- 336 (o más si se amplió el seed)
```

---

<a name="parte-10"></a>
## PARTE 10 — Flujo de trabajo diario

Una vez configurado todo, el flujo del día a día es:

### Arrancar el entorno

```powershell
# 1. Levantar infraestructura (si Docker Desktop está corriendo, es instantáneo)
docker compose --env-file .env up -d timescaledb mosquitto

# 2. Arrancar el backend → IntelliJ IDEA: Shift+F10 o botón Run

# 3. Arrancar el frontend
cd C:\Dev\wattimizer-app\frontend
ng serve
```

### Parar el entorno

```powershell
# Frontend: Ctrl+C en la terminal de ng serve
# Backend: botón Stop en IntelliJ

# Parar contenedores Docker (opcional, o déjalos correr)
docker compose stop timescaledb mosquitto
```

### Actualizar el código desde GitHub

```powershell
cd C:\Dev\wattimizer-app
git pull
# Luego reinicia el backend desde IntelliJ para que cargue los cambios
# El frontend se recarga automáticamente con ng serve (hot-reload)
```

---

<a name="parte-11"></a>
## PARTE 11 — Solución de problemas frecuentes

### El contenedor `db_iot` no arranca — "port 5432 already in use"

```powershell
# Encontrar qué proceso usa el puerto 5432
netstat -ano | findstr ":5432"
# Apunta el PID de la última columna

# Parar el servicio PostgreSQL nativo de Windows (si está instalado)
Stop-Service -Name "postgresql*" -ErrorAction SilentlyContinue

# O matar el proceso directamente (usa el PID del comando anterior)
Stop-Process -Id <PID> -Force
```

### El backend no conecta a la BD — "Connection refused to localhost:5432"

```powershell
# 1. Verificar que el contenedor está corriendo
docker ps | findstr "db_iot"

# 2. Si no está, arrancarlo
docker compose --env-file .env up -d timescaledb

# 3. Esperar ~10 segundos y volver a intentar con IntelliJ
```

### El backend no conecta al broker MQTT — "MQTT hostname can't be null" o "Connection refused to localhost:1883"

1. Verifica que la variable de entorno `MQTT_URL` está definida en la Run Configuration de IntelliJ (paso 6.4).
2. Verifica que el contenedor `broker_mqtt` está corriendo: `docker ps | findstr broker_mqtt`.
3. Verifica que el usuario/contraseña MQTT coinciden con los del `password_file`:
   ```powershell
   docker exec -it broker_mqtt mosquitto_pub -h localhost -p 1883 -u gateway-service -P s3cr3t -t test -m ok
   # Si no hay error, las credenciales son correctas
   ```

### Login desde Angular da 401 aunque el usuario existe

1. Verifica que el backend está arrancado y el endpoint responde:
   ```powershell
   Invoke-WebRequest "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin@wattimizer.dev","password":"Admin_Wattimizer1!"}' | Select StatusCode
   ```
2. Si el backend responde 200 pero el frontend da error, puede ser que el proxy de `ng serve` no esté activo. Verifica que arrancaste Angular con `ng serve` (no abriendo el `index.html` directamente).
3. Si el endpoint devuelve 401, verifica que ejecutaste el script `03-seed-users-dev.sql` con el backend parado (para que recargue los usuarios al arrancar).

### `ng serve` falla con errores de compilación TypeScript

```powershell
# Limpiar la caché de compilación de Angular
Remove-Item -Recurse -Force .angular
ng serve
```

### `npm install` falla con ERESOLVE

```powershell
npm cache clean --force
npm install --legacy-peer-deps --force
```

### El Shelly no aparece en el dashboard aunque está conectado

1. Verifica en TablePlus que el dispositivo existe:
   ```sql
   SELECT id, name, mac_address, user_id FROM devices WHERE mac_address = '9070694d3590';
   ```
2. Si no existe, revisa si el script del Shelly (paso 5.8) se ejecutó correctamente.
3. Si el Shelly publicó un mensaje MQTT antes de que el script insertara el dispositivo, el backend lo habrá auto-aprovisionado con `user_id = null`. Puedes reclamarlo desde la interfaz de gestión de dispositivos en la app.

### Los cálculos de coste muestran siempre 0 €

Asegúrate de haber ejecutado `seed-tariff-calendar-slots.sql` (paso 5.6). Verifica:

```sql
SELECT COUNT(*) FROM tariff_calendar_slots;
-- Debe ser 336
```

---

## APÉNDICE — Referencia rápida de puertos y credenciales

| Servicio | Puerto | Usuario | Contraseña |
|---|---|---|---|
| TimescaleDB | `localhost:5432` | `postgres` | valor de `DB_PASSWORD` en `.env` |
| Mosquitto MQTT | `localhost:1883` | `gateway-service` | `s3cr3t` |
| Spring Boot API | `localhost:8080` | — | — |
| Angular Dev | `localhost:4200` | — | — |
| Admin app (dev) | — | `admin@wattimizer.dev` | `Admin_Wattimizer1!` |
| Usuario normal (dev) | — | `user@wattimizer.dev` | `User_Wattimizer1!` |

## APÉNDICE — Scripts SQL del proyecto

| Fichero | Cuándo ejecutar | Qué hace |
|---|---|---|
| `db/dev-seed/00-extensions.sql` | Tras el primer arranque de Hibernate | Activa pgcrypto y timescaledb |
| `db/dev-seed/01-hypertable.sql` | Justo después del anterior | Convierte `readings` en hypertable |
| `db/tariffs-td-schema.sql` | Después de la hypertable | Añade CHECK constraints e índices de tarifas |
| `db/seed-tariff-calendar-slots.sql` | Después de los constraints | Carga 336 filas del calendario CNMC |
| `db/dev-seed/03-seed-users-dev.sql` | Después del calendario | Inserta admin y user de desarrollo |
| `db/dev-seed/04-seed-device-shelly.sql` | Después de los usuarios | Inserta el Shelly físico (MAC: 9070694d3590) |
| `db/dev-seed/05-seed-device-simulation.sql` | Opcional | Inserta el dispositivo virtual de simulación |
