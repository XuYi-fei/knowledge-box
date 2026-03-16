import {
  ApiOutlined,
  BookOutlined,
  BulbOutlined,
  CompassOutlined,
  DeleteOutlined,
  HistoryOutlined,
  LogoutOutlined,
  MailOutlined,
  MessageOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { App, Button, Card, Collapse, Empty, Input, List, Popconfirm, Select, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, buildApiUrl, buildUserAuthHeaders } from '../../lib/api';
import { clearUserAuthSession, getUserLastSessionId, setUserLastSessionId } from '../../lib/auth';
import { buildErrorSummary } from '../../lib/errors';
import { streamJsonSse } from '../../lib/sse';
import type {
  ChatCitation,
  ChatMessageStatus,
  ChatStreamEvent,
  UserChatMessage,
  UserChatSessionDetail,
  UserChatSessionSummary,
} from '../../lib/types';
import { MarkdownMessage } from './MarkdownMessage';

type SessionSummaryState = UserChatSessionSummary & { localOnly?: boolean };

type NormalizedStreamEvent = {
  type: string;
  sessionId: string | null;
  messageId: string | null;
  content: string | null;
  reasoningSteps: string[] | null;
  citations: ChatCitation[] | null;
  toolCalls: string[] | null;
  status: ChatMessageStatus | null;
  chatModel: string | null;
  errorMessage: string | null;
  // For unknown event types, we may still show a lightweight hint in reasoning area.
  extraReasoningStep: string | null;
};

const suggestedQuestions = [
  '这个知识库系统的核心能力有哪些？',
  'Markdown 文档中的图片会如何存储与索引？',
  '为什么当前 Agent 采用 ReAct 而不是纯检索问答？',
];

function buildSessionTitle(query: string) {
  return query.length > 22 ? `${query.slice(0, 22)}...` : query;
}

function summarizeContent(content: string) {
  const compact = content.replace(/\s+/g, ' ').trim();
  if (!compact) {
    return '等待回答中';
  }
  return compact.length > 38 ? `${compact.slice(0, 38)}...` : compact;
}

function hasPendingAssistant(messages: UserChatMessage[]) {
  return messages.some(
    (message) => message.role === 'assistant' && (message.status === 'PENDING' || message.status === 'STREAMING'),
  );
}

function findResumableAssistant(messages: UserChatMessage[]) {
  return [...messages]
    .reverse()
    .find((message) => message.role === 'assistant' && (message.status === 'PENDING' || message.status === 'STREAMING'));
}

function sortSessions(sessions: SessionSummaryState[]) {
  return [...sessions].sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime());
}

function createDraftSession(selectedChatModel?: string | null) {
  const sessionId = crypto.randomUUID();
  const timestamp = new Date().toISOString();
  const summary: SessionSummaryState = {
    sessionId,
    title: '新对话',
    selectedChatModel: selectedChatModel ?? null,
    messageCount: 0,
    lastMessagePreview: '',
    pending: false,
    updatedAt: timestamp,
    localOnly: true,
  };
  const detail: UserChatSessionDetail = {
    sessionId,
    title: '新对话',
    selectedChatModel: selectedChatModel ?? null,
    messages: [],
  };
  return { summary, detail };
}

function buildSessionSummary(detail: UserChatSessionDetail, localOnly = false): SessionSummaryState {
  const lastMessage = detail.messages[detail.messages.length - 1];
  return {
    sessionId: detail.sessionId,
    title: detail.title,
    selectedChatModel: detail.selectedChatModel,
    messageCount: detail.messages.length,
    lastMessagePreview: lastMessage ? summarizeContent(lastMessage.content || lastMessage.errorMessage || '') : '',
    pending: hasPendingAssistant(detail.messages),
    updatedAt: new Date().toISOString(),
    localOnly,
  };
}

function createOptimisticUserMessage(query: string, clientMessageId: string): UserChatMessage {
  const timestamp = new Date().toISOString();
  return {
    messageId: `local-user-${clientMessageId}`,
    clientMessageId,
    role: 'user',
    content: query,
    status: 'COMPLETED',
    reasoningSteps: [],
    citations: [],
    toolCalls: [],
    chatModel: null,
    errorMessage: null,
    createdAt: timestamp,
    completedAt: timestamp,
  };
}

function createOptimisticAssistantMessage(messageId: string, chatModel?: string | null): UserChatMessage {
  return {
    messageId,
    clientMessageId: null,
    role: 'assistant',
    content: '',
    status: 'PENDING',
    reasoningSteps: ['已接收问题，正在创建回答流'],
    citations: [],
    toolCalls: [],
    chatModel: chatModel ?? null,
    errorMessage: null,
    createdAt: new Date().toISOString(),
    completedAt: null,
  };
}

function sameStringArray(left: string[], right: string[]) {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((value, index) => value === right[index]);
}

function sameCitations(left: ChatCitation[], right: ChatCitation[]) {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((value, index) => {
    const candidate = right[index];
    return (
      value.documentId === candidate.documentId &&
      value.documentTitle === candidate.documentTitle &&
      value.headingPath === candidate.headingPath &&
      value.anchor === candidate.anchor &&
      value.snippet === candidate.snippet
    );
  });
}

function summarizeCitationValues(values: string[], fallback = '') {
  const normalized = Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)));
  if (!normalized.length) {
    return fallback;
  }
  if (normalized.length === 1) {
    return normalized[0];
  }
  const preview = normalized.slice(0, 2).join(' / ');
  return normalized.length > 2 ? `${preview} 等${normalized.length}处` : preview;
}

function mergeCitationsByDocument(citations: ChatCitation[]) {
  const grouped = new Map<number, ChatCitation[]>();
  for (const citation of citations) {
    const existing = grouped.get(citation.documentId);
    if (existing) {
      existing.push(citation);
      continue;
    }
    grouped.set(citation.documentId, [citation]);
  }
  return Array.from(grouped.values()).map((group) => {
    const first = group[0];
    return {
      documentId: first.documentId,
      documentTitle: first.documentTitle,
      headingPath: summarizeCitationValues(group.map((item) => item.headingPath), '未分节'),
      anchor: group.find((item) => item.anchor?.trim())?.anchor ?? first.anchor,
      snippet: summarizeCitationValues(group.map((item) => item.snippet)),
    } satisfies ChatCitation;
  });
}

function normalizeMessageCitations(messages: UserChatMessage[]) {
  return messages.map((message) =>
    message.citations.length
      ? {
          ...message,
          citations: mergeCitationsByDocument(message.citations),
        }
      : message,
  );
}

function sameAssistantMessage(left: UserChatMessage, right: UserChatMessage) {
  return (
    left.messageId === right.messageId &&
    left.content === right.content &&
    left.status === right.status &&
    left.chatModel === right.chatModel &&
    left.errorMessage === right.errorMessage &&
    left.completedAt === right.completedAt &&
    sameStringArray(left.reasoningSteps, right.reasoningSteps) &&
    sameStringArray(left.toolCalls, right.toolCalls) &&
    sameCitations(left.citations, right.citations)
  );
}

function dedupeReasoningSteps(steps: string[]) {
  if (steps.length <= 1) {
    return steps;
  }
  const merged: string[] = [];
  for (const step of steps) {
    if (!step) {
      continue;
    }
    if (!merged.length || merged[merged.length - 1] !== step) {
      merged.push(step);
    }
  }
  return merged;
}

function normalizeStreamEvent(eventName: string, raw: ChatStreamEvent): NormalizedStreamEvent {
  const resolvedType =
    (typeof raw.type === 'string' && raw.type.trim()) ||
    (eventName && eventName !== 'message' ? eventName : '') ||
    'delta';

  const fullContent =
    typeof raw.fullContent === 'string'
      ? raw.fullContent
      : typeof (raw as { content?: unknown }).content === 'string'
        ? ((raw as { content: string }).content as string)
        : null;

  const statusFromType: ChatMessageStatus | null =
    resolvedType === 'done' ? 'COMPLETED' : resolvedType === 'error' ? 'FAILED' : null;

  const delta = typeof raw.delta === 'string' ? raw.delta : null;
  const trimmedDelta = delta?.trim() ?? '';

  const extraReasoningStep =
    resolvedType !== 'delta' &&
    resolvedType !== 'thinking' &&
    resolvedType !== 'snapshot' &&
    resolvedType !== 'done' &&
    resolvedType !== 'error' &&
    trimmedDelta &&
    trimmedDelta.length <= 240
      ? `【${resolvedType}】${trimmedDelta}`
      : null;

  return {
    type: resolvedType,
    sessionId: typeof raw.sessionId === 'string' ? raw.sessionId : null,
    messageId: typeof raw.messageId === 'string' ? raw.messageId : null,
    content: fullContent,
    reasoningSteps: Array.isArray(raw.reasoningSteps) ? raw.reasoningSteps.filter((item) => typeof item === 'string') : null,
    citations: Array.isArray(raw.citations) ? mergeCitationsByDocument(raw.citations as ChatCitation[]) : null,
    toolCalls: Array.isArray(raw.toolCalls) ? raw.toolCalls.filter((item) => typeof item === 'string') : null,
    status: raw.status ?? statusFromType,
    chatModel: raw.chatModel ?? null,
    errorMessage: raw.errorMessage ?? null,
    extraReasoningStep,
  };
}

function compactReasoningLabel(steps: string[]) {
  const latest = steps[steps.length - 1]?.trim();
  if (!latest) {
    return '思考摘要';
  }
  const compact = latest.replace(/\s+/g, ' ');
  const preview = compact.length > 44 ? `${compact.slice(0, 44)}...` : compact;
  return `思考摘要 · ${preview}`;
}

function buildCitationDetailPath(citation: ChatCitation) {
  const params = new URLSearchParams();
  if (citation.headingPath) {
    params.set('headingPath', citation.headingPath);
  }
  if (citation.anchor) {
    params.set('anchor', citation.anchor);
  }
  if (citation.snippet) {
    params.set('snippet', citation.snippet);
  }
  const search = params.toString();
  return `/documents/${citation.documentId}${search ? `?${search}` : ''}`;
}

function scheduleScrollToBottom(
  messagesContainerRef: { current: HTMLDivElement | null },
  messagesEndRef: { current: HTMLDivElement | null },
  scrollRafRef: { current: number | null },
  behavior: ScrollBehavior = 'auto',
) {
  if (scrollRafRef.current !== null) {
    cancelAnimationFrame(scrollRafRef.current);
  }

  scrollRafRef.current = requestAnimationFrame(() => {
    scrollRafRef.current = null;
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ block: 'end', behavior });
      return;
    }
    if (messagesContainerRef.current) {
      messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
    }
  });
}

export function PublicChatPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [input, setInput] = useState('');
  const [sessions, setSessions] = useState<SessionSummaryState[]>([]);
  const [sessionDetails, setSessionDetails] = useState<Record<string, UserChatSessionDetail>>({});
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [loadingSessionId, setLoadingSessionId] = useState<string | null>(null);
  const [streamingSessions, setStreamingSessions] = useState<Record<string, boolean>>({});
  const streamControllersRef = useRef(new Map<string, AbortController>());
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const scrollRafRef = useRef<number | null>(null);
  const isComposingRef = useRef(false);

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

  useEffect(() => {
    return () => {
      for (const controller of streamControllersRef.current.values()) {
        controller.abort();
      }
      streamControllersRef.current.clear();
      if (scrollRafRef.current !== null) {
        cancelAnimationFrame(scrollRafRef.current);
        scrollRafRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!sessionsQuery.data) {
      return;
    }

    setSessions((current) => {
      const remoteSessions = sessionsQuery.data.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const localDrafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...localDrafts]);
    });
  }, [sessionsQuery.data]);

  useEffect(() => {
    const user = userQuery.data;
    if (!user) {
      return;
    }
    if (!sessionsQuery.isFetched) {
      return;
    }

    if (!sessions.length) {
      if ((sessionsQuery.data?.length ?? 0) > 0) {
        // Wait until the sessions state is synchronized from remote query data.
        return;
      }
      if (!activeSessionId) {
        const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel);
        setSessions([draft.summary]);
        setSessionDetails({ [draft.detail.sessionId]: draft.detail });
        setActiveSessionId(draft.detail.sessionId);
        setUserLastSessionId(user.id, draft.detail.sessionId);
      }
      return;
    }

    const sessionIds = new Set(sessions.map((session) => session.sessionId));
    if (activeSessionId && sessionIds.has(activeSessionId)) {
      setUserLastSessionId(user.id, activeSessionId);
      return;
    }

    const preferredSessionId = getUserLastSessionId(user.id);
    const nextSessionId =
      (preferredSessionId && sessionIds.has(preferredSessionId) ? preferredSessionId : sessions[0]?.sessionId) ?? null;
    if (nextSessionId) {
      setActiveSessionId(nextSessionId);
      setUserLastSessionId(user.id, nextSessionId);
    }
  }, [activeSessionId, chatOptionsQuery.data, sessions, sessionsQuery.data, sessionsQuery.isFetched, userQuery.data]);

  function upsertSessionSummary(summary: SessionSummaryState) {
    setSessions((current) => sortSessions([summary, ...current.filter((item) => item.sessionId !== summary.sessionId)]));
  }

  function updateSessionDetail(sessionId: string, updater: (detail: UserChatSessionDetail) => UserChatSessionDetail) {
    let nextDetail: UserChatSessionDetail | null = null;
    setSessionDetails((current) => {
      const existing =
        current[sessionId] ??
        ({
          sessionId,
          title: '新对话',
          selectedChatModel: chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel ?? null,
          messages: [],
        } satisfies UserChatSessionDetail);
      nextDetail = updater(existing);
      return {
        ...current,
        [sessionId]: nextDetail,
      };
    });

    if (nextDetail) {
      const existingSummary = sessions.find((session) => session.sessionId === sessionId);
      upsertSessionSummary({
        ...buildSessionSummary(nextDetail, existingSummary?.localOnly ?? false),
        localOnly: existingSummary?.localOnly ?? false,
      });
    }
  }

  async function reloadSessions() {
    const latestSessions = await api.userChatSessions();
    setSessions((current) => {
      const remoteSessions = latestSessions.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const drafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...drafts]);
    });
  }

  async function loadSessionDetail(sessionId: string, resumePending = true) {
    setLoadingSessionId(sessionId);
    try {
      const detail = await api.userChatSessionDetail(sessionId);
      const normalizedDetail = {
        ...detail,
        messages: normalizeMessageCitations(detail.messages),
      } satisfies UserChatSessionDetail;
      setSessionDetails((current) => ({
        ...current,
        [sessionId]: normalizedDetail,
      }));
      upsertSessionSummary({
        ...buildSessionSummary(normalizedDetail, false),
        localOnly: false,
      });
      const resumableAssistant = resumePending ? findResumableAssistant(normalizedDetail.messages) : null;
      if (resumePending && resumableAssistant) {
        void resumeStream(sessionId, resumableAssistant.messageId);
      }
      return normalizedDetail;
    } finally {
      setLoadingSessionId((current) => (current === sessionId ? null : current));
    }
  }

  useEffect(() => {
    if (!activeSessionId) {
      return;
    }
    if (sessionDetails[activeSessionId]) {
      const resumableAssistant = findResumableAssistant(sessionDetails[activeSessionId].messages);
      if (resumableAssistant) {
        void resumeStream(activeSessionId, resumableAssistant.messageId);
      }
      return;
    }

    const activeSummary = sessions.find((session) => session.sessionId === activeSessionId);
    if (!activeSummary || activeSummary.localOnly) {
      setSessionDetails((current) => ({
        ...current,
        [activeSessionId]:
          current[activeSessionId] ??
          ({
            sessionId: activeSessionId,
            title: activeSummary?.title ?? '新对话',
            selectedChatModel:
              activeSummary?.selectedChatModel ??
              chatOptionsQuery.data?.defaultChatModel ??
              chatOptionsQuery.data?.activeChatModel ??
              null,
            messages: [],
          } satisfies UserChatSessionDetail),
      }));
      return;
    }

    void loadSessionDetail(activeSessionId);
  }, [activeSessionId, chatOptionsQuery.data, sessionDetails, sessions]);

  function setStreaming(sessionId: string, streaming: boolean) {
    setStreamingSessions((current) => {
      if (!streaming) {
        const next = { ...current };
        delete next[sessionId];
        return next;
      }
      return {
        ...current,
        [sessionId]: true,
      };
    });
  }

  function mergeAssistantEvent(sessionId: string, placeholderMessageId: string, eventName: string, event: ChatStreamEvent) {
    updateSessionDetail(sessionId, (detail) => {
      const nextMessages = [...detail.messages];
      const normalized = normalizeStreamEvent(eventName, event);
      const resolvedMessageId = normalized.messageId ?? placeholderMessageId;
      const targetIndex = nextMessages.findIndex((item) => item.messageId === resolvedMessageId || item.messageId === placeholderMessageId);
      const previous =
        targetIndex >= 0
          ? nextMessages[targetIndex]
          : createOptimisticAssistantMessage(placeholderMessageId, normalized.chatModel ?? detail.selectedChatModel);

      const nextStatus: ChatMessageStatus =
        normalized.status ??
        (normalized.type === 'done' ? 'COMPLETED' : normalized.type === 'error' ? 'FAILED' : previous.status === 'PENDING' ? 'STREAMING' : previous.status);

      const nextContent =
        normalized.content ??
        (normalized.type === 'delta' && typeof event.delta === 'string' && event.delta && event.delta !== 'thinking'
          ? `${previous.content}${event.delta}`
          : previous.content);

      const baseReasoningSteps = normalized.reasoningSteps ?? previous.reasoningSteps;
      const nextReasoningSteps = normalized.extraReasoningStep ? [...baseReasoningSteps, normalized.extraReasoningStep] : baseReasoningSteps;

      const nextCitations = normalized.citations ?? previous.citations;
      const nextToolCalls = normalized.toolCalls ?? previous.toolCalls;

      const nextErrorMessage =
        nextStatus === 'FAILED'
          ? normalized.errorMessage ?? previous.errorMessage ?? '生成失败，请稍后重试'
          : normalized.errorMessage ?? previous.errorMessage ?? null;

      const completedAt =
        nextStatus === 'COMPLETED' || nextStatus === 'FAILED' ? previous.completedAt ?? new Date().toISOString() : null;
      const nextAssistant: UserChatMessage = {
        messageId: resolvedMessageId,
        clientMessageId: null,
        role: 'assistant',
        content: nextContent,
        status: nextStatus,
        reasoningSteps: nextReasoningSteps,
        citations: nextCitations,
        toolCalls: nextToolCalls,
        chatModel: normalized.chatModel ?? previous.chatModel,
        errorMessage: nextErrorMessage,
        createdAt: previous.createdAt ?? new Date().toISOString(),
        completedAt,
      };

      if (targetIndex >= 0) {
        if (sameAssistantMessage(previous, nextAssistant)) {
          return detail;
        }
        nextMessages[targetIndex] = nextAssistant;
      } else {
        nextMessages.push(nextAssistant);
      }

      return {
        ...detail,
        selectedChatModel: normalized.chatModel ?? detail.selectedChatModel,
        messages: nextMessages,
      };
    });
  }

  async function consumeStream(
    sessionId: string,
    placeholderMessageId: string,
    inputValue: RequestInfo | URL,
    init: RequestInit,
    recoveryAttempt = 0,
  ) {
    if (streamControllersRef.current.has(sessionId)) {
      return;
    }

    const controller = new AbortController();
    let recoveryMessageId: string | null = null;
    let latestFailedMessage: string | null = null;
    let fallbackError: string | null = null;
    streamControllersRef.current.set(sessionId, controller);
    setStreaming(sessionId, true);

    try {
      await streamJsonSse<ChatStreamEvent>(inputValue, init, {
        signal: controller.signal,
        onEvent: (eventName, event) => {
          mergeAssistantEvent(sessionId, placeholderMessageId, eventName, event);
          const type = typeof event.type === 'string' ? event.type : eventName;
          if (type === 'snapshot') {
            void reloadSessions();
          }
        },
      });
      await reloadSessions();
      await loadSessionDetail(sessionId);
    } catch (error) {
      if ((error as DOMException).name === 'AbortError') {
        return;
      }

      try {
        const detail = await loadSessionDetail(sessionId, false);
        const resumableAssistant = detail ? findResumableAssistant(detail.messages) : null;
        const latestAssistant = [...(detail?.messages ?? [])].reverse().find((item) => item.role === 'assistant') ?? null;

        if (resumableAssistant && recoveryAttempt < 1) {
          recoveryMessageId = resumableAssistant.messageId;
          return;
        }

        if (latestAssistant?.status === 'COMPLETED') {
          return;
        }

        if (latestAssistant?.status === 'FAILED') {
          latestFailedMessage = latestAssistant.errorMessage;
          return;
        }
      } catch {
        // Fall through to optimistic failure handling below.
      }

      fallbackError = buildErrorSummary(error, '流式输出中断，请稍后重试');
      updateSessionDetail(sessionId, (detail) => ({
        ...detail,
        messages: detail.messages.map((item) =>
          item.messageId === placeholderMessageId
            ? {
                ...item,
                status: 'FAILED',
                errorMessage: fallbackError,
                completedAt: new Date().toISOString(),
              }
            : item,
        ),
      }));
    } finally {
      streamControllersRef.current.delete(sessionId);
      setStreaming(sessionId, false);
    }

    if (recoveryMessageId) {
      await resumeStream(sessionId, recoveryMessageId, recoveryAttempt + 1);
      return;
    }

    if (latestFailedMessage) {
      message.error(latestFailedMessage);
      return;
    }

    if (fallbackError) {
      message.error(fallbackError);
    }
  }

  async function resumeStream(sessionId: string, messageId: string, recoveryAttempt = 0) {
    if (streamControllersRef.current.has(sessionId)) {
      return;
    }

    await consumeStream(
      sessionId,
      messageId,
      buildApiUrl(`/api/app/chat/sessions/${sessionId}/messages/${messageId}/stream`),
      {
        method: 'GET',
        headers: buildUserAuthHeaders({
          Accept: 'text/event-stream',
        }),
      },
      recoveryAttempt,
    );
  }

  async function startStream(sessionId: string, clientMessageId: string, query: string, chatModel?: string | null) {
    const placeholderMessageId = `assistant-${clientMessageId}`;
    await consumeStream(
      sessionId,
      placeholderMessageId,
      buildApiUrl('/api/app/chat/messages/stream'),
      {
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
      0,
    );
  }

  function createNewSession() {
    const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel);
    setSessions((current) => sortSessions([draft.summary, ...current]));
    setSessionDetails((current) => ({
      ...current,
      [draft.detail.sessionId]: draft.detail,
    }));
    setActiveSessionId(draft.detail.sessionId);
    if (userQuery.data) {
      setUserLastSessionId(userQuery.data.id, draft.detail.sessionId);
    }
    setInput('');
  }

  function removeSessionLocally(sessionId: string) {
    const remainingSessions = sessions.filter((session) => session.sessionId !== sessionId);
    const nextDetails = { ...sessionDetails };
    delete nextDetails[sessionId];
    setSessionDetails(nextDetails);
    setSessions(remainingSessions);

    if (activeSessionId !== sessionId) {
      return;
    }

    const nextActive = remainingSessions[0]?.sessionId ?? null;
    if (nextActive) {
      setActiveSessionId(nextActive);
      if (userQuery.data) {
        setUserLastSessionId(userQuery.data.id, nextActive);
      }
      return;
    }

    const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel);
    setSessions([draft.summary]);
    setSessionDetails({ [draft.detail.sessionId]: draft.detail });
    setActiveSessionId(draft.detail.sessionId);
    if (userQuery.data) {
      setUserLastSessionId(userQuery.data.id, draft.detail.sessionId);
    }
  }

  async function deleteSession(session: SessionSummaryState) {
    const runningController = streamControllersRef.current.get(session.sessionId);
    if (runningController) {
      runningController.abort();
      streamControllersRef.current.delete(session.sessionId);
      setStreaming(session.sessionId, false);
    }

    if (session.localOnly) {
      removeSessionLocally(session.sessionId);
      message.success('草稿会话已删除');
      return;
    }

    await api.deleteUserChatSession(session.sessionId);
    removeSessionLocally(session.sessionId);
    message.success('会话已删除');
  }

  async function sendQuery(rawQuery: string) {
    const query = rawQuery.trim();
    if (!query || !activeSessionId) {
      return;
    }

    const activeDetail =
      sessionDetails[activeSessionId] ??
      ({
        sessionId: activeSessionId,
        title: '新对话',
        selectedChatModel: chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel ?? null,
        messages: [],
      } satisfies UserChatSessionDetail);

    if (hasPendingAssistant(activeDetail.messages) || streamingSessions[activeSessionId]) {
      message.warning('当前会话仍有回答进行中，请等待完成后再发送新问题');
      return;
    }

    const clientMessageId = crypto.randomUUID();
    const selectedChatModel =
      activeDetail.selectedChatModel ??
      chatOptionsQuery.data?.defaultChatModel ??
      chatOptionsQuery.data?.activeChatModel ??
      null;
    const title = activeDetail.title === '新对话' ? buildSessionTitle(query) : activeDetail.title;
    const userMessage = createOptimisticUserMessage(query, clientMessageId);
    const assistantMessage = createOptimisticAssistantMessage(`assistant-${clientMessageId}`, selectedChatModel);

    setInput('');
    updateSessionDetail(activeSessionId, (detail) => ({
      ...detail,
      title,
      selectedChatModel,
      messages: [...detail.messages, userMessage, assistantMessage],
    }));
    setSessions((current) =>
      sortSessions(
        current.map((session) =>
          session.sessionId === activeSessionId
            ? {
                ...session,
                title,
                selectedChatModel,
                pending: true,
                messageCount: activeDetail.messages.length + 2,
                lastMessagePreview: summarizeContent(query),
                updatedAt: new Date().toISOString(),
              }
            : session,
        ),
      ),
    );

    try {
      await startStream(activeSessionId, clientMessageId, query, selectedChatModel);
    } catch {
      // consumeStream already updates UI and error feedback
    }
  }

  const activeDetail = activeSessionId ? sessionDetails[activeSessionId] : null;
  const hasConversation = Boolean(activeDetail?.messages.length);
  const selectedChatModel =
    activeDetail?.selectedChatModel ?? chatOptionsQuery.data?.defaultChatModel ?? chatOptionsQuery.data?.activeChatModel;
  const activeStreaming = Boolean(activeSessionId && streamingSessions[activeSessionId]);

  function openCitationDetail(citation: ChatCitation) {
    navigate(buildCitationDetailPath(citation));
  }

  useEffect(() => {
    if (!activeSessionId) {
      return;
    }
    if (loadingSessionId === activeSessionId && !activeDetail) {
      return;
    }
    scheduleScrollToBottom(messagesContainerRef, messagesEndRef, scrollRafRef);
  }, [activeDetail, activeDetail?.messages.length, activeSessionId, loadingSessionId]);

  useEffect(() => {
    if (!activeSessionId || !activeStreaming || !activeDetail) {
      return;
    }
    scheduleScrollToBottom(messagesContainerRef, messagesEndRef, scrollRafRef);
  }, [activeDetail?.messages, activeSessionId, activeDetail, activeStreaming]);

  if ((userQuery.isLoading || sessionsQuery.isLoading) && !sessions.length) {
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
                <span className="chat-sidebar-brand-subtitle">Authenticated Workspace</span>
              </div>
            </div>

            <div className="chat-sidebar-top">
              <Button type="primary" icon={<PlusOutlined />} block className="chat-sidebar-primary" onClick={createNewSession}>
                新对话
              </Button>

              <div className="chat-sidebar-section-title">
                <HistoryOutlined />
                <span>历史对话</span>
              </div>

              <div className="chat-session-list">
                {sessions.map((session) => (
                  <div
                    key={session.sessionId}
                    className={`chat-session-item ${session.sessionId === activeSessionId ? 'chat-session-item-active' : ''}`}
                  >
                    <button
                      type="button"
                      className="chat-session-main"
                      onClick={() => {
                        setActiveSessionId(session.sessionId);
                      }}
                    >
                      <MessageOutlined className="chat-session-icon" />
                      <div className="chat-session-copy">
                        <span className="chat-session-title">{session.title}</span>
                        <span className="chat-session-meta">
                          {session.pending ? '回答进行中' : session.lastMessagePreview || '尚未开始对话'}
                        </span>
                      </div>
                    </button>
                    <Popconfirm
                      title="删除这段对话？"
                      description="删除后该会话历史不可恢复。"
                      okText="删除"
                      cancelText="取消"
                      placement="rightTop"
                      onConfirm={() => {
                        void deleteSession(session).catch((error) => {
                          message.error(buildErrorSummary(error, '删除会话失败，请稍后重试'));
                        });
                      }}
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
                ))}
              </div>
            </div>

            <div className="chat-sidebar-bottom">
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

              <div className="chat-sidebar-user">
                <Space size={8}>
                  <MailOutlined />
                  <span>{userQuery.data?.email ?? '当前用户'}</span>
                </Space>
                <Button
                  type="text"
                  icon={<LogoutOutlined />}
                  onClick={() => {
                    clearUserAuthSession();
                    navigate('/login', { replace: true });
                  }}
                >
                  退出登录
                </Button>
              </div>
            </div>
          </div>
        </aside>

        <main className="chat-main">
          <Card className="chat-panel chat-card" title="问答会话">
              <div className="chat-messages" ref={messagesContainerRef}>
                {loadingSessionId === activeSessionId && !activeDetail ? (
                  <div className="chat-empty-state">
                    <Spin />
                  </div>
                ) : hasConversation && activeDetail ? (
                  <List
                    dataSource={activeDetail.messages}
                    renderItem={(item) => (
                      <List.Item className={`chat-message-list-item chat-message-list-item-${item.role}`}>
                        {(() => {
                          const normalizedReasoningSteps = dedupeReasoningSteps(item.reasoningSteps);
                          const visibleReasoningSteps =
                            item.status === 'STREAMING'
                              ? normalizedReasoningSteps.slice(-1)
                              : normalizedReasoningSteps;
                          return (
                            <div className={`chat-message-row chat-message-row-${item.role}`}>
                              {item.role === 'assistant' ? (
                                <div className="message-avatar message-avatar-assistant">KB</div>
                              ) : null}

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
                                ) : item.role === 'assistant' && item.status !== 'FAILED' ? (
                                  <Typography.Paragraph className="message-placeholder" type="secondary">
                                    正在生成回答...
                                  </Typography.Paragraph>
                                ) : null}

                                {item.errorMessage ? (
                                  <Typography.Paragraph className="message-error" type="danger">
                                    {item.errorMessage}
                                  </Typography.Paragraph>
                                ) : null}

                                {visibleReasoningSteps.length && item.role === 'assistant' ? (
                                  <Collapse
                                    ghost
                                    size="small"
                                    className="reasoning-collapse"
                                    collapsible="icon"
                                    items={[
                                      {
                                        key: `${item.messageId}-reasoning`,
                                        label: (
                                          <span className="reasoning-trigger">
                                            <BulbOutlined />
                                            {compactReasoningLabel(visibleReasoningSteps)}
                                          </span>
                                        ),
                                        children: (
                                          <div className="reasoning-steps">
                                            {visibleReasoningSteps.map((step, index) => (
                                              <div key={`${item.messageId}-step-${index}`} className="reasoning-step">
                                                {step}
                                              </div>
                                            ))}
                                          </div>
                                        ),
                                      },
                                    ]}
                                  />
                                ) : null}

                                {item.toolCalls.length ? (
                                  <Space wrap className="message-tools">
                                    {item.toolCalls.map((tool) => (
                                      <Tag key={tool} color="cyan" bordered={false}>
                                        {tool}
                                      </Tag>
                                    ))}
                                  </Space>
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
                                          onClick={() => openCitationDetail(citation)}
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

                              {item.role === 'user' ? (
                                <div className="message-avatar message-avatar-user">我</div>
                              ) : null}
                            </div>
                          );
                        })()}
                      </List.Item>
                    )}
                  />
                ) : (
                  <div className="chat-empty-state">
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={
                        <Space direction="vertical" size={6}>
                          <Typography.Text strong>从一个问题开始</Typography.Text>
                          <Typography.Text type="secondary">
                            点击快捷卡片会立即发送消息，并在服务端创建或延续当前会话。
                          </Typography.Text>
                        </Space>
                      }
                    >
                      <div className="chat-empty-actions">
                        {suggestedQuestions.map((question) => (
                          <Button key={question} onClick={() => void sendQuery(question)}>
                            {question}
                          </Button>
                        ))}
                      </div>
                    </Empty>
                  </div>
                )}
                <div className="chat-messages-end-anchor" ref={messagesEndRef} />
              </div>

              <div className="chat-composer">
                <Space wrap style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }}>
                  <Space wrap>
                    <Typography.Text type="secondary">入口模型</Typography.Text>
                    <Select
                      value={selectedChatModel ?? undefined}
                      style={{ minWidth: 240 }}
                      placeholder="使用后台默认模型"
                      disabled={!chatOptionsQuery.data?.models.length}
                      options={chatOptionsQuery.data?.models.map((item) => ({
                        label: `${item.displayName} (${item.code})`,
                        value: item.code,
                      }))}
                      onChange={(value) => {
                        if (!activeSessionId) {
                          return;
                        }
                        updateSessionDetail(activeSessionId, (detail) => ({
                          ...detail,
                          selectedChatModel: value,
                        }));
                      }}
                    />
                  </Space>

                  <Typography.Text type="secondary">
                    会话按用户 + 前端随机 sessionId 绑定，刷新后可恢复历史与未完成回答。
                  </Typography.Text>
                </Space>

                <Input.TextArea
                  value={input}
                  rows={4}
                  placeholder="输入你的问题，例如：这个系统如何保证流式回答在刷新后继续恢复？"
                  disabled={activeStreaming}
                  onChange={(event) => setInput(event.target.value)}
                  onCompositionStart={() => {
                    isComposingRef.current = true;
                  }}
                  onCompositionEnd={() => {
                    isComposingRef.current = false;
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter') {
                      return;
                    }

                    const composing = isComposingRef.current || event.nativeEvent.isComposing || event.keyCode === 229;
                    if (composing) {
                      return;
                    }

                    if (!event.shiftKey) {
                      event.preventDefault();
                      void sendQuery(input);
                    }
                  }}
                />
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={activeStreaming}
                  onClick={() => void sendQuery(input)}
                >
                  发送问题
                </Button>
              </div>
            </Card>
        </main>

      </div>
    </div>
  );
}
