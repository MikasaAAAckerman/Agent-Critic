package com.agentcritic.core.evaluator;

import com.agentcritic.core.domain.EvalResult;
import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.core.judge.Judge;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 评测编排器 —— 逐条执行 TestCase（M1 = EvalSample），收集打分结果。
 *
 * <p>换评测面（模型/RAG/Agent）只需要换 Judge 或 Sample 来源，编排逻辑不变。
 */
@Slf4j
public class Evaluator {

    private final Judge judge;

    public Evaluator(Judge judge) {
        this.judge = judge;
    }

    /**
     * 评测一批样本，返回每条的结果（单条打分失败不中断整体，记入结果）。
     */
    public List<EvalResult> evaluate(List<EvalSample> samples) {
        List<EvalResult> results = new ArrayList<>();
        for (EvalSample sample : samples) {
            try {
                results.add(EvalResult.builder()
                        .sample(sample)
                        .scores(judge.score(sample))
                        .build());
            } catch (Exception e) {
                log.error("[Evaluator] 单条打分失败，跳过 | callId={} traceId={}", sample.getCallId(), sample.getTraceId(), e);
            }
        }
        log.info("[Evaluator] 评测完成 | total={} success={}", samples.size(), results.size());
        return results;
    }
}
