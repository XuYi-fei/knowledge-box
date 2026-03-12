import { useQuery } from '@tanstack/react-query';
import { Card, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../lib/api';
import { WebhookSubscription } from '../../lib/types';

const columns: ColumnsType<WebhookSubscription> = [
  { title: '事件', dataIndex: 'eventType' },
  { title: '目标地址', dataIndex: 'targetUrl' },
  { title: '签名', dataIndex: 'secretMasked' },
  { title: '启用', render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{String(record.enabled)}</Tag> },
];

export function HooksPage() {
  const { data = [] } = useQuery({
    queryKey: ['hooks'],
    queryFn: api.hooks,
  });

  return (
    <div className="page-stack">
      <Typography.Title level={3}>Hooks</Typography.Title>
      <Card title="Webhook 订阅">
        <Table rowKey="id" columns={columns} dataSource={data} pagination={false} />
      </Card>
    </div>
  );
}

