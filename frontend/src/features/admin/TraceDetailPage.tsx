import { DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Collapse, Descriptions, Empty, List, Popconfirm, Space, Spin, Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { AgentExecutionEvent, AgentExecutionSpan } from '../../lib/types';

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
      renderItem={(event) => (
        <List.Item className="trace-detail-event-item">
          <Space direction="vertical" size={8} style={{ width: '100%' }}>
            <div className="trace-detail-event-meta">
              <Space wrap>
                <Tag color="blue">#{event.sequenceNo}</Tag>
                <Tag>{event.eventType ?? 'UNKNOWN'}</Tag>
                <Typography.Text type="secondary">{formatDateTime(event.occurredAt)}</Typography.Text>
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
  status,
  stepNo,
  durationMs,
  startedAt,
  eventCount,
  extraTag,
}: {
  title: string;
  subtitle?: string;
  status?: string | null;
  stepNo: number | string;
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
      </div>
      <div className="trace-step-header-meta">
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
        <Descriptions.Item label="顺序">#{span.sequenceNo}</Descriptions.Item>
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
          {orderedSpans.length || orphanEvents.length ? (
            <div className="trace-step-list">
              {orderedSpans.map((span) => {
                const events = (eventsBySpan.get(span.spanId) ?? []).sort((left, right) => left.sequenceNo - right.sequenceNo);
                return (
                  <StepCard
                    key={span.spanId}
                    panelKey={span.spanId}
                    header={
                      <StepHeader
                        title={span.spanName ?? span.spanId}
                        subtitle={span.parentSpanId ? `parent: ${span.parentSpanId}` : undefined}
                        status={span.status}
                        stepNo={span.sequenceNo}
                        durationMs={span.durationMs}
                        startedAt={span.startedAt}
                        eventCount={events.length}
                        extraTag={span.spanType}
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
                      status="UNKNOWN"
                      stepNo="?"
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
