import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Col, Form, Input, InputNumber, Modal, Row, Space, Switch, Table, Tag, Typography, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload/interface';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import { McpServer, RuntimeEnvRequirement, SkillBinding, ToolDefinition } from '../../lib/types';

type ToolFormValues = {
  code?: string;
  name: string;
  className: string;
  beanName?: string;
  configJson: string;
  runtimeEnvRequirementsJson: string;
  enabled: boolean;
};

type McpFormValues = {
  code?: string;
  transportType: string;
  target: string;
  headersJson: string;
  queryParamsJson: string;
  runtimeEnvRequirementsJson: string;
  timeoutMs?: number;
  initializationTimeoutMs?: number;
  enabled: boolean;
};

type SkillUploadFormValues = {
  code?: string;
  name?: string;
  description?: string;
  enabled: boolean;
  replace: boolean;
  zipFileList?: UploadFile[];
};

type SkillEditFormValues = {
  name: string;
  description?: string;
  runtimeEnvRequirementsJson: string;
  enabled: boolean;
};

function summarizeJson(value: string | null | undefined) {
  if (!value) {
    return '-';
  }
  return <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 220, display: 'inline-block' }}>{value}</Typography.Text>;
}

function normalizeJsonObjectText(rawText: string | undefined, fieldLabel: string, fallback = '{}') {
  const resolved = rawText?.trim() ? rawText.trim() : fallback;
  let parsed: unknown = {};
  try {
    parsed = JSON.parse(resolved);
  } catch {
    throw new Error(`${fieldLabel} 必须是有效 JSON`);
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${fieldLabel} 必须是 JSON 对象`);
  }
  return JSON.stringify(parsed);
}

function parseStringMapJson(rawText: string | undefined, fieldLabel: string) {
  const resolved = normalizeJsonObjectText(rawText, fieldLabel);
  const parsed = JSON.parse(resolved) as Record<string, unknown>;
  return Object.entries(parsed).reduce<Record<string, string>>((acc, [key, value]) => {
    const normalizedKey = key.trim();
    if (!normalizedKey || value == null) {
      return acc;
    }
    acc[normalizedKey] = String(value);
    return acc;
  }, {});
}

function parseJsonArray<T>(rawText: string | undefined, fieldLabel: string): T[] {
  const resolved = rawText?.trim() ? rawText.trim() : '[]';
  let parsed: unknown = [];
  try {
    parsed = JSON.parse(resolved);
  } catch {
    throw new Error(`${fieldLabel} 必须是有效 JSON`);
  }
  if (!Array.isArray(parsed)) {
    throw new Error(`${fieldLabel} 必须是 JSON 数组`);
  }
  return parsed as T[];
}

function normalizeUploadFileList(event: unknown): UploadFile[] {
  if (Array.isArray(event)) {
    return event;
  }
  if (event && typeof event === 'object' && 'fileList' in event) {
    const fileList = (event as { fileList?: UploadFile[] }).fileList;
    if (Array.isArray(fileList)) {
      return fileList;
    }
  }
  return [];
}

export function IntegrationsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [toolForm] = Form.useForm<ToolFormValues>();
  const [mcpForm] = Form.useForm<McpFormValues>();
  const [skillUploadForm] = Form.useForm<SkillUploadFormValues>();
  const [skillEditForm] = Form.useForm<SkillEditFormValues>();
  const [toolModalOpen, setToolModalOpen] = useState(false);
  const [mcpModalOpen, setMcpModalOpen] = useState(false);
  const [skillUploadModalOpen, setSkillUploadModalOpen] = useState(false);
  const [skillEditModalOpen, setSkillEditModalOpen] = useState(false);
  const [editingTool, setEditingTool] = useState<ToolDefinition | null>(null);
  const [editingMcp, setEditingMcp] = useState<McpServer | null>(null);
  const [editingSkill, setEditingSkill] = useState<SkillBinding | null>(null);

  const toolsQuery = useQuery({
    queryKey: ['tools'],
    queryFn: api.tools,
  });
  const mcpQuery = useQuery({
    queryKey: ['mcpServers'],
    queryFn: api.mcpServers,
  });
  const skillsQuery = useQuery({
    queryKey: ['skills'],
    queryFn: api.skills,
  });

  const saveToolMutation = useMutation({
    mutationFn: async (values: ToolFormValues) => {
      const payload = {
        name: values.name.trim(),
        className: values.className.trim(),
        beanName: values.beanName?.trim() ? values.beanName.trim() : null,
        configJson: normalizeJsonObjectText(values.configJson, 'Tool 配置 JSON'),
        runtimeEnvRequirements: parseJsonArray<RuntimeEnvRequirement>(values.runtimeEnvRequirementsJson, 'Tool 运行时环境变量 JSON'),
        enabled: values.enabled,
      };
      if (editingTool) {
        return api.updateTool(editingTool.code, payload);
      }
      if (!values.code?.trim()) {
        throw new Error('请输入 Tool 编码');
      }
      return api.createTool({
        code: values.code.trim(),
        ...payload,
      });
    },
    onSuccess: () => {
      message.success(editingTool ? 'Tool 已更新' : 'Tool 已创建');
      setToolModalOpen(false);
      setEditingTool(null);
      toolForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['tools'] });
    },
    onError: (error) => {
      modal.error({
        title: editingTool ? '更新 Tool 失败' : '创建 Tool 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查 Tool 配置后重试')}</pre>,
      });
    },
  });

  const deleteToolMutation = useMutation({
    mutationFn: (code: string) => api.deleteTool(code),
    onSuccess: () => {
      message.success('Tool 已删除');
      void queryClient.invalidateQueries({ queryKey: ['tools'] });
    },
    onError: (error) => {
      modal.error({
        title: '删除 Tool 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请确认 Tool 未被版本绑定后重试')}</pre>,
      });
    },
  });

  const saveMcpMutation = useMutation({
    mutationFn: async (values: McpFormValues) => {
      const payload = {
        transportType: values.transportType.trim(),
        target: values.target.trim(),
        headers: parseStringMapJson(values.headersJson, 'Headers JSON'),
        queryParams: parseStringMapJson(values.queryParamsJson, 'Query Params JSON'),
        runtimeEnvRequirements: parseJsonArray<RuntimeEnvRequirement>(values.runtimeEnvRequirementsJson, 'MCP 运行时环境变量 JSON'),
        timeoutMs: values.timeoutMs ?? null,
        initializationTimeoutMs: values.initializationTimeoutMs ?? null,
        enabled: values.enabled,
      };
      if (editingMcp) {
        return api.updateMcpServer(editingMcp.code, payload);
      }
      if (!values.code?.trim()) {
        throw new Error('请输入 MCP 编码');
      }
      return api.createMcpServer({
        code: values.code.trim(),
        ...payload,
      });
    },
    onSuccess: () => {
      message.success(editingMcp ? 'MCP Server 已更新' : 'MCP Server 已创建');
      setMcpModalOpen(false);
      setEditingMcp(null);
      mcpForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['mcpServers'] });
    },
    onError: (error) => {
      modal.error({
        title: editingMcp ? '更新 MCP Server 失败' : '创建 MCP Server 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查 MCP 配置后重试')}</pre>,
      });
    },
  });

  const deleteMcpMutation = useMutation({
    mutationFn: (code: string) => api.deleteMcpServer(code),
    onSuccess: () => {
      message.success('MCP Server 已删除');
      void queryClient.invalidateQueries({ queryKey: ['mcpServers'] });
    },
    onError: (error) => {
      modal.error({
        title: '删除 MCP Server 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请确认 MCP 未被版本绑定后重试')}</pre>,
      });
    },
  });

  const uploadSkillMutation = useMutation({
    mutationFn: async (values: SkillUploadFormValues) => {
      const zipFile = values.zipFileList?.[0]?.originFileObj;
      if (!(zipFile instanceof File)) {
        throw new Error('请先上传 Skill zip 包');
      }
      return api.uploadSkill({
        code: values.code?.trim() || undefined,
        name: values.name?.trim() || undefined,
        description: values.description?.trim() || undefined,
        enabled: values.enabled,
        replace: values.replace,
        zip: zipFile,
      });
    },
    onSuccess: () => {
      message.success('Skill 已上传');
      setSkillUploadModalOpen(false);
      skillUploadForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['skills'] });
    },
    onError: (error) => {
      modal.error({
        title: '上传 Skill 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查 zip 包与元信息后重试')}</pre>,
      });
    },
  });

  const updateSkillMutation = useMutation({
    mutationFn: async (values: SkillEditFormValues) => {
      if (!editingSkill) {
        throw new Error('未选中 Skill');
      }
      return api.updateSkill(editingSkill.code, {
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        runtimeEnvRequirements: parseJsonArray<RuntimeEnvRequirement>(values.runtimeEnvRequirementsJson, 'Skill 运行时环境变量 JSON'),
        enabled: values.enabled,
      });
    },
    onSuccess: () => {
      message.success('Skill 已更新');
      setSkillEditModalOpen(false);
      setEditingSkill(null);
      skillEditForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['skills'] });
    },
    onError: (error) => {
      modal.error({
        title: '更新 Skill 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查 Skill 配置后重试')}</pre>,
      });
    },
  });

  const deleteSkillMutation = useMutation({
    mutationFn: (code: string) => api.deleteSkill(code),
    onSuccess: () => {
      message.success('Skill 已删除');
      void queryClient.invalidateQueries({ queryKey: ['skills'] });
    },
    onError: (error) => {
      modal.error({
        title: '删除 Skill 失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请确认 Skill 未被版本绑定后重试')}</pre>,
      });
    },
  });

  const toolColumns: ColumnsType<ToolDefinition> = [
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: 'Class', dataIndex: 'className', ellipsis: true },
    { title: 'Bean', dataIndex: 'beanName', render: (value: string | null) => value || '-' },
    { title: '配置(JSON)', dataIndex: 'configJson', render: (value: string) => summarizeJson(value) },
    { title: '运行时变量', render: (_, record) => summarizeJson(JSON.stringify(record.runtimeEnvRequirements ?? [])) },
    {
      title: '启用',
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已停用'}</Tag>,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setEditingTool(record);
              toolForm.setFieldsValue({
                code: record.code,
                name: record.name,
                className: record.className,
                beanName: record.beanName ?? undefined,
                configJson: record.configJson || '{}',
                runtimeEnvRequirementsJson: JSON.stringify(record.runtimeEnvRequirements ?? [], null, 2),
                enabled: record.enabled,
              });
              setToolModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            onClick={() => {
              modal.confirm({
                title: `删除 Tool：${record.code}`,
                content: '删除后将无法再被 Agent Profile 绑定。',
                okText: '删除',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => deleteToolMutation.mutateAsync(record.code),
              });
            }}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const mcpColumns: ColumnsType<McpServer> = [
    { title: '编码', dataIndex: 'code' },
    { title: '传输', dataIndex: 'transportType' },
    { title: '目标', dataIndex: 'target', ellipsis: true },
    { title: 'Headers(JSON)', render: (_, record) => summarizeJson(record.headersJson || record.headersMaskedJson) },
    { title: 'Query(JSON)', dataIndex: 'queryParamsJson', render: (value: string) => summarizeJson(value) },
    { title: '运行时变量', render: (_, record) => summarizeJson(JSON.stringify(record.runtimeEnvRequirements ?? [])) },
    { title: '超时(ms)', dataIndex: 'timeoutMs', render: (value: number | null) => value ?? '-' },
    { title: '初始化超时(ms)', dataIndex: 'initializationTimeoutMs', render: (value: number | null) => value ?? '-' },
    {
      title: '启用',
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已停用'}</Tag>,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setEditingMcp(record);
              mcpForm.setFieldsValue({
                code: record.code,
                transportType: record.transportType,
                target: record.target,
                headersJson: record.headersJson || record.headersMaskedJson || '{}',
                queryParamsJson: record.queryParamsJson || '{}',
                runtimeEnvRequirementsJson: JSON.stringify(record.runtimeEnvRequirements ?? [], null, 2),
                timeoutMs: record.timeoutMs ?? undefined,
                initializationTimeoutMs: record.initializationTimeoutMs ?? undefined,
                enabled: record.enabled,
              });
              setMcpModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            onClick={() => {
              modal.confirm({
                title: `删除 MCP Server：${record.code}`,
                content: '删除后将无法再被 Agent Profile 绑定。',
                okText: '删除',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => deleteMcpMutation.mutateAsync(record.code),
              });
            }}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const skillColumns: ColumnsType<SkillBinding> = [
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (value: string | null) => value || '-' },
    { title: '来源', dataIndex: 'sourceType', render: (value: string | null) => value || '-' },
    { title: '对象Key', dataIndex: 'ossObjectKey', ellipsis: true, render: (value: string | null) => value || '-' },
    { title: 'MD5', dataIndex: 'checksumMd5', render: (value: string | null) => value || '-' },
    { title: '运行时变量', render: (_, record) => summarizeJson(JSON.stringify(record.runtimeEnvRequirements ?? [])) },
    {
      title: '启用',
      render: (_, record) => <Tag color={record.enabled ? 'green' : 'default'}>{record.enabled ? '已启用' : '已停用'}</Tag>,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setEditingSkill(record);
              skillEditForm.setFieldsValue({
                name: record.name,
                description: record.description ?? undefined,
                runtimeEnvRequirementsJson: JSON.stringify(record.runtimeEnvRequirements ?? [], null, 2),
                enabled: record.enabled,
              });
              setSkillEditModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            onClick={() => {
              modal.confirm({
                title: `删除 Skill：${record.code}`,
                content: '删除后将无法再被 Agent Profile 绑定。',
                okText: '删除',
                okButtonProps: { danger: true },
                cancelText: '取消',
                onOk: () => deleteSkillMutation.mutateAsync(record.code),
              });
            }}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <Typography.Title level={3}>Tools / MCP / Skills</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col xs={24}>
          <Card
            title="Tool 管理"
            extra={
              <Button
                type="primary"
                onClick={() => {
                  setEditingTool(null);
                  toolForm.resetFields();
                  toolForm.setFieldsValue({ enabled: true, configJson: '{}', runtimeEnvRequirementsJson: '[]' });
                  setToolModalOpen(true);
                }}
              >
                新增 Tool
              </Button>
            }
          >
            <Table rowKey="id" columns={toolColumns} dataSource={toolsQuery.data ?? []} loading={toolsQuery.isLoading} pagination={false} />
          </Card>
        </Col>
        <Col xs={24}>
          <Card
            title="MCP Server 管理"
            extra={
              <Button
                type="primary"
                onClick={() => {
                  setEditingMcp(null);
                  mcpForm.resetFields();
                  mcpForm.setFieldsValue({
                    transportType: 'sse',
                    enabled: true,
                    headersJson: '{}',
                    queryParamsJson: '{}',
                    runtimeEnvRequirementsJson: '[]',
                  });
                  setMcpModalOpen(true);
                }}
              >
                新增 MCP Server
              </Button>
            }
          >
            <Table rowKey="id" columns={mcpColumns} dataSource={mcpQuery.data ?? []} loading={mcpQuery.isLoading} pagination={false} />
          </Card>
        </Col>
        <Col xs={24}>
          <Card
            title="Skill 管理"
            extra={
              <Button
                type="primary"
                onClick={() => {
                  skillUploadForm.resetFields();
                  skillUploadForm.setFieldsValue({
                    enabled: true,
                    replace: false,
                    zipFileList: [],
                  });
                  setSkillUploadModalOpen(true);
                }}
              >
                上传 Skill
              </Button>
            }
          >
            <Table rowKey="id" columns={skillColumns} dataSource={skillsQuery.data ?? []} loading={skillsQuery.isLoading} pagination={false} />
          </Card>
        </Col>
      </Row>

      <Modal
        open={toolModalOpen}
        title={editingTool ? `编辑 Tool：${editingTool.code}` : '新增 Tool'}
        onCancel={() => {
          setToolModalOpen(false);
          setEditingTool(null);
        }}
        onOk={() => toolForm.submit()}
        confirmLoading={saveToolMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={toolForm} onFinish={(values) => saveToolMutation.mutate(values)}>
          <Form.Item label="编码" name="code" rules={[{ required: !editingTool, message: '请输入 Tool 编码' }]}>
            <Input disabled={Boolean(editingTool)} placeholder="例如 weather-tool" />
          </Form.Item>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入 Tool 名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="Class 名称" name="className" rules={[{ required: true, message: '请输入 Class 名称' }]}>
            <Input placeholder="com.example.agent.WeatherTool" />
          </Form.Item>
          <Form.Item label="Bean 名称" name="beanName">
            <Input placeholder="可选，Spring Bean 名称" />
          </Form.Item>
          <Form.Item label="配置 JSON" name="configJson" rules={[{ required: true, message: '请输入配置 JSON' }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item label="运行时环境变量 JSON" name="runtimeEnvRequirementsJson" rules={[{ required: true, message: '请输入运行时环境变量 JSON' }]}>
            <Input.TextArea rows={4} placeholder='[{"key":"TAVILY_API_KEY","required":false,"secret":true,"description":"Optional Tavily API key"}]' />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={mcpModalOpen}
        title={editingMcp ? `编辑 MCP：${editingMcp.code}` : '新增 MCP Server'}
        onCancel={() => {
          setMcpModalOpen(false);
          setEditingMcp(null);
        }}
        onOk={() => mcpForm.submit()}
        confirmLoading={saveMcpMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={mcpForm} onFinish={(values) => saveMcpMutation.mutate(values)}>
          <Form.Item label="编码" name="code" rules={[{ required: !editingMcp, message: '请输入 MCP 编码' }]}>
            <Input disabled={Boolean(editingMcp)} placeholder="例如 search-mcp" />
          </Form.Item>
          <Form.Item label="传输类型" name="transportType" rules={[{ required: true, message: '请输入传输类型' }]}>
            <Input placeholder="例如 sse" />
          </Form.Item>
          <Form.Item label="目标地址" name="target" rules={[{ required: true, message: '请输入目标地址' }]}>
            <Input placeholder="https://example.com/mcp/sse" />
          </Form.Item>
          <Form.Item label="Headers JSON" name="headersJson" rules={[{ required: true, message: '请输入 Headers JSON' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="Query Params JSON" name="queryParamsJson" rules={[{ required: true, message: '请输入 Query Params JSON' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="运行时环境变量 JSON" name="runtimeEnvRequirementsJson" rules={[{ required: true, message: '请输入运行时环境变量 JSON' }]}>
            <Input.TextArea rows={4} placeholder='[{"key":"API_TOKEN","required":true,"secret":true,"description":"Token injected by agent runtime"}]' />
          </Form.Item>
          <Space style={{ display: 'flex' }} size={12} align="start">
            <Form.Item label="超时(ms)" name="timeoutMs">
              <InputNumber min={1} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item label="初始化超时(ms)" name="initializationTimeoutMs">
              <InputNumber min={1} style={{ width: 220 }} />
            </Form.Item>
          </Space>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={skillUploadModalOpen}
        title="上传 Skill（zip）"
        onCancel={() => setSkillUploadModalOpen(false)}
        onOk={() => skillUploadForm.submit()}
        confirmLoading={uploadSkillMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={skillUploadForm} onFinish={(values) => uploadSkillMutation.mutate(values)}>
          <Form.Item label="编码" name="code">
            <Input placeholder="可选，留空则使用技能包内名称" />
          </Form.Item>
          <Form.Item label="名称" name="name">
            <Input placeholder="可选，留空则使用技能包内名称" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="运行时环境变量 JSON" name="runtimeEnvRequirementsJson" rules={[{ required: true, message: '请输入运行时环境变量 JSON' }]}>
            <Input.TextArea rows={4} placeholder='[{"key":"TAVILY_API_KEY","required":false,"secret":true,"description":"Optional search API key"}]' />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
          <Form.Item label="覆盖已存在 Skill" name="replace" valuePropName="checked">
            <Switch checkedChildren="覆盖" unCheckedChildren="不覆盖" />
          </Form.Item>
          <Form.Item
            label="Skill zip 包"
            name="zipFileList"
            valuePropName="fileList"
            getValueFromEvent={normalizeUploadFileList}
            rules={[{ required: true, message: '请上传 zip 包' }]}
          >
            <Upload beforeUpload={() => false} maxCount={1} accept=".zip">
              <Button>选择 zip 文件</Button>
            </Upload>
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={skillEditModalOpen}
        title={editingSkill ? `编辑 Skill：${editingSkill.code}` : '编辑 Skill'}
        onCancel={() => {
          setSkillEditModalOpen(false);
          setEditingSkill(null);
        }}
        onOk={() => skillEditForm.submit()}
        confirmLoading={updateSkillMutation.isPending}
        destroyOnHidden
      >
        <Form layout="vertical" form={skillEditForm} onFinish={(values) => updateSkillMutation.mutate(values)}>
          <Form.Item label="名称" name="name" rules={[{ required: true, message: '请输入 Skill 名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
