package com.agentcritic.config;

import com.agentcritic.core.judge.LLMJudge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * 多渠道 ChatClient（复用 Travel-Hub 的模式）。
 *
 * <p>底层各 provider（deepseek/qwen/dashscope/openrouter）在 yml 里声明各自的
 * models 映射表，角色通过「引用路径」{provider}.models.{modelKey} 动态绑定模型。
 * 切换 Judge 模型 = 改 yml 的 agent-model.judge 一行，代码零改动。
 *
 * <p>M1 只需一个角色：judge（裁判模型，与被测模型不同 + 低温）。
 *
 * <p>重要：手写 OpenAiChatModel 会令 spring-ai-starter-model-openai 的自动配置退让
 * （@ConditionalOnMissingBean），所以所有模型都完全手写，不依赖自动配置。
 */
@Slf4j
@Configuration
public class JudgeClientConfig {

    private final Environment environment;

    public JudgeClientConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 裁判模型 —— LLM-as-Judge 专用。
     *
     * <p>铁律：与被测模型不同（避免自我偏置）+ 低温（0~0.3）保证分数稳定。
     */
    @Bean("judgeClient")
    public ChatClient judgeClient() {
        String ref = environment.getProperty("agent-model.judge");
        ChatModel model = createModel(ref);
        log.info("[Config] Judge 角色绑定 -> {}", ref);
        return ChatClient.builder(model).build();
    }

    /**
     * LLMJudge Bean（供 EvalRunService 注入）。
     */
    @Bean
    public LLMJudge llmJudge(ChatClient judgeClient,
                             @Value("${critic.dimensions:忠实度,相关性,正确性}") String dimensions) {
        return new LLMJudge(judgeClient,
                Arrays.stream(dimensions.split(",")).map(String::trim).toList());
    }

    /**
     * 根据引用路径动态构建 ChatModel。
     *
     * @param ref 引用路径 {provider}.models.{modelKey}，例：qwen.models.qwen3.7-max
     */
    private ChatModel createModel(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalStateException("模型引用路径为空，请检查 agent-model.* 配置");
        }

        int idx = ref.indexOf(".models.");
        if (idx <= 0) {
            throw new IllegalStateException("模型引用路径格式错误，应为 {provider}.models.{modelKey}，实际：" + ref);
        }
        String provider = ref.substring(0, idx);
        String modelKey = ref.substring(idx + ".models.".length());

        String apiKey = environment.getProperty(provider + ".api-key");
        String endpoint = environment.getProperty(provider + ".endpoint");
        String model = environment.getProperty(provider + ".models." + modelKey);

        if (apiKey == null || endpoint == null || model == null) {
            throw new IllegalStateException("模型配置缺失 | provider=" + provider
                    + " | modelKey=" + modelKey
                    + " | apiKey存在=" + (apiKey != null)
                    + " | endpoint存在=" + (endpoint != null)
                    + " | model存在=" + (model != null));
        }

        log.info("[Config] 动态构建模型 | provider={} | modelKey={} | 实际模型名={} | endpoint={}",
                provider, modelKey, model, endpoint);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.0) // 裁判必须稳定：低温
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
