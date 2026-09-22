package com.agentcritic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentCritic —— 给 LLM 应用装「裁判」。
 *
 * <p>M1 最小闭环：Ingestor 读 Travel-Hub llm_call_log → 标准表 → Evaluator 取样本
 * → LLMJudge 打分 → Markdown 报告。
 */
@Slf4j
@SpringBootApplication
public class AgentCriticApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentCriticApplication.class, args);
        log.info("[AgentCritic] 启动完成，裁判就位喵！");
    }
}
