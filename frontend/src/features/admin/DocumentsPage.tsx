import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Col, Form, Input, Modal, Progress, Row, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { DocumentIndexRebuildJob, KnowledgeDocument } from '../../lib/types';
import { MarkdownWorkbench } from './components/MarkdownWorkbench';

type CreateFormValues = {
  title: string;
  sourceFilename: string;
  visibilityType: KnowledgeDocument['visibilityType'];
  sourceMarkdown: string;
  extensionJson: string;
};

type EditFormValues = {
  title: string;
  sourceFilename: string;
  visibilityType: KnowledgeDocument['visibilityType'];
  sourceMarkdown: string;
  extensionJson: string;
};

const documentColumns: ColumnsType<KnowledgeDocument> = [
  { title: '标题', dataIndex: 'title' },
  {
    title: '可见性',
    dataIndex: 'visibilityType',
    render: (visibilityType: KnowledgeDocument['visibilityType']) => (
      <Tag color={visibilityType === 'PUBLIC' ? 'green' : 'orange'}>{visibilityType === 'PUBLIC' ? '公开展示' : '仅Agent'}</Tag>
    ),
  },
  {
    title: '状态',
    dataIndex: 'status',
    render: (status: KnowledgeDocument['status']) => <Tag color={status === 'READY' ? 'green' : 'blue'}>{status}</Tag>,
  },
  { title: '分类', dataIndex: 'categoryName', render: (value: string | null) => value || '-' },
  { title: '上传人ID', dataIndex: 'uploaderUserId', render: (value: number | null) => value ?? '-' },
  { title: '更新时间', dataIndex: 'updatedAt' },
];

export function DocumentsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [createForm] = Form.useForm<CreateFormValues>();
  const [editForm] = Form.useForm<EditFormValues>();
  const [editingDocumentId, setEditingDocumentId] = useState<number | null>(null);
  const [viewingDocumentId, setViewingDocumentId] = useState<number | null>(null);

  const { data: documents = [] } = useQuery({
    queryKey: ['documents'],
    queryFn: api.documents,
  });

  const { data: latestIndexRebuild } = useQuery({
    queryKey: ['latestDocumentIndexRebuild'],
    queryFn: api.latestDocumentIndexRebuild,
    refetchInterval: (query) => {
      const data = query.state.data as DocumentIndexRebuildJob | null | undefined;
      return data?.status === 'RUNNING' ? 2500 : false;
    },
  });

  const { data: editingDocument } = useQuery({
    queryKey: ['documentDetail', editingDocumentId],
    queryFn: () => api.documentDetail(editingDocumentId!),
    enabled: editingDocumentId != null,
  });

  const { data: viewingDocument } = useQuery({
    queryKey: ['documentDetail', viewingDocumentId],
    queryFn: () => api.documentDetail(viewingDocumentId!),
    enabled: viewingDocumentId != null,
  });

  const uploadPasteImage = async (file: File) => {
    const result = await api.uploadDocumentInlineImage(file);
    return result.url;
  };

  const createMutation = useMutation({
    mutationFn: async () => {
      const values = await createForm.validateFields();
      return api.createDocumentReview({
        title: values.title.trim(),
        sourceFilename: values.sourceFilename.trim(),
        visibilityType: values.visibilityType ?? 'PUBLIC',
        sourceMarkdown: values.sourceMarkdown,
        extensionJson: values.extensionJson,
      });
    },
    onSuccess: (result) => {
      const suffix = result.reviewRequestCode ? `，审核单 ${result.reviewRequestCode}` : '';
      message.success(`已创建新文档审核请求：${result.title}${suffix}`);
      createForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
    },
    onError: (error) => {
      modal.error({
        title: '创建失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查文档和图片资源后重试')}</pre>,
        okText: '知道了',
      });
    },
  });

  const updateSourceMutation = useMutation({
    mutationFn: async () => {
      if (!editingDocumentId) {
        throw new Error('未选择文档');
      }
      const values = await editForm.validateFields();
      return api.updateDocumentSource(editingDocumentId, {
        title: values.title.trim(),
        sourceFilename: values.sourceFilename.trim(),
        visibilityType: values.visibilityType,
        sourceMarkdown: values.sourceMarkdown,
        extensionJson: values.extensionJson,
      });
    },
    onSuccess: (result) => {
      message.success(`已提交编辑审核请求：${result.requestCode}`);
      setEditingDocumentId(null);
      void queryClient.invalidateQueries({ queryKey: ['documents'] });
      void queryClient.invalidateQueries({ queryKey: ['documentReviews'] });
    },
    onError: (error) => {
      modal.error({
        title: '提交失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查文档内容后重试')}</pre>,
      });
    },
  });

  const rebuildMutation = useMutation({
    mutationFn: api.triggerDocumentIndexRebuild,
    onSuccess: (result) => {
      message.success(`已触发索引重建：${result.jobCode}`);
      void queryClient.invalidateQueries({ queryKey: ['latestDocumentIndexRebuild'] });
    },
    onError: (error) => {
      modal.error({
        title: '触发失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '索引重建任务触发失败')}</pre>,
      });
    },
  });

  useEffect(() => {
    if (!editingDocument) {
      return;
    }
    editForm.setFieldsValue({
      title: editingDocument.title,
      sourceFilename: editingDocument.sourceFilename,
      visibilityType: editingDocument.visibilityType,
      sourceMarkdown: editingDocument.sourceMarkdown,
      extensionJson: editingDocument.extensionJson || '{}'
    });
  }, [editingDocument, editForm]);

  const tableColumns: ColumnsType<KnowledgeDocument> = [
    ...documentColumns,
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => setViewingDocumentId(record.id)}>
            查看
          </Button>
          <Button size="small" onClick={() => setEditingDocumentId(record.id)}>
            编辑
          </Button>
        </Space>
      ),
    },
  ];

  const isRebuildRunning = latestIndexRebuild?.status === 'RUNNING';

  return (
    <div className="page-stack">
      <Typography.Title level={3}>知识文档</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card title="索引重建">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div>
                <Typography.Text>最新任务状态：</Typography.Text>{' '}
                {latestIndexRebuild ? (
                  <Tag color={latestIndexRebuild.status === 'RUNNING' ? 'processing' : latestIndexRebuild.status === 'SUCCEEDED' ? 'green' : 'red'}>
                    {latestIndexRebuild.status}
                  </Tag>
                ) : (
                  <Tag>暂无任务</Tag>
                )}
              </div>
              {latestIndexRebuild ? (
                <div>
                  <Typography.Text type="secondary">
                    jobCode: {latestIndexRebuild.jobCode} | startedAt: {latestIndexRebuild.startedAt}
                  </Typography.Text>
                  {latestIndexRebuild.status === 'RUNNING' ? <Progress percent={60} status="active" showInfo={false} style={{ marginTop: 8 }} /> : null}
                  {latestIndexRebuild.status === 'FAILED' && latestIndexRebuild.errorMessage ? (
                    <Alert
                      type="error"
                      showIcon
                      style={{ marginTop: 8 }}
                      message="重建失败"
                      description={latestIndexRebuild.errorMessage}
                    />
                  ) : null}
                </div>
              ) : null}
              <Button
                type="primary"
                onClick={() => rebuildMutation.mutate()}
                loading={rebuildMutation.isPending}
                disabled={isRebuildRunning}
              >
                重建索引
              </Button>
            </Space>
          </Card>
        </Col>
        <Col xs={24}>
          <Card title="新建文档（填写）">
            <Alert
              type="info"
              showIcon
              message="支持直接填写 Markdown；编辑区可粘贴截图，系统会自动上传图片并插入链接。"
              style={{ marginBottom: 16 }}
            />
            <Form
              form={createForm}
              layout="vertical"
              initialValues={{
                title: '',
                sourceFilename: 'new-document.md',
                visibilityType: 'PUBLIC',
                sourceMarkdown: '',
                extensionJson: '{}',
              }}
            >
              <Form.Item label="文档标题" name="title" rules={[{ required: true, message: '请输入标题' }]}>
                <Input placeholder="例如：产品接入指引" />
              </Form.Item>
              <Form.Item label="源文件名" name="sourceFilename" rules={[{ required: true, message: '请输入源文件名' }]}>
                <Input placeholder="例如：product-guide.md" />
              </Form.Item>
              <Form.Item label="可见性" name="visibilityType">
                <Select
                  options={[
                    { label: '公开展示', value: 'PUBLIC' },
                    { label: '仅供 Agent 读取', value: 'AGENT_ONLY' },
                  ]}
                />
              </Form.Item>
              <Form.Item name="sourceMarkdown" label="源 Markdown" rules={[{ required: true, message: '请输入 Markdown 内容' }]}>
                <MarkdownWorkbench rows={20} onPasteImage={uploadPasteImage} />
              </Form.Item>
              <Form.Item name="extensionJson" label="扩展字段(JSON)">
                <Input.TextArea rows={3} />
              </Form.Item>
              <Button type="primary" loading={createMutation.isPending} onClick={() => createMutation.mutate()}>
                提交审核
              </Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24}>
          <Card title="文档列表">
            <Table rowKey="id" columns={tableColumns} dataSource={documents} pagination={false} />
          </Card>
        </Col>
      </Row>

      <Modal
        title={viewingDocument ? `查看文档 · ${viewingDocument.title}` : '查看文档'}
        open={viewingDocumentId != null}
        onCancel={() => setViewingDocumentId(null)}
        footer={null}
        width={1200}
      >
        {viewingDocument ? (
          <Space direction="vertical" size={14} style={{ width: '100%' }}>
            <Space wrap>
              <Tag color={viewingDocument.visibilityType === 'PUBLIC' ? 'green' : 'orange'}>
                {viewingDocument.visibilityType === 'PUBLIC' ? '公开展示' : '仅Agent'}
              </Tag>
              <Tag>{viewingDocument.sourceFilename}</Tag>
              <Tag>{viewingDocument.status}</Tag>
            </Space>
            <MarkdownWorkbench value={viewingDocument.sourceMarkdown} readOnly rows={22} />
          </Space>
        ) : (
          <Typography.Text type="secondary">加载中...</Typography.Text>
        )}
      </Modal>

      <Modal
        title="编辑源文档（提交审核）"
        open={editingDocumentId != null}
        onCancel={() => setEditingDocumentId(null)}
        onOk={() => updateSourceMutation.mutate()}
        okButtonProps={{ loading: updateSourceMutation.isPending }}
        width={1200}
      >
        <Form form={editForm} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="sourceFilename" label="源文件名" rules={[{ required: true, message: '请输入文件名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="visibilityType" label="可见性" rules={[{ required: true, message: '请选择可见性' }]}>
            <Select
              options={[
                { label: '公开展示', value: 'PUBLIC' },
                { label: '仅供 Agent 读取', value: 'AGENT_ONLY' },
              ]}
            />
          </Form.Item>
          <Form.Item name="sourceMarkdown" label="源Markdown" rules={[{ required: true, message: '请输入源文档内容' }]}>
            <MarkdownWorkbench rows={20} onPasteImage={uploadPasteImage} />
          </Form.Item>
          <Form.Item name="extensionJson" label="扩展字段(JSON)">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
