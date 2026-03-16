import { DownOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { extractMarkdownOutline, MarkdownRenderer, type MarkdownOutlineItem } from '../admin/components/MarkdownRenderer';

function parseTagNames(tags: string) {
  return tags
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

type OutlineNode = MarkdownOutlineItem & {
  parentId: string | null;
  children: OutlineNode[];
};

function normalizeComparableHeading(value: string) {
  return value
    .replace(/\s+/g, ' ')
    .replace(/等\d+处/g, '')
    .replace(/[·>#]/g, ' ')
    .trim()
    .toLowerCase();
}

function slugifyValue(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function extractHeadingCandidates(headingPath: string | null, anchor: string | null) {
  const candidates: string[] = [];
  if (headingPath) {
    const rawParts = headingPath
      .split(/\s*\/\s*|\s*>\s*|\s*·\s*/)
      .map((item) => item.replace(/等\d+处/g, '').trim())
      .filter(Boolean);
    candidates.push(...rawParts.reverse());
  }
  if (anchor?.trim()) {
    candidates.push(anchor.trim());
  }
  return Array.from(new Set(candidates));
}

function resolveTargetHeadingId(
  outline: MarkdownOutlineItem[],
  headingPath: string | null,
  anchor: string | null,
  hashHeadingId: string | null,
) {
  if (!outline.length) {
    return null;
  }
  if (hashHeadingId) {
    const exactHashMatch = outline.find((item) => item.id === hashHeadingId);
    if (exactHashMatch) {
      return exactHashMatch.id;
    }
  }
  const candidates = extractHeadingCandidates(headingPath, anchor);
  for (const candidate of candidates) {
    const normalizedCandidate = normalizeComparableHeading(candidate);
    const matchingHeading = outline.find((item) => {
      const normalizedHeading = normalizeComparableHeading(item.text);
      return (
        normalizedHeading === normalizedCandidate ||
        normalizedHeading.includes(normalizedCandidate) ||
        normalizedCandidate.includes(normalizedHeading)
      );
    });
    if (matchingHeading) {
      return matchingHeading.id;
    }
    const slugCandidate = slugifyValue(candidate);
    if (!slugCandidate) {
      continue;
    }
    const slugMatched = outline.find((item) => item.id.endsWith(`-${slugCandidate}`) || item.id.includes(slugCandidate));
    if (slugMatched) {
      return slugMatched.id;
    }
  }
  return outline[0]?.id ?? null;
}

function buildOutlineTree(outline: MarkdownOutlineItem[]) {
  const rootNodes: OutlineNode[] = [];
  const nodeMap = new Map<string, OutlineNode>();
  const stack: OutlineNode[] = [];

  for (const item of outline) {
    while (stack.length && stack[stack.length - 1].level >= item.level) {
      stack.pop();
    }
    const parent = stack[stack.length - 1] ?? null;
    const node: OutlineNode = {
      ...item,
      parentId: parent?.id ?? null,
      children: [],
    };
    if (parent) {
      parent.children.push(node);
    } else {
      rootNodes.push(node);
    }
    stack.push(node);
    nodeMap.set(node.id, node);
  }

  return {
    rootNodes,
    nodeMap,
  };
}

function collectPathIds(nodeMap: Map<string, OutlineNode>, headingId: string | null) {
  const path = new Set<string>();
  let currentId = headingId;
  while (currentId) {
    path.add(currentId);
    currentId = nodeMap.get(currentId)?.parentId ?? null;
  }
  return path;
}

function scrollToHeadingInWindow(target: HTMLElement, behavior: ScrollBehavior) {
  const top = Math.max(target.getBoundingClientRect().top + globalThis.window.scrollY - 20, 0);
  globalThis.window.scrollTo({ top, behavior });
}

function syncWindowHash(headingId: string) {
  const url = new URL(globalThis.window.location.href);
  if (decodeURIComponent(url.hash.replace(/^#/, '')) === headingId) {
    return;
  }
  url.hash = headingId;
  globalThis.window.history.replaceState(globalThis.window.history.state, '', url);
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
  const [highlightedHeadingId, setHighlightedHeadingId] = useState<string | null>(null);
  const [expandedHeadingIds, setExpandedHeadingIds] = useState<string[]>([]);
  const initialFocusRef = useRef<string | null>(null);
  const initialHashHeadingIdRef = useRef<string | null>(
    decodeURIComponent(location.hash.replace(/^#/, '').trim()) || null,
  );

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
  const outlineTree = useMemo(() => buildOutlineTree(outline), [outline]);
  const activePathIds = useMemo(
    () => collectPathIds(outlineTree.nodeMap, activeHeadingId),
    [activeHeadingId, outlineTree.nodeMap],
  );

  useEffect(() => {
    if (!outline.length) {
      setActiveHeadingId(null);
      setExpandedHeadingIds([]);
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
    globalThis.window.addEventListener('scroll', handleScroll, { passive: true } as AddEventListenerOptions);
    globalThis.window.addEventListener('resize', handleScroll);
    return () => {
      if (frameId) {
        cancelAnimationFrame(frameId);
      }
      globalThis.window.removeEventListener('scroll', handleScroll as EventListener);
      globalThis.window.removeEventListener('resize', handleScroll);
    };
  }, [outline]);

  useEffect(() => {
    if (!outline.length) {
      return;
    }
    const targetId = resolveTargetHeadingId(outline, headingPath, anchor, initialHashHeadingIdRef.current);
    if (!targetId || initialFocusRef.current === `${numericDocumentId}:${targetId}`) {
      return;
    }
    initialFocusRef.current = `${numericDocumentId}:${targetId}`;
    const pathIds = Array.from(collectPathIds(outlineTree.nodeMap, targetId));
    setExpandedHeadingIds((current) => Array.from(new Set([...current, ...pathIds])));
    setActiveHeadingId(targetId);
    setHighlightedHeadingId(targetId);
    syncWindowHash(targetId);
    requestAnimationFrame(() => {
      const target = globalThis.document.getElementById(targetId);
      if (target) {
        scrollToHeadingInWindow(target, 'auto');
      }
    });
  }, [anchor, headingPath, numericDocumentId, outline, outlineTree.nodeMap]);

  useEffect(() => {
    if (!activeHeadingId) {
      return;
    }
    const pathIds = Array.from(collectPathIds(outlineTree.nodeMap, activeHeadingId));
    setExpandedHeadingIds((current) => Array.from(new Set([...current, ...pathIds])));
  }, [activeHeadingId, outlineTree.nodeMap]);

  useEffect(() => {
    const previous = globalThis.document.querySelector('.document-heading-highlight');
    if (previous instanceof HTMLElement) {
      previous.classList.remove('document-heading-highlight');
    }
    if (!highlightedHeadingId) {
      return;
    }
    const target = globalThis.document.getElementById(highlightedHeadingId);
    if (!(target instanceof HTMLElement)) {
      return;
    }
    target.classList.add('document-heading-highlight');
    return () => {
      target.classList.remove('document-heading-highlight');
    };
  }, [highlightedHeadingId]);

  function scrollToHeading(headingId: string, behavior: ScrollBehavior = 'smooth') {
    const target = globalThis.document.getElementById(headingId);
    if (!target) {
      return;
    }
    syncWindowHash(headingId);
    scrollToHeadingInWindow(target, behavior);
    setActiveHeadingId(headingId);
    setHighlightedHeadingId(headingId);
    const pathIds = Array.from(collectPathIds(outlineTree.nodeMap, headingId));
    setExpandedHeadingIds((current) => Array.from(new Set([...current, ...pathIds])));
  }

  function toggleHeading(node: OutlineNode) {
    setExpandedHeadingIds((current) => {
      if (current.includes(node.id)) {
        return current.filter((item) => item !== node.id);
      }
      return [...current, node.id];
    });
  }

  function renderOutlineNodes(nodes: OutlineNode[], depth = 0) {
    return nodes.map((node) => {
      const isExpanded = expandedHeadingIds.includes(node.id) || activePathIds.has(node.id);
      const hasChildren = node.children.length > 0;
      return (
        <div key={node.id} className="document-outline-node" style={{ ['--outline-depth' as const]: depth } as CSSProperties}>
          <div className={`document-outline-row ${activeHeadingId === node.id ? 'document-outline-row-active' : ''}`}>
            {hasChildren ? (
              <button
                type="button"
                className="document-outline-toggle"
                onClick={() => toggleHeading(node)}
                aria-label={isExpanded ? `收起 ${node.text}` : `展开 ${node.text}`}
              >
                {isExpanded ? <DownOutlined /> : <RightOutlined />}
              </button>
            ) : (
              <span className="document-outline-toggle document-outline-toggle-placeholder" />
            )}
            <button
              type="button"
              className={`document-outline-item ${activeHeadingId === node.id ? 'document-outline-item-active' : ''}`}
              onClick={() => scrollToHeading(node.id)}
            >
              {node.text}
            </button>
          </div>
          {hasChildren && isExpanded ? <div className="document-outline-children">{renderOutlineNodes(node.children, depth + 1)}</div> : null}
        </div>
      );
    });
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
                {renderOutlineNodes(outlineTree.rootNodes)}
              </div>
            </div>
          </aside>
        ) : null}
      </div>
    </div>
  );
}
