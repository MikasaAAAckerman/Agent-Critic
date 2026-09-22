package com.agentcritic.core.judge;

import com.agentcritic.core.domain.EvalConversation;
import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.core.domain.EvalScore;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-as-Judge —— 让裁判模型给「一轮 in-out」的各维度打分。
 *
 * <p>设计要点（可靠性铁律）：
 * <ul>
 *   <li>Judge 模型与被测模型不同（避免自我偏置）—— 由 starter 装配独立的 judgeClient</li>
 *   <li>温度低（0~0.3），保证分数稳定</li>
 *   <li>结构化输出：JSON（维度 + 分数 + 理由），理由即归因弹药</li>
 * </ul>
 */
@Slf4j
public class LLMJudge implements Judge {

    /** Judge 专用 ChatClient（由 starter 装配，与被测模型分离） */
    private final ChatClient judgeClient;

    /** 打分维度 */
    private final List<String> dimensions;

    public LLMJudge(ChatClient judgeClient, List<String> dimensions) {
        this.judgeClient = judgeClient;
        this.dimensions = dimensions;
    }

    @Override
    public List<EvalScore> score(EvalSample sample) {
        String prompt = buildJudgePrompt(sample);
        String content = judgeClient.prompt()
                .user(prompt)
                .call()
                .content();
        return parseScores(content);
    }

    /**
     * 对一段「多轮对话」整体打分（勾选的多个样本拼成的上下文）。
     *
     * <p>与单样本打分的区别：Judge 看到的是完整对话流（用户A→Agent B→用户C→Agent D），
     * 评估的是整段对话的连贯性与质量，而非单次 in-out。
     */
    public List<EvalScore> scoreConversation(EvalConversation conversation) {
        String prompt = buildConversationPrompt(conversation);
        String content = judgeClient.prompt()
                .user(prompt)
                .call()
                .content();
        return parseScores(content);
    }

    /**
     * 多轮对话打分 Prompt：把勾选的 turns 按序拼成对话，让 Judge 整体评估。
     */
    private String buildConversationPrompt(EvalConversation conversation) {
        StringBuilder dialog = new StringBuilder();
        int idx = 1;
        for (EvalConversation.Turn turn : conversation.getTurns()) {
            dialog.append("[第").append(idx).append("轮] 用户：")
                  .append(truncate(turn.getInput())).append("\n")
                  .append("       Agent：")
                  .append(truncate(turn.getOutput())).append("\n\n");
            idx++;
        }

        return """
                你是一位严格的 AI 应用质量评审专家。请对下面这段「用户 ↔ Agent」的【多轮对话】进行整体质量打分。

                评测维度（每项 0-100 分）：
                %s

                对话：
                %s

                要求：
                1. 评估整段对话的连贯性、各轮回复质量、是否合理推进用户目标
                2. 每项必须给出「分数」和「扣分理由」（理由要具体，能指导改进）
                3. 严格按以下 JSON 格式输出，不要输出任何其他内容：
                {"scores":[{"dimension":"连贯性","score":85,"reason":"..."}]}
                """.formatted(
                String.join(" / ", dimensions),
                dialog);
    }

    /**
     * 打分 Prompt：要求 LLM 按 JSON 输出各维度分数 + 理由。
     */
    private String buildJudgePrompt(EvalSample sample) {
        return """
                你是一位严格的 AI 应用质量评审专家。请对下面这一轮「输入 → 输出」进行打分。

                评测维度（每项 0-100 分）：
                %s

                输入：
                %s

                输出：
                %s

                要求：
                1. 只依据「输出」相对「输入」的质量打分，不要臆测
                2. 每项必须给出「分数」和「扣分理由」（理由要具体，能指导改进）
                3. 严格按以下 JSON 格式输出，不要输出任何其他内容：
                {"scores":[{"dimension":"忠实度","score":85,"reason":"..."}]}
                """.formatted(
                String.join(" / ", dimensions),
                truncate(sample.getInput()),
                truncate(sample.getOutput()));
    }

    private List<EvalScore> parseScores(String content) {
        List<EvalScore> scores = new ArrayList<>();
        try {
            // 容忍 LLM 输出前后带 ```json 包裹
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "");
            }
            JSONObject root = JSON.parseObject(json);
            JSONArray arr = root.getJSONArray("scores");
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                scores.add(EvalScore.builder()
                        .dimension(o.getString("dimension"))
                        .score(o.getIntValue("score"))
                        .reason(o.getString("reason"))
                        .build());
            }
        } catch (Exception e) {
            scores.add(EvalScore.builder()
                    .dimension("parse_error")
                    .score(0)
                    .reason("Judge 输出无法解析: " + truncate(content))
                    .build());
        }
        return scores;
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 2000 ? text.substring(0, 2000) + "...(截断)" : text;
    }
}
