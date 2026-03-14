import { DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Collapse, Descriptions, Empty, List, Popconfirm, Space, Spin, Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { AgentExecutionEvent, AgentExecutionSpan } from '../../lib/types';

const SPAN_TYPE_LABELS: Record<string, string> = {
  REQUEST: '接收请求',
  ROUTING: '查询路由',
  STREAM: '回答生成',
  TOOL: '工具调用',
  FINALIZE: '结果收尾',
};

const EVENT_TYPE_LABELS: Record<string, string> = {
  'agent.call.start': 'Agent 开始执行',
  'agent.call.end': 'Agent 执行结束',
  'prompt.injected': '注入 Prompt',
  'tool.start': '工具开始',
  'tool.chunk': '工具流式片段',
  'tool.end': '工具结束',
  'agent.error': 'Agent 异常',
  'query.routed': '路由完成',
};

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatDuration(value?: number | null) {
  if (value == null) {
    return '-';
  }
  return `${value} ms`;
}

function parseJsonText(value?: string | null) {
  if (!value || !value.trim()) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseJsonValue(value?: string | null): unknown {
  if (!value || !value.trim()) {
    return null;
  }
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return value;
  }
}

function statusColor(status?: string | null) {
  if (status === 'COMPLETED') {
    return 'green';
  }
  if (status === 'FAILED') {
    return 'red';
  }
  if (status === 'CANCELLED') {
    return 'orange';
  }
  return 'processing';
}

function normalizeDisplayValue(value: unknown): string | null {
  if (value == null) {
    return null;
  }
  if (typeof value === 'string') {
    const compact = value.replace(/\s+/g, ' ').trim();
    return compact || null;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (Array.isArray(value)) {
    if (!value.length) {
      return null;
    }
    return value
      .map((item): string | null => normalizeDisplayValue(item))
      .filter((item): item is string => Boolean(item))
      .join('、');
  }
  if (typeof value === 'object') {
    const objectValue = value as Record<string, unknown>;
    if (typeof objectValue.text === 'string') {
      return normalizeDisplayValue(objectValue.text);
    }
    if (typeof objectValue.name === 'string') {
      return normalizeDisplayValue(objectValue.name);
    }
    if (typeof objectValue.query === 'string') {
      return normalizeDisplayValue(objectValue.query);
    }
  }
  return null;
}

function trimPreview(value: string | null, limit = 72) {
  if (!value) {
    return null;
  }
  return value.length > limit ? `${value.slice(0, limit)}...` : value;
}

function pickSummaryValue(source: unknown, preferredKeys: string[]) {
  if (!source || typeof source !== 'object' || Array.isArray(source)) {
    return null;
  }
  const objectSource = source as Record<string, unknown>;
  for (const key of preferredKeys) {
    const candidate = normalizeDisplayValue(objectSource[key]);
    if (candidate) {
      return candidate;
    }
  }
  return null;
}

function summarizeSpanInput(span: AgentExecutionSpan) {
  const input = parseJsonValue(span.inputJson);
  if (span.spanType === 'REQUEST') {
    return trimPreview(pickSummaryValue(input, ['query', 'requestQueryMasked', 'clientMessageId']));
  }
  if (span.spanType === 'ROUTING') {
    return trimPreview(pickSummaryValue(input, ['query']));
  }
  if (span.spanType === 'STREAM') {
    const objectInput = input && typeof input === 'object' && !Array.isArray(input) ? (input as Record<string, unknown>) : null;
    if (!objectInput) {
      return null;
    }
    const parts = [
      normalizeDisplayValue(objectInput.chatModel),
      objectInput.enableKnowledgeBase == null ? null : `知识库:${objectInput.enableKnowledgeBase ? '开启' : '关闭'}`,
      objectInput.historyTurns == null ? null : `历史:${objectInput.historyTurns} 条`,
    ].filter((item): item is string => Boolean(item));
    return parts.length ? parts.join(' | ') : null;
  }
  if (span.spanType === 'TOOL') {
    const objectInput = input && typeof input === 'object' && !Array.isArray(input) ? (input as Record<string, unknown>) : null;
    if (!objectInput) {
      return null;
    }
    const toolName = normalizeDisplayValue(objectInput.toolName);
    const toolInput = trimPreview(normalizeDisplayValue(objectInput.toolInput), 64);
    return [toolName, toolInput].filter((item): item is string => Boolean(item)).join(' | ') || null;
  }
  return trimPreview(pickSummaryValue(input, ['query', 'toolName', 'chatModel', 'message']));
}

function summarizeSpanOutput(span: AgentExecutionSpan) {
  const output = parseJsonValue(span.outputJson);
  if (span.spanType === 'ROUTING') {
    const objectOutput = output && typeof output === 'object' && !Array.isArray(output) ? (output as Record<string, unknown>) : null;
    if (!objectOutput) {
      return null;
    }
    const parts = [
      objectOutput.enableKnowledgeBase == null ? null : `知识库:${objectOutput.enableKnowledgeBase ? '开启' : '跳过'}`,
      normalizeDisplayValue(objectOutput.source),
      trimPreview(normalizeDisplayValue(objectOutput.reason), 48),
    ].filter((item): item is string => Boolean(item));
    return parts.length ? parts.join(' | ') : null;
  }
  if (span.spanType === 'STREAM') {
    const objectOutput = output && typeof output === 'object' && !Array.isArray(output) ? (output as Record<string, unknown>) : null;
    if (!objectOutput) {
      return null;
    }
    const parts = [
      objectOutput.answerLength == null ? null : `回答长度:${objectOutput.answerLength}`,
      objectOutput.reasoningStepCount == null ? null : `摘要步骤:${objectOutput.reasoningStepCount}`,
      Array.isArray(objectOutput.toolCalls) ? `工具:${objectOutput.toolCalls.length} 个` : null,
    ].filter((item): item is string => Boolean(item));
    return parts.length ? parts.join(' | ') : null;
  }
  if (span.spanType === 'TOOL') {
    const objectOutput = output && typeof output === 'object' && !Array.isArray(output) ? (output as Record<string, unknown>) : null;
    const toolResult = objectOutput?.toolResult;
    return trimPreview(normalizeDisplayValue(toolResult), 72);
  }
  return trimPreview(pickSummaryValue(output, ['message', 'result', 'summary', 'answerLength']));
}

function summarizeEvent(event: AgentExecutionEvent) {
  const payload = parseJsonValue(event.payloadJson);
  const objectPayload = payload && typeof payload === 'object' && !Array.isArray(payload) ? (payload as Record<string, unknown>) : null;
  if (!objectPayload) {
    return null;
  }
  if (event.eventType === 'prompt.injected') {
    const phase = normalizeDisplayValue(objectPayload.phase);
    const modelName = normalizeDisplayValue(objectPayload.modelName);
    const inputs = Array.isArray(objectPayload.inputMessages) ? `消息:${objectPayload.inputMessages.length} 条` : null;
    return [phase, modelName, inputs].filter((item): item is string => Boolean(item)).join(' | ') || null;
  }
  if (event.eventType === 'tool.start' || event.eventType === 'tool.end' || event.eventType === 'tool.chunk') {
    return trimPreview(
      pickSummaryValue(objectPayload, ['toolName', 'toolCallId', 'chunk', 'toolResult', 'toolInput']),
      72,
    );
  }
  if (event.eventType === 'query.routed') {
    const parts = [
      objectPayload.enableKnowledgeBase == null ? null : `知识库:${objectPayload.enableKnowledgeBase ? '开启' : '跳过'}`,
      normalizeDisplayValue(objectPayload.source),
      trimPreview(normalizeDisplayValue(objectPayload.reason), 40),
    ].filter((item): item is string => Boolean(item));
    return parts.length ? parts.join(' | ') : null;
  }
  return trimPreview(
    pickSummaryValue(objectPayload, ['message', 'exceptionClass', 'toolName', 'source', 'phase', 'query']),
    72,
  );
}

function describeSpan(span: AgentExecutionSpan) {
  const typeLabel = SPAN_TYPE_LABELS[span.spanType] ?? span.spanType ?? '未知阶段';
  if (span.spanName?.startsWith('tool.call')) {
    return `${typeLabel} · ${span.spanName}`;
  }
  return span.spanName ? `${typeLabel} · ${span.spanName}` : typeLabel;
}

function JsonBlock({ title, value }: { title: string; value?: string | null }) {
  if (!value || !value.trim()) {
    return null;
  }
  return (
    <div className="trace-detail-json-block">
      <Typography.Text strong>{title}</Typography.Text>
      <pre className="trace-detail-json-pre">{parseJsonText(value)}</pre>
    </div>
  );
}

function EventList({ events }: { events: AgentExecutionEvent[] }) {
  if (!events.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该步骤暂无事件" />;
  }
  return (
    <List
      size="small"
      className="trace-detail-event-list"
      dataSource={events}
      renderItem={(event, index) => (
        <List.Item className="trace-detail-event-item">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <div className="trace-detail-event-meta">
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                <Space wrap>
                  <Tag color="blue">事件 {index + 1}</Tag>
                  <Tag>全局序号 #{event.sequenceNo}</Tag>
                  <Tag>{EVENT_TYPE_LABELS[event.eventType ?? ''] ?? event.eventType ?? 'UNKNOWN'}</Tag>
                  <Typography.Text type="secondary">{formatDateTime(event.occurredAt)}</Typography.Text>
                </Space>
                {summarizeEvent(event) ? (
                  <Typography.Text type="secondary">{summarizeEvent(event)}</Typography.Text>
                ) : null}
              </Space>
            </div>
            <pre className="trace-detail-json-pre">{parseJsonText(event.payloadJson)}</pre>
          </Space>
        </List.Item>
      )}
    />
  );
}

function StepHeader({
  title,
  subtitle,
  inputSummary,
  outputSummary,
  status,
  stepNo,
  globalSequenceNo,
  durationMs,
  startedAt,
  eventCount,
  extraTag,
}: {
  title: string;
  subtitle?: string;
  inputSummary?: string | null;
  outputSummary?: string | null;
  status?: string | null;
  stepNo: number | string;
  globalSequenceNo?: number | null;
  durationMs?: number | null;
  startedAt?: string | null;
  eventCount: number;
  extraTag?: string;
}) {
  return (
    <div className="trace-step-header">
      <div className="trace-step-header-main">
        <Space wrap>
          <Tag color="blue">步骤 {stepNo}</Tag>
          {extraTag ? <Tag>{extraTag}</Tag> : null}
          <Tag color={statusColor(status)}>{status ?? 'UNKNOWN'}</Tag>
          <Typography.Text strong>{title}</Typography.Text>
        </Space>
        {subtitle ? (
          <Typography.Text type="secondary" className="trace-step-header-subtitle">
            {subtitle}
          </Typography.Text>
        ) : null}
        {inputSummary ? (
          <Typography.Text type="secondary" className="trace-step-header-subtitle">
            输入：{inputSummary}
          </Typography.Text>
        ) : null}
        {outputSummary ? (
          <Typography.Text type="secondary" className="trace-step-header-subtitle">
            输出：{outputSummary}
          </Typography.Text>
        ) : null}
      </div>
      <div className="trace-step-header-meta">
        {globalSequenceNo != null ? <span>全局序号 #{globalSequenceNo}</span> : null}
        <span>{formatDuration(durationMs)}</span>
        <span>{formatDateTime(startedAt)}</span>
        <span>{eventCount} events</span>
      </div>
    </div>
  );
}

function SpanPanelBody({ span, events }: { span: AgentExecutionSpan; events: AgentExecutionEvent[] }) {
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="spanId">{span.spanId}</Descriptions.Item>
        <Descriptions.Item label="parentSpanId">{span.parentSpanId || '-'}</Descriptions.Item>
        <Descriptions.Item label="全局序号">#{span.sequenceNo}</Descriptions.Item>
        <Descriptions.Item label="阶段类型">{SPAN_TYPE_LABELS[span.spanType] ?? span.spanType ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="attempt">{span.attemptNo}</Descriptions.Item>
        <Descriptions.Item label="开始时间">{formatDateTime(span.startedAt)}</Descriptions.Item>
        <Descriptions.Item label="结束时间">{formatDateTime(span.endedAt)}</Descriptions.Item>
        <Descriptions.Item label="耗时">{formatDuration(span.durationMs)}</Descriptions.Item>
        <Descriptions.Item label="事件数">{events.length}</Descriptions.Item>
      </Descriptions>
      <JsonBlock title="Input" value={span.inputJson} />
      <JsonBlock title="Output" value={span.outputJson} />
      <JsonBlock title="Tags" value={span.tagsJson} />
      <JsonBlock title="Error" value={span.errorJson} />
      <div>
        <Typography.Text strong>Events</Typography.Text>
        <EventList events={events} />
      </div>
    </Space>
  );
}

function StepCard({
  panelKey,
  header,
  children,
}: {
  panelKey: string;
  header: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Card className="trace-step-card" bodyStyle={{ padding: 0 }}>
      <Collapse
        ghost
        defaultActiveKey={[]}
        items={[
          {
            key: panelKey,
            label: header,
            children: <div className="trace-step-body">{children}</div>,
          },
        ]}
      />
    </Card>
  );
}

export function TraceDetailPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const params = useParams<{ traceId: string }>();
  const traceId = params.traceId ?? '';
  const traceQuery = useQuery({
    queryKey: ['traceDetail', traceId],
    queryFn: () => api.traceDetail(traceId),
    enabled: Boolean(traceId),
  });

  const deleteTraceMutation = useMutation({
    mutationFn: (currentTraceId: string) => api.deleteTrace(currentTraceId),
    onSuccess: (_result, deletedTraceId) => {
      message.success(`已删除 Trace ${deletedTraceId}`);
      void queryClient.invalidateQueries({ queryKey: ['traces'] });
      void queryClient.removeQueries({ queryKey: ['traceDetail', deletedTraceId] });
      navigate('/admin/traces', { replace: true });
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '删除 Trace 失败，请稍后重试'));
    },
  });

  const spanIds = useMemo(() => new Set((traceQuery.data?.spans ?? []).map((span) => span.spanId)), [traceQuery.data?.spans]);

  const eventsBySpan = useMemo(() => {
    const next = new Map<string, AgentExecutionEvent[]>();
    for (const event of traceQuery.data?.events ?? []) {
      const spanId = event.spanId ?? 'unknown-span';
      const current = next.get(spanId) ?? [];
      current.push(event);
      next.set(spanId, current);
    }
    return next;
  }, [traceQuery.data?.events]);

  const orphanEvents = useMemo(
    () =>
      (traceQuery.data?.events ?? [])
        .filter((event) => !event.spanId || !spanIds.has(event.spanId))
        .sort((left, right) => left.sequenceNo - right.sequenceNo),
    [spanIds, traceQuery.data?.events],
  );

  if (traceQuery.isLoading) {
    return (
      <div style={{ minHeight: 240, display: 'grid', placeItems: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (traceQuery.isError || !traceQuery.data) {
    return <Alert type="error" showIcon message="加载 trace 详情失败" description={buildErrorSummary(traceQuery.error, '请稍后重试')} />;
  }

  const { trace, spans } = traceQuery.data;
  const orderedSpans = [...spans].sort((left, right) => left.sequenceNo - right.sequenceNo);

  return (
    <div className="page-stack">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space style={{ justifyContent: 'space-between', width: '100%' }} wrap>
          <Link to="/admin/traces">返回运行追踪</Link>
          <Popconfirm
            title="删除这条 Trace？"
            description="删除后对应的 span 和 event 会一并清除，无法恢复。"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true, loading: deleteTraceMutation.isPending }}
            onConfirm={() => deleteTraceMutation.mutate(traceId)}
          >
            <Button
              danger
              icon={<DeleteOutlined />}
              loading={deleteTraceMutation.isPending}
            >
              删除 Trace
            </Button>
          </Popconfirm>
        </Space>
        <Typography.Title level={3} style={{ marginBottom: 0 }}>
          Trace 详情
        </Typography.Title>
        <Card title={trace.traceId}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="状态">
              <Tag color={statusColor(trace.status)}>{trace.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{formatDuration(trace.durationMs)}</Descriptions.Item>
            <Descriptions.Item label="用户 ID">{trace.userId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="attempt">{trace.attemptCount}</Descriptions.Item>
            <Descriptions.Item label="Session">{trace.sessionCode ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Assistant Message">{trace.assistantMessageCode ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="Client Message">{trace.clientMessageId || '-'}</Descriptions.Item>
            <Descriptions.Item label="Profile / Model">{`${trace.profileCode ?? '-'} / ${trace.chatModelCode ?? '-'}`}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{formatDateTime(trace.startedAt)}</Descriptions.Item>
            <Descriptions.Item label="结束时间">{formatDateTime(trace.endedAt)}</Descriptions.Item>
            <Descriptions.Item label="错误码">{trace.errorCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="错误信息">{trace.errorMessage || '-'}</Descriptions.Item>
            <Descriptions.Item label="请求摘要" span={2}>
              {trace.requestQueryMasked ?? '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="执行时间线">
          <Alert
            type="info"
            showIcon
            message="编号说明"
            description="步骤号和事件号是当前页面按时间线重新编号后的顺序编号；“全局序号”是后端在整条 trace 内统一递增的原始链路序号，所以会因为中间穿插 event 而跳号。"
            style={{ marginBottom: 16 }}
          />
          {orderedSpans.length || orphanEvents.length ? (
            <div className="trace-step-list">
              {orderedSpans.map((span, index) => {
                const events = (eventsBySpan.get(span.spanId) ?? []).sort((left, right) => left.sequenceNo - right.sequenceNo);
                return (
                  <StepCard
                    key={span.spanId}
                    panelKey={span.spanId}
                    header={
                      <StepHeader
                        title={describeSpan(span)}
                        subtitle={span.parentSpanId ? `父步骤: ${span.parentSpanId}` : undefined}
                        inputSummary={summarizeSpanInput(span)}
                        outputSummary={summarizeSpanOutput(span)}
                        status={span.status}
                        stepNo={index + 1}
                        globalSequenceNo={span.sequenceNo}
                        durationMs={span.durationMs}
                        startedAt={span.startedAt}
                        eventCount={events.length}
                        extraTag={SPAN_TYPE_LABELS[span.spanType] ?? span.spanType}
                      />
                    }
                  >
                    <SpanPanelBody span={span} events={events} />
                  </StepCard>
                );
              })}

              {orphanEvents.length ? (
                <StepCard
                  panelKey="orphan-events"
                  header={
                    <StepHeader
                      title="未绑定 Span 的事件"
                      subtitle="这些事件没有对应的 spanId，通常表示链路异常或落库不完整。"
                      inputSummary={null}
                      outputSummary={null}
                      status="UNKNOWN"
                      stepNo="?"
                      globalSequenceNo={null}
                      durationMs={null}
                      startedAt={orphanEvents[0]?.occurredAt}
                      eventCount={orphanEvents.length}
                    />
                  }
                >
                  <EventList events={orphanEvents} />
                </StepCard>
              ) : null}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前 trace 暂无 span 数据" />
          )}
        </Card>
      </Space>
    </div>
  );
}
