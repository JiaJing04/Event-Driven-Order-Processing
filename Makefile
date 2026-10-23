.PHONY: help build test verify infra up down logs demo clean

help:            ## list targets
	@grep -E '^[a-z]+:.*##' $(MAKEFILE_LIST) | sed 's/:.*##/ -/'

build:           ## compile and package everything, skipping tests
	mvn -B -DskipTests package

test:            ## unit tests only (no Docker needed)
	mvn -B test

verify:          ## unit tests + end-to-end integration tests (needs Docker)
	mvn -B verify

infra:           ## start only PostgreSQL + Kafka (run the services from your IDE / mvn spring-boot:run)
	docker compose up -d --wait

up:              ## start PostgreSQL + Kafka + the three services
	docker compose --profile apps up --build -d --wait

down:            ## stop everything and delete volumes
	docker compose --profile apps down -v

logs:            ## follow service logs
	docker compose --profile apps logs -f order-service inventory-service payment-service

demo:            ## run the demo scenarios against the running stack
	./scripts/demo.sh

clean:           ## remove build output
	mvn -B clean
