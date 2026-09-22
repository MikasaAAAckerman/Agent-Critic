package com.agentcritic.eval;

import com.agentcritic.core.domain.EvalConversation;
import com.agentcritic.core.domain.EvalSample;
import com.agentcritic.core.domain.EvalScore;
import com.agentcritic.core.judge.LLMJudge;
import com.agentcritic.repository.EvalSampleRepository;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测运行编排：一次 run = 勾选的多行拼成一段多轮对话 → 整体打分 → 推结果。
 *
 * <p>协作式取消（复用 Travel-Hub 已验证的模式）：
 * <ul>
 *   <li>打分前检查 cancelled —— 命中则不发起 Judge 调用</li>
 *   <li>dispose() 掐 SSE 流 —— 页面立即关闭</li>
 *   <li>正在执行的 Judge LLM call 是阻塞调用（物理边界会跑完），
 *       但页面已断开、流已结束</li>
 * </ul>
 */
@Slf4j
@Service
public class EvalRunService {

    private final EvalSampleRepository repository;
    private final LLMJudge llmJudge;
    private final EvalRunRegistry registry;

    public EvalRunService(EvalSampleRepository repository, LLMJudge llmJudge, EvalRunRegistry registry) {
        this.repository = repository;
        this.llmJudge = llmJudge;
        this.registry = registry;
    }

    /**
     * 启动一次评测运行，返回 SSE 流（注册进 registry 以便停止）。
     */
    public Flux<String> run(String source, List<Long> ids) {
        // ⚠️ 用 replay(1) 而不是 unicast：unicast 在订阅者出现前 emit 会被丢弃
        //    （started 事件在框架订阅 sink 之前就发了，unicast 会丢它 → runId 拿不到 → 停止失效）
        Sinks.Many<String> sink = Sinks.many().replay().latest();
        String runId = registry.register(sink);

        // 先推 started 事件（带 runId）—— 前端靠它拿到停止句柄（replay 会缓存给订阅者）
        sink.tryEmitNext(JSON.toJSONString(Map.of("runId", runId, "status", "started")));

        // 打分放独立线程，避免阻塞 WebFlux 事件循环
        var disposable = Flux.defer(() -> {
                    if (registry.isCancelled(runId)) {
                        sink.tryEmitComplete();
                        return Flux.empty();
                    }

                    log.info("[EvalRun] 开始查询样本 | runId={} ids={}", runId, ids);
                    List<EvalSample> samples = repository.findByIds(ids);
                    EvalConversation conversation = buildConversation(source, samples);
                    log.info("[EvalRun] 开始打分 | runId={} turns={}", runId, samples.size());

                    List<EvalScore> scores = llmJudge.scoreConversation(conversation);

                    Map<String, Object> result = new HashMap<>();
                    result.put("runId", runId);
                    result.put("status", "done");
                    result.put("source", source);
                    result.put("turns", conversation.getTurns());
                    result.put("scores", scores);
                    sink.tryEmitNext(JSON.toJSONString(result));
                    sink.tryEmitComplete();
                    registry.remove(runId);
                    log.info("[EvalRun] 打分完成 | runId={}", runId);
                    return Flux.empty();
                })
                .doOnError(e -> {
                    // ⚠️ 必须把异常对象传给 logback（最后参数）才能看到完整堆栈；
                    // printStackTrace 走 System.err，绕过 logback 看不到
                    log.error("[EvalRun] 打分异常 | runId={}", runId, e);
                    if (sink.currentSubscriberCount() > 0) {
                        sink.tryEmitNext("{\"error\":\"" + e.getMessage() + "\"}");
                    }
                    sink.tryEmitComplete();
                    registry.remove(runId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        registry.bind(runId, disposable);
        return sink.asFlux();
    }

    /**
     * 把勾选的样本按序拼成多轮对话（每行 = 一轮 用户输入 → Agent 输出）。
     */
    private EvalConversation buildConversation(String source, List<EvalSample> samples) {
        List<EvalConversation.Turn> turns = samples.stream()
                .map(s -> EvalConversation.Turn.builder()
                        .callId(s.getCallId())
                        .role("用户→Agent")
                        .input(s.getInput())
                        .output(s.getOutput())
                        .build())
                .toList();
        return EvalConversation.builder()
                .source(source)
                .turns(turns)
                .build();
    }
}
