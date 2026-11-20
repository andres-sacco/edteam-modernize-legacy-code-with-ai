# Runbooks · plataforma de observabilidad

Alertas de `observability/prometheus/rules/platform.rules.yml`: no hablan de un servicio sino del stack que lo vigila.

## Watchdog

Siempre está disparando. Si el receptor `deadman` deja de recibirla, Prometheus o Alertmanager están caídos y ninguna
otra alerta va a llegar.

## PrometheusRuleEvaluationFailures

Prometheus no puede evaluar un grupo de reglas: sus alertas no disparan. Ver el error en
http://localhost:9090/rules y validar con `make check-observability`.

## PrometheusNotificationsFailing

Prometheus no llega a Alertmanager. `docker logs alertmanager` y `docker logs prometheus`.

## AlertmanagerNotificationsFailing

Alertmanager no puede notificar. El caso más común en local: falta
`observability/alertmanager/secrets/slack-webhook-url`, así que **ninguna alerta `page` ni `ticket` le llega a
nadie**. Crear el archivo con el webhook de Slack y `make reload-observability`.
