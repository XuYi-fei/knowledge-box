import { DownOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState, type CSSProperties, type MouseEvent } from 'react';
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

type DebugLogEntry = {
  id: number;
  timestamp: string;
  message: string;
  details?: string;
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

function normalizeHeadingTextContent(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

function buildRenderedOutline(markdownRoot: HTMLDivElement | null, headingIdPrefix: string) {
  if (!markdownRoot) {
    return [];
  }
  const headingElements = Array.from(markdownRoot.querySelectorAll<HTMLElement>('.admin-markdown h1, .admin-markdown h2, .admin-markdown h3, .admin-markdown h4, .admin-markdown h5, .admin-markdown h6'));
  const counter = new Map<string, number>();
  return headingElements
    .map((element) => {
      const text = normalizeHeadingTextContent(element.textContent ?? '');
      if (!text) {
        return null;
      }
      const baseId = `${headingIdPrefix}-${slugifyValue(text) || 'section'}`;
      const current = counter.get(baseId) ?? 0;
      counter.set(baseId, current + 1);
      const fallbackId = current === 0 ? baseId : `${baseId}-${current + 1}`;
      const id = element.id || fallbackId;
      if (!element.id) {
        element.id = id;
      }
      return {
        id,
        text,
        level: Number(element.tagName.slice(1)),
      } satisfies MarkdownOutlineItem;
    })
    .filter((item): item is MarkdownOutlineItem => item !== null);
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

function scrollToHeadingInContainer(container: HTMLElement | null, target: HTMLElement, behavior: ScrollBehavior) {
  if (!container) {
    target.scrollIntoView({ block: 'start', behavior });
    return;
  }
  const top = Math.max(target.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - 20, 0);
  container.scrollTo({ top, behavior });
}

function replaceWindowHash(headingId: string) {
  const url = new URL(globalThis.window.location.href);
  if (decodeURIComponent(url.hash.replace(/^#/, '')) === headingId) {
    return;
  }
  url.hash = headingId;
  globalThis.window.history.replaceState(globalThis.window.history.state, '', url);
}

function pushWindowHash(headingId: string) {
  const url = new URL(globalThis.window.location.href);
  if (decodeURIComponent(url.hash.replace(/^#/, '')) === headingId) {
    return;
  }
  url.hash = headingId;
  globalThis.window.history.pushState(globalThis.window.history.state, '', url);
}

export function UserDocumentDetailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { documentId } = useParams<{ documentId: string }>();
  const params = new URLSearchParams(location.search);
  const headingPath = params.get('headingPath');
  const anchor = params.get('anchor');
  const snippet = params.get('snippet');
  const debugOutline = params.get('debugOutline') === '1';
  const numericDocumentId = Number(documentId);
  const [activeHeadingId, setActiveHeadingId] = useState<string | null>(null);
  const [highlightedHeadingId, setHighlightedHeadingId] = useState<string | null>(null);
  const [expandedHeadingIds, setExpandedHeadingIds] = useState<string[]>([]);
  const [renderedOutline, setRenderedOutline] = useState<MarkdownOutlineItem[]>([]);
  const [debugLogs, setDebugLogs] = useState<DebugLogEntry[]>([]);
  const initialFocusRef = useRef<string | null>(null);
  const initialHashHeadingIdRef = useRef<string | null>(
    decodeURIComponent(location.hash.replace(/^#/, '').trim()) || null,
  );
  const markdownRootRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const debugSeqRef = useRef(0);
  const lastActiveDebugRef = useRef<string | null>(null);

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
  const fallbackOutline = useMemo(
    () => (detailDocument ? extractMarkdownOutline(detailDocument.sourceMarkdown, headingIdPrefix) : []),
    [detailDocument, headingIdPrefix],
  );
  const outline = renderedOutline.length ? renderedOutline : fallbackOutline;
  const outlineTree = useMemo(() => buildOutlineTree(outline), [outline]);
  const activePathIds = useMemo(
    () => collectPathIds(outlineTree.nodeMap, activeHeadingId),
    [activeHeadingId, outlineTree.nodeMap],
  );

  function appendDebugLog(message: string, payload?: unknown) {
    if (!debugOutline) {
      return;
    }
    const details = payload == null
      ? undefined
      : typeof payload === 'string'
        ? payload
        : JSON.stringify(payload, null, 2);
    const entry: DebugLogEntry = {
      id: debugSeqRef.current + 1,
      timestamp: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
      message,
      details,
    };
    debugSeqRef.current += 1;
    setDebugLogs((current) => [...current.slice(-79), entry]);
  }

  useEffect(() => {
    if (!detailDocument) {
      setRenderedOutline([]);
      appendDebugLog('文档详情尚未加载，清空 renderedOutline');
      return;
    }
    const frameId = requestAnimationFrame(() => {
      const nextOutline = buildRenderedOutline(markdownRootRef.current, headingIdPrefix);
      setRenderedOutline(nextOutline);
      appendDebugLog('基于真实 DOM 生成大纲', {
        count: nextOutline.length,
        headings: nextOutline.map((item) => ({
          level: item.level,
          id: item.id,
          text: item.text,
        })),
      });
    });
    return () => cancelAnimationFrame(frameId);
  }, [debugOutline, detailDocument, headingIdPrefix]);

  useEffect(() => {
    if (!outline.length) {
      setActiveHeadingId(null);
      setExpandedHeadingIds([]);
      appendDebugLog('当前 outline 为空，无法建立滚动高亮');
      return;
    }
    let frameId = 0;
    const resolveActiveHeading = () => {
      const scrollContainer = scrollContainerRef.current;
      const headingElements = outline
        .map((item) => globalThis.document.getElementById(item.id))
        .filter((item): item is HTMLElement => item instanceof HTMLElement);
      if (!headingElements.length) {
        appendDebugLog('滚动监听未找到任何真实标题节点', {
          outlineIds: outline.map((item) => item.id),
        });
        return;
      }
      const threshold = 140;
      const containerTop = scrollContainer?.getBoundingClientRect().top ?? 0;
      let currentHeading = headingElements[0].id;
      for (const headingElement of headingElements) {
        if (headingElement.getBoundingClientRect().top - containerTop <= threshold) {
          currentHeading = headingElement.id;
        } else {
          break;
        }
      }
      if (lastActiveDebugRef.current !== currentHeading) {
        lastActiveDebugRef.current = currentHeading;
        appendDebugLog('activeHeading 更新', {
          currentHeading,
          top: globalThis.document.getElementById(currentHeading)?.getBoundingClientRect().top ?? null,
          scrollTop: scrollContainer?.scrollTop ?? null,
        });
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
    const scrollTarget = scrollContainerRef.current;
    scrollTarget?.addEventListener('scroll', handleScroll, { passive: true } as AddEventListenerOptions);
    globalThis.window.addEventListener('resize', handleScroll);
    return () => {
      if (frameId) {
        cancelAnimationFrame(frameId);
      }
      scrollTarget?.removeEventListener('scroll', handleScroll as EventListener);
      globalThis.window.removeEventListener('resize', handleScroll);
    };
  }, [debugOutline, outline]);

  useEffect(() => {
    if (!outline.length) {
      return;
    }
    const targetId = resolveTargetHeadingId(outline, headingPath, anchor, initialHashHeadingIdRef.current);
    appendDebugLog('解析初始目标标题', {
      headingPath,
      anchor,
      initialHash: initialHashHeadingIdRef.current,
      resolvedTargetId: targetId,
    });
    if (!targetId || initialFocusRef.current === `${numericDocumentId}:${targetId}`) {
      return;
    }
    initialFocusRef.current = `${numericDocumentId}:${targetId}`;
    const pathIds = Array.from(collectPathIds(outlineTree.nodeMap, targetId));
    setExpandedHeadingIds((current) => Array.from(new Set([...current, ...pathIds])));
    setActiveHeadingId(targetId);
    setHighlightedHeadingId(targetId);
    replaceWindowHash(targetId);
    requestAnimationFrame(() => {
      const target = globalThis.document.getElementById(targetId);
      appendDebugLog('尝试执行初始定位', {
        targetId,
        targetFound: Boolean(target),
        targetTop: target?.getBoundingClientRect().top ?? null,
      });
      if (target) {
        scrollToHeadingInContainer(scrollContainerRef.current, target, 'auto');
      }
    });
  }, [anchor, debugOutline, headingPath, numericDocumentId, outline, outlineTree.nodeMap]);

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
    appendDebugLog('点击大纲准备跳转', {
      headingId,
      behavior,
      targetFound: Boolean(target),
      hashBefore: globalThis.window.location.hash,
      scrollTopBefore: scrollContainerRef.current?.scrollTop ?? null,
      targetTopBefore: target?.getBoundingClientRect().top ?? null,
    });
    if (!target) {
      return;
    }
    pushWindowHash(headingId);
    scrollToHeadingInContainer(scrollContainerRef.current, target, behavior);
    setActiveHeadingId(headingId);
    setHighlightedHeadingId(headingId);
    const pathIds = Array.from(collectPathIds(outlineTree.nodeMap, headingId));
    setExpandedHeadingIds((current) => Array.from(new Set([...current, ...pathIds])));
    appendDebugLog('点击大纲完成跳转请求', {
      headingId,
      hashAfter: globalThis.window.location.hash,
      scrollTopAfter: scrollContainerRef.current?.scrollTop ?? null,
      targetTopAfter: target.getBoundingClientRect().top,
    });
  }

  function handleOutlineClick(event: MouseEvent<HTMLAnchorElement>, headingId: string) {
    event.preventDefault();
    scrollToHeading(headingId);
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
            <a
              className={`document-outline-item ${activeHeadingId === node.id ? 'document-outline-item-active' : ''}`}
              href={`#${node.id}`}
              onClick={(event) => handleOutlineClick(event, node.id)}
            >
              {node.text}
            </a>
          </div>
          {hasChildren && isExpanded ? <div className="document-outline-children">{renderOutlineNodes(node.children, depth + 1)}</div> : null}
        </div>
      );
    });
  }

  return (
    <div ref={scrollContainerRef} className="document-detail-page">
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

              <div ref={markdownRootRef} className="document-detail-markdown">
                <MarkdownRenderer
                  content={detailDocument.sourceMarkdown}
                  headingIdPrefix={headingIdPrefix}
                  headingIds={fallbackOutline.map((item) => item.id)}
                />
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
      {debugOutline ? (
        <div
          style={{
            position: 'fixed',
            right: 12,
            bottom: 12,
            width: 'min(520px, calc(100vw - 24px))',
            maxHeight: '42vh',
            overflow: 'auto',
            zIndex: 2000,
            padding: 12,
            borderRadius: 16,
            background: 'rgba(16, 42, 43, 0.94)',
            color: '#f8fbfa',
            boxShadow: '0 18px 40px rgba(16, 42, 43, 0.28)',
            fontSize: 12,
            lineHeight: 1.5,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 8 }}>
            <strong>Outline Debug</strong>
            <span>{debugLogs.length} entries</span>
          </div>
          <div style={{ display: 'grid', gap: 8 }}>
            {debugLogs.map((log) => (
              <div key={log.id} style={{ padding: '8px 10px', borderRadius: 10, background: 'rgba(255,255,255,0.08)' }}>
                <div style={{ color: 'rgba(255,255,255,0.72)' }}>
                  #{log.id} {log.timestamp}
                </div>
                <div>{log.message}</div>
                {log.details ? (
                  <pre
                    style={{
                      margin: '6px 0 0',
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      fontFamily: '"IBM Plex Mono", "SFMono-Regular", monospace',
                      color: 'rgba(255,255,255,0.86)',
                    }}
                  >
                    {log.details}
                  </pre>
                ) : null}
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
