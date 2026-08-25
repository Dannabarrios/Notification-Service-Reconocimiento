package com.sena.notification_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfiguration {
    @Bean(destroyMethod = "close")
    DataSource dataSource() {
        String dsn = requireEnv("NOTIFICATION_DB_DSN");
        URI uri = URI.create(dsn);
        if (!"postgres".equalsIgnoreCase(uri.getScheme()) && !"postgresql".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("NOTIFICATION_DB_DSN must use postgres:// or postgresql://");
        }

        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            throw new IllegalArgumentException("NOTIFICATION_DB_DSN must include user and password");
        }
        int separator = userInfo.indexOf(':');
        String username = userInfo.substring(0, separator);
        String password = userInfo.substring(separator + 1);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath()
                + (query == null || query.isBlank() ? "" : "?" + query);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("notification-db");
        dataSource.setMaximumPoolSize(10);
        return dataSource;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required; no default is provided for connection secrets");
        }
        return value;
    }
}
