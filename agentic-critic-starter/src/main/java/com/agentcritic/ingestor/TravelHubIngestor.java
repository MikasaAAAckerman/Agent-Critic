package com.agentcritic.ingestor;

import com.agentcritic.repository.EvalSampleRepository;
import com.agentcritic.repository.entity.EvalSampleEntity;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Travel-Hub 数据采集器（Pull + Adapter / ETL）。
 *
 * <p>读取 Travel-Hub 的 PG 的 llm_call_log 表，清洗成标准 EvalSample，
 * 增量同步到 AgentCritic 自己的 MySQL（eval_sample 表）。
 *
 * <p>增量语义（主人 6.5 拍板）：只做增量 insert，不删不 update；
 * 哪怕源表被清空，这里已有的数据也不动。水位线记在 ingest_watermark 表。
 */
@Slf4j
@Service
public class TravelHubIngestor {

    private static final String SOURCE = "travel_hub";

    private final JdbcTemplate travelHubJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final EvalSampleRepository evalSampleRepository;

    @Value("${critic.ingest-batch-size:200}")
    private int batchSize;

    public TravelHubIngestor(
            @Qualifier("travelHubJdbcTemplate") JdbcTemplate travelHubJdbcTemplate,
            JdbcTemplate jdbcTemplate,
            EvalSampleRepository evalSampleRepository) {
        this.travelHubJdbcTemplate = travelHubJdbcTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.evalSampleRepository = evalSampleRepository;
    }

    /**
     * 执行一轮增量同步，返回本次入库条数。
     */
    @Transactional
    public int sync() {
        long watermark = getWatermark();
        log.info("[Ingestor] 开始增量同步 | source={} watermark={}", SOURCE, watermark);

        List<Map<String, Object>> rows = travelHubJdbcTemplate.queryForList("""
                SELECT id, trace_id, call_type, agent_name, user_input, llm_output,
                       duration_ms, success, total_tokens, created_at
                FROM llm_call_log
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
                """, watermark, batchSize);

        if (rows.isEmpty()) {
            log.info("[Ingestor] 无新增数据，跳过");
            return 0;
        }

        long maxId = watermark;
        for (Map<String, Object> row : rows) {
            long callId = ((Number) row.get("id")).longValue();
            maxId = Math.max(maxId, callId);
            insertSample(row);
        }

        updateWatermark(maxId);
        log.info("[Ingestor] 同步完成 | inserted={} newWatermark={}", rows.size(), maxId);
        return rows.size();
    }

    private void insertSample(Map<String, Object> row) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("durationMs", row.get("duration_ms"));
        extra.put("success", row.get("success"));
        extra.put("totalTokens", row.get("total_tokens"));
        extra.put("createdAt", row.get("created_at"));

        EvalSampleEntity entity = new EvalSampleEntity();
        entity.setSource(SOURCE);
        entity.setCallId(((Number) row.get("id")).longValue());
        entity.setTraceId(str(row.get("trace_id")));
        entity.setCallType(str(row.get("call_type")));
        entity.setAgentName(str(row.get("agent_name")));
        entity.setInput(str(row.get("user_input")));
        entity.setOutput(str(row.get("llm_output")));
        entity.setExtra(JSON.toJSONString(extra));

        evalSampleRepository.insert(entity);
    }

    private long getWatermark() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT last_id FROM ingest_watermark WHERE source = ?", Long.class, SOURCE);
        return ids.isEmpty() ? 0L : ids.get(0);
    }

    private void updateWatermark(long maxId) {
        jdbcTemplate.update("""
                INSERT INTO ingest_watermark (source, last_id) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE last_id = ?
                """, SOURCE, maxId, maxId);
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }
}
