package com.agentcritic.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件。
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        // 关闭 count SQL 优化：默认会用 JSqlParser 把主查询改写成 count（autoCountSql），
        // 某些字段名会解析失败（WARN），关掉后走 lowLevelCountSql（包一层 COUNT(*)）更稳
        pagination.setOptimizeJoin(false);
        interceptor.addInnerInterceptor(pagination);
        log.info("[Config] MyBatis-Plus 分页插件已配置（count 优化已关闭）");
        return interceptor;
    }
}
