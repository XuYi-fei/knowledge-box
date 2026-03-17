import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Descriptions, Form, InputNumber, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { DocumentDuplicateCleanupItem, DocumentDuplicateCleanupPreview, DocumentDuplicateKeepStrategy, KnowledgeDocument } from '../../lib/types';

type FilterFormValues = {
  visibilityType: KnowledgeDocument['visibilityType'];
  status: KnowledgeDocument['status'];
  keepStrategy: DocumentDuplicateKeepStrategy;
  limit: number;
  triggerIndexRebuild: boolean;
};

const columns: ColumnsType<DocumentDuplicateCleanupItem> = [
  {
    title: '保留文档',
    key: 'keepDocument',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>ID {record.keepDocumentId}</Typography.Text>
        <Typography.Text type="secondary">{record.keepSourceFilename}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {record.keepImportKey || '无 importKey'}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: '待删除文档',
    key: 'duplicateDocument',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>ID {record.duplicateDocumentId}</Typography.Text>
        <Typography.Text type="secondary">{record.duplicateSourceFilename}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {record.duplicateImportKey || '无 importKey'}
        </Typography.Text>
      </Space>
    ),
  },
  {
    title: '分类 / 标题',
    key: 'title',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Typography.Text>{record.title}</Typography.Text>
        <Typography.Text type="secondary">{record.categoryName || '未分类'}</Typography.Text>
      </Space>
    ),
  },
  {
    title: '关联计数',
    key: 'counts',
    render: (_, record) => (
      <Space wrap size={[4, 4]}>
        <Tag>chunk {record.chunkCount}</Tag>
        <Tag>asset {record.assetCount}</Tag>
        <Tag>tag {record.tagCount}</Tag>
        <Tag color={record.sourceReviewRefCount > 0 ? 'blue' : 'default'}>source review {record.sourceReviewRefCount}</Tag>
        <Tag color={record.publishedReviewRefCount > 0 ? 'purple' : 'default'}>published review {record.publishedReviewRefCount}</Tag>
        <Tag color={record.ingestionRefCount > 0 ? 'orange' : 'default'}>ingestion {record.ingestionRefCount}</Tag>
      </Space>
    ),
  },
  {
    title: '正文指纹',
    dataIndex: 'contentFingerprint',
    render: (value: string) => (
      <Typography.Text code style={{ fontSize: 12 }}>
        {value}
      </Typography.Text>
    ),
  },
];

export function DocumentDuplicatesPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FilterFormValues>();
  const [preview, setPreview] = useState<DocumentDuplicateCleanupPreview | null>(null);

  const previewMutation = useMutation({
    mutationFn: (values: FilterFormValues) =>
      api.previewDocumentDuplicates({
        visibilityType: values.visibilityType,
        status: values.status,
        keepStrategy: values.keepStrategy,
        limit: values.limit,
      }),
    onSuccess: (data) => {
      setPreview(data);
      if (data.previewCount === 0) {
        message.info('当前筛选条件下没有发现重复正式文档');
      } else {
        message.success(`已加载 ${data.previewCount} 条待清理重复文档`);
      }
    },
    onError: (error) => {
      modal.error({
        title: '预览失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请稍后重试')}</pre>,
      });
    },
  });

  const cleanupMutation = useMutation({
    mutationFn: (values: FilterFormValues) =>
      api.cleanupDocumentDuplicates({
        visibilityType: values.visibilityType,
        status: values.status,
        keepStrategy: values.keepStrategy,
        limit: values.limit,
        triggerIndexRebuild: values.triggerIndexRebuild,
      }),
    onSuccess: async (result, values) => {
      const rebuildSuffix = result.indexRebuildJob ? `，并已触发索引重建 ${result.indexRebuildJob.jobCode}` : '';
      message.success(`已清理 ${result.duplicateDocumentsDeleted} 条重复文档${rebuildSuffix}`);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['documents'] }),
        queryClient.invalidateQueries({ queryKey: ['latestDocumentIndexRebuild'] }),
      ]);
      previewMutation.mutate(values);
    },
    onError: (error) => {
      modal.error({
        title: '清理失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请确认当前没有索引重建任务后重试')}</pre>,
      });
    },
  });

  const previewSummary = useMemo(() => {
    if (!preview) {
      return null;
    }
    const totals = preview.items.reduce(
      (acc, item) => ({
        chunks: acc.chunks + item.chunkCount,
        assets: acc.assets + item.assetCount,
        tags: acc.tags + item.tagCount,
        sourceReviews: acc.sourceReviews + item.sourceReviewRefCount,
        publishedReviews: acc.publishedReviews + item.publishedReviewRefCount,
        ingestions: acc.ingestions + item.ingestionRefCount,
      }),
      { chunks: 0, assets: 0, tags: 0, sourceReviews: 0, publishedReviews: 0, ingestions: 0 },
    );
    return totals;
  }, [preview]);

  const loadPreview = async () => {
    const values = await form.validateFields();
    previewMutation.mutate(values);
  };

  const confirmCleanup = async () => {
    const values = await form.validateFields();
    if (!preview || preview.previewCount === 0) {
      message.warning('请先预览并确认存在重复文档');
      return;
    }
    modal.confirm({
      title: '确认执行重复文档清理？',
      content: (
        <Space direction="vertical" size={8}>
          <Typography.Text>
            将删除 <Typography.Text strong>{preview.previewCount}</Typography.Text> 条重复正式文档，并重挂它们的审核单 / ingestion 引用。
          </Typography.Text>
          <Typography.Text type="secondary">
            保留策略：{values.keepStrategy}，可见性：{values.visibilityType}，状态：{values.status}
          </Typography.Text>
          <Typography.Text type="secondary">
            {values.triggerIndexRebuild ? '清理完成后会自动触发一次索引重建。' : '清理完成后不会自动重建索引。'}
          </Typography.Text>
        </Space>
      ),
      okText: '确认清理',
      cancelText: '取消',
      onOk: async () => {
        cleanupMutation.mutate(values);
      },
    });
  };

  return (
    <div className="page-stack">
      <Typography.Title level={3}>重复文档治理</Typography.Title>
      <Alert
        type="warning"
        showIcon
        message="仅管理员可执行。该操作会修改正式文档、审核单引用和向量索引，请先执行预览再清理。"
      />

      <Card title="筛选与执行参数">
        <Form<FilterFormValues>
          form={form}
          layout="inline"
          initialValues={{
            visibilityType: 'PUBLIC',
            status: 'READY',
            keepStrategy: 'OLDEST',
            limit: 200,
            triggerIndexRebuild: true,
          }}
        >
          <Form.Item name="visibilityType" label="可见性">
            <Select
              style={{ width: 140 }}
              options={[
                { label: '公开展示', value: 'PUBLIC' },
                { label: '仅 Agent', value: 'AGENT_ONLY' },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              style={{ width: 140 }}
              options={[
                { label: 'READY', value: 'READY' },
                { label: 'FAILED', value: 'FAILED' },
                { label: 'ARCHIVED', value: 'ARCHIVED' },
              ]}
            />
          </Form.Item>
          <Form.Item name="keepStrategy" label="保留策略">
            <Select
              style={{ width: 140 }}
              options={[
                { label: '保留最早', value: 'OLDEST' },
                { label: '保留最新', value: 'NEWEST' },
              ]}
            />
          </Form.Item>
          <Form.Item name="limit" label="预览上限" rules={[{ type: 'number', min: 0, max: 1000, message: '请输入 0-1000 之间的数值' }]}>
            <InputNumber min={0} max={1000} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="triggerIndexRebuild" label="清理后重建索引" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" onClick={() => void loadPreview()} loading={previewMutation.isPending}>
                预览重复项
              </Button>
              <Button danger disabled={!preview || preview.previewCount === 0} loading={cleanupMutation.isPending} onClick={() => void confirmCleanup()}>
                执行清理
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {preview ? (
        <Card title="预览结果">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions size="small" bordered column={3}>
              <Descriptions.Item label="待清理文档数">{preview.previewCount}</Descriptions.Item>
              <Descriptions.Item label="保留策略">{preview.keepStrategy}</Descriptions.Item>
              <Descriptions.Item label="预览上限">{preview.limit === 0 ? 'ALL' : preview.limit}</Descriptions.Item>
              <Descriptions.Item label="可见性">{preview.visibilityType}</Descriptions.Item>
              <Descriptions.Item label="状态">{preview.status}</Descriptions.Item>
              <Descriptions.Item label="当前结果">仅展示符合条件的重复正式文档</Descriptions.Item>
            </Descriptions>

            {previewSummary ? (
              <Space wrap>
                <Tag color="blue">chunk {previewSummary.chunks}</Tag>
                <Tag color="cyan">asset {previewSummary.assets}</Tag>
                <Tag color="green">tag {previewSummary.tags}</Tag>
                <Tag color="purple">source review {previewSummary.sourceReviews}</Tag>
                <Tag color="magenta">published review {previewSummary.publishedReviews}</Tag>
                <Tag color="orange">ingestion {previewSummary.ingestions}</Tag>
              </Space>
            ) : null}

            <Table
              rowKey="duplicateDocumentId"
              columns={columns}
              dataSource={preview.items}
              pagination={false}
              scroll={{ x: 1280 }}
            />
          </Space>
        </Card>
      ) : null}
    </div>
  );
}
