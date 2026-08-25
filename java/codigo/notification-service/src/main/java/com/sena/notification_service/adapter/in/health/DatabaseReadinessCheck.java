package com.sena.notification_service.adapter.in.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseReadinessCheck implements ReadinessCheck {
    private final JdbcTemplate jdbc;

    public DatabaseReadinessCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public void check() {
        jdbc.queryForObject("SELECT 1", Integer.class);
    }
}
