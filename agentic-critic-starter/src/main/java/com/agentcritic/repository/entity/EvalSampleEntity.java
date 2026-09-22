package com.agentcritic.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * eval_sample 标准表实体 —— 评分业务唯一数据源。
 *
 * <p>其他数据源（Travel-Hub 等）如何汇总进这张表是 Ingestor 的事，
 * 评分/展示逻辑只依赖本表（call_id 是源表 llm_call_log 的主键）。
 */
@Data
@TableName("eval_sample")
public class EvalSampleEntity {

    /** 本表自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源（travel_hub / sillytavern） */
    private String source;

    /** 源表 llm_call_log.id（页面勾选用它） */
    private Long callId;

    /** 会话级 traceId */
    private String traceId;

    /** PLANNER / WORKER / ROUTER ... */
    private String callType;

    /** Agent 名称 */
    private String agentName;

    /** 输入 */
    private String input;

    /** 输出 */
    private String output;

    /** 附加元数据（JSON） */
    private String extra;

    private LocalDateTime createdAt;
}
