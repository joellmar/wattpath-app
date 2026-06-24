# Guía de primer despliegue en producción — Hetzner VPS

Esta guía documenta los pasos exactos para poner en marcha Wattimizer por primera vez en un VPS Hetzner con Ubuntu 24.04 LTS.
Una vez completado, los despliegues siguientes son automáticos por GitHub Actions.

---

## Requisitos previos

| Recurso | Mínimo recomendado |
|---|---|
| VPS Hetzner | CX22 (2 vCPU, 4 GB RAM) |
| OS | Ubuntu 24.04 LTS |
| Dominio | `wattimizer.com` apuntando a la IP del VPS |
| DNS | Cloudflare (configuración detallada más abajo) |

---

## 1. Preparar el servidor

Conéctate al VPS como `root` por SSH y actualiza el sistema:

```bash
apt update && apt upgrade -y
apt install -y curl ca-certificates git
```

---

## 2. Instalar Docker y Docker Compose

Ubuntu 24.04 LTS incluye `docker-compose-v2` en sus repositorios oficiales. Para instalar Docker Engine y el plugin oficial de Docker Inc.:

```bash
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin
```

> **Posible conflicto en Ubuntu 24.04:** Si aparece el error `trying to overwrite '/usr/libexec/docker/cli-plugins/docker-compose'` al instalar `docker-compose-plugin`, no es crítico — Ubuntu ya tiene `docker-compose-v2` instalado y es compatible. Ignora el error.

Activa el daemon de Docker (no arranca automáticamente tras la instalación):

```bash
systemctl enable docker
systemctl start docker

# Verificar
docker version
docker compose version
```

---

## 3. Configurar el cortafuegos (UFW)

El objetivo es exponer solo los puertos necesarios.
Los puertos `8080` (backend) y `5432` (base de datos) **nunca** deben abrirse al exterior.

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 1883/tcp   # MQTT para el Shelly físico

ufw enable           # Confirmar con "y"
ufw status verbose
```

---

## 4. Configurar DNS y Cloudflare

En el panel DNS de Cloudflare añade tres registros A apuntando a la IP del VPS:

| Subdominio | Tipo | Proxy (nube) | Motivo |
|---|---|---|---|
| `wattimizer.com` | A → IP | Nube naranja | CDN + DDoS |
| `www.wattimizer.com` | A → IP | Nube naranja | CDN + DDoS |
| `api.wattimizer.com` | A → IP | **Nube gris** | WebSocket directo sin timeout CDN |

> **Importante:** `api.wattimizer.com` en nube gris expone la IP directamente.
> Certbot necesita resolver este subdominio sin proxy para emitir el certificado.

---

## 5. Instalar Certbot y obtener el certificado SSL

Certbot se ejecuta de forma nativa en el host Ubuntu (no en Docker).
Los certificados se almacenan en `/etc/letsencrypt`, donde Nginx los leerá
a través del bind mount de solo lectura.

```bash
apt install -y certbot

# Crear el directorio webroot que comparte con el contenedor Nginx
mkdir -p /var/www/certbot

# Asegúrate de que el puerto 80 está libre.
# Si hay contenedores Docker escuchando en 80, páralos primero:
docker stop $(docker ps -q) 2>/dev/null || true

# Modo standalone: Certbot levanta un servidor HTTP temporal en el puerto 80
certbot certonly --standalone \
  -d wattimizer.com \
  -d www.wattimizer.com \
  -d api.wattimizer.com \
  --non-interactive \
  --agree-tos \
  --email tu-email@dominio.com
```

> **Si ya existe un certificado parcial** (solo con algunos dominios), añade `--expand`
> para ampliar el certificado existente con los dominios que faltan:
> ```bash
> certbot certonly --standalone --expand \
>   -d wattimizer.com -d www.wattimizer.com -d api.wattimizer.com \
>   --non-interactive --agree-tos --email tu-email@dominio.com
> ```

```bash
# Verificar que se han generado los certificados
ls /etc/letsencrypt/live/wattimizer.com/

# Comprobar que el timer de renovación automática está activo
systemctl status certbot.timer
```

Tras el primer despliegue completo, cambia el método de renovación a webroot para que
Certbot renueve sin parar Nginx. Edita `/etc/letsencrypt/renewal/wattimizer.com.conf`
y asegúrate de que el bloque `[renewalparams]` contiene:

```ini
authenticator = webroot
webroot_path = /var/www/certbot
```

Prueba la renovación:
```bash
certbot renew --dry-run
```

---

## 6. Registrar las OAuth Apps de producción

Antes de continuar, registra las aplicaciones OAuth2 en Google y GitHub con las URLs
de producción. Los valores obtenidos van al `.env` en el paso 8.

### Google OAuth2

1. Ve a [console.cloud.google.com](https://console.cloud.google.com) → APIs y servicios → Credenciales → Crear credencial → ID de cliente OAuth 2.0.
2. Tipo de aplicación: **Aplicación web**.
3. Rellena:

| Campo | Valor |
|---|---|
| Orígenes de JavaScript autorizados | `https://wattimizer.com` y `https://www.wattimizer.com` |
| URIs de redireccionamiento autorizados | `https://wattimizer.com/login/oauth2/code/google` |

### GitHub OAuth2

Crea una OAuth App **exclusiva para producción** (no reutilices la de desarrollo):

1. Ve a GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**.
2. Rellena:

| Campo | Valor |
|---|---|
| Application name | `Wattimizer Production` |
| Homepage URL | `https://wattimizer.com` |
| Authorization callback URL | `https://wattimizer.com/login/oauth2/code/github` |

---

## 7. Clonar el repositorio

```bash
git clone https://github.com/joellmar/wattpath-app.git /root/wattimizer-app
cd /root/wattimizer-app
```

> **Si el directorio ya existe** de un despliegue anterior, no vuelvas a clonar.
> Actualiza en su lugar:
> ```bash
> cd /root/wattimizer-app
> git fetch origin main
> git reset --hard origin/main
> ```

---

## 8. Generar el `password_file` de Mosquitto

El archivo del repositorio contiene credenciales de desarrollo.
**Regenerar en el VPS** con las credenciales de producción.

> El contenedor Docker necesita TTY para pedir la contraseña de forma interactiva.
> Usa el flag `-it`:

```bash
cd /root/wattimizer-app

# Crear el password_file (-c lo crea desde cero, sobrescribe el anterior)
docker run --rm -it \
  -v "$(pwd)/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2.1.2-alpine \
  mosquitto_passwd -c /mosquitto/config/password_file wattimizer-gateway
# Introduce la contraseña cuando se solicite (2 veces). Guárdala: es PROD_MQTT_PASSWORD.
```

Si prefieres pasar la contraseña sin prompt interactivo (útil en scripts):

```bash
docker run --rm \
  -v "$(pwd)/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2.1.2-alpine \
  mosquitto_passwd -c -b /mosquitto/config/password_file wattimizer-gateway "TU_CONTRASEÑA"
```

> El `password_file` está en `.gitignore`. **Nunca** lo subas al repositorio.
>
> Si el Shelly usa el mismo usuario y contraseña que el backend, no es necesario
> crear un segundo usuario.

---

## 9. Crear el archivo `.env` de producción

```bash
cp .env.example .env
nano .env
```

Rellena **todos** los valores vacíos. Consulta `.env.example` para la descripción
de cada variable. Presta atención a:

- `PROD_MQTT_USER` y `PROD_MQTT_PASSWORD` deben coincidir exactamente con lo que
  generaste en el paso anterior con `mosquitto_passwd`.
- `GH_OAUTH_CLIENT_ID` y `GH_OAUTH_CLIENT_SECRET` corresponden a la OAuth App de
  GitHub **de producción** creada en el paso 6.
  > GitHub reserva el prefijo `GITHUB_` para sus propias variables internas.
  > Por eso las variables se llaman `GH_OAUTH_*` y no `GITHUB_*`.
- `APP_CORS_ALLOWED_ORIGINS` se escribe todo en una línea, separado por coma y sin espacios:
  `https://wattimizer.com,https://www.wattimizer.com`
- `SIMULATION_ENABLED=true` activa el modo demostración con simuladores IoT (ver sección 15).
  Ponlo a `false` si solo quieres telemetría Shelly real.

```bash
chmod 600 .env  # Solo root puede leerlo
```

---

## 10. Primer arranque (inicio en frío)

Arranca todos los servicios de una vez. Docker Compose construye las imágenes y
levanta los contenedores en el orden correcto según `depends_on`.
El backend puede fallar en el primer intento si TimescaleDB aún no está listo;
`restart: always` lo recuperará automáticamente.

```bash
cd /root/wattimizer-app
docker compose --env-file .env up -d --build --remove-orphans

# Esperar a que Spring Boot termine de inicializar y Hibernate cree las tablas
sleep 40

# Verificar que todos los contenedores están activos
docker compose ps
```

Todos deben aparecer con estado `Up`. Si el backend aparece en `Restarting`,
espera 30 segundos más y vuelve a comprobar — el backend puede tardar varios
intentos si TimescaleDB no estaba completamente listo al primer arranque.

---

## 11. Inicializar la base de datos (orden exacto de scripts SQL)

Ejecutar los scripts en este orden. Si alguno falla, revisar el log antes de continuar.

> Los scripts de extensiones e hypertable se encuentran en `dev-seed/`, no en la raíz de `db/`.

```bash
PSQL="docker compose exec -T timescaledb psql -U postgres -d wattimizer_db"

# 1. Extensiones TimescaleDB y pgcrypto
$PSQL < backend/src/main/resources/db/dev-seed/00-extensions.sql

# 2. Hypertable de lecturas
# ⚠️ Debe ejecutarse ANTES de que entren datos en la tabla readings.
# Si la tabla ya tiene datos (el backend recibió MQTT antes), usa migrate_data:
$PSQL < backend/src/main/resources/db/dev-seed/01-hypertable.sql
# Si falla con "table is not empty", ejecuta esto en su lugar:
# $PSQL -c "SELECT create_hypertable('readings', 'time', migrate_data => true);"

# 3. CHECK constraints del modelo tarifario (Hibernate no los genera)
# El script es idempotente: si ya existen los constraints, los omite sin error.
$PSQL < backend/src/main/resources/db/tariffs-td-schema.sql

# 4. Datos de referencia: calendario CNMC tarifas TD
$PSQL < backend/src/main/resources/db/seed-tariff-calendar-slots.sql

# 5. Seeds de desarrollo — NO ejecutar en producción salvo necesidad explícita
# $PSQL < backend/src/main/resources/db/dev-seed/03-seed-users-dev.sql
# $PSQL < backend/src/main/resources/db/dev-seed/04-seed-device-shelly.sql
# $PSQL < backend/src/main/resources/db/dev-seed/05-seed-device-simulation.sql

# 6. Resincronizar secuencias de identidad (siempre el último)
$PSQL < backend/src/main/resources/db/prod/99-resync-sequences.sql
```

Confirma que las 9 tablas están creadas:

```bash
docker compose exec -T timescaledb psql -U postgres -d wattimizer_db -c "\dt"
```

Deben aparecer: `alerts`, `devices`, `federated_identities`, `periods`, `readings`,
`tariff_calendar_slots`, `tariff_contracted_powers`, `tariffs`, `users`.

> Si faltan `tariff_contracted_powers`, `tariff_calendar_slots` o `federated_identities`,
> el backend aún no ha terminado de inicializar. Espera y comprueba sus logs:
> ```bash
> docker compose logs backend --tail=5 | grep "Started\|ERROR"
> ```
> Cuando veas `Started JwtAuthBackendDemoApplication`, Hibernate habrá creado las tablas.
> Reinicia el backend si es necesario: `docker compose restart backend`

---

## 12. Verificar el despliegue

```bash
# Nginx responde con HTTPS (debe devolver 200)
curl -I https://wattimizer.com

# La API responde — debe devolver 401 (credenciales incorrectas, pero el backend funciona)
curl -s -o /dev/null -w "%{http_code}" \
  -X POST https://wattimizer.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# Revisar logs del backend
docker compose logs backend --since=5m | grep -v "WebSocketSession\|MessageBroker"

# Revisar logs de Nginx (errores de proxy)
docker compose logs nginx --tail=20
```

> **Si la API devuelve 502:** Nginx puede tener cacheada la IP antigua del backend
> si el contenedor fue reiniciado. Solución: `docker compose restart nginx`

---

## 13. Crear el usuario administrador de producción

```bash
curl -s -X POST https://wattimizer.com/api/v1/auth/register/admin \
  -H "Content-Type: application/json" \
  -H "X-Wattimizer-Admin-Secret: TU_PROD_ADMIN_KEY" \
  -d '{"username":"admin","password":"contraseña_segura"}'
```

> La cabecera se llama `X-Wattimizer-Admin-Secret` (no `X-Admin-Key`).
> El valor es el de `PROD_ADMIN_KEY` en tu `.env`.

Si la respuesta es 200 con los datos del usuario, el sistema está listo.
Accede a `https://wattimizer.com` y haz login con las credenciales recién creadas.

---

## 14. Configurar GitHub Secrets para los despliegues automáticos

En el repositorio de GitHub, ve a **Settings → Secrets and variables → Actions**
y crea los siguientes secretos. Con estos configurados, cada `push` a `main`
desplegará automáticamente en el VPS.

| Nombre del secreto | Descripción |
|---|---|
| `HOST` | IP pública del VPS Hetzner |
| `USER` | Usuario SSH (normalmente `root`) |
| `SSH_PRIVATE_KEY` | Clave privada SSH dedicada para CI/CD |
| `DB_NAME` | Nombre de la base de datos (`wattimizer_db`) |
| `DB_USER` | Usuario de PostgreSQL (`postgres`) |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `PROD_MQTT_USER` | Usuario MQTT del backend |
| `PROD_MQTT_PASSWORD` | Contraseña MQTT del backend |
| `PROD_JWT_SECRET` | Clave para firmar JWT (mín. 256 bits) |
| `PROD_ADMIN_KEY` | Clave maestra del endpoint de registro admin |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth2 |
| `GOOGLE_CLIENT_SECRET` | Client Secret de Google OAuth2 |
| `GH_OAUTH_CLIENT_ID` | Client ID de GitHub OAuth2 (prefijo `GH_OAUTH_`, no `GITHUB_`) |
| `GH_OAUTH_CLIENT_SECRET` | Client Secret de GitHub OAuth2 |
| `OAUTH2_FRONTEND_CALLBACK_URI` | `https://wattimizer.com/auth/oauth/callback` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://wattimizer.com,https://www.wattimizer.com` |
| `SIMULATION_ENABLED` | *(opcional)* `true` para modo demostración web; `false` para desactivar telemetría simulada |

> **GitHub no permite** crear secrets con nombres que empiecen por `GITHUB_`
> (prefijo reservado para variables internas de Actions). Por eso los secrets
> de GitHub OAuth se llaman `GH_OAUTH_CLIENT_ID` y `GH_OAUTH_CLIENT_SECRET`.

Para generar la clave SSH dedicada a CI/CD y autorizarla en el VPS:

```bash
# En el VPS
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_actions -N ""
cat ~/.ssh/github_actions.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

# Muestra la clave PRIVADA — pega su contenido en el secret SSH_PRIVATE_KEY
cat ~/.ssh/github_actions
```

Para probar que el workflow funciona correctamente, haz un push vacío:

```bash
git commit --allow-empty -m "ci: trigger first automated deploy"
git push origin main
```

Verifica el resultado en GitHub → pestaña **Actions**.

---

## 15. Simuladores de consumo en producción (modo demostración)

Esta sección explica cómo dejar activos los **simuladores IoT** en `https://wattimizer.com` para que cualquier visitante registrado pueda probar el dashboard, costes y alertas **sin un Shelly físico**.

### 15.1. Por qué existe `SIMULATION_ENABLED`

El backend incluye un job programado (`IotTelemetrySimulationJob`) que, cada 5 segundos, genera lecturas sintéticas para dispositivos con `is_simulated=true`. Esas lecturas entran en el mismo pipeline que la telemetría real: TimescaleDB, WebSocket, analíticas y alertas de maxímetro.

La variable **`SIMULATION_ENABLED`** actúa como interruptor maestro de ese job:

| Valor | Comportamiento |
|---|---|
| `true` | Los simuladores emiten lecturas. Modo demostración web activo. |
| `false` | El job no corre. Solo entra telemetría real por MQTT (Shelly). |

**Por qué la pusimos inicialmente a `false`:** en el primer despliegue productivo la prioridad era no mezclar datos artificiales con mediciones reales: evita consumo de CPU/BD innecesario, alertas de maxímetro provocadas por perfiles como `CONSTANT_HIGH_LOAD`, y confusión si alguien monitoriza consumo real. Era el valor conservador correcto para un entorno “solo hardware”.

**Por qué ahora la activamos por defecto (`true`):** la web pública funciona como demostración del producto. Cualquier usuario registrado debe poder probar los perfiles sin hardware. Los simuladores son **multitenant** (cada usuario ve solo los suyos) y no sustituyen a un Shelly real salvo que el usuario los cree.

> El perfil `CONSTANT_HIGH_LOAD` puede disparar alertas si la tarifa tiene potencia contratada baja. Es intencionado para probar el maxímetro en demo.

### 15.2. Qué está automatizado (sin intervención manual)

Tras cada `push` a `main`, el pipeline CI/CD ya hace lo siguiente:

1. Compila frontend y backend.
2. Regenera `.env` en el VPS incluyendo `SIMULATION_ENABLED=true` (salvo que definas el secret `SIMULATION_ENABLED=false` en GitHub).
3. Ejecuta `docker compose up -d --build`, que inyecta la variable al contenedor `spring_backend`.
4. Spring Boot lee `simulation.enabled=${SIMULATION_ENABLED:true}`.

Archivos implicados:

| Archivo | Rol |
|---|---|
| `docker-compose.yml` | `SIMULATION_ENABLED: ${SIMULATION_ENABLED:-true}` |
| `.env.example` | Documenta la variable para el VPS |
| `.github/workflows/deploy.yml` | Escribe `SIMULATION_ENABLED` en el `.env` del servidor |
| `backend/.../application.properties` | Mapea la variable al job de simulación |

**No hace falta ejecutar el seed SQL de desarrollo (`05-seed-device-simulation.sql`) en producción.** Ese script crea simuladores bajo `admin@wattimizer.dev`, un usuario que solo existe en local. En producción cada usuario crea los suyos desde la web.

### 15.3. Pasos manuales (tu input)

#### A) Primera activación en un VPS ya desplegado (una sola vez)

Si el servidor ya estaba corriendo con `SIMULATION_ENABLED=false`, basta con desplegar la versión actual (push a `main`) o, si quieres forzarlo sin esperar al pipeline:

```bash
cd /root/wattimizer-app

# Asegúrate de tener la última versión
git fetch origin main
git reset --hard origin/main

# Añade la variable al .env si no existe
grep -q '^SIMULATION_ENABLED=' .env || echo 'SIMULATION_ENABLED=true' >> .env
sed -i 's/^SIMULATION_ENABLED=.*/SIMULATION_ENABLED=true/' .env

# Recrea el backend para que coja la variable
docker compose --env-file .env up -d --build backend
```

Comprueba que el job está activo:

```bash
docker compose exec backend printenv SIMULATION_ENABLED
# Debe mostrar: true

docker compose logs backend --since=2m | grep -i simul
# Tras crear simuladores, verás trazas debug de telemetría simulada
```

#### B) Para cada visitante que quiera probar (flujo de usuario final)

No requiere acceso SSH. El usuario solo necesita cuenta en la web:

1. Entrar en `https://wattimizer.com` e **iniciar sesión** (registro, Google o GitHub).
2. Ir a **Dispositivos** (`/devices`).
3. Pulsar **“Añadir pack de demostración”** (botón verde bajo el formulario).
   - Crea **9 simuladores**, uno por perfil: horno, lavadora, televisor, ventilador, PC, nevera, consumo fantasma, carga alta y onda de prueba.
   - Es **idempotente**: si ya tienes un perfil, no lo duplica.
4. Ir al **Panel** (`/dashboard`), elegir un simulador en el selector y observar la curva de consumo en unos segundos.
5. *(Opcional)* Volver a Dispositivos, **apagar** un simulador y confirmar que la potencia baja a 0 W.
6. *(Opcional)* Crear un simulador individual: tipo **Simulado** → elegir perfil → guardar.

Alternativa avanzada por API (con token JWT de sesión):

```bash
curl -s -X POST https://wattimizer.com/api/v1/devices/simulated/demo-pack \
  -H "Authorization: Bearer TU_JWT" \
  -H "Content-Type: application/json"
```

#### C) Configurar tarifa (recomendado para ver costes)

Los simuladores generan kWh, pero el panel de costes necesita una tarifa asignada. Si el visitante no la tiene:

1. Ir a **Tarifas** y crear o asignar una tarifa de prueba.
2. Volver al panel y comprobar coste acumulado y consumo fantasma.

### 15.4. Desactivar simulación en producción

Cuando la web deje de ser demo y solo quieras Shelly real:

**Opción 1 — GitHub Secret (recomendada, persiste en cada deploy):**

1. GitHub → Settings → Secrets → Actions → New secret.
2. Nombre: `SIMULATION_ENABLED`, valor: `false`.
3. Haz un push vacío o redeploy manual en el VPS.

**Opción 2 — Solo en el VPS (hasta el próximo deploy):**

```bash
sed -i 's/^SIMULATION_ENABLED=.*/SIMULATION_ENABLED=false/' /root/wattimizer-app/.env
cd /root/wattimizer-app
docker compose --env-file .env up -d --build backend
```

Los dispositivos simulados **siguen en la base de datos**, pero dejan de emitir lecturas nuevas.

### 15.5. Verificación rápida post-activación

```bash
# 1. Variable activa en el contenedor
docker compose exec backend printenv SIMULATION_ENABLED

# 2. Tras que un usuario pulse "Añadir pack de demostración", deben existir filas simuladas
docker compose exec -T timescaledb psql -U postgres -d wattimizer_db -c \
  "SELECT name, mac_address, simulation_profile, is_on FROM devices WHERE is_simulated = true ORDER BY name LIMIT 15;"

# 3. Lecturas entrando (espera ~10 s tras crear simuladores)
docker compose exec -T timescaledb psql -U postgres -d wattimizer_db -c \
  "SELECT time, power_w FROM readings ORDER BY time DESC LIMIT 5;"
```

En el navegador: login → Dispositivos → pack de demostración → Panel → selector de dispositivo → gráfica moviéndose.

### 15.6. Resumen: automatizado vs manual

| Acción | ¿Automatizado? | Responsable |
|---|---|---|
| Compilar y desplegar código con soporte simuladores | Sí (CI/CD en push a `main`) | GitHub Actions |
| `SIMULATION_ENABLED=true` por defecto en `.env` del VPS | Sí (deploy.yml) | GitHub Actions |
| Reiniciar backend con la variable | Sí (docker compose en deploy) | GitHub Actions |
| Crear los 9 simuladores por usuario | No (botón en la web) | Usuario visitante |
| Asignar tarifa para ver costes | No | Usuario visitante |
| Desactivar simulación (`false`) | Opcional secret `SIMULATION_ENABLED` | Administrador |

---

## Notas de seguridad pendientes

- **MQTT sin TLS (1883):** el tráfico de telemetría circula sin cifrar. Migrar a `8883/TLS` o encapsular en VPN cuando el hardware Shelly lo soporte.
- **ddl-auto=update:** permite a Hibernate modificar el esquema en cada arranque. Cambiar a `validate` cuando el esquema esté estable en producción.
- **UFW + Fail2Ban:** considerar instalar `fail2ban` para proteger SSH contra fuerza bruta.
