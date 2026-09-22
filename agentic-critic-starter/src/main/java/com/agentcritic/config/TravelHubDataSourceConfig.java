package com.agentcritic.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 双数据源（都用 DataSourceProperties 初始化，避免 url/jdbcUrl 翻译问题）。
 *
 * <ul>
 *   <li>主数据源（@Primary）：AgentCritic 自己的 MySQL 标准表（spring.datasource）</li>
 *   <li>travelHubDataSource（只读）：Travel-Hub 的 PG（travel-hub.datasource），
 *       Ingestor 从这里拉 llm_call_log</li>
 * </ul>
 */
@Slf4j
@Configuration
public class TravelHubDataSourceConfig {

    // ═══════════ 主数据源：AgentCritic 标准表（MySQL）═══════════

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties props) {
        log.info("[Config] 初始化主数据源 | url={}", props.getUrl());
        return props.initializeDataSourceBuilder().build();
    }

    /**
     * 主库专用 JdbcTemplate。
     *
     * <p>⚠️ 必须显式定义：一旦自定义了任何 JdbcOperations bean（如 travelHubJdbcTemplate），
     * Boot 的 JdbcTemplateAutoConfiguration 会因 @ConditionalOnMissingBean(JdbcOperations)
     * 跳过默认 jdbcTemplate —— 不显式定义的话，无 @Qualifier 的注入会错误落到 PG 那个。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("primaryDataSource") DataSource ds) {
        log.info("[Config] 初始化主库 JdbcTemplate");
        return new JdbcTemplate(ds);
    }

    // ═══════════ 从数据源：Travel-Hub PG（只读）═══════════

    @Bean
    @ConfigurationProperties("travel-hub.datasource")
    public DataSourceProperties travelHubDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("travelHubDataSource")
    public DataSource travelHubDataSource(@Qualifier("travelHubDataSourceProperties") DataSourceProperties props) {
        log.info("[Config] 初始化 Travel-Hub 只读数据源 | url={}", props.getUrl());
        return props.initializeDataSourceBuilder().build();
    }

    @Bean("travelHubJdbcTemplate")
    public JdbcTemplate travelHubJdbcTemplate(@Qualifier("travelHubDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
