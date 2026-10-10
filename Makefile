# Короткие команды проекта. Список команд: make (или make help)

.DEFAULT_GOAL := help
SHELL := /bin/bash

# Если JAVA_HOME не задан, берём JDK по умолчанию из SDKMAN (если он установлен)
ifeq ($(JAVA_HOME),)
  SDKMAN_JAVA := $(HOME)/.sdkman/candidates/java/current
  ifneq ($(wildcard $(SDKMAN_JAVA)),)
    export JAVA_HOME := $(SDKMAN_JAVA)
  endif
endif

SONAR_COMPOSE := docker compose -f sonar/docker-compose.yml

.PHONY: help build test run sonar sonar-local sonar-up sonar-down sonar-logs

help: ## Показать список команд
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  make %-12s %s\n", $$1, $$2}'

## --- Приложение ---

build: ## Собрать JAR без тестов
	./mvnw clean package -DskipTests

test: ## Запустить все тесты (нужен запущенный Docker)
	./mvnw clean verify

run: ## Запустить приложение (профиль dev, нужен PostgreSQL на localhost:5432)
	./mvnw spring-boot:run

## --- SonarQube ---

sonar: ## Тесты + анализ в SonarQube Cloud (токен SONAR_TOKEN в sonar/.env)
	sonar/sonar.sh cloud

sonar-local: ## Тесты + анализ на локальном SonarQube (токен SONAR_LOCAL_TOKEN в sonar/.env)
	sonar/sonar.sh local

sonar-up: ## Запустить локальный SonarQube в Docker (http://localhost:9000)
	$(SONAR_COMPOSE) up -d

sonar-down: ## Остановить локальный SonarQube (данные сохраняются)
	$(SONAR_COMPOSE) down

sonar-logs: ## Логи локального SonarQube
	$(SONAR_COMPOSE) logs -f sonarqube
