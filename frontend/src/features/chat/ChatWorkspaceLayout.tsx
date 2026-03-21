import {
  DeleteOutlined,
  HistoryOutlined,
  LogoutOutlined,
  MailOutlined,
  MessageOutlined,
  PlusOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Button, Card, Empty, Input, List, Popconfirm, Select, Space, Spin, Typography } from 'antd';
import type { ReactNode } from 'react';
import type { ChatCitation } from '../../lib/types';
import { AssistantMessageProcessTimeline } from './AssistantMessageProcessTimeline';
import { MarkdownMessage } from './MarkdownMessage';
import { ChatWorkspaceController, dedupeReasoningSteps, SessionSummaryState } from './chatWorkspaceShared';

type ChatWorkspaceLayoutProps = {
  controller: ChatWorkspaceController;
  loading: boolean;
  brandSubtitle: string;
  panelTitle: string;
  newSessionDisabled?: boolean;
  userEmail?: string | null;
  onLogout: () => void;
  onOpenCitationDetail: (citation: ChatCitation) => void;
  onDeleteSessionError: (error: unknown) => void;
  onStopError: (error: unknown) => void;
  sidebarTopExtra?: ReactNode;
  sidebarBottomExtra?: ReactNode;
  emptyTitle: string;
  emptyDescription: string;
  composerHint: string;
  composerPlaceholder: string;
  showInternalReasoning: boolean;
  suggestedQuestions: string[];
};

export function ChatWorkspaceLayout({
  controller,
  loading,
  brandSubtitle,
  panelTitle,
  newSessionDisabled = false,
  userEmail,
  onLogout,
  onOpenCitationDetail,
  onDeleteSessionError,
  onStopError,
  sidebarTopExtra,
  sidebarBottomExtra,
  emptyTitle,
  emptyDescription,
  composerHint,
  composerPlaceholder,
  showInternalReasoning,
  suggestedQuestions,
}: ChatWorkspaceLayoutProps) {
  if (loading && !controller.sessions.length) {
    return (
      <div className="chat-shell chat-shell-loading">
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className="chat-shell">
      <div className="chat-content">
        <aside className="chat-sidebar">
          <div className="chat-sidebar-shell">
            <div className="chat-sidebar-brand">
              <span className="chat-sidebar-brand-mark">KB</span>
              <div className="chat-sidebar-brand-copy">
                <span className="chat-sidebar-brand-title">Knowledge Box</span>
                <span className="chat-sidebar-brand-subtitle">{brandSubtitle}</span>
              </div>
            </div>

            <div className="chat-sidebar-top">
              {sidebarTopExtra}

              <Button
                type="primary"
                icon={<PlusOutlined />}
                block
                className="chat-sidebar-primary"
                onClick={controller.createNewSession}
                disabled={newSessionDisabled}
              >
                新对话
              </Button>

              <div className="chat-sidebar-section-title">
                <HistoryOutlined />
                <span>历史对话</span>
              </div>

              <div className="chat-session-list">
                {controller.sessions.map((session) => (
                  <SessionListItem
                    key={session.sessionId}
                    session={session}
                    active={session.sessionId === controller.activeSessionId}
                    onSelect={() => controller.setActiveSessionId(session.sessionId)}
                    onDelete={() => {
                      void controller.deleteSession(session).catch(onDeleteSessionError);
                    }}
                  />
                ))}
              </div>
            </div>

            <div className="chat-sidebar-bottom">
              {sidebarBottomExtra}

              <div className="chat-sidebar-user">
                <Space size={8}>
                  <MailOutlined />
                  <span>{userEmail ?? '当前用户'}</span>
                </Space>
                <Button type="text" icon={<LogoutOutlined />} onClick={onLogout}>
                  退出登录
                </Button>
              </div>
            </div>
          </div>
        </aside>

        <main className="chat-main">
          <Card className="chat-panel chat-card" title={panelTitle}>
            <div className="chat-messages" ref={controller.messagesContainerRef}>
              {controller.loadingSessionId === controller.activeSessionId && !controller.activeDetail ? (
                <div className="chat-empty-state">
                  <Spin />
                </div>
              ) : controller.hasConversation && controller.activeDetail ? (
                <List
                  dataSource={controller.activeDetail.messages}
                  renderItem={(item) => (
                    <List.Item className={`chat-message-list-item chat-message-list-item-${item.role}`}>
                      <div className={`chat-message-row chat-message-row-${item.role}`}>
                        {item.role === 'assistant' ? <div className="message-avatar message-avatar-assistant">KB</div> : null}

                        <div
                          className={`message-card message-${item.role}${
                            item.role === 'assistant' && item.chatModel ? ' message-assistant-with-model' : ''
                          }`}
                        >
                          {item.role === 'assistant' && item.chatModel ? (
                            <span className="message-chip message-chip-model message-chip-model-floating">{item.chatModel}</span>
                          ) : null}

                          {item.content ? (
                            <div className="message-content">
                              <MarkdownMessage content={item.content} />
                            </div>
                          ) : item.role === 'assistant' && item.status !== 'FAILED' && item.status !== 'CANCELLED' ? (
                            <Typography.Paragraph className="message-placeholder" type="secondary">
                              正在生成回答...
                            </Typography.Paragraph>
                          ) : null}

                          {item.status === 'CANCELLED' && item.errorMessage ? (
                            <Typography.Paragraph className="message-cancelled" type="secondary">
                              {item.errorMessage}
                            </Typography.Paragraph>
                          ) : null}

                          {item.status !== 'CANCELLED' && item.errorMessage ? (
                            <Typography.Paragraph className="message-error" type="danger">
                              {item.errorMessage}
                            </Typography.Paragraph>
                          ) : null}

                          {item.role === 'assistant' ? (
                            <AssistantMessageProcessTimeline
                              messageId={item.messageId}
                              reasoningSteps={dedupeReasoningSteps(item.reasoningSteps)}
                              processDetails={item.processDetails}
                              toolCalls={item.toolCalls}
                              status={item.status}
                              content={item.content}
                              errorMessage={item.errorMessage}
                              showInternalReasoning={showInternalReasoning}
                            />
                          ) : null}

                          {item.role === 'assistant' && item.citations.length ? (
                            <div className="message-citations">
                              <div className="message-citations-label">关联资料</div>
                              <div className="message-citation-list">
                                {item.citations.map((citation, index) => (
                                  <button
                                    key={`${item.messageId}-citation-${citation.documentId}-${citation.anchor || index}`}
                                    type="button"
                                    className="message-citation-item"
                                    onClick={() => onOpenCitationDetail(citation)}
                                  >
                                    <span className="message-citation-title">{citation.documentTitle}</span>
                                    <span className="message-citation-meta">
                                      {[citation.headingPath, citation.anchor ? `#${citation.anchor}` : null].filter(Boolean).join(' · ')}
                                    </span>
                                    <span className="message-citation-snippet">{citation.snippet}</span>
                                  </button>
                                ))}
                              </div>
                            </div>
                          ) : null}
                        </div>

                        {item.role === 'user' ? <div className="message-avatar message-avatar-user">我</div> : null}
                      </div>
                    </List.Item>
                  )}
                />
              ) : (
                <div className="chat-empty-state">
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      <Space direction="vertical" size={6}>
                        <Typography.Text strong>{emptyTitle}</Typography.Text>
                        <Typography.Text type="secondary">{emptyDescription}</Typography.Text>
                      </Space>
                    }
                  >
                    <div className="chat-empty-actions">
                      {suggestedQuestions.map((question) => (
                        <Button key={question} onClick={() => void controller.sendQuery(question)}>
                          {question}
                        </Button>
                      ))}
                    </div>
                  </Empty>
                </div>
              )}
              <div className="chat-messages-end-anchor" ref={controller.messagesEndRef} />
            </div>

            <div className="chat-composer">
              <Space wrap style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
                <Space wrap>
                  <Typography.Text type="secondary">入口模型</Typography.Text>
                  <Select
                    value={controller.selectedChatModel ?? undefined}
                    style={{ minWidth: 240 }}
                    placeholder="使用后台默认模型"
                    disabled={controller.composerBusy || !controller.availableModels.length}
                    options={controller.availableModels.map((item) => ({
                      label: `${item.displayName} (${item.code})`,
                      value: item.code,
                    }))}
                    onChange={controller.updateSelectedChatModel}
                  />
                </Space>

                <Typography.Text type="secondary">{composerHint}</Typography.Text>
              </Space>

              <Input.TextArea
                value={controller.input}
                rows={4}
                placeholder={composerPlaceholder}
                disabled={controller.composerBusy}
                onChange={(event) => controller.setInput(event.target.value)}
                onCompositionStart={() => {
                  controller.isComposingRef.current = true;
                }}
                onCompositionEnd={() => {
                  controller.isComposingRef.current = false;
                }}
                onKeyDown={(event) => {
                  if (event.key !== 'Enter') {
                    return;
                  }

                  const composing =
                    controller.isComposingRef.current || event.nativeEvent.isComposing || event.keyCode === 229;
                  if (composing) {
                    return;
                  }

                  if (!event.shiftKey) {
                    event.preventDefault();
                    void controller.sendQuery(controller.input);
                  }
                }}
              />
              <div className="chat-composer-actions">
                <Button
                  danger
                  icon={<StopOutlined />}
                  disabled={!controller.activeResumableAssistant || controller.activeStopping}
                  loading={controller.activeStopping}
                  onClick={() => {
                    void controller.stopCurrentAnswer().catch(onStopError);
                  }}
                >
                  停止回答
                </Button>
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={controller.activeStreaming}
                  disabled={controller.composerBusy}
                  onClick={() => void controller.sendQuery(controller.input)}
                >
                  发送问题
                </Button>
              </div>
            </div>
          </Card>
        </main>
      </div>
    </div>
  );
}

function SessionListItem({
  session,
  active,
  onSelect,
  onDelete,
}: {
  session: SessionSummaryState;
  active: boolean;
  onSelect: () => void;
  onDelete: () => void;
}) {
  return (
    <div className={`chat-session-item ${active ? 'chat-session-item-active' : ''}`}>
      <button type="button" className="chat-session-main" onClick={onSelect}>
        <MessageOutlined className="chat-session-icon" />
        <div className="chat-session-copy">
          <span className="chat-session-title">{session.title}</span>
          <span className="chat-session-meta">{session.pending ? '回答进行中' : session.lastMessagePreview || '尚未开始对话'}</span>
        </div>
      </button>
      <Popconfirm
        title="删除这段对话？"
        description="删除后该会话历史不可恢复。"
        okText="删除"
        cancelText="取消"
        placement="rightTop"
        onConfirm={onDelete}
      >
        <Button
          type="text"
          size="small"
          className="chat-session-delete"
          icon={<DeleteOutlined />}
          aria-label={`删除${session.title}`}
          onClick={(event) => event.stopPropagation()}
        />
      </Popconfirm>
    </div>
  );
}
