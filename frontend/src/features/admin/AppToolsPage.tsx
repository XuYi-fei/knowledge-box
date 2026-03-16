import { DeleteOutlined, EditOutlined, MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Divider, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import {
  buildDefaultValuesJson,
  buildInputSchemaJson,
  buildResultSchemaJson,
  formatOptionsText,
  parseAppToolDefaultValues,
  parseAppToolInputSchema,
  parseAppToolResultSchema,
  parseOptionsText,
  type AppToolFieldSchema,
  type AppToolFieldType,
  type AppToolResultDisplayMode,
} from '../../lib/appToolSchema';
import { buildErrorSummary } from '../../lib/errors';
import type { AppToolDefinition, AppToolExecutionMode, AppToolRateLimitScope } from '../../lib/types';

type AppToolFieldFormValue = {
  name: string;
  label: string;
  type: AppToolFieldType;
  required: boolean;
  placeholder?: string;
  description?: string;
  maxLength?: number;
  rows?: number;
  min?: number;
  max?: number;
  step?: number;
  optionsText?: string;
  defaultValueText?: string;
  defaultValueNumber?: number;
  defaultValueBoolean?: boolean;
};

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
  serverConfigJson: string;
  timeoutMs?: number;
  rateLimitScope: AppToolRateLimitScope;
  rateLimitMaxRequests?: number;
  rateLimitWindowSeconds?: number;
  auditEnabled: boolean;
  payloadLimitBytes?: number;
  schemaFields: AppToolFieldFormValue[];
  resultDisplayMode: AppToolResultDisplayMode;
  resultPrimaryField?: string;
  resultCopyable: boolean;
  resultRows?: number;
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
  return <Typography.Text ellipsis={{ tooltip: value }} style={{ maxWidth: 280, display: 'inline-block' }}>{value}</Typography.Text>;
}

function buildDefaultField(type: AppToolFieldType = 'textarea'): AppToolFieldFormValue {
  return {
    name: '',
    label: '',
    type,
    required: true,
    placeholder: '',
    description: '',
    maxLength: type === 'textarea' || type === 'text' || type === 'password' ? 20000 : undefined,
    rows: type === 'textarea' ? 8 : undefined,
    min: undefined,
    max: undefined,
    step: undefined,
    optionsText: '',
    defaultValueText: '',
    defaultValueNumber: undefined,
    defaultValueBoolean: false,
  };
}

function mapSchemaFieldToFormValue(field: AppToolFieldSchema, defaults: Record<string, unknown>): AppToolFieldFormValue {
  const rawDefault = Object.prototype.hasOwnProperty.call(defaults, field.name) ? defaults[field.name] : field.defaultValue;
  return {
    name: field.name,
    label: field.label,
    type: field.type,
    required: Boolean(field.required),
    placeholder: field.placeholder,
    description: field.description,
    maxLength: field.maxLength,
    rows: field.rows,
    min: field.min,
    max: field.max,
    step: field.step,
    optionsText: formatOptionsText(field.options),
    defaultValueText: typeof rawDefault === 'string' ? rawDefault : '',
    defaultValueNumber: typeof rawDefault === 'number' ? rawDefault : undefined,
    defaultValueBoolean: typeof rawDefault === 'boolean' ? rawDefault : false,
  };
}

function toFormValues(record: AppToolDefinition): AppToolFormValues {
  const inputSchema = parseAppToolInputSchema(record.inputSchemaJson);
  const defaultValues = parseAppToolDefaultValues(record.defaultValuesJson);
  const resultSchema = parseAppToolResultSchema(record.resultSchemaJson);
  return {
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
    serverConfigJson: record.serverConfigJson,
    timeoutMs: record.timeoutMs ?? undefined,
    rateLimitScope: record.rateLimitScope,
    rateLimitMaxRequests: record.rateLimitMaxRequests ?? undefined,
    rateLimitWindowSeconds: record.rateLimitWindowSeconds ?? undefined,
    auditEnabled: record.auditEnabled,
    payloadLimitBytes: record.payloadLimitBytes ?? undefined,
    schemaFields: inputSchema.fields.length
      ? inputSchema.fields.map((field) => mapSchemaFieldToFormValue(field, defaultValues))
      : [buildDefaultField()],
    resultDisplayMode: resultSchema.displayMode ?? 'text',
    resultPrimaryField: resultSchema.primaryField,
    resultCopyable: Boolean(resultSchema.copyable),
    resultRows: resultSchema.rows ?? 8,
  };
}

function serializeSchemaFields(fields: AppToolFieldFormValue[]): AppToolFieldSchema[] {
  const normalizedFields = fields.map((field, index) => {
    const name = field.name.trim();
    const label = field.label.trim();
    if (!name) {
      throw new Error(`第 ${index + 1} 个字段缺少字段名`);
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(name)) {
      throw new Error(`字段 ${name} 仅支持字母开头，后续使用字母、数字或下划线`);
    }
    if (!label) {
      throw new Error(`字段 ${name} 缺少展示名称`);
    }
    const options = field.type === 'select' ? parseOptionsText(field.optionsText) : [];
    if (field.type === 'select' && !options.length) {
      throw new Error(`字段 ${name} 为下拉类型时必须提供至少一个选项`);
    }
    let defaultValue: string | number | boolean | null = null;
    if (field.type === 'number') {
      defaultValue = field.defaultValueNumber ?? null;
    } else if (field.type === 'switch') {
      defaultValue = Boolean(field.defaultValueBoolean);
    } else {
      defaultValue = field.defaultValueText?.trim() ? field.defaultValueText.trim() : null;
    }
    return {
      name,
      label,
      type: field.type,
      required: field.type === 'switch' ? false : Boolean(field.required),
      placeholder: field.placeholder?.trim() || undefined,
      description: field.description?.trim() || undefined,
      maxLength: field.type === 'text' || field.type === 'textarea' || field.type === 'password' ? field.maxLength ?? undefined : undefined,
      rows: field.type === 'textarea' ? field.rows ?? undefined : undefined,
      min: field.type === 'number' ? field.min ?? undefined : undefined,
      max: field.type === 'number' ? field.max ?? undefined : undefined,
      step: field.type === 'number' ? field.step ?? undefined : undefined,
      options: field.type === 'select' ? options : undefined,
      defaultValue,
    } satisfies AppToolFieldSchema;
  });
  if (!normalizedFields.length) {
    throw new Error('至少需要配置一个输入字段');
  }
  return normalizedFields;
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

const fieldTypeOptions: Array<{ label: string; value: AppToolFieldType }> = [
  { label: '单行文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '密码', value: 'password' },
  { label: '数字', value: 'number' },
  { label: '下拉选择', value: 'select' },
  { label: '开关', value: 'switch' },
];

const resultDisplayModeOptions: Array<{ label: string; value: AppToolResultDisplayMode }> = [
  { label: '文本', value: 'text' },
  { label: 'JSON', value: 'json' },
];

export function AppToolsPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<AppToolFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTool, setEditingTool] = useState<AppToolDefinition | null>(null);
  const executionMode = Form.useWatch('executionMode', form) ?? 'CLIENT';
  const rateLimitScope = Form.useWatch('rateLimitScope', form) ?? 'NONE';
  const schemaFields = Form.useWatch('schemaFields', form) ?? [];
  const resultDisplayMode = Form.useWatch('resultDisplayMode', form) ?? 'text';
  const resultPrimaryField = Form.useWatch('resultPrimaryField', form);
  const resultCopyable = Form.useWatch('resultCopyable', form) ?? false;
  const resultRows = Form.useWatch('resultRows', form) ?? 8;
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

  const generatedInputSchemaJson = useMemo(() => {
    try {
      return buildInputSchemaJson(serializeSchemaFields(schemaFields));
    } catch {
      return '{}';
    }
  }, [schemaFields]);

  const generatedDefaultValuesJson = useMemo(() => {
    try {
      return buildDefaultValuesJson(serializeSchemaFields(schemaFields));
    } catch {
      return '{}';
    }
  }, [schemaFields]);

  const generatedResultSchemaJson = useMemo(
    () =>
      buildResultSchemaJson({
        displayMode: resultDisplayMode,
        primaryField: resultPrimaryField?.trim() || undefined,
        copyable: resultCopyable,
        rows: resultRows,
      }),
    [resultCopyable, resultDisplayMode, resultPrimaryField, resultRows],
  );

  const toolsQuery = useQuery({
    queryKey: ['adminAppTools'],
    queryFn: api.appTools,
  });

  const saveMutation = useMutation({
    mutationFn: async (values: AppToolFormValues) => {
      const normalizedFields = serializeSchemaFields(values.schemaFields);
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
        inputSchemaJson: buildInputSchemaJson(normalizedFields),
        defaultValuesJson: buildDefaultValuesJson(normalizedFields),
        resultSchemaJson: buildResultSchemaJson({
          displayMode: values.resultDisplayMode,
          primaryField: values.resultPrimaryField?.trim() || undefined,
          copyable: values.resultCopyable,
          rows: values.resultRows,
        }),
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
    { title: '字段数', render: (_, record) => parseAppToolInputSchema(record.inputSchemaJson).fields.length },
    { title: '结果', render: (_, record) => summarizeJson(record.resultSchemaJson) },
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
              form.setFieldsValue(toFormValues(record));
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
                serverConfigJson: '{}',
                rateLimitScope: 'NONE',
                auditEnabled: false,
                payloadLimitBytes: 65536,
                tagsText: '',
                schemaFields: [
                  {
                    ...buildDefaultField('textarea'),
                    name: 'text',
                    label: '输入文本',
                    placeholder: '请输入要处理的文本',
                  },
                ],
                resultDisplayMode: 'text',
                resultCopyable: true,
                resultRows: 8,
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
        width={980}
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
              rules={isServerMode ? [{ required: true, message: '后端执行工具必须配置请求大小上限' }] : []}
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
              rules={isServerMode && rateLimitScope !== 'NONE' ? [{ required: true, message: '启用限流时必须配置最大请求数' }] : []}
            >
              <InputNumber min={1} disabled={!isServerMode || rateLimitScope === 'NONE'} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              label="时间窗口(秒)"
              name="rateLimitWindowSeconds"
              rules={isServerMode && rateLimitScope !== 'NONE' ? [{ required: true, message: '启用限流时必须配置时间窗口' }] : []}
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
          </div>

          <Divider orientation="left">输入字段配置</Divider>
          <Typography.Paragraph type="secondary">
            这里配置的是前端工作台如何渲染表单。新增 `SERVER` 工具时，只要后端支持对应 handler，前端会按这里的 schema 自动生成输入界面，不需要重新部署。
          </Typography.Paragraph>

          <Form.List name="schemaFields">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {fields.map((field, index) => {
                  const currentField = schemaFields[index] as AppToolFieldFormValue | undefined;
                  const currentType = currentField?.type ?? 'text';
                  return (
                    <Card
                      key={field.key}
                      size="small"
                      title={`字段 ${index + 1}`}
                      extra={
                        fields.length > 1 ? (
                          <Button type="text" danger icon={<MinusCircleOutlined />} onClick={() => remove(field.name)}>
                            删除字段
                          </Button>
                        ) : null
                      }
                    >
                      <div className="app-tool-form-grid">
                        <Form.Item name={[field.name, 'name']} label="字段名" rules={[{ required: true, message: '请输入字段名' }]}>
                          <Input placeholder="例如 text / keyword" />
                        </Form.Item>
                        <Form.Item name={[field.name, 'label']} label="展示名称" rules={[{ required: true, message: '请输入展示名称' }]}>
                          <Input placeholder="例如 输入文本" />
                        </Form.Item>
                        <Form.Item name={[field.name, 'type']} label="字段类型" rules={[{ required: true, message: '请选择字段类型' }]}>
                          <Select options={fieldTypeOptions} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'required']} label="必填" valuePropName="checked">
                          <Switch disabled={currentType === 'switch'} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'placeholder']} label="占位提示">
                          <Input />
                        </Form.Item>
                        <Form.Item name={[field.name, 'description']} label="补充说明">
                          <Input />
                        </Form.Item>

                        {currentType === 'textarea' || currentType === 'text' || currentType === 'password' ? (
                          <Form.Item name={[field.name, 'maxLength']} label="最大长度">
                            <InputNumber min={1} style={{ width: '100%' }} />
                          </Form.Item>
                        ) : null}
                        {currentType === 'textarea' ? (
                          <Form.Item name={[field.name, 'rows']} label="默认行数">
                            <InputNumber min={2} style={{ width: '100%' }} />
                          </Form.Item>
                        ) : null}
                        {currentType === 'number' ? (
                          <>
                            <Form.Item name={[field.name, 'min']} label="最小值">
                              <InputNumber style={{ width: '100%' }} />
                            </Form.Item>
                            <Form.Item name={[field.name, 'max']} label="最大值">
                              <InputNumber style={{ width: '100%' }} />
                            </Form.Item>
                            <Form.Item name={[field.name, 'step']} label="步长">
                              <InputNumber min={0.0001} style={{ width: '100%' }} />
                            </Form.Item>
                          </>
                        ) : null}
                        {currentType === 'select' ? (
                          <Form.Item name={[field.name, 'optionsText']} label="选项列表" className="app-tool-form-grid-span-2" rules={[{ required: true, message: '请输入选项列表' }]}>
                            <Input.TextArea rows={4} placeholder={"每行一个选项，格式：展示名:值\n例如：Base64编码:base64-encode"} />
                          </Form.Item>
                        ) : null}

                        {currentType === 'number' ? (
                          <Form.Item name={[field.name, 'defaultValueNumber']} label="默认值">
                            <InputNumber style={{ width: '100%' }} />
                          </Form.Item>
                        ) : currentType === 'switch' ? (
                          <Form.Item name={[field.name, 'defaultValueBoolean']} label="默认值" valuePropName="checked">
                            <Switch />
                          </Form.Item>
                        ) : (
                          <Form.Item name={[field.name, 'defaultValueText']} label="默认值">
                            <Input />
                          </Form.Item>
                        )}
                      </div>
                    </Card>
                  );
                })}
                <Button
                  type="dashed"
                  icon={<PlusOutlined />}
                  onClick={() => add(buildDefaultField())}
                  block
                >
                  新增字段
                </Button>
              </Space>
            )}
          </Form.List>

          <Divider orientation="left">结果展示配置</Divider>
          <div className="app-tool-form-grid">
            <Form.Item label="展示模式" name="resultDisplayMode" rules={[{ required: true, message: '请选择结果展示模式' }]}>
              <Select options={resultDisplayModeOptions} />
            </Form.Item>
            <Form.Item label="结果行数" name="resultRows">
              <InputNumber min={4} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="主结果字段" name="resultPrimaryField">
              <Input placeholder="文本模式下优先读取的字段，例如 digest / text" />
            </Form.Item>
            <Form.Item label="允许复制结果" name="resultCopyable" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>

          <Divider orientation="left">生成的 Schema 预览</Divider>
          <div className="app-tool-form-grid">
            <Form.Item label="输入 Schema JSON" className="app-tool-form-grid-span-2">
              <Input.TextArea readOnly rows={6} value={generatedInputSchemaJson} />
            </Form.Item>
            <Form.Item label="默认值 JSON" className="app-tool-form-grid-span-2">
              <Input.TextArea readOnly rows={4} value={generatedDefaultValuesJson} />
            </Form.Item>
            <Form.Item label="结果 Schema JSON" className="app-tool-form-grid-span-2">
              <Input.TextArea readOnly rows={4} value={generatedResultSchemaJson} />
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
