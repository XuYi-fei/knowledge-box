import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Checkbox, Col, Form, Input, List, Pagination, Progress, Row, Select, Space, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { DocumentReviewRequestSummary, DocumentReviewStatus } from '../../lib/types';
import { MarkdownWorkbench } from './components/MarkdownWorkbench';

type TaxonomyFormValues = {
  categoryName: string;
  columnName: string;
  tags: string[];
};

function parseTagsJson(value: string | null | undefined): string[] {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.map((item) => String(item).trim()).filter(Boolean);
  } catch {
    return [];
  }
}

function reviewStatusTagColor(status: DocumentReviewRequestSummary['status']) {
  if (status === 'PENDING_REVIEW') {
    return 'processing';
  }
  if (status === 'APPROVED') {
    return 'green';
  }
  if (status === 'REJECTED' || status === 'FAILED') {
    return 'red';
  }
  return 'default';
}

type ReviewFilterStatus = DocumentReviewStatus | 'ALL';

const REVIEW_STATUS_OPTIONS: Array<{ label: string; value: ReviewFilterStatus }> = [
  { label: '全部状态', value: 'ALL' },
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已创建', value: 'CREATED' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '失败', value: 'FAILED' },
];

function formatDateTimeToSeconds(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (raw: number) => String(raw).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function DocumentReviewsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [taxonomyForm] = Form.useForm<TaxonomyFormValues>();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedReviewIds, setSelectedReviewIds] = useState<number[]>([]);
  const [filterStatus, setFilterStatus] = useState<ReviewFilterStatus>('ALL');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const { data: reviewsPage } = useQuery({
    queryKey: ['documentReviews', filterStatus, page, pageSize],
    queryFn: () =>
      api.documentReviews({
        status: filterStatus === 'ALL' ? undefined : filterStatus,
        page,
        pageSize,
      }),
    refetchInterval: 3000,
  });
  const reviews = reviewsPage?.items ?? [];
  const totalReviews = reviewsPage?.total ?? 0;

  useEffect(() => {
    if (reviews.length === 0) {
      setSelectedId(null);
      return;
    }
    if (selectedId == null || !reviews.some((item) => item.id === selectedId)) {
      setSelectedId(reviews[0].id);
    }
  }, [reviews, selectedId]);

  const selectedSummary = useMemo(
    () => reviews.find((item) => item.id === selectedId) ?? null,
    [reviews, selectedId],
  );
  const currentPendingReviewIds = useMemo(
    () => reviews.filter((item) => item.status === 'PENDING_REVIEW').map((item) => item.id),
    [reviews],
  );

  useEffect(() => {
    const selectableReviewIds = new Set(currentPendingReviewIds);
    setSelectedReviewIds((current) => {
      const next = current.filter((id) => selectableReviewIds.has(id));
      if (next.length === current.length && next.every((id, index) => id === current[index])) {
        return current;
      }
      return next;
    });
  }, [currentPendingReviewIds]);

  const selectedPendingReviewCount = selectedReviewIds.length;
  const allPendingOnPageSelected =
    currentPendingReviewIds.length > 0 && currentPendingReviewIds.every((id) => selectedReviewIds.includes(id));

  const { data: detail } = useQuery({
    queryKey: ['documentReviewDetail', selectedId],
    queryFn: () => api.documentReviewDetail(selectedId!),
    enabled: selectedId != null,
  });

  const { data: categories = [] } = useQuery({
    queryKey: ['documentCategories'],
    queryFn: api.documentCategories,
  });
  const { data: columns = [] } = useQuery({
    queryKey: ['documentColumns'],
    queryFn: api.documentColumns,
  });
  const { data: tags = [] } = useQuery({
    queryKey: ['documentTags'],
    queryFn: api.documentTags,
  });

  useEffect(() => {
    if (!detail) {
      return;
    }
    taxonomyForm.setFieldsValue({
      categoryName: detail.selectedCategoryName || detail.suggestedCategoryName || '',
      columnName: detail.selectedColumnName || '',
      tags: parseTagsJson(detail.selectedTagsJson || detail.suggestedTagsJson),
    });
  }, [detail, taxonomyForm]);

  const saveTaxonomyMutation = useMutation({
    mutationFn: async () => {
      if (!selectedId) {
        throw new Error('未选择审核单');
      }
      const values = await taxonomyForm.validateFields();
      return api.updateDocumentReviewTaxonomy(selectedId, {
        categoryName: values.categoryName.trim(),
        columnName: values.columnName?.trim() || null,
        tags: values.tags ?? [],
      });
    },
    onSuccess: () => {
      message.success('分类、专栏与标签已更新');
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviewDetail', selectedId] });
    },
    onError: (error) => {
      modal.error({
        title: '保存失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查分类、专栏与标签后重试')}</pre>,
      });
    },
  });

  const approveMutation = useMutation({
    mutationFn: (reason?: string) => api.approveDocumentReview(selectedId!, reason),
    onSuccess: () => {
      message.success('审核通过，文档已发布');
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviewDetail', selectedId] });
    },
    onError: (error) => {
      modal.error({
        title: '审核失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '审核通过操作失败')}</pre>,
      });
    },
  });

  const batchApproveMutation = useMutation({
    mutationFn: (reason?: string) => api.batchApproveDocumentReviews(selectedReviewIds, reason),
    onSuccess: (result) => {
      message.success(`批量审核通过完成，已发布 ${result.processedCount} 条文档`);
      setSelectedReviewIds([]);
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviewDetail', selectedId] });
    },
    onError: (error) => {
      modal.error({
        title: '批量审核失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '批量审核通过操作失败')}</pre>,
      });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (reason?: string) => api.rejectDocumentReview(selectedId!, reason),
    onSuccess: () => {
      message.success('已驳回该审核单');
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviewDetail', selectedId] });
    },
    onError: (error) => {
      modal.error({
        title: '驳回失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '驳回操作失败')}</pre>,
      });
    },
  });

  const openApproveConfirm = () => {
    let reason = '';
    modal.confirm({
      title: '确认通过该审核单？',
      content: (
        <Input.TextArea
          rows={4}
          placeholder="可选：填写审核备注"
          onChange={(event) => {
            reason = event.target.value;
          }}
        />
      ),
      onOk: async () => approveMutation.mutateAsync(reason),
    });
  };

  const openRejectConfirm = () => {
    let reason = '';
    modal.confirm({
      title: '确认驳回该审核单？',
      content: (
        <Input.TextArea
          rows={4}
          placeholder="可选：填写驳回原因"
          onChange={(event) => {
            reason = event.target.value;
          }}
        />
      ),
      onOk: async () => rejectMutation.mutateAsync(reason),
    });
  };

  const openBatchApproveConfirm = () => {
    let reason = '';
    modal.confirm({
      title: `确认批量通过已选 ${selectedPendingReviewCount} 条审核单？`,
      content: (
        <Input.TextArea
          rows={4}
          placeholder="可选：填写本次批量审核备注"
          onChange={(event) => {
            reason = event.target.value;
          }}
        />
      ),
      onOk: async () => batchApproveMutation.mutateAsync(reason),
    });
  };

  const toggleReviewSelection = (reviewId: number, checked: boolean) => {
    setSelectedReviewIds((current) => {
      if (checked) {
        return current.includes(reviewId) ? current : [...current, reviewId];
      }
      return current.filter((id) => id !== reviewId);
    });
  };

  const reviewPending = detail?.status === 'PENDING_REVIEW';

  return (
    <div className="page-stack">
      <Typography.Title level={3}>文档审核</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={8}>
          <Card title="审核队列">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Space wrap>
                <Typography.Text type="secondary">状态筛选</Typography.Text>
                <Select<ReviewFilterStatus>
                  value={filterStatus}
                  options={REVIEW_STATUS_OPTIONS}
                  style={{ width: 180 }}
                  onChange={(next) => {
                    setFilterStatus(next);
                    setPage(1);
                  }}
                />
                <Typography.Text type="secondary">共 {totalReviews} 条</Typography.Text>
              </Space>
              <Space wrap>
                <Typography.Text type="secondary">当前页待审核 {currentPendingReviewIds.length} 条</Typography.Text>
                <Typography.Text type="secondary">已选 {selectedPendingReviewCount} 条</Typography.Text>
                <Button
                  onClick={() => setSelectedReviewIds(currentPendingReviewIds)}
                  disabled={currentPendingReviewIds.length === 0 || allPendingOnPageSelected}
                >
                  全选当前页待审核
                </Button>
                <Button onClick={() => setSelectedReviewIds([])} disabled={selectedPendingReviewCount === 0}>
                  清空选择
                </Button>
                <Button
                  type="primary"
                  onClick={openBatchApproveConfirm}
                  loading={batchApproveMutation.isPending}
                  disabled={selectedPendingReviewCount === 0}
                >
                  批量审核通过
                </Button>
              </Space>
            <List
              dataSource={reviews}
              locale={{ emptyText: '暂无审核请求' }}
              renderItem={(item) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    borderRadius: 8,
                    padding: 12,
                    background: selectedId === item.id ? '#f3faf8' : 'transparent',
                  }}
                  onClick={() => setSelectedId(item.id)}
                >
                  <Space direction="vertical" size={4} style={{ width: '100%' }}>
                    <Space>
                      <Checkbox
                        checked={selectedReviewIds.includes(item.id)}
                        disabled={item.status !== 'PENDING_REVIEW'}
                        onClick={(event) => event.stopPropagation()}
                        onChange={(event) => toggleReviewSelection(item.id, event.target.checked)}
                      />
                      <Typography.Text strong>{item.title}</Typography.Text>
                      <Tag color={reviewStatusTagColor(item.status)}>{item.status}</Tag>
                    </Space>
                    <Typography.Text type="secondary">阶段: {item.stage}</Typography.Text>
                    <Progress percent={item.progressPercent} size="small" />
                    <Typography.Text type="secondary">更新时间: {formatDateTimeToSeconds(item.updatedAt)}</Typography.Text>
                  </Space>
                </List.Item>
              )}
            />
              <Pagination
                current={page}
                pageSize={pageSize}
                total={totalReviews}
                showSizeChanger
                pageSizeOptions={['10', '20', '50']}
                onChange={(nextPage, nextPageSize) => {
                  setPage(nextPage);
                  if (nextPageSize !== pageSize) {
                    setPageSize(nextPageSize);
                  }
                }}
              />
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={16}>
          <Card title={selectedSummary ? `审核详情 · ${selectedSummary.requestCode}` : '审核详情'}>
            {!detail ? (
              <Typography.Text type="secondary">请选择左侧审核单</Typography.Text>
            ) : (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Space wrap>
                  <Tag color={reviewStatusTagColor(detail.status)}>{detail.status}</Tag>
                  <Tag>{detail.visibilityType}</Tag>
                  <Tag>assets: {detail.assets.length}</Tag>
                  <Tag>chunks: {detail.chunks.length}</Tag>
                </Space>

                <Card size="small" title="建议分类与标签">
                  <Typography.Paragraph style={{ marginBottom: 8 }}>
                    建议分类：<Typography.Text code>{detail.suggestedCategoryName || '-'}</Typography.Text>
                  </Typography.Paragraph>
                  <Typography.Paragraph style={{ marginBottom: 8 }}>
                    建议标签：<Typography.Text code>{detail.suggestedTagsJson}</Typography.Text>
                  </Typography.Paragraph>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    推理摘要：{detail.taxonomyReasoning || '-'}
                  </Typography.Paragraph>
                </Card>

                <Card size="small" title="审核可编辑项（分类/专栏/标签）">
                  <Form form={taxonomyForm} layout="vertical">
                    <Form.Item name="categoryName" label="分类" rules={[{ required: true, message: '请输入分类' }]}>
                      <Input list="category-options" placeholder="输入或选择分类" disabled={!reviewPending} />
                    </Form.Item>
                    <datalist id="category-options">
                      {categories.map((category) => (
                        <option key={category.id} value={category.name} />
                      ))}
                    </datalist>
                    <Form.Item name="columnName" label="专栏">
                      <Input list="column-options" placeholder="输入或选择专栏，不填则留空" disabled={!reviewPending} />
                    </Form.Item>
                    <datalist id="column-options">
                      {columns.map((column) => (
                        <option key={column.id} value={column.name} />
                      ))}
                    </datalist>
                    <Form.Item name="tags" label="标签">
                      <Select
                        mode="tags"
                        disabled={!reviewPending}
                        options={tags.map((tag) => ({ label: tag.name, value: tag.name }))}
                        tokenSeparators={[',', '，']}
                        placeholder="输入或选择标签"
                      />
                    </Form.Item>
                  </Form>
                  <Button type="primary" onClick={() => saveTaxonomyMutation.mutate()} loading={saveTaxonomyMutation.isPending} disabled={!reviewPending}>
                    保存分类、专栏与标签
                  </Button>
                </Card>

                <Card size="small" title="源Markdown（预览）">
                  <MarkdownWorkbench value={detail.sourceMarkdown} readOnly rows={18} />
                </Card>

                <Space>
                  <Button type="primary" onClick={openApproveConfirm} loading={approveMutation.isPending} disabled={!reviewPending}>
                    审核通过并发布
                  </Button>
                  <Button danger onClick={openRejectConfirm} loading={rejectMutation.isPending} disabled={!reviewPending}>
                    驳回
                  </Button>
                </Space>
              </Space>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
