import { useQueries } from '@tanstack/react-query';
import { Card, Col, Row, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../lib/api';
import { McpServer, SkillBinding, ToolDefinition } from '../../lib/types';

const toolColumns: ColumnsType<ToolDefinition> = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: 'Endpoint', dataIndex: 'endpoint' },
  { title: '启用', render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{String(record.enabled)}</Tag> },
];

const mcpColumns: ColumnsType<McpServer> = [
  { title: '编码', dataIndex: 'code' },
  { title: '传输', dataIndex: 'transportType' },
  { title: '目标', dataIndex: 'target' },
  { title: '启用', render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{String(record.enabled)}</Tag> },
];

const skillColumns: ColumnsType<SkillBinding> = [
  { title: '编码', dataIndex: 'code' },
  { title: '名称', dataIndex: 'name' },
  { title: '启用', render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{String(record.enabled)}</Tag> },
];

export function IntegrationsPage() {
  const [toolsQuery, mcpQuery, skillsQuery] = useQueries({
    queries: [
      { queryKey: ['tools'], queryFn: api.tools },
      { queryKey: ['mcpServers'], queryFn: api.mcpServers },
      { queryKey: ['skills'], queryFn: api.skills },
    ],
  });

  return (
    <div className="page-stack">
      <Typography.Title level={3}>Tools / MCP / Skills</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card title="Tool 绑定">
            <Table rowKey="id" columns={toolColumns} dataSource={toolsQuery.data ?? []} pagination={false} />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="MCP Server">
            <Table rowKey="id" columns={mcpColumns} dataSource={mcpQuery.data ?? []} pagination={false} />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card title="Skill Binding">
            <Table rowKey="id" columns={skillColumns} dataSource={skillsQuery.data ?? []} pagination={false} />
          </Card>
        </Col>
      </Row>
    </div>
  );
}

