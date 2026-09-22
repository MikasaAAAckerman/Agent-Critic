package com.agentcritic.core.judge;

import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.core.domain.EvalScore;

import java.util.List;

/**
 * 裁判抽象 —— 三态 Judge 的统一接口。
 *
 * <ul>
 *   <li>{@link LLMJudge}：LLM-as-Judge（忠实度 / 相关性 / 正确性，主观质量）</li>
 *   <li>RuleJudge：规则打分（格式 / 关键词 / JSON Schema，客观约束）</li>
 *   <li>MetricJudge：指标计算（Recall@K / MRR / NDCG，数值检索指标）</li>
 * </ul>
 */
public interface Judge {

    /**
     * 对一个评测样本打分，返回各维度分数。
     */
    List<EvalScore> score(EvalSample sample);
}
