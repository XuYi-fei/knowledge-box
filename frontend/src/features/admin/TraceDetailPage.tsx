import { useQuery } from '@tanstack/react-query';
import { Alert, Card, Descriptions, Empty, List, Space, Spin, Tag, Timeline, Typography } from 'antd';
import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { AgentExecutionEvent, AgentExecutionSpan } from '../../lib/types';
import { buildErrorSummary } from '../../lib/errors';

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

function JsonBlock({ title, value }: { title: string; value?: string | null }) {
  if (!value || !value.trim()) {
    return null;
  }
  return (
    <div>
      <Typography.Text strong>{title}</Typography.Text>
      <pre style={{ margin: '8px 0 0', padding: 12, background: '#f6f8fa', borderRadius: 8, overflowX: 'auto' }}>{parseJsonText(value)}</pre>
    </div>
  );
}

function EventList({ events }: { events: AgentExecutionEvent[] }) {
  if (!events.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该 span 暂无事件" />;
  }
  return (
    <List
      size="small"
      dataSource={events}
      renderItem={(event) => (
        <List.Item>
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <Space wrap>
              <Tag color="blue">#{event.sequenceNo}</Tag>
              <Tag>{event.eventType ?? 'UNKNOWN'}</Tag>
              <Typography.Text type="secondary">{formatDateTime(event.occurredAt)}</Typography.Text>
            </Space>
            <pre style={{ margin: 0, padding: 12, background: '#f6f8fa', borderRadius: 8, overflowX: 'auto' }}>{parseJsonText(event.payloadJson)}</pre>
          </Space>
        </List.Item>
      )}
    />
  );
}

function SpanCard({ span, events }: { span: AgentExecutionSpan; events: AgentExecutionEvent[] }) {
  return (
    <Card
      size="small"
      title={
        <Space wrap>
          <span>{span.spanName ?? span.spanId}</span>
          <Tag>{span.spanType}</Tag>
          <Tag color={span.status === 'COMPLETED' ? 'green' : span.status === 'FAILED' ? 'red' : span.status === 'CANCELLED' ? 'orange' : 'processing'}>
            {span.status}
          </Tag>
        </Space>
      }
    >
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
    </Card>
  );
}

export function TraceDetailPage() {
  const params = useParams<{ traceId: string }>();
  const traceId = params.traceId ?? '';
  const traceQuery = useQuery({
    queryKey: ['traceDetail', traceId],
    queryFn: () => api.traceDetail(traceId),
    enabled: Boolean(traceId),
  });

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

  if (traceQuery.isLoading) {
    return <div style={{ minHeight: 240, display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>;
  }

  if (traceQuery.isError || !traceQuery.data) {
    return <Alert type="error" showIcon message="加载 trace 详情失败" description={buildErrorSummary(traceQuery.error, '请稍后重试')} />;
  }

  const { trace, spans } = traceQuery.data;

  return (
    <div className="page-stack">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Space>
          <Link to="/admin/traces">返回运行追踪</Link>
        </Space>
        <Typography.Title level={3} style={{ marginBottom: 0 }}>Trace 详情</Typography.Title>
        <Card title={trace.traceId}>
          <Descriptions column={2} bordered size="small">
            <Descriptions.Item label="状态">
              <Tag color={trace.status === 'COMPLETED' ? 'green' : trace.status === 'FAILED' ? 'red' : trace.status === 'CANCELLED' ? 'orange' : 'processing'}>
                {trace.status}
              </Tag>
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
            <Descriptions.Item label="请求摘要" span={2}>{trace.requestQueryMasked ?? '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="执行时间线">
          {spans.length ? (
            <Timeline
              items={spans.map((span) => ({
                color: span.status === 'COMPLETED' ? 'green' : span.status === 'FAILED' ? 'red' : span.status === 'CANCELLED' ? 'orange' : 'blue',
                children: <SpanCard span={span} events={eventsBySpan.get(span.spanId) ?? []} />,
              }))}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前 trace 暂无 span 数据" />
          )}
        </Card>
      </Space>
    </div>
  );
}
