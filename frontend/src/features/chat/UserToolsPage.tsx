import {
  CodeOutlined,
  CopyOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, Empty, Form, Input, InputNumber, Segmented, Select, Space, Spin, Switch, Tag, Typography } from 'antd';
import type { Rule } from 'antd/es/form';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import {
  buildDefaultValuesFromSchema,
  parseAppToolDefaultValues,
  parseAppToolInputSchema,
  parseAppToolResultSchema,
  type AppToolFieldSchema,
  type AppToolFormValue,
  type AppToolResultSchema,
} from '../../lib/appToolSchema';
import { buildErrorSummary } from '../../lib/errors';
import type { AppToolCatalogItem, AppToolExecutionResult } from '../../lib/types';
import { MarkdownMessage } from './MarkdownMessage';

const iconMap = {
  code: <CodeOutlined />,
  unlock: <UnlockOutlined />,
  safety: <SafetyCertificateOutlined />,
  tool: <ToolOutlined />,
} as const;

function utf8ToBase64(value: string) {
  return btoa(String.fromCharCode(...new TextEncoder().encode(value)));
}

function base64ToUtf8(value: string) {
  const decoded = atob(value);
  const bytes = Uint8Array.from(decoded, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function asString(value: AppToolFormValue) {
  return typeof value === 'string' ? value : '';
}

async function sha256Hex(value: string) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest))
    .map((item) => item.toString(16).padStart(2, '0'))
    .join('');
}

function normalizeJson(value: string, pretty: boolean) {
  const parsed = JSON.parse(value);
  return JSON.stringify(parsed, null, pretty ? 2 : 0);
}

function convertTimestamp(value: string, mode: string, unit: string) {
  if (mode === 'datetime-to-timestamp') {
    const parsedDate = new Date(value);
    if (Number.isNaN(parsedDate.getTime())) {
      throw new Error('请输入合法的日期时间文本');
    }
    const timestamp = unit === 'seconds'
      ? Math.floor(parsedDate.getTime() / 1000)
      : parsedDate.getTime();
    return {
      text: String(timestamp),
      iso: parsedDate.toISOString(),
      unit,
      mode,
    };
  }

  const rawTimestamp = Number(value);
  if (!Number.isFinite(rawTimestamp)) {
    throw new Error('请输入合法的时间戳数字');
  }
  const milliseconds = unit === 'seconds' ? rawTimestamp * 1000 : rawTimestamp;
  const parsedDate = new Date(milliseconds);
  if (Number.isNaN(parsedDate.getTime())) {
    throw new Error('时间戳无法转换为有效日期');
  }
  return {
    text: `${parsedDate.toISOString()}\n本地时间: ${parsedDate.toLocaleString('zh-CN', { hour12: false })}`,
    iso: parsedDate.toISOString(),
    local: parsedDate.toLocaleString('zh-CN', { hour12: false }),
    unit,
    mode,
  };
}

async function executeClientTool(tool: AppToolCatalogItem, input: Record<string, AppToolFormValue>): Promise<AppToolExecutionResult> {
  const text = asString(input.text);
  const value = asString(input.value);
  const startedAt = performance.now();
  let resultPayload: Record<string, unknown>;

  switch (tool.handlerCode) {
    case 'base64-encode':
      resultPayload = { text: utf8ToBase64(text), sourceText: text };
      break;
    case 'base64-decode':
      resultPayload = { text: base64ToUtf8(text), sourceText: text };
      break;
    case 'url-encode':
      resultPayload = { text: encodeURIComponent(text), sourceText: text };
      break;
    case 'url-decode':
      resultPayload = { text: decodeURIComponent(text), sourceText: text };
      break;
    case 'sha256-digest': {
      const digest = await sha256Hex(text);
      resultPayload = { text: digest, digest, sourceText: text, algorithm: 'SHA-256' };
      break;
    }
    case 'json-format':
      resultPayload = { text: normalizeJson(text, true), sourceText: text };
      break;
    case 'json-minify':
      resultPayload = { text: normalizeJson(text, false), sourceText: text };
      break;
    case 'timestamp-convert':
      resultPayload = convertTimestamp(
        value,
        asString(input.mode) || 'timestamp-to-datetime',
        asString(input.unit) || 'milliseconds',
      );
      break;
    default:
      throw new Error(`未实现的前端工具: ${tool.handlerCode}`);
  }

  const preview = typeof resultPayload.text === 'string'
    ? resultPayload.text
    : JSON.stringify(resultPayload);
  return {
    toolCode: tool.code,
    executionMode: 'CLIENT',
    resultType: 'text',
    result: resultPayload,
    resultPreview: preview,
    durationMs: Math.max(0, Math.round(performance.now() - startedAt)),
    executionId: `client-${tool.code}-${Date.now()}`,
  };
}

function buildFieldRules(field: AppToolFieldSchema): Rule[] {
  const rules: Rule[] = [];
  if (field.required) {
    rules.push({
      required: true,
      message: field.type === 'switch' ? `请确认${field.label}` : `请输入${field.label}`,
    });
  }
  if (field.maxLength && (field.type === 'text' || field.type === 'textarea' || field.type === 'password')) {
    rules.push({ max: field.maxLength, message: `长度不能超过 ${field.maxLength}` });
  }
  return rules;
}

function resolveResultDisplay(
  result: AppToolExecutionResult | null,
  resultSchema: AppToolResultSchema,
): { content: string; displayMode: 'text' | 'json'; rows: number } {
  if (!result) {
    return { content: '', displayMode: 'text', rows: resultSchema.rows ?? 8 };
  }
  if (resultSchema.displayMode === 'json') {
    return {
      content: JSON.stringify(result.result ?? {}, null, 2),
      displayMode: 'json',
      rows: resultSchema.rows ?? 12,
    };
  }
  const payload = result.result;
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    const payloadObject = payload as Record<string, unknown>;
    if (resultSchema.primaryField) {
      const primaryValue = payloadObject[resultSchema.primaryField];
      if (typeof primaryValue === 'string') {
        return { content: primaryValue, displayMode: 'text', rows: resultSchema.rows ?? 8 };
      }
      if (typeof primaryValue === 'number' || typeof primaryValue === 'boolean') {
        return { content: String(primaryValue), displayMode: 'text', rows: resultSchema.rows ?? 8 };
      }
    }
    const textValue = payloadObject.text;
    if (typeof textValue === 'string') {
      return { content: textValue, displayMode: 'text', rows: resultSchema.rows ?? 8 };
    }
  }
  return { content: result.resultPreview, displayMode: 'text', rows: resultSchema.rows ?? 8 };
}

function renderToolField(field: AppToolFieldSchema) {
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea rows={field.rows ?? 8} placeholder={field.placeholder} showCount maxLength={field.maxLength} />;
    case 'password':
      return <Input.Password placeholder={field.placeholder} maxLength={field.maxLength} />;
    case 'number':
      return <InputNumber min={field.min} max={field.max} step={field.step} placeholder={field.placeholder} style={{ width: '100%' }} />;
    case 'select':
      return <Select placeholder={field.placeholder} options={(field.options ?? []).map((option) => ({ label: option.label, value: option.value }))} />;
    case 'switch':
      return <Switch />;
    case 'text':
    default:
      return <Input placeholder={field.placeholder} maxLength={field.maxLength} />;
  }
}

export function UserToolsPage() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<Record<string, AppToolFormValue>>();
  const [searchKeyword, setSearchKeyword] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [selectedToolCode, setSelectedToolCode] = useState<string | null>(null);
  const [latestResult, setLatestResult] = useState<AppToolExecutionResult | null>(null);

  const toolsQuery = useQuery({
    queryKey: ['appToolsCatalog'],
    queryFn: api.appToolsCatalog,
    staleTime: 5 * 60 * 1000,
  });

  const tools = toolsQuery.data ?? [];

  const categoryOptions = useMemo(() => {
    const categories = Array.from(new Set(tools.map((item) => item.categoryCode).filter(Boolean)));
    return [{ label: '全部分类', value: 'ALL' }, ...categories.map((value) => ({ label: value, value }))];
  }, [tools]);

  const filteredTools = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();
    return tools.filter((tool) => {
      const matchesCategory = categoryFilter === 'ALL' || tool.categoryCode === categoryFilter;
      const matchesKeyword =
        !keyword ||
        tool.name.toLowerCase().includes(keyword) ||
        tool.summary.toLowerCase().includes(keyword) ||
        tool.tags.some((tag) => tag.toLowerCase().includes(keyword));
      return matchesCategory && matchesKeyword;
    });
  }, [categoryFilter, searchKeyword, tools]);

  useEffect(() => {
    if (!filteredTools.length) {
      setSelectedToolCode(null);
      return;
    }
    if (!selectedToolCode || !filteredTools.some((item) => item.code === selectedToolCode)) {
      setSelectedToolCode(filteredTools[0].code);
    }
  }, [filteredTools, selectedToolCode]);

  const selectedTool = useMemo(
    () => filteredTools.find((item) => item.code === selectedToolCode) ?? null,
    [filteredTools, selectedToolCode],
  );

  const selectedInputSchema = useMemo(
    () => parseAppToolInputSchema(selectedTool?.inputSchemaJson),
    [selectedTool],
  );

  const selectedDefaultValues = useMemo(
    () => buildDefaultValuesFromSchema(selectedInputSchema.fields, parseAppToolDefaultValues(selectedTool?.defaultValuesJson)),
    [selectedInputSchema.fields, selectedTool],
  );

  const selectedResultSchema = useMemo(
    () => parseAppToolResultSchema(selectedTool?.resultSchemaJson),
    [selectedTool],
  );

  const resultDisplay = useMemo(
    () => resolveResultDisplay(latestResult, selectedResultSchema),
    [latestResult, selectedResultSchema],
  );

  useEffect(() => {
    if (!selectedTool) {
      form.resetFields();
      setLatestResult(null);
      return;
    }
    form.setFieldsValue(selectedDefaultValues);
    setLatestResult(null);
  }, [form, selectedDefaultValues, selectedTool]);

  const executeMutation = useMutation({
    mutationFn: async (values: Record<string, AppToolFormValue>) => {
      if (!selectedTool) {
        throw new Error('当前未选中工具');
      }
      if (selectedTool.executionMode === 'CLIENT') {
        return executeClientTool(selectedTool, values);
      }
      return api.executeAppTool(selectedTool.code, values);
    },
    onSuccess: (result) => {
      setLatestResult(result);
      message.success(`工具执行完成，耗时 ${result.durationMs}ms`);
    },
    onError: (error) => {
      modal.error({
        title: '工具执行失败',
        content: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{buildErrorSummary(error, '请检查输入后重试')}</pre>,
      });
    },
  });

  const handleSubmit = async () => {
    const values = await form.validateFields();
    executeMutation.mutate(values);
  };

  const handleCopyResult = async () => {
    if (!resultDisplay.content) {
      return;
    }
    try {
      await navigator.clipboard.writeText(resultDisplay.content);
      message.success('结果已复制');
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  return (
    <div className="chat-shell user-tools-shell">
      <div className="user-tools-layout">
        <Card className="chat-panel user-tools-directory-card" title="工具目录">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div className="user-tools-directory-toolbar">
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="搜索工具名称、简介或标签"
                value={searchKeyword}
                onChange={(event) => setSearchKeyword(event.target.value)}
              />
              <Segmented value={categoryFilter} options={categoryOptions} onChange={(value) => setCategoryFilter(String(value))} />
            </div>

            {toolsQuery.isLoading ? (
              <div className="chat-empty-state">
                <Spin />
              </div>
            ) : toolsQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message="工具目录加载失败"
                description={buildErrorSummary(toolsQuery.error, '请检查后端服务或登录状态后重试')}
                action={
                  <Button size="small" onClick={() => void toolsQuery.refetch()}>
                    重试
                  </Button>
                }
              />
            ) : filteredTools.length ? (
              <div className="user-tools-grid">
                {filteredTools.map((tool) => {
                  const icon = iconMap[tool.iconKey as keyof typeof iconMap] ?? <ToolOutlined />;
                  return (
                    <button
                      key={tool.code}
                      type="button"
                      className={`user-tool-card ${tool.code === selectedToolCode ? 'user-tool-card-active' : ''}`}
                      onClick={() => setSelectedToolCode(tool.code)}
                    >
                      <div className="user-tool-card-icon">{icon}</div>
                      <div className="user-tool-card-copy">
                        <div className="user-tool-card-head">
                          <Typography.Text strong>{tool.name}</Typography.Text>
                          <Tag color={tool.executionMode === 'CLIENT' ? 'cyan' : 'gold'} bordered={false}>
                            {tool.executionMode === 'CLIENT' ? '前端执行' : '后端执行'}
                          </Tag>
                        </div>
                        <Typography.Paragraph className="user-tool-card-summary">{tool.summary}</Typography.Paragraph>
                        <Space size={[6, 6]} wrap>
                          {tool.tags.map((tag) => (
                            <Tag key={tag} bordered={false}>
                              {tag}
                            </Tag>
                          ))}
                        </Space>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="chat-empty-state">
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前筛选条件下没有工具。" />
              </div>
            )}
          </Space>
        </Card>

        <Card className="chat-panel user-tools-workbench-card" title={selectedTool ? selectedTool.name : '工具工作区'}>
          {selectedTool ? (
            <div className="user-tools-workbench">
              <div className="user-tools-intro">
                <Space wrap size={[8, 8]}>
                  <Tag color="geekblue" bordered={false}>{selectedTool.categoryCode}</Tag>
                  <Tag color={selectedTool.executionMode === 'CLIENT' ? 'cyan' : 'gold'} bordered={false}>
                    {selectedTool.executionMode === 'CLIENT' ? '浏览器本地执行' : '调用后端执行'}
                  </Tag>
                </Space>
                <Typography.Paragraph>{selectedTool.summary}</Typography.Paragraph>
                <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  新增或调整 `SERVER` 工具时，只要修改后台元数据和后端执行器即可，无需重部署前端；只有新增一种全新的 `CLIENT` 执行器时，才需要前端发版。
                </Typography.Paragraph>
                {selectedTool.descriptionMarkdown ? (
                  <div className="user-tools-markdown">
                    <MarkdownMessage content={selectedTool.descriptionMarkdown} />
                  </div>
                ) : null}
              </div>

              <Form<Record<string, AppToolFormValue>> form={form} layout="vertical" onFinish={handleSubmit}>
                {selectedInputSchema.fields.map((field) => (
                  <Form.Item
                    key={field.name}
                    label={field.label}
                    name={field.name}
                    valuePropName={field.type === 'switch' ? 'checked' : 'value'}
                    extra={field.description}
                    rules={buildFieldRules(field)}
                  >
                    {renderToolField(field)}
                  </Form.Item>
                ))}
                <Space>
                  <Button type="primary" onClick={() => void form.submit()} loading={executeMutation.isPending}>
                    立即执行
                  </Button>
                  <Button onClick={() => form.setFieldsValue(selectedDefaultValues)}>恢复默认</Button>
                </Space>
              </Form>

              <Card
                size="small"
                className="user-tools-result-card"
                title="执行结果"
                extra={
                  selectedResultSchema.copyable && latestResult ? (
                    <Button size="small" icon={<CopyOutlined />} onClick={() => void handleCopyResult()}>
                      复制结果
                    </Button>
                  ) : null
                }
              >
                {latestResult ? (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Space wrap>
                      <Tag color="green" bordered={false}>executionId: {latestResult.executionId}</Tag>
                      <Tag bordered={false}>耗时 {latestResult.durationMs}ms</Tag>
                      <Tag bordered={false}>{resultDisplay.displayMode === 'json' ? 'JSON 展示' : '文本展示'}</Tag>
                    </Space>
                    <Input.TextArea
                      readOnly
                      autoSize={{ minRows: resultDisplay.rows, maxRows: Math.max(resultDisplay.rows, 18) }}
                      value={resultDisplay.content}
                    />
                  </Space>
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="执行后将在这里展示结果。" />
                )}
              </Card>
            </div>
          ) : (
            <div className="chat-empty-state">
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一个工具开始使用。" />
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
