import { CaretRightOutlined, CheckCircleOutlined, CloseCircleOutlined, FileOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Divider, List, Progress, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import type {
  KnowledgeIngestionTask,
  KnowledgeIngestionTaskDocumentDetail,
  KnowledgeIngestionTaskDocumentSummary,
  KnowledgeIngestionTaskStage,
} from '../../lib/types';
import { MarkdownWorkbench } from '../admin/components/MarkdownWorkbench';

const stageColor = (status: KnowledgeIngestionTask['status']) => {
  switch (status) {
    case 'CANCELLED':
    case 'FAILED':
      return 'red';
    case 'COMPLETED':
      return 'green';
    default:
      return 'blue';
  }
};

export function KnowledgeIngestionTaskPage() {
  const navigate = useNavigate();
  const { taskId: rawTaskId } = useParams<{ taskId?: string }>();
  const taskId = rawTaskId ? Number(rawTaskId) : null;
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null);
  const { data: detail, isLoading, refetch } = useQuery({
    queryKey: ['knowledgeIngestionTask', taskId],
    queryFn: () => api.knowledgeIngestionTaskDetail(taskId!),
    enabled: taskId != null,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ['QUEUED', 'RUNNING', 'PARTIAL_FAILED', 'CANCELLING'].includes(status) ? 1000 : false;
    },
  });

  useEffect(() => {
    if (!detail) {
      return;
    }
    if (!selectedDocumentId || !detail.documents.some((doc) => doc.id === selectedDocumentId)) {
      setSelectedDocumentId(detail.documents[0]?.id ?? null);
    }
  }, [detail, selectedDocumentId]);

  const stageView = useMemo(() => detail?.stages ?? [], [detail?.stages]);
  const documentDetailQuery = useQuery<KnowledgeIngestionTaskDocumentDetail>({
    queryKey: ['knowledgeIngestionTaskDocument', detail?.id, selectedDocumentId],
    queryFn: () => api.knowledgeIngestionTaskDocument(detail!.id, selectedDocumentId!),
    enabled: detail != null && selectedDocumentId != null,
  });
  const selectedDocumentSummary = detail?.documents.find((doc) => doc.id === selectedDocumentId) ?? null;
  const selectedDocumentDetail = documentDetailQuery.data ?? null;
  const selectedDocument = selectedDocumentDetail ?? selectedDocumentSummary;

  const handleCancel = async () => {
    if (!detail) {
      return;
    }
    await api.cancelKnowledgeIngestionTask(detail.id);
    refetch();
  };

  if (!taskId) {
    return (
      <Card className="chat-panel chat-card" title="无效任务">
        <Typography.Text>无法解析任务 ID，请返回上一页重新访问。</Typography.Text>
      </Card>
    );
  }

  if (isLoading || !detail) {
    return (
      <Card className="chat-panel chat-card" title="任务详情">
        <Skeleton active />
      </Card>
    );
  }

  return (
    <div className="chat-shell" style={{ overflowY: 'auto', overflowX: 'hidden' }}>
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        className="chat-panel chat-card"
        title={`任务 · ${detail.taskCode}`}
        extra={(
          <Space>
            <Button type="link" onClick={() => void navigate('/ingest/tasks')}>
              返回任务中心
            </Button>
            <Tag color={stageColor(detail.status)}>{detail.status}</Tag>
          </Space>
        )}
      >
        <Row gutter={16}>
          <Col span={16}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text>原始文件：{detail.sourceFilename}</Typography.Text>
              <Typography.Text type="secondary">页数：{detail.pageCount ?? '未知'}</Typography.Text>
              <Progress percent={detail.progressPercent} status={detail.status === 'FAILED' ? 'exception' : 'active'} />
              {detail.summaryText ? <Typography.Text type="secondary">{detail.summaryText}</Typography.Text> : null}
              {detail.failureReason ? <Alert type="error" message="任务报错" description={detail.failureReason} showIcon /> : null}
            </Space>
          </Col>
          <Col span={8} style={{ textAlign: 'right' }}>
            <Space direction="vertical">
              <Button icon={<FileOutlined />} onClick={() => window.open(detail.sourceFileUrl ?? '', '_blank')} disabled={!detail.sourceFileUrl}>
                查看原始 PDF
              </Button>
              <Button icon={<StopOutlined />} danger onClick={handleCancel} disabled={detail.cancelRequested || detail.status === 'COMPLETED'}>
                {detail.cancelRequested ? '取消中…' : '取消任务'}
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={16}>
        <Col span={8}>
          <Card title="阶段">
            <List<KnowledgeIngestionTaskStage>
              dataSource={stageView}
              renderItem={(stage) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space>
                        <CaretRightOutlined />
                        <strong>{stage.name}</strong>
                      </Space>
                    }
                    description={
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <Typography.Text type="secondary">{stage.status}</Typography.Text>
                        <Progress percent={stage.progressPercent} size="small" />
                        {stage.message ? <Typography.Text type="secondary">{stage.message}</Typography.Text> : null}
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>

          <Card title="子产物">
            <List<KnowledgeIngestionTaskDocumentSummary>
              dataSource={detail.documents}
              renderItem={(doc) => (
                <List.Item style={{ cursor: 'pointer' }} onClick={() => setSelectedDocumentId(doc.id)}>
                  <List.Item.Meta
                    title={
                      <Space>
                        <CheckCircleOutlined />
                        {doc.title}
                      </Space>
                    }
                    description={
                      <Space>
                        <Tag color="blue">{doc.status}</Tag>
                        <Typography.Text type="secondary">{doc.pageRange || '页码未知'}</Typography.Text>
                      </Space>
                    }
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col span={16}>
          {!selectedDocument ? (
            <Card title="暂无子产物">
              <Typography.Text type="secondary">任务正在生成中，稍后会出现可预览的文档。</Typography.Text>
            </Card>
          ) : (
            <Card title={`文档 · ${selectedDocument.title}`}>
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Text>分类：{selectedDocument.categoryName || '-'}</Typography.Text>
                <Typography.Text>阶段：{selectedDocument.stage}</Typography.Text>
                <Typography.Text>Status: {selectedDocument.status}</Typography.Text>
                {selectedDocumentDetail?.confirmedReviewRequestCode ? (
                  <Space wrap>
                    <Typography.Text>审核单：{selectedDocumentDetail.confirmedReviewRequestCode}</Typography.Text>
                    <Button type="link" onClick={() => void navigate('/admin/document-reviews')}>
                      前往审核页
                    </Button>
                  </Space>
                ) : null}
                {selectedDocumentDetail?.errorMessage ? (
                  <Alert type="error" showIcon message="子文档生成失败" description={selectedDocumentDetail.errorMessage} />
                ) : null}
                <MarkdownWorkbench value={selectedDocumentDetail?.generatedMarkdown ?? ''} readOnly rows={18} />
              </Space>
            </Card>
          )}
        </Col>
      </Row>
      </Space>
    </div>
  );
}
