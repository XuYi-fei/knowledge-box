import { useQuery } from '@tanstack/react-query';
import { Card, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../lib/api';
import { AgentTrace } from '../../lib/types';

const columns: ColumnsType<AgentTrace> = [
  { title: 'Trace', dataIndex: 'traceCode' },
  { title: 'Session', dataIndex: 'sessionCode' },
  { title: '阶段', dataIndex: 'stage' },
  { title: 'Payload', dataIndex: 'payloadJson' },
];

export function TracesPage() {
  const { data = [] } = useQuery({
    queryKey: ['traces'],
    queryFn: api.traces,
  });

  return (
    <div className="page-stack">
      <Typography.Title level={3}>运行追踪</Typography.Title>
      <Card title="Agent Trace">
        <Table rowKey="id" columns={columns} dataSource={data} pagination={false} scroll={{ x: 960 }} />
      </Card>
    </div>
  );
}

