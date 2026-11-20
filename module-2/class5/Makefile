# Random passwords of the users of the catalog in .env (only the ones that do not exist yet)
env:
	./scripts/init-env.sh

start: env
	mvn clean package -DskipTests
	docker compose build
	docker compose up

stop:
	docker compose down

start-infra: env
	docker compose -f docker-compose-infrastructure.yml up

stop-infra:
	docker compose -f docker-compose-infrastructure.yml down

# With start-infra running
run-monolith:
	mvn -pl twa-flights-monolith spring-boot:run

run-catalog: env
	set -a && . ./.env && set +a && DB_PASSWORD="$$CATALOG_DB_PASSWORD" mvn -pl twa-flights-catalog spring-boot:run

# Observability of the rollout of the catalog (M2 C4): the stack plus Prometheus, Alertmanager and Grafana
COMPOSE_OBS := docker compose -f docker-compose.yml -f docker-compose.observability.yml
PROMETHEUS_IMAGE := prom/prometheus:v3.5.0
ALERTMANAGER_IMAGE := prom/alertmanager:v0.28.1
RUN_OBS := docker run --rm -v $(CURDIR)/observability:/work -w /work

start-observability: env
	mvn clean package -DskipTests
	$(COMPOSE_OBS) up -d --build

stop-observability:
	$(COMPOSE_OBS) down

# Applies changes of the rules and of alertmanager.yml without a restart
reload-observability:
	curl -fsS -X POST http://localhost:9090/-/reload
	curl -fsS -X POST http://localhost:9093/-/reload

check-observability:
	$(RUN_OBS) --entrypoint promtool $(PROMETHEUS_IMAGE) check rules prometheus/rules/platform.rules.yml prometheus/rules/catalog-rollout.rules.yml prometheus/rules/catalog-rollout-alerts.rules.yml prometheus/rules/catalog-parity.rules.yml
	$(RUN_OBS) -v $(CURDIR)/observability/prometheus:/etc/prometheus --entrypoint promtool $(PROMETHEUS_IMAGE) check config --syntax-only /etc/prometheus/prometheus.yml
	$(RUN_OBS) -v $(CURDIR)/observability/alertmanager:/etc/alertmanager --entrypoint amtool $(ALERTMANAGER_IMAGE) check-config /etc/alertmanager/alertmanager.yml

test-observability:
	$(RUN_OBS) --entrypoint promtool $(PROMETHEUS_IMAGE) test rules prometheus/tests/catalog-rollout.test.yml

dashboards:
	python3 observability/scripts/build_dashboards.py

# Drill of the automatic rollback with evidence in docs/ (needs start-observability)
rollback-drill:
	./scripts/rollback-drill.sh

# Restart of one instance of the monolith at a time under load (prerequisite P1), with evidence in docs/
rolling-restart:
	./scripts/rolling-restart.sh

.PHONY: env start stop start-infra stop-infra run-monolith run-catalog start-observability stop-observability \
	reload-observability check-observability test-observability dashboards rollback-drill rolling-restart
