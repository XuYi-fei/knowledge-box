import { LeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { MarkdownRenderer } from '../admin/components/MarkdownRenderer';

function parseTagNames(tags: string) {
  return tags
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

export function UserDocumentDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { documentId } = useParams<{ documentId: string }>();
  const params = new URLSearchParams(location.search);
  const headingPath = params.get('headingPath');
  const anchor = params.get('anchor');
  const snippet = params.get('snippet');
  const numericDocumentId = Number(documentId);

  const documentQuery = useQuery({
    queryKey: ['userDocumentDetail', numericDocumentId],
    queryFn: () => api.userDocumentDetail(numericDocumentId),
    enabled: Number.isFinite(numericDocumentId) && numericDocumentId > 0,
  });

  if (!Number.isFinite(numericDocumentId) || numericDocumentId <= 0) {
    return (
      <div className="document-detail-page">
        <Card className="document-detail-card">
          <Empty description="文档编号无效" />
        </Card>
      </div>
    );
  }

  const document = documentQuery.data;
  const tagNames = document ? parseTagNames(document.tags) : [];

  return (
    <div className="document-detail-page">
      <Card className="document-detail-card" loading={documentQuery.isLoading}>
        <div className="document-detail-header">
          <div>
            <Typography.Text type="secondary">关联文档详情</Typography.Text>
            <Typography.Title level={3}>{document?.title ?? '文档详情'}</Typography.Title>
          </div>
          <Space wrap>
            <Button icon={<LeftOutlined />} onClick={() => navigate('/', { replace: false })}>
              返回对话
            </Button>
            <Button onClick={() => window.close()}>关闭窗口</Button>
          </Space>
        </div>

        {documentQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="文档加载失败"
            description={buildErrorSummary(documentQuery.error, '请确认该文档仍为公开且已发布状态')}
          />
        ) : null}

        {document ? (
          <div className="document-detail-body">
            {(headingPath || anchor || snippet) && (
              <div className="document-detail-context">
                <Typography.Text strong>本次命中片段</Typography.Text>
                <Typography.Paragraph type="secondary">
                  {[headingPath, anchor ? `#${anchor}` : null].filter(Boolean).join(' · ') || '未提供段落定位'}
                </Typography.Paragraph>
                {snippet ? <Typography.Paragraph>{snippet}</Typography.Paragraph> : null}
              </div>
            )}

            <div className="document-detail-meta">
              <div className="document-detail-meta-item">
                <Typography.Text type="secondary">分类</Typography.Text>
                <Typography.Text>{document.categoryName || '-'}</Typography.Text>
              </div>
              <div className="document-detail-meta-item">
                <Typography.Text type="secondary">来源文件</Typography.Text>
                <Typography.Text>{document.sourceFilename}</Typography.Text>
              </div>
              <div className="document-detail-meta-item">
                <Typography.Text type="secondary">更新时间</Typography.Text>
                <Typography.Text>{new Date(document.updatedAt).toLocaleString('zh-CN')}</Typography.Text>
              </div>
              <div className="document-detail-meta-item">
                <Typography.Text type="secondary">标签</Typography.Text>
                <Space wrap>
                  {tagNames.length ? tagNames.map((tag) => <Tag key={tag}>{tag}</Tag>) : <Typography.Text>-</Typography.Text>}
                </Space>
              </div>
            </div>

            <div className="document-detail-markdown">
              <MarkdownRenderer content={document.sourceMarkdown} />
            </div>
          </div>
        ) : documentQuery.isLoading ? (
          <div className="chat-empty-state">
            <Spin />
          </div>
        ) : (
          <Empty description="文档不存在或暂不可查看" />
        )}
      </Card>
    </div>
  );
}
