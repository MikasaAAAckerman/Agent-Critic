package com.agentcritic.repository;

import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.repository.entity.EvalSampleEntity;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测样本仓储 —— 评分业务唯一数据源 = eval_sample 标准表（MyBatis-Plus 参数化查询）。
 *
 * <p>其他数据源（Travel-Hub 等）如何汇总进这张表是 Ingestor 的事，评分/展示逻辑不感知。
 */
@Slf4j
@Repository
public class EvalSampleRepository {

    private final EvalSampleMapper mapper;

    public EvalSampleRepository(EvalSampleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按 source 分组统计（页面分组列表）。
     */
    public List<Map<String, Object>> findSourceGroups() {
        return mapper.countBySource();
    }

    /**
     * 按 source 分页查询（MyBatis-Plus 分页插件，参数化）。
     */
    public Page<EvalSample> findBySourcePage(String source, int page, int size) {
        Page<EvalSampleEntity> p = mapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<EvalSampleEntity>()
                        .eq(EvalSampleEntity::getSource, source)
                        .orderByDesc(EvalSampleEntity::getId));

        Page<EvalSample> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toDomain).toList());
        return result;
    }

    /**
     * 按勾选 id 查询（eval_sample 主键 id，统一标准表主键），保持勾选顺序（多轮对话时序不能乱）。
     *
     * <p>⚠️ 用主键 id 而非 call_id：不同来源（Travel-Hub / SillyTavern）的 call_id
     * 来自各自源表自增，会撞 id；eval_sample 主键全局唯一。
     * 不拼接 ORDER BY FIELD（避免注入面）：查回后在内存里按传入顺序重排。
     */
    public List<EvalSample> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<EvalSampleEntity> entities = mapper.selectList(
                new LambdaQueryWrapper<EvalSampleEntity>()
                        .in(EvalSampleEntity::getId, ids));

        // 按传入顺序重排（LinkedHashMap 保序）
        Map<Long, EvalSample> byId = new LinkedHashMap<>();
        for (EvalSampleEntity e : entities) {
            byId.put(e.getId(), toDomain(e));
        }
        List<EvalSample> ordered = new ArrayList<>();
        for (Long id : ids) {
            EvalSample s = byId.get(id);
            if (s != null) {
                ordered.add(s);
            }
        }
        return ordered;
    }

    /**
     * 插入一条样本（Ingestor 汇总用）。
     */
    public int insert(EvalSampleEntity entity) {
        return mapper.insert(entity);
    }

    private EvalSample toDomain(EvalSampleEntity e) {
        return EvalSample.builder()
                .id(e.getId())
                .source(e.getSource())
                .callId(e.getCallId())
                .traceId(e.getTraceId())
                .callType(e.getCallType())
                .agentName(e.getAgentName())
                .input(e.getInput())
                .output(e.getOutput())
                .extra(parseExtra(e.getExtra()))
                .build();
    }

    private Map<String, Object> parseExtra(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return JSON.parseObject(json);
        } catch (Exception ex) {
            log.warn("[Repository] extra 解析失败，忽略 | {}", json);
            return Map.of();
        }
    }
}
