package com.sena.notification_service.adapter.out.persistence;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.OutboxEvent;
import com.sena.notification_service.domain.model.SendStatus;
import com.sena.notification_service.domain.model.SentNotification;
import com.sena.notification_service.port.out.NotificationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationRepository implements NotificationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;

    public JdbcNotificationRepository(JdbcTemplate jdbc, TransactionTemplate transactionTemplate, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SentNotification save(SentNotification notification) {
        String sql = """
                INSERT INTO notification.sent_notification
                    (recipient_id, recipient_email, channel, subject, body_summary, send_status,
                     template_id, source_service, source_event_id)
                VALUES (?::uuid, ?, ?, ?, NULLIF(?,''), ?,
                        NULLIF(?,'')::uuid, NULLIF(?,''), NULLIF(?,'')::uuid)
                RETURNING id, created_at
                """;

        return jdbc.queryForObject(sql, (rs, rowNum) -> {
            notification.setId(rs.getObject("id", UUID.class));
            notification.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            return notification;
        },
                notification.getRecipientId().toString(),
                notification.getRecipientEmail(),
                notification.getChannel().name(),
                notification.getSubject(),
                emptyIfNull(notification.getBodySummary()),
                notification.getSendStatus().name(),
                nullableUuid(notification.getTemplateId()),
                emptyIfNull(notification.getSourceService()),
                nullableUuid(notification.getSourceEventId()));
    }

    @Override
    public boolean saveWithOutbox(SentNotification notification, OutboxEvent event) {
        Boolean alreadyProcessed = transactionTemplate.execute(status -> {
            String insertNotification = """
                    INSERT INTO notification.sent_notification
                        (id, recipient_id, recipient_email, channel, subject, body_summary, send_status,
                         failure_reason, template_id, source_service, source_event_id, sent_at, created_at)
                    VALUES (?::uuid, ?::uuid, ?, ?, ?, NULLIF(?,''), ?,
                            NULLIF(?,''), NULLIF(?,'')::uuid, NULLIF(?,''), NULLIF(?,'')::uuid, ?, ?)
                    ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                    """;
            int inserted = jdbc.update(insertNotification,
                    notification.getId().toString(),
                    notification.getRecipientId().toString(),
                    notification.getRecipientEmail(),
                    notification.getChannel().name(),
                    notification.getSubject(),
                    emptyIfNull(notification.getBodySummary()),
                    notification.getSendStatus().name(),
                    emptyIfNull(notification.getFailureReason()),
                    nullableUuid(notification.getTemplateId()),
                    emptyIfNull(notification.getSourceService()),
                    nullableUuid(notification.getSourceEventId()),
                    timestamp(notification.getSentAt()),
                    timestamp(notification.getCreatedAt()));

            if (inserted == 0) {
                return true;
            }

            if (event != null) {
                String insertOutbox = """
                        INSERT INTO notification.outbox (id, event_id, event_type, payload, created_at)
                        VALUES (?::uuid, ?::uuid, ?, ?::jsonb, ?)
                        """;
                try {
                    jdbc.update(insertOutbox,
                            event.id().toString(),
                            event.eventId().toString(),
                            event.eventType(),
                            jsonMapper.writeValueAsString(event.payload()),
                            timestamp(event.createdAt()));
                } catch (Exception ex) {
                    throw new IllegalStateException("could not serialize outbox payload", ex);
                }
            }
            return false;
        });
        return Boolean.TRUE.equals(alreadyProcessed);
    }

    @Override
    public Optional<SentNotification> findById(UUID id) {
        String sql = """
                SELECT id, recipient_id, channel, subject, send_status, sent_at
                FROM notification.sent_notification
                WHERE id = ?::uuid
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            SentNotification notification = new SentNotification();
            notification.setId(rs.getObject("id", UUID.class));
            notification.setRecipientId(rs.getObject("recipient_id", UUID.class));
            notification.setChannel(Channel.valueOf(rs.getString("channel")));
            notification.setSubject(rs.getString("subject"));
            notification.setSendStatus(SendStatus.valueOf(rs.getString("send_status")));
            Timestamp sentAt = rs.getTimestamp("sent_at");
            notification.setSentAt(sentAt == null ? null : sentAt.toInstant());
            return Optional.of(notification);
        }, id.toString());
    }

    private static String nullableUuid(UUID value) {
        return value == null ? "" : value.toString();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
