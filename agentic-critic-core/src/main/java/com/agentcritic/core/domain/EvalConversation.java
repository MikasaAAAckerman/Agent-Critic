package com.agentcritic.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 一段待评测的多轮对话 —— 勾选的多个样本按序拼成。
 *
 * <p>场景：用户说A → Agent 回B → 用户说C → Agent 回D，
 * 勾选 A/B/C/D 拼成一个 {@link EvalConversation}，Judge 对整个对话质量打一次分。
 */
@Data
@Builder
public class EvalConversation {

    /** 来源标识（travel_hub / sillytavern） */
    private String source;

    /** 多轮对话，顺序即对话时序（勾选顺序） */
    private List<Turn> turns;

    @Data
    @Builder
    public static class Turn {

        /** 对应 eval_sample.id（溯源用） */
        private Long callId;

        /** 角色（用户 / Agent），由样本的 in-out 结构推断 */
        private String role;

        /** 该轮输入 */
        private String input;

        /** 该轮输出 */
        private String output;
    }
}
