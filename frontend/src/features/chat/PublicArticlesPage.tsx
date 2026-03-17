import {
  FilterOutlined,
  LeftOutlined,
  ReadOutlined,
  RightOutlined,
  RollbackOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Empty, Pagination, Space, Spin, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { PublicDocumentCategoryFacet, PublicDocumentTagFacet } from '../../lib/types';
import { MarkdownRenderer } from '../admin/components/MarkdownRenderer';

const PAGE_SIZE = 12;

function parsePositiveNumber(value: string | null) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
}

function parseSelectedTagIds(searchParams: URLSearchParams) {
  return Array.from(
    new Set(
      searchParams
        .getAll('tagId')
        .map((value) => Number(value))
        .filter((value) => Number.isFinite(value) && value > 0),
    ),
  );
}

function parseTagNames(tags: string) {
  try {
    const parsed = JSON.parse(tags) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : [];
  } catch {
    return [];
  }
}

function buildSearch(searchParams: URLSearchParams) {
  const search = searchParams.toString();
  return search ? `?${search}` : '';
}

export function PublicArticlesPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { documentId } = useParams<{ documentId?: string }>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const categoryId = parsePositiveNumber(searchParams.get('categoryId'));
  const selectedTagIds = parseSelectedTagIds(searchParams);
  const currentPage = parsePositiveNumber(searchParams.get('page')) ?? 1;
  const numericDocumentId = parsePositiveNumber(documentId ?? null);

  const facetsQuery = useQuery({
    queryKey: ['publicDocumentFacets'],
    queryFn: api.publicDocumentFacets,
    staleTime: 5 * 60 * 1000,
  });

  const articlesQuery = useQuery({
    queryKey: ['publicDocuments', categoryId, selectedTagIds, currentPage, PAGE_SIZE],
    queryFn: () =>
      api.publicDocuments({
        categoryId,
        tagIds: selectedTagIds,
        page: currentPage,
        pageSize: PAGE_SIZE,
      }),
    staleTime: 30 * 1000,
  });

  const detailQuery = useQuery({
    queryKey: ['publicDocumentDetail', numericDocumentId],
    queryFn: () => api.publicDocumentDetail(numericDocumentId!),
    enabled: numericDocumentId != null,
  });

  const facets = facetsQuery.data;
  const articlesPage = articlesQuery.data;
  const selectedCategory = useMemo(
    () => facets?.categories.find((item) => item.id === categoryId) ?? null,
    [categoryId, facets],
  );
  const availableTags = selectedCategory?.tags ?? facets?.allTags ?? [];
  const detailDocument = detailQuery.data;
  const detailTags = detailDocument ? parseTagNames(detailDocument.tags) : [];
  const columnDocuments = detailDocument?.columnDocuments ?? [];

  function navigateToList(nextSearchParams: URLSearchParams) {
    navigate({ pathname: '/articles', search: buildSearch(nextSearchParams) });
  }

  function updateFilters(mutator: (next: URLSearchParams) => void) {
    const next = new URLSearchParams(searchParams);
    mutator(next);
    navigateToList(next);
  }

  function handleCategorySelect(nextCategoryId: number | null) {
    updateFilters((next) => {
      if (nextCategoryId == null) {
        next.delete('categoryId');
      } else {
        next.set('categoryId', String(nextCategoryId));
      }
      next.delete('tagId');
      next.delete('page');
    });
  }

  function handleTagToggle(tagId: number) {
    updateFilters((next) => {
      const nextTagIds = new Set(parseSelectedTagIds(next));
      if (nextTagIds.has(tagId)) {
        nextTagIds.delete(tagId);
      } else {
        nextTagIds.add(tagId);
      }
      next.delete('tagId');
      Array.from(nextTagIds)
        .sort((left, right) => left - right)
        .forEach((value) => next.append('tagId', String(value)));
      next.delete('page');
    });
  }

  function handlePageChange(page: number) {
    const next = new URLSearchParams(searchParams);
    if (page <= 1) {
      next.delete('page');
    } else {
      next.set('page', String(page));
    }
    navigate({ pathname: numericDocumentId ? `/articles/${numericDocumentId}` : '/articles', search: buildSearch(next) });
  }

  function openDetail(id: number) {
    navigate({ pathname: `/articles/${id}`, search: buildSearch(new URLSearchParams(searchParams)) });
  }

  function openColumnDocument(id: number) {
    navigate({ pathname: `/articles/${id}`, search: buildSearch(new URLSearchParams(searchParams)) });
  }

  const resultCountLabel = articlesPage ? `共 ${articlesPage.total} 篇公开文章` : '公开文章';

  return (
    <div className="chat-shell public-articles-shell">
      <div className={`public-articles-layout ${sidebarCollapsed ? 'public-articles-layout-collapsed' : ''}`}>
        <aside className={`public-articles-sidebar ${sidebarCollapsed ? 'public-articles-sidebar-collapsed' : ''}`}>
          <Card
            className="chat-panel public-articles-sidebar-card"
            title={sidebarCollapsed ? <FilterOutlined /> : '筛选'}
            extra={
              <Button
                type="text"
                icon={sidebarCollapsed ? <RightOutlined /> : <LeftOutlined />}
                onClick={() => setSidebarCollapsed((current) => !current)}
                aria-label={sidebarCollapsed ? '展开筛选栏' : '收起筛选栏'}
              />
            }
          >
            {sidebarCollapsed ? (
              <div className="public-articles-sidebar-collapsed-summary">
                <FilterOutlined />
                <Typography.Text type="secondary">{facets?.totalDocumentCount ?? 0}</Typography.Text>
              </div>
            ) : facetsQuery.isLoading ? (
              <div className="chat-empty-state">
                <Spin />
              </div>
            ) : facetsQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="筛选数据加载失败"
                description={buildErrorSummary(facetsQuery.error, '请稍后重试')}
                action={
                  <Button size="small" onClick={() => void facetsQuery.refetch()}>
                    重试
                  </Button>
                }
              />
            ) : (
              <div className="public-articles-sidebar-body">
                {numericDocumentId && columnDocuments.length ? (
                  <div className="public-articles-filter-block">
                    <div className="public-articles-filter-title">
                      <ReadOutlined />
                      <span>{detailDocument?.columnName ? `专栏 · ${detailDocument.columnName}` : '专栏文章'}</span>
                    </div>
                    <div className="public-articles-category-list">
                      {columnDocuments.map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          className={`public-articles-category-item ${item.id === numericDocumentId ? 'public-articles-category-item-active' : ''}`}
                          onClick={() => openColumnDocument(item.id)}
                        >
                          <span>{item.title}</span>
                          <span>{new Date(item.createdAt).toLocaleDateString('zh-CN')}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}
                <div className="public-articles-filter-block">
                  <div className="public-articles-filter-title">
                    <ReadOutlined />
                    <span>分类</span>
                  </div>
                  <div className="public-articles-category-list">
                    <button
                      type="button"
                      className={`public-articles-category-item ${categoryId == null ? 'public-articles-category-item-active' : ''}`}
                      onClick={() => handleCategorySelect(null)}
                    >
                      <span>全部</span>
                      <span>{facets?.totalDocumentCount ?? 0}</span>
                    </button>
                    {(facets?.categories ?? []).map((category: PublicDocumentCategoryFacet) => (
                      <button
                        key={category.id}
                        type="button"
                        className={`public-articles-category-item ${category.id === categoryId ? 'public-articles-category-item-active' : ''}`}
                        onClick={() => handleCategorySelect(category.id)}
                      >
                        <span>{category.name}</span>
                        <span>{category.documentCount}</span>
                      </button>
                    ))}
                  </div>
                </div>

                <div className="public-articles-filter-block">
                  <div className="public-articles-filter-title">
                    <TagsOutlined />
                    <span>{selectedCategory ? `${selectedCategory.name} 标签` : '全部标签'}</span>
                  </div>
                  <div className="public-articles-tag-list">
                    {availableTags.length ? (
                      availableTags.map((tag: PublicDocumentTagFacet) => {
                        const checked = selectedTagIds.includes(tag.id);
                        return (
                          <button
                            key={tag.id}
                            type="button"
                            className={`public-articles-tag-item ${checked ? 'public-articles-tag-item-active' : ''}`}
                            onClick={() => handleTagToggle(tag.id)}
                          >
                            <span>{tag.name}</span>
                            <span>{tag.documentCount}</span>
                          </button>
                        );
                      })
                    ) : (
                      <Typography.Text type="secondary">当前分类下暂无可筛选标签。</Typography.Text>
                    )}
                  </div>
                </div>
              </div>
            )}
          </Card>
        </aside>

        <main className="public-articles-main">
          <Card
            className="chat-panel public-articles-main-card"
            title={numericDocumentId ? (detailDocument?.title ?? '文章详情') : '公开文章'}
            extra={
              numericDocumentId ? (
                <Button icon={<RollbackOutlined />} onClick={() => navigate({ pathname: '/articles', search: buildSearch(new URLSearchParams(searchParams)) })}>
                  返回列表
                </Button>
              ) : (
                <Typography.Text type="secondary">{resultCountLabel}</Typography.Text>
              )
            }
          >
            {numericDocumentId ? (
              detailQuery.isLoading ? (
                <div className="chat-empty-state">
                  <Spin size="large" />
                </div>
              ) : detailQuery.isError ? (
                <Alert
                  type="error"
                  showIcon
                  message="文章详情加载失败"
                  description={buildErrorSummary(detailQuery.error, '请检查文章是否仍为公开状态')}
                />
              ) : detailDocument ? (
                <div className="public-articles-detail">
                  <div className="document-detail-meta public-articles-detail-meta">
                    <div className="document-detail-meta-item public-articles-detail-meta-item">
                      <Typography.Text type="secondary">分类</Typography.Text>
                      <Typography.Text>{detailDocument.categoryName || '未分类'}</Typography.Text>
                    </div>
                    <div className="document-detail-meta-item public-articles-detail-meta-item">
                      <Typography.Text type="secondary">来源文件</Typography.Text>
                      <Typography.Text>{detailDocument.sourceFilename}</Typography.Text>
                    </div>
                    <div className="document-detail-meta-item public-articles-detail-meta-item">
                      <Typography.Text type="secondary">专栏</Typography.Text>
                      <Typography.Text>{detailDocument.columnName || '无'}</Typography.Text>
                    </div>
                    <div className="document-detail-meta-item public-articles-detail-meta-item">
                      <Typography.Text type="secondary">最近更新</Typography.Text>
                      <Typography.Text>{new Date(detailDocument.updatedAt).toLocaleString('zh-CN')}</Typography.Text>
                    </div>
                    <div className="document-detail-meta-item public-articles-detail-meta-item public-articles-detail-meta-item-tags">
                      <Typography.Text type="secondary">标签</Typography.Text>
                      <Space size={[6, 6]} wrap>
                        {detailTags.length ? detailTags.map((tag) => <Tag key={tag}>{tag}</Tag>) : <Typography.Text>无</Typography.Text>}
                      </Space>
                    </div>
                  </div>

                  <div className="document-detail-markdown public-articles-detail-markdown">
                    <MarkdownRenderer content={detailDocument.sourceMarkdown} className="public-articles-markdown" headingIdPrefix={`public-article-${detailDocument.id}`} />
                  </div>
                </div>
              ) : (
                <div className="chat-empty-state">
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="文章不存在或暂不可查看。" />
                </div>
              )
            ) : articlesQuery.isLoading ? (
              <div className="chat-empty-state">
                <Spin size="large" />
              </div>
            ) : articlesQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="公开文章加载失败"
                description={buildErrorSummary(articlesQuery.error, '请稍后重试')}
                action={
                  <Button size="small" onClick={() => void articlesQuery.refetch()}>
                    重试
                  </Button>
                }
              />
            ) : articlesPage && articlesPage.items.length ? (
              <div className="public-articles-main-body">
                <div className="public-articles-card-list">
                  {articlesPage.items.map((article) => (
                    <button key={article.id} type="button" className="public-article-card" onClick={() => openDetail(article.id)}>
                      <div className="public-article-card-head">
                        <div>
                          <Typography.Title level={5}>{article.title}</Typography.Title>
                          <Typography.Text type="secondary">
                            {article.categoryName || '未分类'} · {new Date(article.updatedAt).toLocaleDateString('zh-CN')}
                          </Typography.Text>
                        </div>
                        <ReadOutlined className="public-article-card-arrow" />
                      </div>
                      <Typography.Paragraph className="public-article-card-excerpt">{article.excerpt}</Typography.Paragraph>
                      <Space size={[6, 6]} wrap>
                        {article.tags.map((tag) => (
                          <Tag key={tag} bordered={false} color="geekblue">
                            {tag}
                          </Tag>
                        ))}
                      </Space>
                    </button>
                  ))}
                </div>

                <div className="public-articles-pagination">
                  <Pagination
                    current={articlesPage.page}
                    total={articlesPage.total}
                    pageSize={articlesPage.pageSize}
                    showSizeChanger={false}
                    onChange={handlePageChange}
                  />
                </div>
              </div>
            ) : (
              <div className="chat-empty-state">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有公开文章。" />
              </div>
            )}
          </Card>
        </main>
      </div>
    </div>
  );
}
