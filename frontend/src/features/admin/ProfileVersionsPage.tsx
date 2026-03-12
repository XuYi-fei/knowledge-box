import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { AgentProfileVersion, ModelCatalog, ModelType } from '../../lib/types';

const columns: ColumnsType<AgentProfileVersion> = [
  { title: 'Profile', dataIndex: 'profileCode' },
  { title: '版本', dataIndex: 'versionNumber' },
  { title: '聊天模型', dataIndex: 'chatModel' },
  {
    title: '路由模型',
    dataIndex: 'routingModel',
    render: (value: AgentProfileVersion['routingModel']) => value ?? '-',
  },
  { title: 'Embedding', dataIndex: 'embeddingModel' },
  { title: 'Rerank', dataIndex: 'rerankModel' },
  { title: 'Temperature', dataIndex: 'temperature' },
  { title: 'TopK', dataIndex: 'retrievalTopK' },
  { title: '思考级别', dataIndex: 'reasoningBudget' },
  {
    title: '状态',
    render: (_, record) => <Tag color={record.published ? 'green' : 'gold'}>{record.status}</Tag>,
  },
  {
    title: '操作',
    render: () => null,
  },
];

export function ProfileVersionsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [profileForm] = Form.useForm<{
    chatModel: string;
    routingModel: string;
    embeddingModel: string;
    rerankModel?: string | null;
    temperature: number;
    retrievalTopK: number;
    reasoningBudget: number;
  }>();
  const [modelForm] = Form.useForm<{
    code: string;
    displayName: string;
    provider: string;
    modelType: ModelType;
    description?: string;
    enabled: boolean;
    publicSelectable: boolean;
    defaultForPublic: boolean;
  }>();
  const [editingProfile, setEditingProfile] = useState<AgentProfileVersion | null>(null);
  const [editingModel, setEditingModel] = useState<ModelCatalog | null>(null);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [modelModalOpen, setModelModalOpen] = useState(false);
  const selectedModelType = Form.useWatch('modelType', modelForm);

  const { data = [] } = useQuery({
    queryKey: ['profileVersions'],
    queryFn: api.profileVersions,
  });
  const { data: modelCatalogs = [] } = useQuery({
    queryKey: ['modelCatalogs'],
    queryFn: api.modelCatalogs,
  });

  const updateProfileMutation = useMutation({
    mutationFn: (values: {
      chatModel: string;
      routingModel: string;
      embeddingModel: string;
      rerankModel?: string | null;
      temperature: number;
      retrievalTopK: number;
      reasoningBudget: number;
    }) => {
      if (!editingProfile) {
        throw new Error('未选中配置版本');
      }
      return api.updateProfileVersion(editingProfile.id, values);
    },
    onSuccess: () => {
      message.success('Agent 版本已更新');
      setProfileModalOpen(false);
      setEditingProfile(null);
      queryClient.invalidateQueries({ queryKey: ['profileVersions'] });
    },
    onError: (error) => {
      modal.error({
        title: '更新 Agent 版本失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查模型配置是否有效')}</pre>,
        okText: '知道了',
      });
    },
  });

  const saveModelMutation = useMutation({
    mutationFn: (values: {
      code: string;
      displayName: string;
      provider: string;
      modelType: ModelType;
      description?: string;
      enabled: boolean;
      publicSelectable: boolean;
      defaultForPublic: boolean;
    }) => {
      const normalizedValues = values.modelType === 'CHAT'
        ? values
        : {
            ...values,
            publicSelectable: false,
            defaultForPublic: false,
          };

      if (editingModel) {
        return api.updateModelCatalog(editingModel.id, {
          displayName: normalizedValues.displayName,
          provider: normalizedValues.provider,
          description: normalizedValues.description,
          enabled: normalizedValues.enabled,
          publicSelectable: normalizedValues.publicSelectable,
          defaultForPublic: normalizedValues.defaultForPublic,
        });
      }
      return api.createModelCatalog(normalizedValues);
    },
    onSuccess: () => {
      message.success(editingModel ? '模型目录已更新' : '模型已创建');
      setModelModalOpen(false);
      setEditingModel(null);
      queryClient.invalidateQueries({ queryKey: ['modelCatalogs'] });
    },
    onError: (error) => {
      modal.error({
        title: '保存模型失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查模型目录配置后重试')}</pre>,
        okText: '知道了',
      });
    },
  });

  const chatOptions = modelCatalogs.filter((item) => item.modelType === 'CHAT' && item.enabled);
  const embeddingOptions = modelCatalogs.filter((item) => item.modelType === 'EMBEDDING' && item.enabled);
  const rerankOptions = modelCatalogs.filter((item) => item.modelType === 'RERANK' && item.enabled);

  const profileColumns: ColumnsType<AgentProfileVersion> = columns.map((column) => {
    if (column.title !== '操作') {
      return column;
    }
    return {
      ...column,
      render: (_, record) => (
        <Button
          type="link"
          onClick={() => {
            setEditingProfile(record);
            profileForm.setFieldsValue({
              chatModel: record.chatModel,
              routingModel: record.routingModel ?? record.chatModel,
              embeddingModel: record.embeddingModel,
              rerankModel: record.rerankModel ?? undefined,
              temperature: record.temperature,
              retrievalTopK: record.retrievalTopK,
              reasoningBudget: record.reasoningBudget,
            });
            setProfileModalOpen(true);
          }}
        >
          编辑
        </Button>
      ),
    };
  });

  const modelColumns: ColumnsType<ModelCatalog> = [
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'displayName' },
    { title: 'Provider', dataIndex: 'provider' },
    {
      title: '类型',
      render: (_, record) => <Tag color={record.modelType === 'CHAT' ? 'blue' : record.modelType === 'EMBEDDING' ? 'cyan' : 'purple'}>{record.modelType}</Tag>,
    },
    {
      title: '启用',
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已停用'}</Tag>,
    },
    {
      title: '公开可选',
      render: (_, record) => <Tag color={record.publicSelectable ? 'geekblue' : 'default'}>{record.publicSelectable ? '可选' : '隐藏'}</Tag>,
    },
    {
      title: '公开默认',
      render: (_, record) => <Tag color={record.defaultForPublic ? 'gold' : 'default'}>{record.defaultForPublic ? '默认' : '否'}</Tag>,
    },
    { title: '说明', dataIndex: 'description', ellipsis: true },
    {
      title: '操作',
      render: (_, record) => (
        <Button
          type="link"
          onClick={() => {
            setEditingModel(record);
            modelForm.setFieldsValue({
              code: record.code,
              displayName: record.displayName,
              provider: record.provider,
              modelType: record.modelType,
              description: record.description ?? undefined,
              enabled: record.enabled,
              publicSelectable: record.publicSelectable,
              defaultForPublic: record.defaultForPublic,
            });
            setModelModalOpen(true);
          }}
        >
          编辑
        </Button>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <Typography.Title level={3}>Agent 配置与模型目录</Typography.Title>
      <Card
        title="版本配置"
        extra={<Typography.Text type="secondary">当前问答实际使用已发布版本中的聊天模型，例如默认是 `qwen-max`。</Typography.Text>}
      >
        <Table rowKey="id" columns={profileColumns} dataSource={data} pagination={false} />
      </Card>
      <Card
        title="可用模型目录"
        extra={
          <Button
            type="primary"
            onClick={() => {
              setEditingModel(null);
              modelForm.resetFields();
              modelForm.setFieldsValue({
                enabled: true,
                provider: 'dashscope',
                modelType: 'CHAT',
                publicSelectable: true,
                defaultForPublic: false,
              });
              setModelModalOpen(true);
            }}
          >
            新增模型
          </Button>
        }
      >
        <Table rowKey="id" columns={modelColumns} dataSource={modelCatalogs} pagination={false} />
      </Card>

      <Modal
        open={profileModalOpen}
        title={editingProfile ? `编辑 ${editingProfile.profileCode} v${editingProfile.versionNumber}` : '编辑 Agent 版本'}
        onCancel={() => {
          setProfileModalOpen(false);
          setEditingProfile(null);
        }}
        onOk={() => profileForm.submit()}
        confirmLoading={updateProfileMutation.isPending}
        destroyOnHidden
      >
        <Form
          layout="vertical"
          form={profileForm}
          onFinish={(values) =>
            updateProfileMutation.mutate({
              ...values,
              rerankModel: values.rerankModel ?? null,
            })
          }
        >
          <Form.Item label="聊天模型" name="chatModel" rules={[{ required: true, message: '请选择聊天模型' }]}>
            <Select
              options={chatOptions.map((item) => ({
                label: `${item.displayName} (${item.code})`,
                value: item.code,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="路由模型"
            name="routingModel"
            rules={[{ required: true, message: '请选择路由模型' }]}
            extra="用于规则未命中时的轻量路由判定模型，建议选择低成本聊天模型。"
          >
            <Select
              options={chatOptions.map((item) => ({
                label: `${item.displayName} (${item.code})`,
                value: item.code,
              }))}
            />
          </Form.Item>
          <Form.Item label="Embedding 模型" name="embeddingModel" rules={[{ required: true, message: '请选择 embedding 模型' }]}>
            <Select
              options={embeddingOptions.map((item) => ({
                label: `${item.displayName} (${item.code})`,
                value: item.code,
              }))}
            />
          </Form.Item>
          <Form.Item label="Rerank 模型" name="rerankModel">
            <Select
              allowClear
              options={rerankOptions.map((item) => ({
                label: `${item.displayName} (${item.code})`,
                value: item.code,
              }))}
            />
          </Form.Item>
          <Space style={{ display: 'flex' }} size={12} align="start">
            <Form.Item label="Temperature" name="temperature" rules={[{ required: true }]}>
              <InputNumber min={0} max={2} step={0.1} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item label="TopK" name="retrievalTopK" rules={[{ required: true }]}>
              <InputNumber min={1} max={20} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item label="思考级别" name="reasoningBudget" rules={[{ required: true }]}>
              <InputNumber min={0} max={32} style={{ width: 140 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        open={modelModalOpen}
        title={editingModel ? `编辑模型 ${editingModel.code}` : '新增模型'}
        onCancel={() => {
          setModelModalOpen(false);
          setEditingModel(null);
        }}
        onOk={() => modelForm.submit()}
        confirmLoading={saveModelMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={modelForm} onFinish={(values) => saveModelMutation.mutate(values)}>
          <Form.Item label="模型编码" name="code" rules={[{ required: true, message: '请输入模型编码' }]}>
            <Input disabled={Boolean(editingModel)} placeholder="例如 qwen-max" />
          </Form.Item>
          <Form.Item label="显示名称" name="displayName" rules={[{ required: true, message: '请输入显示名称' }]}>
            <Input placeholder="例如 Qwen Max" />
          </Form.Item>
          <Space style={{ display: 'flex' }} size={12} align="start">
            <Form.Item label="Provider" name="provider" rules={[{ required: true }]}>
              <Input placeholder="dashscope" />
            </Form.Item>
            <Form.Item label="模型类型" name="modelType" rules={[{ required: true }]}>
              <Select
                disabled={Boolean(editingModel)}
                style={{ width: 180 }}
                options={[
                  { label: 'Chat', value: 'CHAT' },
                  { label: 'Embedding', value: 'EMBEDDING' },
                  { label: 'Rerank', value: 'RERANK' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item label="说明" name="description">
            <Input.TextArea rows={4} placeholder="可选，用于说明模型用途或成本层级" />
          </Form.Item>
          <Form.Item label="启用状态" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item
            label="对公开访客可选"
            name="publicSelectable"
            valuePropName="checked"
            extra="只有聊天模型会出现在公开问答页的模型选择器里。"
          >
            <Switch disabled={selectedModelType !== 'CHAT'} checkedChildren="公开" unCheckedChildren="隐藏" />
          </Form.Item>
          <Form.Item
            label="公开默认模型"
            name="defaultForPublic"
            valuePropName="checked"
            extra="访客首次进入页面时默认选中该模型。"
          >
            <Switch disabled={selectedModelType !== 'CHAT'} checkedChildren="默认" unCheckedChildren="否" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
