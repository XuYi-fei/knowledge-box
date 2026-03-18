import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { AgentProfileVersion, AgentProfileVersionBindings, ModelCatalog, ModelType } from '../../lib/types';

type ProfileConfigFormValues = {
  agentType: AgentProfileVersion['agentType'];
  chatModel: string;
  routingModel: string;
  embeddingModel: string;
  rerankModel?: string | null;
  temperature: number;
  retrievalTopK: number;
  reasoningBudget: number;
};

type CreateProfileFormValues = ProfileConfigFormValues & {
  profileCode: string;
  profileName: string;
  description?: string;
};

type ProfileBindingsFormValues = {
  toolCodes: string[];
  skillCodes: string[];
  childAgentVersionIds: number[];
  mcpBindings: {
    mcpCode: string;
    enableTools: string[];
    disableTools: string[];
  }[];
};

function agentTypeColor(value: AgentProfileVersion['agentType']) {
  if (value === 'MAIN') {
    return 'volcano';
  }
  if (value === 'ENTRY') {
    return 'blue';
  }
  if (value === 'ORCHESTRATOR') {
    return 'purple';
  }
  return 'gold';
}

function normalizeMcpBindingsForForm(bindings: AgentProfileVersionBindings['mcpBindings']): ProfileBindingsFormValues['mcpBindings'] {
  const normalized = new Map<string, ProfileBindingsFormValues['mcpBindings'][number]>();
  for (const binding of bindings ?? []) {
    const mcpCode = binding?.mcpCode?.trim();
    if (!mcpCode) {
      continue;
    }
    if (!normalized.has(mcpCode)) {
      normalized.set(mcpCode, {
        mcpCode,
        enableTools: binding.enableTools ?? [],
        disableTools: binding.disableTools ?? [],
      });
    }
  }
  return Array.from(normalized.values());
}

export function ProfileVersionsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [createForm] = Form.useForm<CreateProfileFormValues>();
  const [profileForm] = Form.useForm<ProfileConfigFormValues>();
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
  const [bindingsForm] = Form.useForm<ProfileBindingsFormValues>();
  const [editingProfile, setEditingProfile] = useState<AgentProfileVersion | null>(null);
  const [editingModel, setEditingModel] = useState<ModelCatalog | null>(null);
  const [bindingProfile, setBindingProfile] = useState<AgentProfileVersion | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [profileModalOpen, setProfileModalOpen] = useState(false);
  const [modelModalOpen, setModelModalOpen] = useState(false);
  const [bindingsModalOpen, setBindingsModalOpen] = useState(false);
  const selectedModelType = Form.useWatch('modelType', modelForm);

  const { data = [] } = useQuery({
    queryKey: ['profileVersions'],
    queryFn: api.profileVersions,
  });
  const { data: modelCatalogs = [] } = useQuery({
    queryKey: ['modelCatalogs'],
    queryFn: api.modelCatalogs,
  });
  const { data: tools = [] } = useQuery({
    queryKey: ['tools'],
    queryFn: api.tools,
  });
  const { data: mcpServers = [] } = useQuery({
    queryKey: ['mcpServers'],
    queryFn: api.mcpServers,
  });
  const { data: skills = [] } = useQuery({
    queryKey: ['skills'],
    queryFn: api.skills,
  });
  const { data: profileBindings, isFetching: profileBindingsLoading } = useQuery({
    queryKey: ['profileVersionBindings', bindingProfile?.id],
    queryFn: () => api.getProfileVersionBindings(bindingProfile!.id),
    enabled: bindingsModalOpen && bindingProfile != null,
  });

  useEffect(() => {
    if (!bindingsModalOpen) {
      return;
    }
    if (!profileBindings) {
      bindingsForm.setFieldsValue({
        toolCodes: [],
        skillCodes: [],
        childAgentVersionIds: [],
        mcpBindings: [],
      });
      return;
    }
    bindingsForm.setFieldsValue({
      toolCodes: profileBindings.toolCodes ?? [],
      skillCodes: profileBindings.skillCodes ?? [],
      childAgentVersionIds: (profileBindings.childAgentBindings ?? []).map((binding) => binding.profileVersionId),
      mcpBindings: normalizeMcpBindingsForForm(profileBindings.mcpBindings),
    });
  }, [bindingsModalOpen, profileBindings, bindingsForm]);

  const createProfileMutation = useMutation({
    mutationFn: (values: CreateProfileFormValues) =>
      api.createProfile({
        ...values,
        rerankModel: values.rerankModel ?? null,
        description: values.description?.trim() || undefined,
      }),
    onSuccess: () => {
      message.success('Agent 已创建');
      setCreateModalOpen(false);
      createForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['profileVersions'] });
    },
    onError: (error) => {
      modal.error({
        title: '创建 Agent 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查 Agent 编码、类型和模型配置')}</pre>,
        okText: '知道了',
      });
    },
  });

  const updateProfileMutation = useMutation({
    mutationFn: (values: ProfileConfigFormValues) => {
      if (!editingProfile) {
        throw new Error('未选中配置版本');
      }
      return api.updateProfileVersion(editingProfile.id, {
        ...values,
        rerankModel: values.rerankModel ?? null,
      });
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

  const deleteProfileMutation = useMutation({
    mutationFn: (id: number) => api.deleteProfileVersion(id),
    onSuccess: () => {
      message.success('Agent 已删除');
      queryClient.invalidateQueries({ queryKey: ['profileVersions'] });
    },
    onError: (error) => {
      modal.error({
        title: '删除 Agent 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '主入口 Agent 不允许删除')}</pre>,
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

  const saveBindingsMutation = useMutation({
    mutationFn: async (values: ProfileBindingsFormValues) => {
      if (!bindingProfile) {
        throw new Error('未选中配置版本');
      }
      return api.updateProfileVersionBindings(bindingProfile.id, {
        toolCodes: values.toolCodes ?? [],
        skillCodes: values.skillCodes ?? [],
        mcpBindings: (values.mcpBindings ?? [])
          .filter((binding) => binding && binding.mcpCode?.trim())
          .map((binding) => ({
            mcpCode: binding.mcpCode.trim(),
            enableTools: (binding.enableTools ?? []).filter((code) => Boolean(code && code.trim())),
            disableTools: (binding.disableTools ?? []).filter((code) => Boolean(code && code.trim())),
          })),
        childAgentVersionIds: values.childAgentVersionIds ?? [],
      });
    },
    onSuccess: () => {
      message.success('版本绑定已保存');
      const bindingProfileId = bindingProfile?.id;
      setBindingsModalOpen(false);
      setBindingProfile(null);
      bindingsForm.resetFields();
      if (bindingProfileId != null) {
        queryClient.invalidateQueries({ queryKey: ['profileVersionBindings', bindingProfileId] });
      }
    },
    onError: (error) => {
      modal.error({
        title: '保存绑定失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查绑定配置后重试')}</pre>,
        okText: '知道了',
      });
    },
  });

  const chatOptions = modelCatalogs.filter((item) => item.modelType === 'CHAT' && item.enabled);
  const embeddingOptions = modelCatalogs.filter((item) => item.modelType === 'EMBEDDING' && item.enabled);
  const rerankOptions = modelCatalogs.filter((item) => item.modelType === 'RERANK' && item.enabled);
  const mainProfile = data.find((item) => item.agentType === 'MAIN') ?? null;
  const defaultChatModel = modelCatalogs.find((item) => item.modelType === 'CHAT' && item.enabled && item.defaultForPublic)?.code ?? chatOptions[0]?.code ?? '';
  const defaultEmbeddingModel = embeddingOptions[0]?.code ?? '';
  const defaultRerankModel = rerankOptions[0]?.code;

  const defaultCreateValues = useMemo<CreateProfileFormValues>(() => ({
    profileCode: '',
    profileName: '',
    description: '',
    agentType: 'ENTRY',
    chatModel: defaultChatModel,
    routingModel: defaultChatModel,
    embeddingModel: defaultEmbeddingModel,
    rerankModel: defaultRerankModel,
    temperature: 0.2,
    retrievalTopK: 6,
    reasoningBudget: 1,
  }), [defaultChatModel, defaultEmbeddingModel, defaultRerankModel]);

  const toolCodeOptions = tools.map((item) => ({
    label: `${item.name} (${item.code})`,
    value: item.code,
  }));
  const skillCodeOptions = skills.map((item) => ({
    label: `${item.name} (${item.code})`,
    value: item.code,
  }));
  const mcpCodeOptions = mcpServers.map((item) => ({
    label: `${item.code} (${item.transportType})`,
    value: item.code,
  }));
  const atomicAgentOptions = data
    .filter((item) => item.agentType === 'ATOMIC' && item.id !== bindingProfile?.id)
    .map((item) => ({
      label: `${item.profileName} (${item.profileCode} v${item.versionNumber})`,
      value: item.id,
    }));

  const buildAgentTypeOptions = (currentType?: AgentProfileVersion['agentType']) => [
    {
      label: 'MAIN',
      value: 'MAIN',
      disabled: Boolean(mainProfile && currentType !== 'MAIN'),
    },
    { label: 'ENTRY', value: 'ENTRY' },
    { label: 'ORCHESTRATOR', value: 'ORCHESTRATOR' },
    { label: 'ATOMIC', value: 'ATOMIC' },
  ];

  const openCreateModal = () => {
    createForm.resetFields();
    createForm.setFieldsValue(defaultCreateValues);
    setCreateModalOpen(true);
  };

  const profileColumns: ColumnsType<AgentProfileVersion> = [
    { title: 'Profile', dataIndex: 'profileCode' },
    { title: '名称', dataIndex: 'profileName' },
    { title: '版本', dataIndex: 'versionNumber' },
    {
      title: '类型',
      dataIndex: 'agentType',
      render: (value: AgentProfileVersion['agentType']) => <Tag color={agentTypeColor(value)}>{value}</Tag>,
    },
    { title: '聊天模型', dataIndex: 'chatModel' },
    {
      title: '路由模型',
      dataIndex: 'routingModel',
      render: (value: AgentProfileVersion['routingModel']) => value ?? '-',
    },
    { title: 'Embedding', dataIndex: 'embeddingModel' },
    {
      title: '状态',
      render: (_, record) => <Tag color={record.published ? 'green' : 'gold'}>{record.status}</Tag>,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space size="small" wrap>
          <Button
            type="link"
            onClick={() => {
              setEditingProfile(record);
              profileForm.setFieldsValue({
                agentType: record.agentType,
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
          <Button
            type="link"
            onClick={() => {
              bindingsForm.resetFields();
              setBindingProfile(record);
              setBindingsModalOpen(true);
              bindingsForm.setFieldsValue({
                toolCodes: [],
                skillCodes: [],
                childAgentVersionIds: [],
                mcpBindings: [],
              });
            }}
          >
            绑定管理
          </Button>
          <Button
            type="link"
            danger
            disabled={record.agentType === 'MAIN' || deleteProfileMutation.isPending}
            onClick={() => {
              modal.confirm({
                title: `删除 Agent ${record.profileCode}`,
                content: '会删除该 Agent 的整个 Profile、全部版本和绑定关系。MAIN 主入口 Agent 不允许删除。',
                okText: '确认删除',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => deleteProfileMutation.mutateAsync(record.id),
              });
            }}
          >
            删除 Agent
          </Button>
        </Space>
      ),
    },
  ];

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
        title="Agent 配置"
        extra={
          <Space size={12} wrap>
            <Typography.Text type="secondary">
              公开对话只会命中唯一的 `MAIN` 已发布版本；新增 Agent 默认用于内部编排或子 Agent 能力。
            </Typography.Text>
            <Button type="primary" onClick={openCreateModal} disabled={!defaultChatModel || !defaultEmbeddingModel}>
              新增 Agent
            </Button>
          </Space>
        }
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
        open={createModalOpen}
        title="新增 Agent"
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
        onOk={() => createForm.submit()}
        confirmLoading={createProfileMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={createForm} onFinish={(values) => createProfileMutation.mutate(values)}>
          <Form.Item label="Agent 编码" name="profileCode" rules={[{ required: true, message: '请输入 Agent 编码' }]} extra="仅支持小写字母、数字、-、_。">
            <Input placeholder="例如 spring-router" />
          </Form.Item>
          <Form.Item label="Agent 名称" name="profileName" rules={[{ required: true, message: '请输入 Agent 名称' }]}>
            <Input placeholder="例如 Spring Router" />
          </Form.Item>
          <Form.Item label="说明" name="description">
            <Input.TextArea rows={3} placeholder="可选，用于说明这个 Agent 的职责" />
          </Form.Item>
          <Form.Item label="Agent 类型" name="agentType" rules={[{ required: true, message: '请选择 Agent 类型' }]}>
            <Select options={buildAgentTypeOptions()} />
          </Form.Item>
          <Form.Item label="聊天模型" name="chatModel" rules={[{ required: true, message: '请选择聊天模型' }]}>
            <Select options={chatOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="路由模型" name="routingModel" rules={[{ required: true, message: '请选择路由模型' }]} extra="用于规则未命中时的轻量路由判定模型。">
            <Select options={chatOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="Embedding 模型" name="embeddingModel" rules={[{ required: true, message: '请选择 embedding 模型' }]}>
            <Select options={embeddingOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="Rerank 模型" name="rerankModel">
            <Select allowClear options={rerankOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
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
        <Form layout="vertical" form={profileForm} onFinish={(values) => updateProfileMutation.mutate(values)}>
          <Form.Item label="Agent 类型" name="agentType" rules={[{ required: true, message: '请选择 Agent 类型' }]}>
            <Select options={buildAgentTypeOptions(editingProfile?.agentType)} />
          </Form.Item>
          <Form.Item label="聊天模型" name="chatModel" rules={[{ required: true, message: '请选择聊天模型' }]}>
            <Select options={chatOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="路由模型" name="routingModel" rules={[{ required: true, message: '请选择路由模型' }]} extra="用于规则未命中时的轻量路由判定模型，建议选择低成本聊天模型。">
            <Select options={chatOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="Embedding 模型" name="embeddingModel" rules={[{ required: true, message: '请选择 embedding 模型' }]}>
            <Select options={embeddingOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
          </Form.Item>
          <Form.Item label="Rerank 模型" name="rerankModel">
            <Select allowClear options={rerankOptions.map((item) => ({ label: `${item.displayName} (${item.code})`, value: item.code }))} />
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
        open={bindingsModalOpen}
        title={bindingProfile ? `绑定管理 · ${bindingProfile.profileCode} v${bindingProfile.versionNumber}` : '绑定管理'}
        onCancel={() => {
          setBindingsModalOpen(false);
          setBindingProfile(null);
          bindingsForm.resetFields();
        }}
        onOk={() => bindingsForm.submit()}
        confirmLoading={saveBindingsMutation.isPending || profileBindingsLoading}
        width={1000}
        destroyOnHidden
      >
        <Form
          layout="vertical"
          form={bindingsForm}
          onFinish={(values) => saveBindingsMutation.mutate(values)}
          initialValues={{ toolCodes: [], skillCodes: [], childAgentVersionIds: [], mcpBindings: [] }}
        >
          <Form.Item label="工具绑定" name="toolCodes" extra="该版本默认可用的 Tool 编码集合。">
            <Select mode="multiple" allowClear showSearch options={toolCodeOptions} placeholder="选择 toolCodes" />
          </Form.Item>
          <Form.Item label="技能绑定" name="skillCodes" extra="该版本默认可用的 Skill 编码集合。">
            <Select mode="multiple" allowClear showSearch options={skillCodeOptions} placeholder="选择 skillCodes" />
          </Form.Item>
          <Form.Item
            label="子 Agent 绑定"
            name="childAgentVersionIds"
            extra={bindingProfile?.agentType === 'ATOMIC' ? 'ATOMIC 版本不能绑定子 Agent。' : 'MAIN / ENTRY / ORCHESTRATOR 仅允许绑定 ATOMIC 版本。'}
          >
            <Select
              mode="multiple"
              allowClear
              showSearch
              disabled={bindingProfile?.agentType === 'ATOMIC'}
              options={atomicAgentOptions}
              placeholder="选择可调用的原子 Agent 版本"
            />
          </Form.Item>
          <Form.List name="mcpBindings">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Typography.Text strong>MCP 绑定（mcpCode + enableTools + disableTools）</Typography.Text>
                {fields.map((field) => (
                  <Card key={field.key} size="small">
                    <Space direction="vertical" size={8} style={{ width: '100%' }}>
                      <Form.Item
                        {...field}
                        label="MCP 编码"
                        name={[field.name, 'mcpCode']}
                        rules={[{ required: true, message: '请选择 MCP 编码' }]}
                      >
                        <Select showSearch options={mcpCodeOptions} placeholder="选择 mcpCode" />
                      </Form.Item>
                      <Form.Item {...field} label="启用 Tool 列表" name={[field.name, 'enableTools']}>
                        <Select mode="multiple" allowClear showSearch options={toolCodeOptions} placeholder="选择 enableTools" />
                      </Form.Item>
                      <Form.Item {...field} label="禁用 Tool 列表" name={[field.name, 'disableTools']}>
                        <Select mode="multiple" allowClear showSearch options={toolCodeOptions} placeholder="选择 disableTools" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>
                        删除此 MCP 绑定
                      </Button>
                    </Space>
                  </Card>
                ))}
                <Button type="dashed" onClick={() => add({ mcpCode: '', enableTools: [], disableTools: [] })}>
                  新增 MCP 绑定
                </Button>
              </Space>
            )}
          </Form.List>
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
