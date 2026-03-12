import { useQuery } from '@tanstack/react-query';
import { Card, Col, Result, Row, Statistic, Typography } from 'antd';
import { api } from '../../lib/api';

export function DashboardPage() {
  const { data, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.dashboard,
  });

  if (isError) {
    return <Result status="warning" title="无法读取后台数据" subTitle="请确认后端已启动且管理端凭据正确。" />;
  }

  return (
    <div className="page-stack">
      <Typography.Title level={3}>系统概览</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card><Statistic title="Profile 版本" value={data?.profileCount ?? 0} /></Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card><Statistic title="知识文档" value={data?.documentCount ?? 0} /></Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card><Statistic title="启用 Hook" value={data?.activeHookCount ?? 0} /></Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card><Statistic title="最近 Trace" value={data?.recentTraceCount ?? 0} /></Card>
        </Col>
      </Row>
      <Card title="运行时摘要">
        当前骨架默认启用单知识库模式、公开问答入口、管理端轻量登录，以及 ReAct Supervisor + RetrievalAgent + CapabilityAgent 的运行时分层。
      </Card>
    </div>
  );
}

