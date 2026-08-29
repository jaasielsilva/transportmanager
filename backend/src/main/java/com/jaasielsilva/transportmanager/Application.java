package com.jaasielsilva.transportmanager;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Molde do kit — Application.java (raiz do pacote)
 *
 * EnableScheduling  -> DunningJob, expiracao de trial, purge
 * EnableSchedulerLock -> impede o job de rodar em dobro com 2+ instancias
 * EnableAsync       -> envio de e-mail fora da thread da requisicao
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()      // relogio do banco: imune a drift entre instancias
                        .build());
    }
}
