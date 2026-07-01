# Documentación técnica de la memoria DAW

Esta carpeta contiene la documentación técnica de **Wattimizer** preparada para usarse como anexos de la memoria final del proyecto de Desarrollo de Aplicaciones Web.

La documentación se ha redactado a partir del código actual del repositorio, no como una explicación genérica de las tecnologías. Por eso cada apartado referencia controladores, servicios, DTOs, stores, componentes, scripts SQL y flujos reales de la aplicación.

## Índice de documentos

| Documento | Contenido |
| --- | --- |
| [`memoria-final-daw.md`](./memoria-final-daw.md) | Estructura principal de la memoria: introducción, análisis funcional, diseño técnico, implementación, pruebas, despliegue, conclusiones y bibliografía. |
| [`anexo-a-backend-rest.md`](./anexo-a-backend-rest.md) | Controladores REST de Spring Boot, endpoints, parámetros, DTOs y flujo de seguridad. |
| [`anexo-b-frontend-angular.md`](./anexo-b-frontend-angular.md) | Componentes, servicios, rutas, formularios, RxJS y estado con NgRx Signals en Angular. |
| [`anexo-c-telemetria-mqtt.md`](./anexo-c-telemetria-mqtt.md) | Ingesta asíncrona de telemetría mediante Spring Integration MQTT, persistencia y emisión WebSocket. |
| [`anexo-d-timescaledb-analitica.md`](./anexo-d-timescaledb-analitica.md) | Modelo de datos, hypertable `readings`, consultas analíticas y cálculo de costes energéticos. |

## Uso recomendado en la memoria final

- Usar `memoria-final-daw.md` como base del cuerpo principal del documento.
- Incorporar los anexos técnicos en la parte final o enlazarlos desde los apartados 3 y 4.
- Mantener los diagramas Mermaid como apoyo visual, ya que explican mejor el recorrido de datos entre frontend, backend, MQTT y base de datos.
