import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { AppToolDefinition, AppToolExecutionMode, AppToolRateLimitScope } from '../../lib/types';

type AppToolFormValues = {
  code?: string;
  name: string;
  summary: string;
  descriptionMarkdown: string;
  categoryCode: string;
  iconKey: string;
  tagsText: string;
  displayOrder?: number;
  enabled: boolean;
  executionMode: AppToolExecutionMode;
  rendererCode: string;
  handlerCode: string;
  inputSchemaJson: string;
  defaultValuesJson: string;
  resultSchemaJson: string;
  serverConfigJson: string;
  timeoutMs?: number;
  rateLimitScope: AppToolRateLimitScope;
  rateLimitMaxRequests?: number;
  rateLimitWindowSeconds?: number;
  auditEnabled: boolean;
  payloadLimitBytes?: number;
};

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

function tagsTextToList(value: string | undefined) {
  return Array.from(new Set((value ?? '').split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)));
}

function summarizeJson(value: string) {
  return <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 220, display: 'inline-block' }}>{value}</Typography.Text>;
}

const executionModeOptions: Array<{ label: string; value: AppToolExecutionMode }> = [
  { label: '前端执行', value: 'CLIENT' },
  { label: '后端执行', value: 'SERVER' },
];

const rateLimitScopeOptions: Array<{ label: string; value: AppToolRateLimitScope }> = [
  { label: '不限制', value: 'NONE' },
  { label: '按用户', value: 'USER' },
  { label: '按用户 + IP', value: 'USER_AND_IP' },
];

const rendererOptions = [{ label: 'text-workbench', value: 'text-workbench' }] as const;

const clientHandlerOptions = [
  { label: 'base64-encode', value: 'base64-encode' },
  { label: 'base64-decode', value: 'base64-decode' },
] as const;

const serverHandlerOptions = [{ label: 'md5-digest', value: 'md5-digest' }] as const;

export function AppToolsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AppToolFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTool, setEditingTool] = useState<AppToolDefinition | null>(null);
  const executionMode = Form.useWatch('executionMode', form) ?? 'CLIENT';
  const rateLimitScope = Form.useWatch('rateLimitScope', form) ?? 'NONE';
  const isServerMode = executionMode === 'SERVER';
  const activeHandlerOptions = isServerMode ? serverHandlerOptions : clientHandlerOptions;

  useEffect(() => {
    if (!modalOpen) {
      return;
    }
    if (isServerMode) {
      const currentHandlerCode = form.getFieldValue('handlerCode');
      if (!serverHandlerOptions.some((option) => option.value === currentHandlerCode)) {
        form.setFieldValue('handlerCode', serverHandlerOptions[0].value);
      }
      if (!form.getFieldValue('payloadLimitBytes')) {
        form.setFieldValue('payloadLimitBytes', 65536);
      }
      return;
    }
    const currentHandlerCode = form.getFieldValue('handlerCode');
    if (!clientHandlerOptions.some((option) => option.value === currentHandlerCode)) {
      form.setFieldValue('handlerCode', clientHandlerOptions[0].value);
    }
    form.setFieldsValue({
      timeoutMs: undefined,
      rateLimitScope: 'NONE',
      rateLimitMaxRequests: undefined,
      rateLimitWindowSeconds: undefined,
      auditEnabled: false,
    });
  }, [form, isServerMode, modalOpen]);

  const toolsQuery = useQuery({
    queryKey: ['adminAppTools'],
    queryFn: api.appTools,
  });

  const saveMutation = useMutation({
    mutationFn: async (values: AppToolFormValues) => {
      const payload = {
        name: values.name.trim(),
        summary: values.summary.trim(),
        descriptionMarkdown: values.descriptionMarkdown.trim(),
        categoryCode: values.categoryCode.trim(),
        iconKey: values.iconKey.trim(),
        tags: tagsTextToList(values.tagsText),
        displayOrder: values.displayOrder ?? 0,
        enabled: values.enabled,
        executionMode: values.executionMode,
        rendererCode: values.rendererCode.trim(),
        handlerCode: values.handlerCode.trim(),
        inputSchemaJson: normalizeJsonObjectText(values.inputSchemaJson, '输入 Schema'),
        defaultValuesJson: normalizeJsonObjectText(values.defaultValuesJson, '默认值 JSON'),
        resultSchemaJson: normalizeJsonObjectText(values.resultSchemaJson, '结果 Schema'),
        serverConfigJson: normalizeJsonObjectText(values.serverConfigJson, '服务端配置 JSON'),
        timeoutMs: values.timeoutMs ?? null,
        rateLimitScope: values.rateLimitScope,
        rateLimitMaxRequests: values.rateLimitMaxRequests ?? null,
        rateLimitWindowSeconds: values.rateLimitWindowSeconds ?? null,
        auditEnabled: values.auditEnabled,
        payloadLimitBytes: values.payloadLimitBytes ?? null,
      };
      if (editingTool) {
        return api.updateAppTool(editingTool.code, payload);
      }
      if (!values.code?.trim()) {
        throw new Error('请输入工具编码');
      }
      return api.createAppTool({ code: values.code.trim(), ...payload });
    },
    onSuccess: () => {
      message.success(editingTool ? '工具已更新' : '工具已创建');
      setModalOpen(false);
      setEditingTool(null);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['adminAppTools'] });
    },
    onError: (error) => {
      modal.error({
        title: editingTool ? '更新工具失败' : '创建工具失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查配置后重试')}</pre>,
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (code: string) => api.deleteAppTool(code),
    onSuccess: () => {
      message.success('工具已删除');
      void queryClient.invalidateQueries({ queryKey: ['adminAppTools'] });
    },
    onError: (error) => {
      modal.error({
        title: '删除工具失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请稍后重试')}</pre>,
      });
    },
  });

  const columns: ColumnsType<AppToolDefinition> = [
    { title: '名称', dataIndex: 'name', render: (_, record) => <Space direction="vertical" size={2}><Typography.Text strong>{record.name}</Typography.Text><Typography.Text type="secondary">{record.code}</Typography.Text></Space> },
    { title: '分类', dataIndex: 'categoryCode' },
    { title: '执行方式', dataIndex: 'executionMode', render: (value) => <Tag color={value === 'CLIENT' ? 'cyan' : 'gold'} bordered={false}>{value === 'CLIENT' ? '前端' : '后端'}</Tag> },
    { title: '处理器', dataIndex: 'handlerCode' },
    { title: '排序', dataIndex: 'displayOrder', width: 80 },
    { title: '限流', dataIndex: 'rateLimitScope', render: (_, record) => record.executionMode === 'SERVER' ? `${record.rateLimitScope}${record.rateLimitMaxRequests ? ` / ${record.rateLimitMaxRequests}` : ''}` : '-' },
    { title: '启用', dataIndex: 'enabled', render: (value: boolean) => <Tag color={value ? 'green' : 'default'} bordered={false}>{value ? '启用' : '停用'}</Tag> },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => {
              setEditingTool(record);
              form.setFieldsValue({
                code: record.code,
                name: record.name,
                summary: record.summary,
                descriptionMarkdown: record.descriptionMarkdown,
                categoryCode: record.categoryCode,
                iconKey: record.iconKey,
                tagsText: record.tags.join(', '),
                displayOrder: record.displayOrder,
                enabled: record.enabled,
                executionMode: record.executionMode,
                rendererCode: record.rendererCode,
                handlerCode: record.handlerCode,
                inputSchemaJson: record.inputSchemaJson,
                defaultValuesJson: record.defaultValuesJson,
                resultSchemaJson: record.resultSchemaJson,
                serverConfigJson: record.serverConfigJson,
                timeoutMs: record.timeoutMs ?? undefined,
                rateLimitScope: record.rateLimitScope,
                rateLimitMaxRequests: record.rateLimitMaxRequests ?? undefined,
                rateLimitWindowSeconds: record.rateLimitWindowSeconds ?? undefined,
                auditEnabled: record.auditEnabled,
                payloadLimitBytes: record.payloadLimitBytes ?? undefined,
              });
              setModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title={`确认删除 ${record.name}？`} onConfirm={() => deleteMutation.mutate(record.code)}>
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="page-stack">
      <Card
        title="用户工具目录"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingTool(null);
              form.resetFields();
              form.setFieldsValue({
                enabled: true,
                executionMode: 'CLIENT',
                rendererCode: 'text-workbench',
                handlerCode: 'base64-encode',
                inputSchemaJson: '{"fields":[{"name":"text","label":"输入文本","type":"textarea","required":true,"maxLength":20000}]}',
                defaultValuesJson: '{"text":""}',
                resultSchemaJson: '{"copyable":true}',
                serverConfigJson: '{}',
                rateLimitScope: 'NONE',
                auditEnabled: false,
                payloadLimitBytes: 65536,
                tagsText: '',
              });
              setModalOpen(true);
            }}
          >
            新增工具
          </Button>
        }
      >
        {toolsQuery.isError ? (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
            message="工具目录加载失败"
            description={buildErrorSummary(toolsQuery.error, '请检查后端服务或管理员登录状态后重试')}
            action={
              <Button size="small" onClick={() => void toolsQuery.refetch()}>
                重试
              </Button>
            }
          />
        ) : null}
        <Table rowKey="id" columns={columns} dataSource={toolsQuery.data ?? []} loading={toolsQuery.isLoading} pagination={false} />
      </Card>

      <Modal
        title={editingTool ? `编辑工具 · ${editingTool.name}` : '新增工具'}
        open={modalOpen}
        width={860}
        destroyOnClose
        onCancel={() => {
          if (saveMutation.isPending) {
            return;
          }
          setModalOpen(false);
          setEditingTool(null);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
      >
        <Form<AppToolFormValues> form={form} layout="vertical" preserve={false} onFinish={(values) => saveMutation.mutate(values)}>
          <div className="app-tool-form-grid">
            <Form.Item label="工具编码" name="code" rules={[{ required: !editingTool, message: '请输入工具编码' }]}> 
              <Input disabled={Boolean(editingTool)} placeholder="例如 md5-digest" />
            </Form.Item>
            <Form.Item label="工具名称" name="name" rules={[{ required: true, message: '请输入工具名称' }]}> 
              <Input />
            </Form.Item>
            <Form.Item label="分类编码" name="categoryCode" rules={[{ required: true, message: '请输入分类编码' }]}> 
              <Input placeholder="例如 encoding" />
            </Form.Item>
            <Form.Item label="图标编码" name="iconKey" rules={[{ required: true, message: '请输入图标编码' }]}> 
              <Input placeholder="例如 code / unlock / safety" />
            </Form.Item>
            <Form.Item label="执行方式" name="executionMode" rules={[{ required: true, message: '请选择执行方式' }]}> 
              <Select options={executionModeOptions} />
            </Form.Item>
            <Form.Item label="渲染器编码" name="rendererCode" rules={[{ required: true, message: '请选择渲染器编码' }]}>
              <Select options={rendererOptions.map((option) => ({ ...option }))} />
            </Form.Item>
            <Form.Item label="处理器编码" name="handlerCode" rules={[{ required: true, message: '请选择处理器编码' }]}>
              <Select options={activeHandlerOptions.map((option) => ({ ...option }))} />
            </Form.Item>
            <Form.Item label="显示顺序" name="displayOrder">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="请求大小上限(Byte)"
              name="payloadLimitBytes"
              rules={
                isServerMode
                  ? [{ required: true, message: '后端执行工具必须配置请求大小上限' }]
                  : []
              }
            >
              <InputNumber min={64} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="超时(ms)" name="timeoutMs">
              <InputNumber min={1} disabled={!isServerMode} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="限流范围" name="rateLimitScope" rules={[{ required: true, message: '请选择限流范围' }]}> 
              <Select disabled={!isServerMode} options={rateLimitScopeOptions} />
            </Form.Item>
            <Form.Item
              label="最大请求数"
              name="rateLimitMaxRequests"
              rules={
                isServerMode && rateLimitScope !== 'NONE'
                  ? [{ required: true, message: '启用限流时必须配置最大请求数' }]
                  : []
              }
            >
              <InputNumber min={1} disabled={!isServerMode || rateLimitScope === 'NONE'} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="时间窗口(秒)"
              name="rateLimitWindowSeconds"
              rules={
                isServerMode && rateLimitScope !== 'NONE'
                  ? [{ required: true, message: '启用限流时必须配置时间窗口' }]
                  : []
              }
            >
              <InputNumber min={1} disabled={!isServerMode || rateLimitScope === 'NONE'} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="标签" name="tagsText" className="app-tool-form-grid-span-2">
              <Input placeholder="使用逗号分隔，例如 编码, 文本" />
            </Form.Item>
            <Form.Item label="简介" name="summary" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入简介' }]}> 
              <Input.TextArea rows={3} />
            </Form.Item>
            <Form.Item label="说明 Markdown" name="descriptionMarkdown" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入说明 Markdown' }]}> 
              <Input.TextArea rows={5} />
            </Form.Item>
            <Form.Item label="输入 Schema JSON" name="inputSchemaJson" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入输入 Schema JSON' }]}> 
              <Input.TextArea rows={6} />
            </Form.Item>
            <Form.Item label="默认值 JSON" name="defaultValuesJson" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入默认值 JSON' }]}> 
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item label="结果 Schema JSON" name="resultSchemaJson" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入结果 Schema JSON' }]}> 
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item label="服务端配置 JSON" name="serverConfigJson" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入服务端配置 JSON' }]}> 
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item label="启用" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="记录审计日志" name="auditEnabled" valuePropName="checked">
              <Switch disabled={!isServerMode} />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
