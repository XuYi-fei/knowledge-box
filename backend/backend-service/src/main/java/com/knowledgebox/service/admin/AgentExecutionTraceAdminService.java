package com.knowledgebox.service.admin;

import com.knowledgebox.common.ApiException;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import com.knowledgebox.domain.chat.AgentExecutionTrace;
import com.knowledgebox.repository.AgentExecutionTraceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionTraceAdminService {

    private final AgentExecutionTraceRepository traceRepository;

    public AgentExecutionTraceAdminService(AgentExecutionTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    @Transactional
    public void deleteTrace(String traceId) {
        AgentExecutionTrace trace = traceRepository.findByTraceId(traceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRACE_NOT_FOUND", "执行链路不存在"));
        if (trace.getStatus() == AgentExecutionStatus.RUNNING) {
            throw new ApiException(HttpStatus.CONFLICT, "TRACE_RUNNING", "运行中的执行链路不允许删除");
        }
        traceRepository.delete(trace);
    }
}
