package com.agentcritic.eval;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进行中的评测运行注册表。
 *
 * <p>runId → EvalRunSession，支撑「停止」：
 * <ul>
 *   <li>cancelled 标志：打分线程检查点（正在执行的 LLM call 跑完后不再继续）</li>
 *   <li>dispose()：掐 SSE 流（物理断开）</li>
 *   <li>sink.tryEmitComplete()：前端 SSE 正常关闭</li>
 * </ul>
 */
@Slf4j
@Component
public class EvalRunRegistry {

    private final ConcurrentHashMap<String, EvalRunSession> runs = new ConcurrentHashMap<>();

    /**
     * 注册并返回 runId。
     */
    public String register(Sinks.Many<String> sink) {
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        runs.put(runId, new EvalRunSession(sink));
        log.info("[EvalRun] 注册运行 | runId={}", runId);
        return runId;
    }

    /**
     * 绑订 Disposable（打分线程拿到订阅后才能绑）。
     */
    public void bind(String runId, Disposable disposable) {
        EvalRunSession session = runs.get(runId);
        if (session != null) {
            session.setSubscription(disposable);
        }
    }

    public EvalRunSession remove(String runId) {
        return runs.remove(runId);
    }

    public boolean isCancelled(String runId) {
        EvalRunSession s = runs.get(runId);
        return s != null && s.getCancelled().get();
    }

    @Data
    public static class EvalRunSession {
        private final Sinks.Many<String> sink;
        private volatile Disposable subscription;
        private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);

        public EvalRunSession(Sinks.Many<String> sink) {
            this.sink = sink;
        }
    }
}
