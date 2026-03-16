import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { api } from '../../lib/api';
import type { AppToolExecutionLog } from '../../lib/types';

const statusOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '限流', value: 'RATE_LIMITED' },
];

export function AppToolExecutionsPage() {
  const [toolCode, setToolCode] = useState('');
  const [status, setStatus] = useState('ALL');
  const [userIdInput, setUserIdInput] = useState<number | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const logsQuery = useQuery({
    queryKey: ['adminAppToolExecutions', toolCode, status, userIdInput, page, pageSize],
    queryFn: () =>
      api.appToolExecutions({
        toolCode: toolCode.trim() || undefined,
        status: status === 'ALL' ? undefined : status,
        userId: userIdInput,
        page,
        pageSize,
      }),
  });

  const columns: ColumnsType<AppToolExecutionLog> = useMemo(
    () => [
      {
        title: '执行信息',
        key: 'executionMeta',
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Typography.Text strong>{record.toolCode}</Typography.Text>
            <Typography.Text type="secondary">{record.executionId}</Typography.Text>
          </Space>
        ),
      },
      { title: '用户', dataIndex: 'userId', width: 100 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: AppToolExecutionLog['status']) => {
          const color = value === 'SUCCESS' ? 'green' : value === 'RATE_LIMITED' ? 'gold' : 'red';
          return <Tag color={color} bordered={false}>{value}</Tag>;
        },
      },
      { title: '耗时(ms)', dataIndex: 'durationMs', width: 110 },
      { title: '来源 IP', dataIndex: 'clientIpMasked', width: 140 },
      {
        title: '请求摘要',
        dataIndex: 'requestSummaryJson',
        render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 260, display: 'inline-block' }}>{value}</Typography.Text>,
      },
      {
        title: '响应摘要',
        dataIndex: 'responseSummaryJson',
        render: (value: string) => <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 260, display: 'inline-block' }}>{value}</Typography.Text>,
      },
      { title: '时间', dataIndex: 'createdAt', width: 180, render: (value: string) => new Date(value).toLocaleString('zh-CN') },
    ],
    [],
  );

  return (
    <div className="page-stack">
      <Card title="工具执行日志">
        <Space wrap style={{ marginBottom: 16 }}>
          <Input
            allowClear
            placeholder="按工具编码筛选"
            value={toolCode}
            onChange={(event) => {
              setToolCode(event.target.value);
              setPage(1);
            }}
            style={{ width: 220 }}
          />
          <Select
            value={status}
            options={statusOptions}
            onChange={(value) => {
              setStatus(value);
              setPage(1);
            }}
            style={{ width: 160 }}
          />
          <InputNumber
            min={1}
            placeholder="用户 ID"
            value={userIdInput}
            onChange={(value) => {
              setUserIdInput(value ?? undefined);
              setPage(1);
            }}
            style={{ width: 140 }}
          />
          <Button onClick={() => { setToolCode(''); setStatus('ALL'); setUserIdInput(undefined); setPage(1); }}>重置筛选</Button>
        </Space>
        {logsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="工具执行日志加载失败"
            description="请检查后端服务、管理员登录状态或筛选条件后重试。"
            action={
              <Button size="small" onClick={() => void logsQuery.refetch()}>
                重试
              </Button>
            }
          />
        ) : null}
        <Table
          rowKey="executionId"
          columns={columns}
          dataSource={logsQuery.data?.items ?? []}
          loading={logsQuery.isLoading}
          pagination={{
            current: page,
            pageSize,
            total: logsQuery.data?.total ?? 0,
            onChange: (nextPage, nextPageSize) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            },
          }}
        />
      </Card>
    </div>
  );
}
