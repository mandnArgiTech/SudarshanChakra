package com.sudarshanchakra.device.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
public abstract class AbstractDeviceIntegrationTest {

    private static final Path INIT_SQL = Path.of(System.getProperty("user.dir"))
            .getParent()
            .getParent()
            .resolve("cloud/db/init.sql");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sudarshanchakra")
            .withUsername("scadmin")
            .withPassword("devpassword123")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INIT_SQL.toAbsolutePath().toString()),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    static {
        if (!Files.exists(INIT_SQL)) {
            throw new IllegalStateException("Schema not found: " + INIT_SQL.toAbsolutePath());
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
