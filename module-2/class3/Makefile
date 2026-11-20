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

.PHONY: env start stop start-infra stop-infra run-monolith run-catalog
