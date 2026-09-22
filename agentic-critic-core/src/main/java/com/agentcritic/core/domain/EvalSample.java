package com.agentcritic.core.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 评测样本 —— 一条待评分的「一轮 in-out」。
 *
 * <p>M1 粒度：llm_call_log 一行 = 一个 EvalSample（单次调用 input → output）。
 * 后续升级轮次级：trace_id 分组 + 最终输出。
 */
@Data
@Builder
public class EvalSample {

    /** eval_sample 主键 id（统一数据源的标准主键，页面勾选用它） */
    private Long id;

    /** 来源标识（travel_hub / sillytavern） */
    private String source;

    /** 源表调用 ID（llm_call_log.id，仅溯源参考，不做勾选键） */
    private Long callId;

    /** 会话级 traceId（一次用户请求一个） */
    private String traceId;

    /** 调用类型（PLANNER / WORKER / ASSISTANT） */
    private String callType;

    /** Agent 名称 */
    private String agentName;

    /** 输入（发给 LLM 的 prompt） */
    private String input;

    /** 输出（LLM 的回复） */
    private String output;

    /** 附加元数据（耗时、token、是否成功等） */
    private Map<String, Object> extra;
}
