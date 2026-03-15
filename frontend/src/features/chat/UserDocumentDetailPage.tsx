import { LeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { extractMarkdownOutline, MarkdownRenderer } from '../admin/components/MarkdownRenderer';

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
  const [activeHeadingId, setActiveHeadingId] = useState<string | null>(null);

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

  const detailDocument = documentQuery.data;
  const tagNames = detailDocument ? parseTagNames(detailDocument.tags) : [];
  const headingIdPrefix = `doc-outline-${numericDocumentId}`;
  const outline = useMemo(
    () => (detailDocument ? extractMarkdownOutline(detailDocument.sourceMarkdown, headingIdPrefix) : []),
    [detailDocument, headingIdPrefix],
  );

  useEffect(() => {
    if (!outline.length) {
      setActiveHeadingId(null);
      return;
    }

    let frameId = 0;
    const resolveActiveHeading = () => {
      const headingElements = outline
        .map((item) => globalThis.document.getElementById(item.id))
        .filter((item): item is HTMLElement => item instanceof HTMLElement);
      if (!headingElements.length) {
        return;
      }
      const threshold = 140;
      let currentHeading = headingElements[0].id;
      for (const headingElement of headingElements) {
        if (headingElement.getBoundingClientRect().top <= threshold) {
          currentHeading = headingElement.id;
        } else {
          break;
        }
      }
      setActiveHeadingId((current) => (current === currentHeading ? current : currentHeading));
    };

    const handleScroll = () => {
      if (frameId) {
        cancelAnimationFrame(frameId);
      }
      frameId = requestAnimationFrame(resolveActiveHeading);
    };

    resolveActiveHeading();
    window.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('resize', handleScroll);
    return () => {
      if (frameId) {
        cancelAnimationFrame(frameId);
      }
      window.removeEventListener('scroll', handleScroll);
      window.removeEventListener('resize', handleScroll);
    };
  }, [outline]);

  function scrollToHeading(headingId: string) {
    const target = globalThis.document.getElementById(headingId);
    if (!target) {
      return;
    }
    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setActiveHeadingId(headingId);
  }

  return (
    <div className="document-detail-page">
      <div className="document-detail-layout">
        <Card className="document-detail-card" loading={documentQuery.isLoading}>
          <div className="document-detail-header">
            <div>
              <Typography.Text type="secondary">关联文档详情</Typography.Text>
              <Typography.Title level={3}>{detailDocument?.title ?? '文档详情'}</Typography.Title>
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

          {detailDocument ? (
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
                  <Typography.Text>{detailDocument.categoryName || '-'}</Typography.Text>
                </div>
                <div className="document-detail-meta-item">
                  <Typography.Text type="secondary">来源文件</Typography.Text>
                  <Typography.Text>{detailDocument.sourceFilename}</Typography.Text>
                </div>
                <div className="document-detail-meta-item">
                  <Typography.Text type="secondary">更新时间</Typography.Text>
                  <Typography.Text>{new Date(detailDocument.updatedAt).toLocaleString('zh-CN')}</Typography.Text>
                </div>
                <div className="document-detail-meta-item">
                  <Typography.Text type="secondary">标签</Typography.Text>
                  <Space wrap>
                    {tagNames.length ? tagNames.map((tag) => <Tag key={tag}>{tag}</Tag>) : <Typography.Text>-</Typography.Text>}
                  </Space>
                </div>
              </div>

              <div className="document-detail-markdown">
                <MarkdownRenderer content={detailDocument.sourceMarkdown} headingIdPrefix={headingIdPrefix} />
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

        {outline.length ? (
          <aside className="document-outline">
            <div className="document-outline-card">
              <Typography.Text type="secondary">文档大纲</Typography.Text>
              <div className="document-outline-list">
                {outline.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`document-outline-item ${activeHeadingId === item.id ? 'document-outline-item-active' : ''}`}
                    style={{ ['--outline-level' as const]: item.level } as CSSProperties}
                    onClick={() => scrollToHeading(item.id)}
                  >
                    {item.text}
                  </button>
                ))}
              </div>
            </div>
          </aside>
        ) : null}
      </div>
    </div>
  );
}
