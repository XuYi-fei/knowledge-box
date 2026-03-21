import { ApiOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Empty, Select, Space, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, buildApiUrl, buildUserAuthHeaders } from '../../lib/api';
import { clearUserAuthSession, getUserDebugLastSessionId, setUserDebugLastSessionId } from '../../lib/auth';
import { buildErrorSummary } from '../../lib/errors';
import type { AgentExecutionTraceSummary } from '../../lib/types';
import { ChatWorkspaceLayout } from './ChatWorkspaceLayout';
import { buildCitationDetailPath, useChatWorkspaceController } from './chatWorkspaceShared';

const suggestedQuestions = [
  '这个知识库系统的核心能力有哪些？',
  'Markdown 文档中的图片会如何存储与索引？',
  '为什么当前 Agent 采用 ReAct 而不是纯检索问答？',
];

export function AgentDebugPage() {
  const navigate = useNavigate();
  const { profileCode: profileCodeParam } = useParams<{ profileCode?: string }>();
  const { message } = App.useApp();
  const userQuery = useQuery({
    queryKey: ['appMe'],
    queryFn: api.currentUser,
    staleTime: 5 * 60 * 1000,
  });
  const chatOptionsQuery = useQuery({
    queryKey: ['userDebugChatOptions'],
    queryFn: api.userDebugChatOptions,
    staleTime: 5 * 60 * 1000,
  });
  const selectedEntry = chatOptionsQuery.data?.entries.find((entry) => entry.profileCode === profileCodeParam) ?? null;
  const selectedProfileCode = selectedEntry?.profileCode ?? profileCodeParam ?? null;
  const sessionsQuery = useQuery({
    queryKey: ['userDebugChatSessions', selectedProfileCode],
    queryFn: () => api.userDebugChatSessions(selectedProfileCode!),
    enabled: Boolean(selectedProfileCode),
  });

  useEffect(() => {
    const entries = chatOptionsQuery.data?.entries ?? [];
    if (!entries.length) {
      return;
    }
    if (!profileCodeParam || !entries.some((entry) => entry.profileCode === profileCodeParam)) {
      const nextEntry = entries.find((entry) => entry.canStartNewConversation) ?? entries[0];
      navigate(`/agent-debug/${nextEntry.profileCode}`, { replace: true });
    }
  }, [chatOptionsQuery.data?.entries, navigate, profileCodeParam]);

  const controller = useChatWorkspaceController({
    workspaceKey: `agent-debug:${selectedProfileCode ?? 'none'}`,
    workspaceReady: Boolean(selectedProfileCode),
    sessionsData: sessionsQuery.data,
    sessionsFetched: sessionsQuery.isFetched,
    defaultChatModel: chatOptionsQuery.data?.defaultChatModel ?? null,
    availableModels: chatOptionsQuery.data?.models ?? [],
    currentUserId: userQuery.data?.id,
    messageApi: message,
    canStartNewConversation: Boolean(selectedEntry?.canStartNewConversation),
    createBlockedMessage: '当前调试入口已下线，不能再创建新对话',
    sendBlockedMessage: '当前调试入口已下线，不能再发送新问题',
    getStoredSessionId: (userId) => (selectedProfileCode ? getUserDebugLastSessionId(userId, selectedProfileCode) : null),
    setStoredSessionId: (userId, sessionId) => {
      if (selectedProfileCode) {
        setUserDebugLastSessionId(userId, selectedProfileCode, sessionId);
      }
    },
    loadSessions: () => api.userDebugChatSessions(selectedProfileCode!),
    loadSessionDetail: (sessionId) => api.userDebugChatSessionDetail(selectedProfileCode!, sessionId),
    deleteSession: (sessionId) => api.deleteUserDebugChatSession(selectedProfileCode!, sessionId),
    stopMessage: (sessionId, messageId) => api.stopUserDebugChatMessage(selectedProfileCode!, sessionId, messageId),
    buildResumeRequest: (sessionId, messageId) => ({
      input: buildApiUrl(`/api/app/agent-debug/${selectedProfileCode}/sessions/${sessionId}/messages/${messageId}/stream`),
      init: {
        method: 'GET',
        headers: buildUserAuthHeaders({
          Accept: 'text/event-stream',
        }),
      },
    }),
    buildStartRequest: (sessionId, clientMessageId, query, chatModel) => ({
      input: buildApiUrl('/api/app/agent-debug/messages/stream'),
      init: {
        method: 'POST',
        headers: buildUserAuthHeaders({
          Accept: 'text/event-stream',
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({
          profileCode: selectedProfileCode,
          sessionId,
          clientMessageId,
          query,
          chatModel,
        }),
      },
    }),
  });
  const tracesQuery = useQuery({
    queryKey: ['userDebugChatTraces', selectedProfileCode, controller.activeSessionId],
    queryFn: () =>
      api.userDebugChatTraces(selectedProfileCode!, {
        sessionId: controller.activeSessionId ?? undefined,
        page: 1,
        pageSize: 10,
      }),
    enabled: Boolean(selectedProfileCode),
  });

  if (chatOptionsQuery.isSuccess && !(chatOptionsQuery.data?.entries.length ?? 0)) {
    return (
      <div className="chat-shell">
        <Card className="chat-panel chat-card">
          <Empty description="当前没有可调试的公开 Entry Agent。请先在管理端把目标 Agent 设为 ENTRY、PUBLISHED，并开启“调试公开”。" />
        </Card>
      </div>
    );
  }

  const traceItems = tracesQuery.data?.items ?? [];

  return (
    <ChatWorkspaceLayout
      controller={controller}
      loading={(userQuery.isLoading || sessionsQuery.isLoading) && !controller.sessions.length}
      brandSubtitle="Agent Debug Workspace"
      panelTitle={selectedEntry ? `Agent 调试 · ${selectedEntry.profileName}` : 'Agent 调试'}
      newSessionDisabled={!selectedEntry?.canStartNewConversation}
      userEmail={userQuery.data?.email}
      onLogout={() => {
        clearUserAuthSession();
        navigate('/login', { replace: true });
      }}
      onOpenCitationDetail={(citation) => navigate(buildCitationDetailPath(citation))}
      onDeleteSessionError={(error) => {
        message.error(buildErrorSummary(error, '删除会话失败，请稍后重试'));
      }}
      onStopError={(error) => {
        message.error(buildErrorSummary(error, '停止回答失败，请稍后重试'));
      }}
      sidebarTopExtra={
        <>
          <div className="chat-sidebar-section-title">
            <ApiOutlined />
            <span>调试入口</span>
          </div>
          <Select
            value={selectedProfileCode ?? undefined}
            placeholder="选择要调试的 Entry Agent"
            options={(chatOptionsQuery.data?.entries ?? []).map((entry) => ({
              label: `${entry.profileName} (${entry.profileCode})`,
              value: entry.profileCode,
            }))}
            onChange={(value) => navigate(`/agent-debug/${value}`)}
          />
          {selectedEntry ? (
            <Space wrap>
              <Tag color={selectedEntry.available ? 'green' : 'default'}>{selectedEntry.available ? '可新建对话' : '仅历史只读'}</Tag>
              {selectedEntry.hasHistory ? <Tag color="blue">有历史</Tag> : null}
            </Space>
          ) : null}
          {selectedEntry && !selectedEntry.canStartNewConversation ? (
            <Alert
              type="warning"
              showIcon
              message="当前入口已下线或不再公开"
              description="旧会话和日志仍可查看，但不能再创建新对话或继续发送问题。"
            />
          ) : null}
        </>
      }
      sidebarBottomExtra={
        <>
          <div className="chat-sidebar-section-title">
            <ApiOutlined />
            <span>最近 Trace</span>
          </div>
          <div className="chat-session-list chat-debug-trace-list">
            {traceItems.length ? (
              traceItems.map((trace: AgentExecutionTraceSummary) => (
                <div key={trace.traceId} className="chat-session-item">
                  <div className="chat-session-main" style={{ cursor: 'default' }}>
                    <ApiOutlined className="chat-session-icon" />
                    <div className="chat-session-copy">
                      <span className="chat-session-title">{trace.traceId}</span>
                      <span className="chat-session-meta">
                        {trace.status} · {trace.durationMs != null ? `${trace.durationMs}ms` : '运行中'}
                      </span>
                    </div>
                  </div>
                </div>
              ))
            ) : (
              <Typography.Text type="secondary" style={{ padding: '0 8px' }}>
                当前会话暂无 trace 记录
              </Typography.Text>
            )}
          </div>
        </>
      }
      emptyTitle="从一个调试问题开始"
      emptyDescription="这里会使用当前选中的 Entry Agent 作为入口，并把会话与日志隔离到该 Agent 下。"
      composerHint="调试会话按用户 + Entry Agent 隔离，刷新后会恢复该入口下的最近会话与未完成回答。"
      composerPlaceholder="输入你的调试问题，例如：这个入口 Agent 为什么会先调用知识库再决定是否调用子 Agent？"
      showInternalReasoning
      suggestedQuestions={suggestedQuestions}
    />
  );
}
