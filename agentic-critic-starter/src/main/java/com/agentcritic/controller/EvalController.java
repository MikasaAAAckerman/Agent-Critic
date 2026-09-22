package com.agentcritic.controller;

import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.eval.EvalRunRegistry;
import com.agentcritic.eval.EvalRunService;
import com.agentcritic.ingestor.TravelHubIngestor;
import com.agentcritic.repository.EvalSampleRepository;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 评测 Web API（Step1：分组 / 分页 / 勾选打分 / 停止）。
 *
 * <ul>
 *   <li>GET  /api/eval/sources            → 按 source 分组统计</li>
 *   <li>GET  /api/eval/samples?source=&page=&size= → 分页样本</li>
 *   <li>POST /api/eval/run                → body {source, ids[]} 触发打分（SSE 流式返回）</li>
 *   <li>POST /api/eval/run/{runId}/stop   → 停止（协作式取消）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalSampleRepository repository;
    private final EvalRunService evalRunService;
    private final EvalRunRegistry registry;
    private final TravelHubIngestor ingestor;

    public EvalController(EvalSampleRepository repository,
                          EvalRunService evalRunService,
                          EvalRunRegistry registry,
                          TravelHubIngestor ingestor) {
        this.repository = repository;
        this.evalRunService = evalRunService;
        this.registry = registry;
        this.ingestor = ingestor;
    }

    /** 手动触发数据同步（拉 Travel-Hub llm_call_log 增量入标准表） */
    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        int inserted = ingestor.sync();
        return Map.of("code", 200, "inserted", inserted);
    }

    /** 分组统计：source → count */
    @GetMapping("/sources")
    public List<Map<String, Object>> sources() {
        return repository.findSourceGroups();
    }

    /** 分页样本（转成前端友好的 {items,total,page,size} 结构，不暴露 MyBatis-Plus Page 的 records） */
    @GetMapping("/samples")
    public Map<String, Object> samples(@RequestParam("source") String source,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<EvalSample> p = repository.findBySourcePage(source, page, size);
        return Map.of(
                "items", p.getRecords(),
                "total", p.getTotal(),
                "page", p.getCurrent(),
                "size", p.getSize());
    }

    /** 触发打分（勾选的多行拼成多轮对话，整体评一次分），SSE 流式返回 */
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> run(@RequestBody Map<String, Object> body) {
        String source = (String) body.get("source");
        List<Long> ids = ((List<?>) body.get("ids")).stream()
                .map(o -> ((Number) o).longValue())
                .toList();
        log.info("[Eval] 触发评测 | source={} ids={}", source, ids);

        return evalRunService.run(source, ids)
                .map(data -> ServerSentEvent.<String>builder()
                        .event("eval-result")
                        .data(data)
                        .build());
    }

    /** 停止：置 cancelled + 掐 SSE 流（幂等） */
    @PostMapping("/run/{runId}/stop")
    public Map<String, Object> stop(@PathVariable String runId) {
        EvalRunRegistry.EvalRunSession session = registry.remove(runId);
        if (session == null) {
            return Map.of("code", 200, "message", "运行不存在或已结束", "runId", runId);
        }
        session.getCancelled().set(true);
        if (session.getSubscription() != null) {
            session.getSubscription().dispose();
        }
        session.getSink().tryEmitComplete();
        log.info("[Eval] 用户停止评测 | runId={}", runId);
        return Map.of("code", 200, "message", "已停止", "runId", runId);
    }
}
