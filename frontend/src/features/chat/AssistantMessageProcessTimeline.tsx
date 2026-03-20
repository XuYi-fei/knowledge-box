import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { Tag, Typography } from 'antd';
import type { ChatMessageStatus, ChatProcessDetail } from '../../lib/types';

type AssistantMessageProcessTimelineProps = {
  messageId: string;
  reasoningSteps: string[];
  processDetails?: ChatProcessDetail[];
  toolCalls: string[];
  status: ChatMessageStatus;
  content: string;
  errorMessage?: string | null;
  showInternalReasoning?: boolean;
};

type ProcessItem = {
  key: string;
  kind: 'reasoning' | 'tool' | 'answer';
  title: string;
  summary: string;
  detail: ReactNode;
  statusLabel: string;
  statusTone: 'thinking' | 'tool' | 'done' | 'error';
};

function summarizeStep(step: string) {
  const normalized = step.replace(/\s+/g, ' ').trim();
  if (!normalized) {
    return '处理中';
  }
  return normalized.length > 44 ? `${normalized.slice(0, 44)}...` : normalized;
}

function previewText(text: string, limit = 140) {
  const normalized = text.replace(/\s+/g, ' ').trim();
  if (!normalized) {
    return '';
  }
  return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized;
}

function answerTitle(status: ChatMessageStatus) {
  if (status === 'STREAMING' || status === 'PENDING') {
    return '生成最终回答';
  }
  if (status === 'COMPLETED') {
    return '最终回答已完成';
  }
  if (status === 'CANCELLED') {
    return '回答已停止';
  }
  if (status === 'FAILED') {
    return '回答生成失败';
  }
  return '回答输出';
}

function answerStatusLabel(status: ChatMessageStatus) {
  if (status === 'STREAMING' || status === 'PENDING') {
    return '进行中';
  }
  if (status === 'COMPLETED') {
    return '已完成';
  }
  if (status === 'CANCELLED') {
    return '已停止';
  }
  if (status === 'FAILED') {
    return '失败';
  }
  return '已记录';
}

function answerStatusTone(status: ChatMessageStatus): ProcessItem['statusTone'] {
  if (status === 'FAILED' || status === 'CANCELLED') {
    return 'error';
  }
  if (status === 'COMPLETED') {
    return 'done';
  }
  return 'thinking';
}

function reasoningStatus(index: number, total: number, status: ChatMessageStatus) {
  const isActiveStep = index === total - 1 && (status === 'STREAMING' || status === 'PENDING');
  if (isActiveStep) {
    return {
      statusLabel: '进行中',
      statusTone: 'thinking' as const,
    };
  }
  return {
    statusLabel: '已完成',
    statusTone: 'done' as const,
  };
}

function statusIcon(tone: ProcessItem['statusTone']) {
  if (tone === 'tool') {
    return <ToolOutlined />;
  }
  if (tone === 'done') {
    return <CheckCircleOutlined />;
  }
  if (tone === 'error') {
    return <CloseCircleOutlined />;
  }
  return <LoadingOutlined />;
}

function statusTagColor(tone: ProcessItem['statusTone']) {
  if (tone === 'tool') {
    return 'cyan';
  }
  if (tone === 'done') {
    return 'green';
  }
  if (tone === 'error') {
    return 'red';
  }
  return 'processing';
}

function isInternalReasoningSummary(summary: string) {
  const normalized = summary.replace(/\s+/g, ' ').trim();
  return normalized.startsWith('查询路由[');
}

function buildProcessItemsFromDetails(
  messageId: string,
  processDetails: ChatProcessDetail[],
  showInternalReasoning: boolean,
): ProcessItem[] {
  let reasoningIndex = 0;
  let toolIndex = 0;
  const isKnownStatusTone = (
    tone: ChatProcessDetail['statusTone'],
  ): tone is ProcessItem['statusTone'] =>
    tone === 'tool' || tone === 'done' || tone === 'error' || tone === 'thinking';
  const resolveStatusTone = (
    detail: ChatProcessDetail,
    kind: 'reasoning' | 'tool',
  ): ProcessItem['statusTone'] => {
    if (isKnownStatusTone(detail.statusTone)) {
      return detail.statusTone;
    }
    return kind === 'tool' ? 'tool' : 'thinking';
  };
  return processDetails.flatMap((detail, index) => {
    const kind = detail.kind === 'tool' ? 'tool' : 'reasoning';
    const summary = detail.summary || '处理中';
    if (!showInternalReasoning && kind === 'reasoning' && isInternalReasoningSummary(summary)) {
      return [];
    }
    if (kind === 'tool') {
      toolIndex += 1;
    } else {
      reasoningIndex += 1;
    }
    return [{
      key: `${messageId}-process-${index}`,
      kind,
      title: kind === 'tool' ? `工具调用 ${toolIndex}` : `思考 ${reasoningIndex}`,
      summary,
      detail: <Typography.Paragraph className="message-process-detail">{detail.detail || detail.summary}</Typography.Paragraph>,
      statusLabel: detail.statusLabel || (kind === 'tool' ? '已执行' : '已记录'),
      statusTone: resolveStatusTone(detail, kind),
    }];
  });
}

function buildProcessItems(
  messageId: string,
  reasoningSteps: string[],
  toolCalls: string[],
  status: ChatMessageStatus,
  content: string,
  showInternalReasoning: boolean,
  errorMessage?: string | null,
): ProcessItem[] {
  const items: ProcessItem[] = [];
  const visibleReasoningSteps = showInternalReasoning
    ? reasoningSteps
    : reasoningSteps.filter((step) => !isInternalReasoningSummary(step));

  visibleReasoningSteps.forEach((step, index) => {
    const reasoningStepStatus = reasoningStatus(index, visibleReasoningSteps.length, status);
    items.push({
      key: `${messageId}-reasoning-${index}`,
      kind: 'reasoning',
      title: `思考 ${index + 1}`,
      summary: summarizeStep(step),
      detail: <Typography.Paragraph className="message-process-detail">{step}</Typography.Paragraph>,
      statusLabel: reasoningStepStatus.statusLabel,
      statusTone: reasoningStepStatus.statusTone,
    });
  });

  toolCalls.forEach((tool, index) => {
    items.push({
      key: `${messageId}-tool-${tool}-${index}`,
      kind: 'tool',
      title: `工具调用 ${index + 1}`,
      summary: tool,
      detail: (
        <div className="message-process-detail-stack">
          <Typography.Paragraph className="message-process-detail">已记录工具调用：{tool}</Typography.Paragraph>
          <Typography.Text type="secondary">该工具名来自当前消息的 Agent 执行结果。</Typography.Text>
        </div>
      ),
      statusLabel: '已执行',
      statusTone: 'tool',
    });
  });

  if (content || errorMessage || status !== 'PENDING') {
    items.push({
      key: `${messageId}-answer`,
      kind: 'answer',
      title: answerTitle(status),
      summary: summarizeStep(
        errorMessage && status !== 'COMPLETED'
          ? errorMessage
          : content || (status === 'STREAMING' ? '回答仍在流式输出中' : '等待生成回答'),
      ),
      detail: (
        <div className="message-process-detail-stack">
          {content ? <Typography.Paragraph className="message-process-detail">回答摘要：{previewText(content)}</Typography.Paragraph> : null}
          {errorMessage ? <Typography.Paragraph className="message-process-detail">状态说明：{previewText(errorMessage, 180)}</Typography.Paragraph> : null}
          <Typography.Text type="secondary">
            {content ? `回答长度约 ${content.length} 个字符。` : '当前还没有可展示的回答正文。'}
          </Typography.Text>
        </div>
      ),
      statusLabel: answerStatusLabel(status),
      statusTone: answerStatusTone(status),
    });
  }

  return items;
}

export function AssistantMessageProcessTimeline({
  messageId,
  reasoningSteps,
  processDetails,
  toolCalls,
  status,
  content,
  errorMessage,
  showInternalReasoning = false,
}: AssistantMessageProcessTimelineProps) {
  const baseItems = processDetails && processDetails.length
    ? buildProcessItemsFromDetails(messageId, processDetails, showInternalReasoning)
    : buildProcessItems(messageId, reasoningSteps, toolCalls, status, content, showInternalReasoning, errorMessage);
  const items = [...baseItems];
  if (processDetails && processDetails.length) {
    if (content || errorMessage || status !== 'PENDING') {
      items.push({
        key: `${messageId}-answer`,
        kind: 'answer',
        title: answerTitle(status),
        summary: summarizeStep(
          errorMessage && status !== 'COMPLETED'
            ? errorMessage
            : content || (status === 'STREAMING' ? '回答仍在流式输出中' : '等待生成回答'),
        ),
        detail: (
          <div className="message-process-detail-stack">
            {content ? <Typography.Paragraph className="message-process-detail">回答摘要：{previewText(content)}</Typography.Paragraph> : null}
            {errorMessage ? <Typography.Paragraph className="message-process-detail">状态说明：{previewText(errorMessage, 180)}</Typography.Paragraph> : null}
            <Typography.Text type="secondary">
              {content ? `回答长度约 ${content.length} 个字符。` : '当前还没有可展示的回答正文。'}
            </Typography.Text>
          </div>
        ),
        statusLabel: answerStatusLabel(status),
        statusTone: answerStatusTone(status),
      });
    }
  }
  if (!items.length) {
    return null;
  }

  return (
    <div className="message-process-timeline">
      <div className="message-process-title">
        <ClockCircleOutlined />
        <span>本次回复过程</span>
      </div>
      <div className="message-process-list">
        {items.map((item) => (
          <div key={item.key} className={`message-process-item message-process-item-${item.kind}`}>
            <div className={`message-process-marker message-process-marker-${item.statusTone}`}>{statusIcon(item.statusTone)}</div>
            <details className="message-process-card">
              <summary className="message-process-summary">
                <div className="message-process-summary-main">
                  <span className="message-process-step-title">{item.title}</span>
                  <span className="message-process-step-summary">{item.summary}</span>
                </div>
                <Tag className="message-process-status" color={statusTagColor(item.statusTone)}>
                  {item.statusLabel}
                </Tag>
              </summary>
              <div className="message-process-body">{item.detail}</div>
            </details>
          </div>
        ))}
      </div>
    </div>
  );
}
