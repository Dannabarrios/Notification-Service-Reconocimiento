import os
from uuid import uuid4

import pytest

from app.adapters.outbound.persistence import PgNotificationRepository, PgTemplateRepository, PostgresDatabase
from app.domain.models import Channel, SendStatus, SentNotification

pytestmark = pytest.mark.integration


def _database_or_skip():
    dsn = os.getenv("NOTIFICATION_DB_DSN")
    if not dsn:
        pytest.skip("NOTIFICATION_DB_DSN not set")
    return PostgresDatabase(dsn)


def test_save_and_find_by_id_against_notification_schema():
    db = _database_or_skip()
    repo = PgNotificationRepository(db)
    notification = SentNotification(
        recipient_id="22222222-2222-2222-2222-222222222222",
        recipient_email="integration-test@example.com",
        channel=Channel.EMAIL,
        subject="FastAPI migration integration test",
        send_status=SendStatus.PENDING,
        source_service="notification-service-it",
        source_event_id=str(uuid4()),
    )
    try:
        repo.save(notification)
        assert notification.id
        found = repo.find_by_id(notification.id)
        assert found is not None
        assert found.send_status == SendStatus.PENDING
    finally:
        if notification.id:
            with db.pool.connection() as connection:
                connection.execute("DELETE FROM notification.sent_notification WHERE id = %s::uuid", (notification.id,))
        db.close()


def test_seeded_template_is_read_without_schema_changes():
    db = _database_or_skip()
    try:
        template = PgTemplateRepository(db).find_by_code("SCHEDULE_PUBLISHED")
        assert template is not None
        assert template.is_active is True
    finally:
        db.close()
