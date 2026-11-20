start:
	mvn clean package -DskipTests
	docker compose build
	docker compose up

stop:
	docker compose down

start-infra:
	docker compose -f docker-compose-infrastructure.yml up

stop-infra:
	docker compose -f docker-compose-infrastructure.yml down

.PHONY: start stop start-infra stop-infra
