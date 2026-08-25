from __future__ import annotations

import os

import uvicorn

from app.adapters.inbound.http import create_app
from app.adapters.outbound.persistence import PgNotificationRepository, PgTemplateRepository, PostgresDatabase
from app.application.use_cases import GetNotification, SendNotification
from app.platform.logging import configure_logging
from app.platform.telemetry import setup_telemetry


def require_env(name: str) -> str:
    value = os.getenv(name, "")
    if not value:
        raise RuntimeError(f"{name} is required; no default is provided for connection secrets")
    return value


def env_or(name: str, default: str) -> str:
    return os.getenv(name, default)


def build_app():
    configure_logging()
    dsn = require_env("NOTIFICATION_DB_DSN")
    providers = setup_telemetry(
        service_name="notification-api",
        environment=env_or("NOTIFICATION_DEPLOYMENT_ENVIRONMENT", "develop"),
        endpoint=env_or("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4317"),
        insecure=env_or("OTEL_EXPORTER_OTLP_INSECURE", "true").lower() == "true",
    )
    database = PostgresDatabase(dsn)
    repository = PgNotificationRepository(database)
    template_repository = PgTemplateRepository(database)
    app = create_app(
        SendNotification(repository, template_repository),
        GetNotification(repository),
        {"database": repository},
        metrics=providers.metrics,
        tracer=providers.tracer,
    )

    def shutdown() -> None:
        database.close()
        providers.shutdown()

    app.add_event_handler("shutdown", shutdown)
    return app


def main() -> None:
    app = build_app()
    uvicorn.run(app, host="0.0.0.0", port=int(env_or("PORT", "8080")), log_config=None)


if __name__ == "__main__":
    main()
