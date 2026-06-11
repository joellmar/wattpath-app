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

## 2. Instalar Docker y Docker Compose Plugin

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
apt install -y docker-ce docker-ce-cli containerd.io \
               docker-buildx-plugin docker-compose-plugin

# Verificar
docker version
docker compose version
```

---

## 3. Configurar el cortafuegos (UFW)

El objetivo es exponer solo los puertos necesarios.
El puerto `8080` (backend) y `5432` (base de datos) **nunca** deben abrirse al exterior.

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

# ⚠️ Bootstrap: en el primer arranque, Nginx aún no está corriendo.
# Usa el modo standalone (Certbot levanta un servidor HTTP temporal en el puerto 80).
# Asegúrate de que el puerto 80 está libre antes de ejecutar esto.

certbot certonly --standalone \
  -d wattimizer.com \
  -d www.wattimizer.com \
  -d api.wattimizer.com \
  --non-interactive \
  --agree-tos \
  --email tu-email@dominio.com

# Verificar que se han generado los certificados
ls /etc/letsencrypt/live/wattimizer.com/
```

Configura la renovación automática. Certbot la instala con el paquete;
comprueba que el timer está activo:

```bash
systemctl status certbot.timer
```

Para la renovación con Nginx activo (post primer despliegue), Certbot usará
webroot (`/var/www/certbot`). Edita `/etc/letsencrypt/renewal/wattimizer.com.conf`
y cambia `authenticator = standalone` por `authenticator = webroot` y añade
`webroot_path = /var/www/certbot` después del primer despliegue.

---

## 6. Clonar el repositorio

```bash
git clone https://github.com/TU_USUARIO/wattimizer-app.git /root/wattimizer-app
cd /root/wattimizer-app
```

---

## 7. Generar el `password_file` de Mosquitto

El archivo del repositorio contiene credenciales de desarrollo.
**Regenerar en el VPS** con las credenciales de producción:

```bash
cd /root/wattimizer-app

# Crear el password_file con el usuario del backend.
# Mosquitto usará este usuario con su contraseña de producción.
docker run --rm \
  -v "$(pwd)/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2.1.2-alpine \
  mosquitto_passwd -c /mosquitto/config/password_file "$PROD_MQTT_USER"
# El comando pedirá la contraseña de forma interactiva (2 veces).

# Si también necesitas un usuario independiente para el Shelly:
docker run --rm \
  -v "$(pwd)/mosquitto/config:/mosquitto/config" \
  eclipse-mosquitto:2.1.2-alpine \
  mosquitto_passwd /mosquitto/config/password_file shelly-gateway
```

> El `password_file` ya está en `.gitignore`. **Nunca** lo subas al repositorio.

---

## 8. Crear el archivo `.env` de producción

```bash
cp .env.example .env
nano .env  # O usa tu editor preferido
```

Rellena **todos** los valores vacíos del archivo.
Consulta `.env.example` para la descripción de cada variable.

```bash
chmod 600 .env  # Solo root puede leerlo
```

---

## 9. Primer arranque (inicio en frío)

El backend necesita levantar antes para que Hibernate cree las tablas,
y el broker debe estar activo para recibir MQTT:

```bash
cd /root/wattimizer-app

# Paso 1: arrancar solo la base de datos y el broker
docker compose --env-file .env up -d timescaledb mosquitto

# Esperar ~10 segundos a que TimescaleDB esté listo para aceptar conexiones
sleep 10

# Paso 2: arrancar el backend para que Hibernate cree las tablas
docker compose --env-file .env up -d backend

# Esperar ~15 segundos a que Spring Boot termine de inicializar y crear tablas
sleep 15
```

---

## 10. Inicializar la base de datos (orden exacto de scripts SQL)

Ejecutar los scripts en este orden. Si alguno falla, revisar el log antes de continuar.

```bash
DB_NAME=${DB_NAME:-wattimizer_db}
DB_USER=${DB_USER:-postgres}

PSQL="docker compose exec -T timescaledb psql -U $DB_USER -d $DB_NAME"

# 1. Extensiones (TimescaleDB + pgcrypto)
$PSQL < backend/src/main/resources/db/00-extensions.sql

# 2. Hypertable de lecturas (ejecutar ANTES de que entren datos en readings)
$PSQL < backend/src/main/resources/db/01-hypertable.sql

# 3. CHECK constraints del modelo tarifario (Hibernate no los genera)
$PSQL < backend/src/main/resources/db/tariffs-td-schema.sql

# 4. Datos de referencia: calendario CNMC tarifas TD
$PSQL < backend/src/main/resources/db/seed-tariff-calendar-slots.sql

# 5. (Solo si se necesita en producción) Seeds de usuarios y dispositivos de desarrollo:
# NO ejecutar en producción salvo que se quiera el usuario demo.
# $PSQL < backend/src/main/resources/db/dev-seed/03-seed-users-dev.sql
# $PSQL < backend/src/main/resources/db/dev-seed/04-seed-device-shelly.sql
# $PSQL < backend/src/main/resources/db/dev-seed/05-seed-device-simulation.sql

# 6. Resincronizar secuencias después de los seeds
$PSQL < backend/src/main/resources/db/prod/99-resync-sequences.sql
```

---

## 11. Levantar todos los servicios

```bash
cd /root/wattimizer-app
docker compose --env-file .env up -d --build
docker compose ps   # Todos deben estar "Up"
```

---

## 12. Verificar el despliegue

```bash
# Comprobar que Nginx responde con HTTPS
curl -I https://wattimizer.com

# Comprobar que la API responde (endpoint público)
curl https://wattimizer.com/api/v1/auth/ping 2>/dev/null || \
  curl https://api.wattimizer.com/api/v1/auth/ping

# Revisar logs del backend por errores de arranque
docker compose logs backend --tail=50

# Revisar logs de Nginx
docker compose logs nginx --tail=20

# Test de conexión MQTT (requiere mosquitto-clients en el host o herramienta externa)
# mosquitto_pub -h wattimizer.com -p 1883 -u "$PROD_MQTT_USER" \
#   -P "$PROD_MQTT_PASSWORD" -t test/ping -m "hello"
```

---

## 13. Crear el usuario administrador de producción

Si no se ejecutaron los seeds de desarrollo, el primer usuario admin se crea via API:

```bash
curl -s -X POST https://wattimizer.com/api/v1/auth/register/admin \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: TU_ADMIN_KEY" \
  -d '{"username":"admin","password":"contraseña_segura"}'
```

---

## 14. Configurar GitHub Secrets para los despliegues automáticos

En el repositorio de GitHub, ve a **Settings → Secrets and variables → Actions**
y crea estos secretos:

| Nombre del secreto | Descripción |
|---|---|
| `HOST` | IP pública del VPS Hetzner |
| `USER` | Usuario SSH (normalmente `root`) |
| `SSH_PRIVATE_KEY` | Clave privada SSH para el acceso al VPS |
| `DB_NAME` | Nombre de la base de datos |
| `DB_USER` | Usuario de PostgreSQL |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `PROD_MQTT_USER` | Usuario MQTT del backend |
| `PROD_MQTT_PASSWORD` | Contraseña MQTT del backend |
| `PROD_JWT_SECRET` | Clave para firmar JWT (256 bits mínimo) |
| `PROD_ADMIN_KEY` | Clave maestra del endpoint de registro admin |
| `GOOGLE_CLIENT_ID` | Client ID de Google OAuth2 |
| `GOOGLE_CLIENT_SECRET` | Client Secret de Google OAuth2 |
| `GITHUB_CLIENT_ID` | Client ID de GitHub OAuth2 |
| `GITHUB_CLIENT_SECRET` | Client Secret de GitHub OAuth2 |
| `OAUTH2_FRONTEND_CALLBACK_URI` | `https://wattimizer.com/auth/oauth/callback` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://wattimizer.com,https://www.wattimizer.com` |

Para generar la clave SSH en el VPS y autorizar el acceso de GitHub Actions:

```bash
# En el VPS, generar un par de claves dedicado para CI/CD
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/github_actions -N ""

# Autorizar la clave pública
cat ~/.ssh/github_actions.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

# Copiar la clave PRIVADA al secret SSH_PRIVATE_KEY de GitHub
cat ~/.ssh/github_actions
```

---

## 15. Renovación del certificado SSL (post primer despliegue)

Tras el primer despliegue, cambia el método de renovación a webroot:

```bash
# Editar la configuración de renovación
nano /etc/letsencrypt/renewal/wattimizer.com.conf
```

Asegúrate de que el bloque `[renewalparams]` contiene:
```ini
authenticator = webroot
webroot_path = /var/www/certbot
```

Haz un test de renovación para confirmar que funciona con Nginx activo:
```bash
certbot renew --dry-run
```

---

## Notas de seguridad pendientes

- **MQTT sin TLS (1883):** el tráfico de telemetría circula sin cifrar. Migrar a `8883/TLS` o encapsular en VPN cuando el hardware Shelly lo soporte.
- **ddl-auto=update:** permite a Hibernate modificar el esquema. Valorar cambiar a `validate` una vez el esquema esté estable en producción.
- **UFW + Fail2Ban:** considerar instalar `fail2ban` para proteger SSH contra fuerza bruta.
