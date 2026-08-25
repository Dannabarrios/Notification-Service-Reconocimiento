from __future__ import annotations

import logging
import os
import signal
import threading

import uvicorn

from app.adapters.inbound.amqp import RabbitMQConsumer
from app.adapters.inbound.http import create_health_app
from app.adapters.outbound.messaging import OutboxRelay, RabbitMQPublisher
from app.adapters.outbound.notifier import CompositeNotifier, InAppNotifier, SMTPNotifier
from app.adapters.outbound.persistence import PgNotificationRepository, PgTemplateRepository, PostgresDatabase
from app.adapters.outbound.recipient import StubRecipientResolver
from app.application.use_cases import ConsumeDomainEvent
from app.platform.logging import configure_logging
from app.platform.telemetry import setup_telemetry

logger = logging.getLogger("notification-worker")


def require_env(name: str) -> str:
    value = os.getenv(name, "")
    if not value:
        raise RuntimeError(f"{name} is required; no default is provided for connection secrets")
    return value


def env_or(name: str, default: str) -> str:
    return os.getenv(name, default)


def main() -> None:
    configure_logging()
    db_dsn = require_env("NOTIFICATION_DB_DSN")
    amqp_url = require_env("NOTIFICATION_AMQP_URL")
    smtp_address = require_env("NOTIFICATION_SMTP_ADDR")
    smtp_from = env_or("NOTIFICATION_SMTP_FROM", "notifications@sena.local")
    stub_email = env_or("NOTIFICATION_RECIPIENT_STUB_EMAIL", "dev-notifications@sena.local")

    providers = setup_telemetry(
        service_name="notification-worker",
        environment=env_or("NOTIFICATION_DEPLOYMENT_ENVIRONMENT", "develop"),
        endpoint=env_or("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4317"),
        insecure=env_or("OTEL_EXPORTER_OTLP_INSECURE", "true").lower() == "true",
    )
    database = PostgresDatabase(db_dsn)
    repository = PgNotificationRepository(database)
    template_repository = PgTemplateRepository(database)
    notifier = CompositeNotifier(
        SMTPNotifier(smtp_address, smtp_from),
        InAppNotifier(),
        providers.metrics,
    )
    use_case = ConsumeDomainEvent(
        StubRecipientResolver(stub_email),
        notifier,
        repository,
        template_repository,
    )

    consumer = RabbitMQConsumer(amqp_url, use_case, tracer=providers.tracer)
    consumer.connect()
    publisher = RabbitMQPublisher(amqp_url)
    publisher.connect()
    relay = OutboxRelay(database, publisher, tracer=providers.tracer)

    stop_event = threading.Event()
    relay_thread = threading.Thread(
        target=relay.run,
        args=(stop_event, 2.0, 20),
        name="notification-outbox-relay",
        daemon=True,
    )
    relay_thread.start()

    health_app = create_health_app(
        {"database": repository, "broker": consumer},
        metrics=providers.metrics,
        tracer=providers.tracer,
    )
    health_server = uvicorn.Server(
        uvicorn.Config(
            health_app,
            host="0.0.0.0",
            port=int(env_or("WORKER_HEALTH_PORT", "8081")),
            log_config=None,
        )
    )
    health_thread = threading.Thread(target=health_server.run, name="worker-health", daemon=True)
    health_thread.start()

    def request_stop(signum=None, frame=None):
        stop_event.set()
        health_server.should_exit = True
        if consumer.connection is not None and getattr(consumer.connection, "is_open", False):
            try:
                consumer.connection.add_callback_threadsafe(consumer.channel.stop_consuming)
            except Exception:
                logger.exception("failed to request AMQP consumer shutdown")

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)

    logger.info("notification-worker: consuming scheduling.schedule.published, monitoring.alert.triggered")
    try:
        consumer.run()
    finally:
        request_stop()
        relay_thread.join(timeout=5)
        health_thread.join(timeout=5)
        consumer.close()
        publisher.close()
        database.close()
        providers.shutdown()
        logger.info("notification-worker: shutting down")


if __name__ == "__main__":
    main()
