# Documentacion de memoria DAW

Esta carpeta contiene la documentacion tecnica en espanol de **Wattimizer**, preparada para usarse como base y anexos de la memoria final del proyecto DAW.

Los documentos se han revisado sobre la rama `cursor/documentaci-n-t-cnica-del-proyecto-8f34`, que parte del commit `3021eba` de `main`. Ese punto del proyecto incluye los ultimos ajustes de despliegue real en Hetzner, CI/CD, OAuth2, Nginx, Mosquitto y scripts SQL de tarifas.

## Documentos

| Documento | Contenido |
| --- | --- |
| [`memoria-final-daw.md`](./memoria-final-daw.md) | Memoria principal siguiendo el indice academico: introduccion, analisis, diseno, implementacion, pruebas, despliegue, conclusiones y bibliografia. |
| [`anexo-a-backend-rest.md`](./anexo-a-backend-rest.md) | Controladores REST de Spring Boot, endpoints, seguridad, DTOs y errores. |
| [`anexo-b-frontend-angular.md`](./anexo-b-frontend-angular.md) | Componentes Angular, servicios, rutas, guard, interceptor, Signals, RxJS y NgRx Signals Store. |
| [`anexo-c-telemetria-mqtt.md`](./anexo-c-telemetria-mqtt.md) | Ingesta asincrona de telemetria con Spring Integration MQTT, Mosquitto, handlers y STOMP. |
| [`anexo-d-timescaledb-analitica.md`](./anexo-d-timescaledb-analitica.md) | Modelo relacional, hypertable `readings`, scripts SQL y consultas analiticas reales. |

## Criterio de redaccion

La documentacion se ha escrito a partir del codigo del repositorio, no desde una plantilla generica. Cuando una parte no esta versionada, como los wireframes o la captura del tablero Kanban, se indica de forma explicita para no mezclar evidencias reales con suposiciones.
