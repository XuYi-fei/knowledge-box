import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Form, Input, InputNumber, List, Pagination, Select, Space, Tag, Typography } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { AgentExecutionTraceStatus, AgentExecutionTraceSummary } from '../../lib/types';

type FilterValues = {
  traceId?: string;
  status?: AgentExecutionTraceStatus | 'ALL';
  sessionCode?: string;
  userId?: number;
  queryKeyword?: string;
};

const STATUS_OPTIONS: Array<{ label: string; value: AgentExecutionTraceStatus | 'ALL' }> = [
  { label: '全部状态', value: 'ALL' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
];

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
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return '-';
  }
  if (value < 1000) {
    return `${value} ms`;
  }
  return `${(value / 1000).toFixed(value >= 10000 ? 1 : 2)} s`;
}

function statusColor(status: AgentExecutionTraceStatus) {
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

function metaItem(label: string, value: ReactNode) {
  return (
    <Space size={4} wrap>
      <Typography.Text type="secondary">{label}:</Typography.Text>
      <Typography.Text>{value}</Typography.Text>
    </Space>
  );
}

export function TracesPage() {
  const [form] = Form.useForm<FilterValues>();
  const [filters, setFilters] = useState<FilterValues>({ status: 'ALL' });
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const traceQuery = useQuery({
    queryKey: ['traces', filters, page, pageSize],
    queryFn: () => api.traces({ ...filters, page, pageSize }),
    placeholderData: (previous) => previous,
  });

  const activeFilters = useMemo(
    () =>
      Object.entries(filters).filter(([, value]) => value !== undefined && value !== '' && value !== 'ALL').length,
    [filters],
  );

  return (
    <div className="page-stack">
      <Typography.Title level={3}>运行追踪</Typography.Title>

      <Card title="筛选条件">
        <Form<FilterValues>
          form={form}
          layout="vertical"
          initialValues={{ status: 'ALL' }}
          onFinish={(values) => {
            setFilters({
              traceId: values.traceId?.trim() || undefined,
              status: values.status ?? 'ALL',
              sessionCode: values.sessionCode?.trim() || undefined,
              userId: values.userId ?? undefined,
              queryKeyword: values.queryKeyword?.trim() || undefined,
            });
            setPage(1);
          }}
        >
          <Space wrap align="start" size={[12, 0]}>
            <Form.Item label="Trace ID" name="traceId" style={{ minWidth: 220 }}>
              <Input allowClear placeholder="trace-..." />
            </Form.Item>
            <Form.Item label="状态" name="status" style={{ minWidth: 160 }}>
              <Select options={STATUS_OPTIONS} />
            </Form.Item>
            <Form.Item label="Session" name="sessionCode" style={{ minWidth: 220 }}>
              <Input allowClear placeholder="session-..." />
            </Form.Item>
            <Form.Item label="用户 ID" name="userId" style={{ minWidth: 140 }}>
              <InputNumber min={1} style={{ width: '100%' }} placeholder="1" />
            </Form.Item>
            <Form.Item label="请求关键词" name="queryKeyword" style={{ minWidth: 220 }}>
              <Input allowClear placeholder="搜索请求摘要" />
            </Form.Item>
          </Space>

          <Space wrap>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                form.resetFields();
                form.setFieldsValue({ status: 'ALL' });
                setFilters({ status: 'ALL' });
                setPage(1);
                setPageSize(20);
              }}
            >
              重置
            </Button>
            <Typography.Text type="secondary">当前筛选 {activeFilters} 项</Typography.Text>
          </Space>
        </Form>
      </Card>

      <Card
        title="Trace 列表"
        extra={<Typography.Text type="secondary">共 {traceQuery.data?.total ?? 0} 条</Typography.Text>}
      >
        {traceQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="加载 trace 列表失败"
            description={buildErrorSummary(traceQuery.error, '请稍后重试')}
            action={
              <Button size="small" onClick={() => traceQuery.refetch()}>
                重试
              </Button>
            }
          />
        ) : null}

        <List
          loading={traceQuery.isLoading || traceQuery.isFetching}
          dataSource={traceQuery.data?.items ?? []}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Trace 数据" /> }}
          renderItem={(trace: AgentExecutionTraceSummary) => (
            <List.Item key={trace.traceId}>
              <Card size="small" style={{ width: '100%' }}>
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Space align="start" wrap style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Space direction="vertical" size={4}>
                      <Link to={`/admin/traces/${encodeURIComponent(trace.traceId)}`}>{trace.traceId}</Link>
                      <Typography.Text type="secondary">
                        {trace.requestQueryMasked?.trim() || '未返回问题摘要'}
                      </Typography.Text>
                    </Space>
                    <Space wrap>
                      <Tag color={statusColor(trace.status)}>{trace.status}</Tag>
                      <Tag>{trace.chatModelCode ?? '未知模型'}</Tag>
                    </Space>
                  </Space>

                  <Space wrap size={[16, 8]}>
                    {metaItem('用户', trace.userId ?? '-')}
                    {metaItem('Session', trace.sessionCode ?? '-')}
                    {metaItem('Profile', trace.profileCode ?? '-')}
                    {metaItem('开始时间', formatDateTime(trace.startedAt))}
                    {metaItem('耗时', formatDuration(trace.durationMs))}
                    {metaItem('重试', trace.attemptCount)}
                    {metaItem('Client Message', trace.clientMessageId ?? '-')}
                    {metaItem('Assistant Message', trace.assistantMessageCode ?? '-')}
                  </Space>

                  {trace.errorCode || trace.errorMessage ? (
                    <Alert
                      type="error"
                      showIcon
                      message={trace.errorCode ?? 'TRACE_ERROR'}
                      description={trace.errorMessage ?? '未返回错误详情'}
                    />
                  ) : null}
                </Space>
              </Card>
            </List.Item>
          )}
        />

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <Pagination
            current={traceQuery.data?.page ?? page}
            pageSize={traceQuery.data?.pageSize ?? pageSize}
            total={traceQuery.data?.total ?? 0}
            showSizeChanger
            showTotal={(total) => `共 ${total} 条`}
            onChange={(nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            }}
          />
        </div>
      </Card>
    </div>
  );
}
