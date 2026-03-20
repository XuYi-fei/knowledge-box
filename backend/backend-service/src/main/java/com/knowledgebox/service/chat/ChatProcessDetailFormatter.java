package com.knowledgebox.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.api.ChatProcessDetailView;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import java.util.List;
import java.util.Map;

final class ChatProcessDetailFormatter {

    private final ObjectMapper objectMapper;

    ChatProcessDetailFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void appendReasoning(List<ChatProcessDetailView> processDetails, String step) {
        completeOpenReasoning(processDetails);
        processDetails.add(new ChatProcessDetailView(
                "reasoning",
                summarize(step, 64),
                expandReasoningDetail(step),
                "进行中",
                "thinking"
        ));
    }

    void completeOpenReasoning(List<ChatProcessDetailView> processDetails) {
        for (int index = processDetails.size() - 1; index >= 0; index--) {
            ChatProcessDetailView candidate = processDetails.get(index);
            if (!"reasoning".equals(candidate.kind())) {
                continue;
            }
            if (!"thinking".equals(candidate.statusTone())) {
                return;
            }
            processDetails.set(index, new ChatProcessDetailView(
                    candidate.kind(),
                    candidate.summary(),
                    candidate.detail(),
                    "已完成",
                    "done"
            ));
            return;
        }
    }

    void appendTool(
            List<ChatProcessDetailView> processDetails,
            String toolName,
            Map<String, Object> toolInput,
            ToolResultBlock result
    ) {
        completeOpenReasoning(processDetails);
        String safeToolName = toolName == null || toolName.isBlank() ? "未知工具" : toolName.trim();
        String inputPreview = toolInput == null || toolInput.isEmpty() ? "" : summarize(stringify(toolInput), 48);
        String summary = inputPreview.isBlank() ? safeToolName : safeToolName + " · " + inputPreview;
        processDetails.add(new ChatProcessDetailView(
                "tool",
                summary,
                buildToolDetail(safeToolName, toolInput, result),
                "已执行",
                "tool"
        ));
    }

    private String expandReasoningDetail(String step) {
        String normalized = step == null ? "" : step.trim();
        if (normalized.isBlank()) {
            return "当前没有可展示的思考细节。";
        }
        if (normalized.startsWith("思考中：")) {
            String detail = normalized.substring("思考中：".length()).trim();
            return "模型当前输出的思考片段：\n" + (detail.isBlank() ? normalized : detail);
        }
        if (normalized.startsWith("模型思考摘要：")) {
            String detail = normalized.substring("模型思考摘要：".length()).trim();
            return "模型在回答收束阶段给出的思考摘要：\n" + (detail.isBlank() ? normalized : detail);
        }
        if (normalized.startsWith("上下文提示：")) {
            String detail = normalized.substring("上下文提示：".length()).trim();
            return "系统记录到的上下文提示内容：\n" + (detail.isBlank() ? normalized : detail);
        }
        if (normalized.startsWith("工具执行：")) {
            String detail = normalized.substring("工具执行：".length()).trim();
            return "系统记录到的工具执行节点：\n" + (detail.isBlank() ? normalized : detail);
        }
        return "系统阶段记录：\n" + normalized;
    }

    private String buildToolDetail(String toolName, Map<String, Object> toolInput, ToolResultBlock result) {
        StringBuilder builder = new StringBuilder();
        builder.append("工具名称：").append(toolName);
        if (result != null && result.getId() != null && !result.getId().isBlank()) {
            builder.append("\n调用 ID：").append(result.getId());
        }
        builder.append("\n\n调用参数：\n");
        builder.append(toolInput == null || toolInput.isEmpty() ? "无结构化入参" : stringify(toolInput));
        builder.append("\n\n执行结果：\n");
        builder.append(extractResultText(result));
        return builder.toString();
    }

    private String extractResultText(ToolResultBlock result) {
        if (result == null) {
            return "未记录到工具返回内容。";
        }
        List<ContentBlock> output = result.getOutput();
        if (output == null || output.isEmpty()) {
            return result.isSuspended() ? "工具调用已挂起，暂未返回结果。" : "工具未返回可展示的输出。";
        }
        StringBuilder builder = new StringBuilder();
        for (ContentBlock block : output) {
            if (block instanceof TextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isBlank()) {
                    if (builder.length() > 0) {
                        builder.append("\n\n");
                    }
                    builder.append(text.strip());
                }
            } else if (block != null) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("返回了 ").append(block.getClass().getSimpleName()).append(" 类型输出。");
            }
        }
        if (builder.length() == 0) {
            return result.isSuspended() ? "工具调用已挂起，暂未返回文本结果。" : "工具未返回可展示的文本输出。";
        }
        return builder.toString();
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String summarize(String value, int limit) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "处理中";
        }
        return normalized.length() > limit ? normalized.substring(0, limit) + "..." : normalized;
    }
}
