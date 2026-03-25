import {
  CheckCircleOutlined,
  FileTextOutlined,
  InboxOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, Descriptions, Form, Input, Progress, Segmented, Select, Space, Tag, Typography, Upload } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { DocumentCategory, DocumentColumn, DocumentTag, KnowledgeIngestionDraft, KnowledgeIngestionDraftStatus } from '../../lib/types';
import { MarkdownWorkbench } from '../admin/components/MarkdownWorkbench';

type DraftMode = 'file' | 'inline';

function parseTagsJson(tagsJson: string) {
  try {
    const parsed = JSON.parse(tagsJson) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0) : [];
  } catch {
    return [];
  }
}

function draftStatusColor(status: KnowledgeIngestionDraftStatus) {
  switch (status) {
    case 'AWAITING_CONFIRMATION':
      return 'blue';
    case 'CONFIRMED':
      return 'green';
    case 'FAILED':
      return 'red';
    default:
      return 'gold';
  }
}

function shouldPollDraft(detail: KnowledgeIngestionDraft | undefined) {
  return detail?.status === 'CREATED' || detail?.status === 'PROCESSING';
}

function buildNameOptions<T extends { name: string }>(items: T[] | undefined, extraName?: string | null) {
  const seen = new Set<string>();
  const names = [...(items ?? []).map((item) => item.name), extraName ?? ''];
  return names
    .map((name) => name.trim())
    .filter((name) => {
      if (!name || seen.has(name)) {
        return false;
      }
      seen.add(name);
      return true;
    })
    .map((name) => ({ label: name, value: name }));
}

export function KnowledgeIngestionPage() {
  const navigate = useNavigate();
  const { draftId: draftIdParam } = useParams<{ draftId?: string }>();
  const { message } = App.useApp();
  const [inlineForm] = Form.useForm<{ content: string; sourceFilename?: string }>();
  const [confirmForm] = Form.useForm<{ title?: string; categoryName?: string; columnName?: string; tags?: string[] }>();
  const [draftMode, setDraftMode] = useState<DraftMode>('file');
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  const draftId = useMemo(() => {
    if (!draftIdParam) {
      return null;
    }
    const parsed = Number(draftIdParam);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }, [draftIdParam]);

  const optionsQuery = useQuery({
    queryKey: ['knowledgeIngestionOptions'],
    queryFn: api.knowledgeIngestionOptions,
    staleTime: 5 * 60 * 1000,
  });

  const detailQuery = useQuery({
    queryKey: ['knowledgeIngestionDraft', draftId],
    queryFn: () => api.knowledgeIngestionDraftDetail(draftId!),
    enabled: draftId != null,
    refetchInterval: (query) => (shouldPollDraft(query.state.data as KnowledgeIngestionDraft | undefined) ? 1500 : false),
  });

  const uploadMutation = useMutation({
    mutationFn: async () => {
      const current = fileList[0]?.originFileObj;
      if (!current) {
        throw new Error('请先选择 Markdown 或 PDF 文件');
      }
      return api.createKnowledgeIngestionUploadDraft(current);
    },
    onSuccess: (draft) => {
      message.success('文件已上传，正在生成知识草稿');
      void navigate(`/ingest/${draft.id}`);
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '上传失败，请稍后重试'));
    },
  });

  const inlineMutation = useMutation({
    mutationFn: (values: { content: string; sourceFilename?: string }) => api.createKnowledgeIngestionInlineDraft(values),
    onSuccess: (draft) => {
      message.success('内容已提交，正在生成知识草稿');
      void navigate(`/ingest/${draft.id}`);
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '创建草稿失败，请稍后重试'));
    },
  });

  const confirmMutation = useMutation({
    mutationFn: (values: { title?: string; categoryName?: string; columnName?: string; tags?: string[] }) =>
      api.confirmKnowledgeIngestionDraft(draftId!, values),
    onSuccess: (draft) => {
      message.success(`已提交待审核单：${draft.confirmedReviewRequestCode ?? '待生成编号'}`);
      void detailQuery.refetch();
    },
    onError: (error) => {
      message.error(buildErrorSummary(error, '提交待审核失败，请稍后重试'));
    },
  });

  useEffect(() => {
    const detail = detailQuery.data;
    if (!detail || detail.status !== 'AWAITING_CONFIRMATION') {
      return;
    }
    confirmForm.setFieldsValue({
      title: detail.suggestedTitle ?? '',
      categoryName: detail.suggestedCategoryName ?? '',
      columnName: undefined,
      tags: parseTagsJson(detail.suggestedTagsJson),
    });
  }, [confirmForm, detailQuery.data]);

  const detail = detailQuery.data;
  const parsedSuggestedTags = parseTagsJson(detail?.suggestedTagsJson ?? '[]');
  const categoryOptions = useMemo(
    () => buildNameOptions<DocumentCategory>(optionsQuery.data?.categories, detail?.suggestedCategoryName),
    [detail?.suggestedCategoryName, optionsQuery.data?.categories],
  );
  const columnOptions = useMemo(
    () => buildNameOptions<DocumentColumn>(optionsQuery.data?.columns),
    [optionsQuery.data?.columns],
  );
  const tagOptions = useMemo(
    () => buildNameOptions<DocumentTag>(optionsQuery.data?.tags).map((option) => ({ ...option, key: option.value })),
    [optionsQuery.data?.tags],
  );

  return (
    <div className="chat-shell">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card
          className="chat-panel chat-card"
          title="知识入库工作台"
          extra={
            draftId ? (
              <Button type="link" onClick={() => void navigate('/ingest')}>
                新建草稿
              </Button>
            ) : null
          }
        >
          <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
            上传 Markdown、文本型 PDF，或直接粘贴正文。系统会保留原文件，并通过 Agent 整理出待确认的知识文档草稿。
          </Typography.Paragraph>

          <Segmented<DraftMode>
            value={draftMode}
            options={[
              { label: '上传文件', value: 'file' },
              { label: '直接粘贴', value: 'inline' },
            ]}
            onChange={(value) => setDraftMode(value)}
            style={{ marginBottom: 16 }}
          />

          {draftMode === 'file' ? (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Upload.Dragger
                accept=".md,.markdown,.pdf,application/pdf,text/markdown"
                beforeUpload={() => false}
                maxCount={1}
                fileList={fileList}
                onChange={(info) => setFileList(info.fileList.slice(-1))}
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">拖拽或点击上传 Markdown / PDF</p>
                <p className="ant-upload-hint">PDF 首版仅支持可直接提取文本的文件</p>
              </Upload.Dragger>
              <Button type="primary" onClick={() => uploadMutation.mutate()} loading={uploadMutation.isPending} disabled={!fileList.length}>
                上传并生成草稿
              </Button>
            </Space>
          ) : (
            <Form layout="vertical" form={inlineForm} onFinish={(values) => inlineMutation.mutate(values)}>
              <Form.Item name="sourceFilename" label="来源文件名（可选）">
                <Input placeholder="例如：redis-multiplexing-notes.md" />
              </Form.Item>
              <Form.Item name="content" label="正文内容" rules={[{ required: true, message: '请输入正文内容' }]}>
                <Input.TextArea rows={12} placeholder="粘贴要整理成知识文档的 Markdown、笔记或原始文本" />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={inlineMutation.isPending}>
                生成草稿
              </Button>
            </Form>
          )}
        </Card>

        {draftId && !detail && detailQuery.isLoading ? (
          <Card className="chat-panel chat-card" title="草稿分析中">
            <Space>
              <SyncOutlined spin />
              <Typography.Text>正在载入草稿详情...</Typography.Text>
            </Space>
          </Card>
        ) : null}

        {draftId && detailQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message="草稿加载失败"
            description={buildErrorSummary(detailQuery.error, '请稍后重试')}
          />
        ) : null}

        {detail ? (
          <Card
            className="chat-panel chat-card"
            title={`草稿详情 · ${detail.draftCode}`}
            extra={<Tag color={draftStatusColor(detail.status)}>{detail.status}</Tag>}
          >
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="来源类型">{detail.sourceType}</Descriptions.Item>
                <Descriptions.Item label="阶段">{detail.stage}</Descriptions.Item>
                <Descriptions.Item label="来源文件">{detail.sourceFilename || '-'}</Descriptions.Item>
                <Descriptions.Item label="原始文件">
                  {detail.sourceFileUrl ? (
                    <a href={detail.sourceFileUrl} target="_blank" rel="noreferrer">
                      查看原始文件
                    </a>
                  ) : (
                    '-'
                  )}
                </Descriptions.Item>
              </Descriptions>

              {detail.status === 'CREATED' || detail.status === 'PROCESSING' ? (
                <Card size="small" title="正在分析中">
                  <Progress percent={detail.progressPercent} status="active" />
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    Agent 正在读取源内容并生成建议标题、分类、标签与整理后的 Markdown。
                  </Typography.Paragraph>
                </Card>
              ) : null}

              {detail.status === 'FAILED' ? (
                <Alert type="error" showIcon message="草稿分析失败" description={detail.errorMessage || '请调整内容后重新提交'} />
              ) : null}

              {detail.status === 'CONFIRMED' ? (
                <Alert
                  type="success"
                  showIcon
                  message="已提交待审核"
                  description={`审核单编号：${detail.confirmedReviewRequestCode ?? '待生成'}`}
                />
              ) : null}

              <Card size="small" title="Agent 建议">
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Typography.Text>
                    <FileTextOutlined /> 建议标题：{detail.suggestedTitle || '-'}
                  </Typography.Text>
                  <Typography.Text>建议分类：{detail.suggestedCategoryName || '-'}</Typography.Text>
                  <Typography.Text>建议标签：{parsedSuggestedTags.length ? parsedSuggestedTags.join('、') : '-'}</Typography.Text>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    {detail.analysisReasoning || detail.summaryText || '暂无额外说明'}
                  </Typography.Paragraph>
                </Space>
              </Card>

              <Card size="small" title="整理后的 Markdown 草稿">
                <MarkdownWorkbench value={detail.generatedMarkdown || ''} readOnly rows={18} />
              </Card>

              {detail.status === 'AWAITING_CONFIRMATION' ? (
                <Card size="small" title="确认后进入待审核单">
                  <Form
                    layout="vertical"
                    form={confirmForm}
                    onFinish={(values) =>
                      confirmMutation.mutate({
                        title: values.title,
                        categoryName: values.categoryName,
                        columnName: values.columnName,
                        tags: values.tags ?? [],
                      })
                    }
                  >
                    <Form.Item name="title" label="标题">
                      <Input placeholder="可自定义标题；留空时回退 Agent 建议" />
                    </Form.Item>
                    <Form.Item name="categoryName" label="分类">
                      <Select
                        allowClear
                        showSearch
                        options={categoryOptions}
                        placeholder="可选分类；留空时回退 Agent 建议"
                        optionFilterProp="label"
                      />
                    </Form.Item>
                    <Form.Item name="columnName" label="专栏（可选）">
                      <Select
                        allowClear
                        showSearch
                        options={columnOptions}
                        placeholder="可选专栏；不填则留空"
                        optionFilterProp="label"
                      />
                    </Form.Item>
                    <Form.Item name="tags" label="标签">
                      <Select
                        mode="tags"
                        options={tagOptions}
                        tokenSeparators={[',', '，']}
                        placeholder="输入或选择标签，最多 5 个"
                      />
                    </Form.Item>
                    <Form.Item shouldUpdate noStyle>
                      {() => (
                        <div style={{ marginBottom: 12 }}>
                          <Space wrap>
                            {(optionsQuery.data?.tags ?? []).slice(0, 12).map((tag) => (
                              <Tag
                                key={tag.id}
                                bordered={false}
                                style={{ cursor: 'pointer' }}
                                onClick={() => {
                                  const current = (confirmForm.getFieldValue('tags') as string[] | undefined) ?? [];
                                  if (current.includes(tag.name)) {
                                    return;
                                  }
                                  confirmForm.setFieldValue('tags', [...current, tag.name]);
                                }}
                              >
                                {tag.name}
                              </Tag>
                            ))}
                          </Space>
                        </div>
                      )}
                    </Form.Item>
                    <Button type="primary" htmlType="submit" icon={<CheckCircleOutlined />} loading={confirmMutation.isPending}>
                      提交待审核
                    </Button>
                  </Form>
                </Card>
              ) : null}
            </Space>
          </Card>
        ) : null}

        {!draftId ? (
          <Card className="chat-panel chat-card">
            <Space direction="vertical" size={8}>
              <Typography.Text strong>工作流</Typography.Text>
              <Typography.Text type="secondary">
                1. 上传 Markdown / PDF 或直接粘贴内容
              </Typography.Text>
              <Typography.Text type="secondary">
                2. Agent 整理出标题、分类、标签与 Markdown 草稿
              </Typography.Text>
              <Typography.Text type="secondary">
                3. 你确认后，系统生成待审核单，交给现有文档治理流程继续处理
              </Typography.Text>
            </Space>
          </Card>
        ) : null}
      </Space>
    </div>
  );
}
