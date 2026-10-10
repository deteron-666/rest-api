#!/usr/bin/env bash
# Тесты + анализ кода в SonarQube. Обычно запускается через make: make sonar / make sonar-local.
#
#   sonar/sonar.sh          SonarQube Cloud: адрес, организация и проект в pom.xml, токен SONAR_TOKEN
#   sonar/sonar.sh local    локальный сервер из sonar/docker-compose.yml, токен SONAR_LOCAL_TOKEN
#
# Токены берутся из sonar/.env (шаблон: sonar/.env.example) или из переменных окружения.
set -euo pipefail

SONAR_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SONAR_DIR")"
TARGET="${1:-cloud}"
LOCAL_URL="http://localhost:9000"

fail() {
  echo "Ошибка: $*" >&2
  exit 1
}

# Загружаем sonar/.env. Переменные, уже заданные в окружении (например, в CI), имеют приоритет над файлом.
if [[ -f "$SONAR_DIR/.env" ]]; then
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    key="${key//[[:space:]]/}"
    [[ -z "$key" || "$key" == \#* ]] && continue
    value="${value%$'\r'}"
    [[ -n "${!key:-}" ]] || export "$key=$value"
  done < "$SONAR_DIR/.env"
fi

# На macOS /usr/bin/java существует всегда, даже без JDK, поэтому проверяем, что Java реально запускается.
# Если нет — берём JDK по умолчанию из SDKMAN.
if [[ -z "${JAVA_HOME:-}" ]] && ! java -version >/dev/null 2>&1 && [[ -d "$HOME/.sdkman/candidates/java/current" ]]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
fi

cd "$PROJECT_DIR"

case "$TARGET" in
  cloud)
    [[ -n "${SONAR_TOKEN:-}" ]] || fail "не задан SONAR_TOKEN. Впишите токен SonarQube Cloud в sonar/.env (шаблон: sonar/.env.example)"
    ./mvnw clean verify sonar:sonar
    ;;
  local)
    [[ -n "${SONAR_LOCAL_TOKEN:-}" ]] || fail "не задан SONAR_LOCAL_TOKEN. Создайте токен на $LOCAL_URL (My Account → Security) и впишите его в sonar/.env"
    curl -fs "$LOCAL_URL/api/system/status" | grep -q '"status":"UP"' \
      || fail "локальный SonarQube не запущен или ещё стартует. Запустите: make sonar-up"
    SONAR_TOKEN="$SONAR_LOCAL_TOKEN" ./mvnw clean verify sonar:sonar -Dsonar.host.url="$LOCAL_URL"
    ;;
  *)
    fail "неизвестная цель '$TARGET'. Используйте: cloud или local"
    ;;
esac
