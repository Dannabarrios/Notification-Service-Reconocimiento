from __future__ import annotations

from app.domain.models import Channel, NotificationTemplate, OutboxEvent, SendStatus, SentNotification


class PostgresDatabase:
    def __init__(self, dsn: str, *, min_size: int = 1, max_size: int = 10):
        from psycopg_pool import ConnectionPool

        self.pool = ConnectionPool(
            conninfo=dsn,
            min_size=min_size,
            max_size=max_size,
            open=True,
        )
        self.pool.wait(timeout=10)

    def ping(self) -> None:
        with self.pool.connection() as connection:
            connection.execute("SELECT 1")

    def close(self) -> None:
        self.pool.close()


class PgNotificationRepository:
    def __init__(self, database: PostgresDatabase):
        self.database = database
        self.pool = database.pool

    def ping(self) -> None:
        self.database.ping()

    def save(self, notification: SentNotification) -> None:
        query = """
            INSERT INTO notification.sent_notification
                (recipient_id, recipient_email, channel, subject, body_summary, send_status,
                 template_id, source_service, source_event_id)
            VALUES (%s::uuid, %s, %s, %s, NULLIF(%s,''), %s,
                    NULLIF(%s,'')::uuid, NULLIF(%s,''), NULLIF(%s,'')::uuid)
            RETURNING id::text, created_at
        """
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    query,
                    (
                        notification.recipient_id,
                        notification.recipient_email,
                        notification.channel.value,
                        notification.subject,
                        notification.body_summary,
                        notification.send_status.value,
                        notification.template_id,
                        notification.source_service,
                        notification.source_event_id,
                    ),
                )
                notification.id, notification.created_at = cursor.fetchone()

    def save_with_outbox(self, notification: SentNotification, event: OutboxEvent | None) -> bool:
        from psycopg.types.json import Jsonb

        insert_notification = """
            INSERT INTO notification.sent_notification
                (id, recipient_id, recipient_email, channel, subject, body_summary, send_status,
                 failure_reason, template_id, source_service, source_event_id, sent_at, created_at)
            VALUES (%s::uuid, %s::uuid, %s, %s, %s, NULLIF(%s,''), %s,
                    NULLIF(%s,''), NULLIF(%s,'')::uuid, NULLIF(%s,''), NULLIF(%s,'')::uuid, %s, %s)
            ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
        """
        insert_outbox = """
            INSERT INTO notification.outbox (id, event_id, event_type, payload, created_at)
            VALUES (%s::uuid, %s::uuid, %s, %s, %s)
        """
        with self.pool.connection() as connection:
            with connection.transaction():
                with connection.cursor() as cursor:
                    cursor.execute(
                        insert_notification,
                        (
                            notification.id,
                            notification.recipient_id,
                            notification.recipient_email,
                            notification.channel.value,
                            notification.subject,
                            notification.body_summary,
                            notification.send_status.value,
                            notification.failure_reason,
                            notification.template_id,
                            notification.source_service,
                            notification.source_event_id,
                            notification.sent_at,
                            notification.created_at,
                        ),
                    )
                    if cursor.rowcount == 0:
                        return True
                    if event is not None:
                        cursor.execute(
                            insert_outbox,
                            (
                                event.id,
                                event.event_id,
                                event.event_type,
                                Jsonb(event.payload),
                                event.created_at,
                            ),
                        )
        return False

    def find_by_id(self, notification_id: str) -> SentNotification | None:
        query = """
            SELECT id::text, recipient_id::text, channel, subject, send_status, sent_at
            FROM notification.sent_notification
            WHERE id = %s::uuid
        """
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(query, (notification_id,))
                row = cursor.fetchone()
        if row is None:
            return None
        return SentNotification(
            id=row[0],
            recipient_id=row[1],
            channel=Channel(row[2]),
            subject=row[3],
            send_status=SendStatus(row[4]),
            sent_at=row[5],
        )


class PgTemplateRepository:
    def __init__(self, database: PostgresDatabase):
        self.pool = database.pool

    def find_by_code(self, code: str) -> NotificationTemplate | None:
        query = """
            SELECT id::text, code, channel, subject_template, body_template, is_active, created_at, updated_at
            FROM notification.notification_template
            WHERE code = %s
        """
        with self.pool.connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(query, (code,))
                row = cursor.fetchone()
        if row is None:
            return None
        return NotificationTemplate(
            id=row[0],
            code=row[1],
            channel=Channel(row[2]),
            subject_template=row[3],
            body_template=row[4],
            is_active=row[5],
            created_at=row[6],
            updated_at=row[7],
        )
