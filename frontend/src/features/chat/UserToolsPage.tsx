import {
  CodeOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, Empty, Form, Input, Segmented, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { buildErrorSummary } from '../../lib/errors';
import type { AppToolCatalogItem, AppToolExecutionResult } from '../../lib/types';
import { MarkdownMessage } from './MarkdownMessage';

type ToolField = {
  name: string;
  label: string;
  type: 'text' | 'textarea';
  required?: boolean;
  maxLength?: number;
  placeholder?: string;
};

type ToolSchema = {
  fields: ToolField[];
};

const iconMap = {
  code: <CodeOutlined />,
  unlock: <UnlockOutlined />,
  safety: <SafetyCertificateOutlined />,
  tool: <ToolOutlined />,
} as const;

function parseToolSchema(raw: string | undefined, fallback: ToolSchema): ToolSchema {
  try {
    const parsed = raw ? (JSON.parse(raw) as unknown) : fallback;
    if (!parsed || typeof parsed !== 'object' || !('fields' in parsed) || !Array.isArray((parsed as ToolSchema).fields)) {
      return fallback;
    }
    return parsed as ToolSchema;
  } catch {
    return fallback;
  }
}

function parseDefaultValues(raw: string | undefined) {
  try {
    const parsed = raw ? (JSON.parse(raw) as unknown) : {};
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return parsed as Record<string, string>;
  } catch {
    return {};
  }
}

function utf8ToBase64(value: string) {
  return btoa(String.fromCharCode(...new TextEncoder().encode(value)));
}

function base64ToUtf8(value: string) {
  const decoded = atob(value);
  const bytes = Uint8Array.from(decoded, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function executeClientTool(tool: AppToolCatalogItem, input: Record<string, unknown>): AppToolExecutionResult {
  const text = typeof input.text === 'string' ? input.text : '';
  const startedAt = performance.now();
  let output = '';
  if (tool.handlerCode === 'base64-encode') {
    output = utf8ToBase64(text);
  } else if (tool.handlerCode === 'base64-decode') {
    output = base64ToUtf8(text);
  } else {
    throw new Error(`未实现的前端工具: ${tool.handlerCode}`);
  }
  return {
    toolCode: tool.code,
    executionMode: 'CLIENT',
    resultType: 'text',
    result: { text: output },
    resultPreview: output,
    durationMs: Math.max(0, Math.round(performance.now() - startedAt)),
    executionId: `client-${tool.code}-${Date.now()}`,
  };
}

function resolveResultText(result: AppToolExecutionResult | null) {
  if (!result) {
    return '';
  }
  if (result.result && typeof result.result === 'object' && result.result !== null && 'text' in result.result) {
    const value = (result.result as Record<string, unknown>).text;
    return typeof value === 'string' ? value : result.resultPreview;
  }
  return result.resultPreview;
}

export function UserToolsPage() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<Record<string, string>>();
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

  const selectedSchema = useMemo(
    () => parseToolSchema(selectedTool?.inputSchemaJson, { fields: [] }),
    [selectedTool],
  );

  useEffect(() => {
    if (!selectedTool) {
      form.resetFields();
      setLatestResult(null);
      return;
    }
    form.setFieldsValue(parseDefaultValues(selectedTool.defaultValuesJson));
    setLatestResult(null);
  }, [form, selectedTool]);

  const executeMutation = useMutation({
    mutationFn: async (values: Record<string, string>) => {
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
                {selectedTool.descriptionMarkdown ? (
                  <div className="user-tools-markdown">
                    <MarkdownMessage content={selectedTool.descriptionMarkdown} />
                  </div>
                ) : null}
              </div>

              <Form<Record<string, string>> form={form} layout="vertical" onFinish={handleSubmit}>
                {selectedSchema.fields.map((field) => (
                  <Form.Item
                    key={field.name}
                    label={field.label}
                    name={field.name}
                    rules={[
                      { required: Boolean(field.required), message: `请输入${field.label}` },
                      field.maxLength ? { max: field.maxLength, message: `长度不能超过 ${field.maxLength}` } : {},
                    ].filter((rule) => Object.keys(rule).length)}
                  >
                    {field.type === 'textarea' ? (
                      <Input.TextArea rows={8} placeholder={field.placeholder} showCount maxLength={field.maxLength} />
                    ) : (
                      <Input placeholder={field.placeholder} maxLength={field.maxLength} />
                    )}
                  </Form.Item>
                ))}
                <Space>
                  <Button type="primary" onClick={() => void form.submit()} loading={executeMutation.isPending}>
                    立即执行
                  </Button>
                  <Button onClick={() => form.setFieldsValue(parseDefaultValues(selectedTool.defaultValuesJson))}>恢复默认</Button>
                </Space>
              </Form>

              <Card size="small" className="user-tools-result-card" title="执行结果">
                {latestResult ? (
                  <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                    <Space wrap>
                      <Tag color="green" bordered={false}>executionId: {latestResult.executionId}</Tag>
                      <Tag bordered={false}>耗时 {latestResult.durationMs}ms</Tag>
                    </Space>
                    <Input.TextArea readOnly autoSize={{ minRows: 6, maxRows: 16 }} value={resolveResultText(latestResult)} />
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
