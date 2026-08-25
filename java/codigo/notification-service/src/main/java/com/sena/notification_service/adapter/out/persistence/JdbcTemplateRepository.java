package com.sena.notification_service.adapter.out.persistence;

import com.sena.notification_service.domain.model.Channel;
import com.sena.notification_service.domain.model.NotificationTemplate;
import com.sena.notification_service.port.out.TemplateRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcTemplateRepository implements TemplateRepository {
    private final JdbcTemplate jdbc;

    public JdbcTemplateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationTemplate> findByCode(String code) {
        String sql = """
                SELECT id, code, channel, subject_template, body_template, is_active, created_at, updated_at
                FROM notification.notification_template
                WHERE code = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new NotificationTemplate(
                    rs.getObject("id", java.util.UUID.class),
                    rs.getString("code"),
                    Channel.valueOf(rs.getString("channel")),
                    rs.getString("subject_template"),
                    rs.getString("body_template"),
                    rs.getBoolean("is_active"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()));
        }, code);
    }
}
