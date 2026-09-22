package com.agentcritic.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 单个维度的打分结果。
 *
 * <p>每个维度 = 分数（0-100）+ 理由（归因弹药：为什么扣分，面试讲失败诊断就靠它）。
 */
@Data
@Builder
public class EvalScore {

    /** 维度名（faithfulness / relevance / correctness ...） */
    private String dimension;

    /** 分数（0-100） */
    private int score;

    /** 打分理由（LLM 输出，用于归因分析） */
    private String reason;
}
