package com.agentcritic.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单条评测结果 = 评测样本 + 各维度打分。
 */
@Data
@Builder
public class EvalResult {

    private EvalSample sample;

    private List<EvalScore> scores;
}
