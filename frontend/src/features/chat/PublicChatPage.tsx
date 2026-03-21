import { ApiOutlined, BookOutlined, CompassOutlined } from '@ant-design/icons';
import { App, Button } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, buildApiUrl, buildUserAuthHeaders } from '../../lib/api';
import { clearUserAuthSession, getUserLastSessionId, setUserLastSessionId } from '../../lib/auth';
import { buildErrorSummary } from '../../lib/errors';
import { ChatWorkspaceLayout } from './ChatWorkspaceLayout';
import { buildCitationDetailPath, useChatWorkspaceController } from './chatWorkspaceShared';

const suggestedQuestions = [
  '这个知识库系统的核心能力有哪些？',
  'Markdown 文档中的图片会如何存储与索引？',
  '为什么当前 Agent 采用 ReAct 而不是纯检索问答？',
];

export function PublicChatPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const userQuery = useQuery({
    queryKey: ['appMe'],
    queryFn: api.currentUser,
    staleTime: 5 * 60 * 1000,
  });
  const chatOptionsQuery = useQuery({
    queryKey: ['userChatOptions'],
    queryFn: api.userChatOptions,
    staleTime: 5 * 60 * 1000,
  });
  const sessionsQuery = useQuery({
    queryKey: ['userChatSessions'],
    queryFn: api.userChatSessions,
  });

  const controller = useChatWorkspaceController({
    workspaceKey: 'public-chat',
    workspaceReady: true,
    sessionsData: sessionsQuery.data,
    sessionsFetched: sessionsQuery.isFetched,
    defaultChatModel: chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel ?? null,
    availableModels: chatOptionsQuery.data?.models ?? [],
    currentUserId: userQuery.data?.id,
    messageApi: message,
    canStartNewConversation: true,
    getStoredSessionId: getUserLastSessionId,
    setStoredSessionId: setUserLastSessionId,
    loadSessions: api.userChatSessions,
    loadSessionDetail: api.userChatSessionDetail,
    deleteSession: api.deleteUserChatSession,
    stopMessage: api.stopUserChatMessage,
    buildResumeRequest: (sessionId, messageId) => ({
      input: buildApiUrl(`/api/app/chat/sessions/${sessionId}/messages/${messageId}/stream`),
      init: {
        method: 'GET',
        headers: buildUserAuthHeaders({
          Accept: 'text/event-stream',
        }),
      },
    }),
    buildStartRequest: (sessionId, clientMessageId, query, chatModel) => ({
      input: buildApiUrl('/api/app/chat/messages/stream'),
      init: {
        method: 'POST',
        headers: buildUserAuthHeaders({
          Accept: 'text/event-stream',
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({
          sessionId,
          clientMessageId,
          query,
          chatModel,
        }),
      },
    }),
  });

  return (
    <ChatWorkspaceLayout
      controller={controller}
      loading={(userQuery.isLoading || sessionsQuery.isLoading) && !controller.sessions.length}
      brandSubtitle="Authenticated Workspace"
      panelTitle="问答会话"
      newSessionDisabled={false}
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
      sidebarBottomExtra={
        <div className="chat-sidebar-shortcuts">
          <Button icon={<CompassOutlined />} type="text" block className="chat-sidebar-shortcut">
            探索模板
          </Button>
          <Button icon={<BookOutlined />} type="text" block className="chat-sidebar-shortcut">
            文档入口
          </Button>
          <Button icon={<ApiOutlined />} type="text" block className="chat-sidebar-shortcut">
            能力扩展
          </Button>
        </div>
      }
      emptyTitle="从一个问题开始"
      emptyDescription="点击快捷卡片会立即发送消息，并在服务端创建或延续当前会话。"
      composerHint="会话按用户 + 前端随机 sessionId 绑定，刷新后可恢复历史与未完成回答。"
      composerPlaceholder="输入你的问题，例如：这个系统如何保证流式回答在刷新后继续恢复？"
      showInternalReasoning={false}
      suggestedQuestions={suggestedQuestions}
    />
  );
}
