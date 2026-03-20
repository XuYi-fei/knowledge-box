import {
  ApiOutlined,
  DeleteOutlined,
  HistoryOutlined,
  LogoutOutlined,
  MailOutlined,
  MessageOutlined,
  PlusOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, Card, Empty, Input, List, Popconfirm, Select, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, buildApiUrl, buildUserAuthHeaders } from '../../lib/api';
import { clearUserAuthSession, getUserDebugLastSessionId, setUserDebugLastSessionId } from '../../lib/auth';
import { buildErrorSummary } from '../../lib/errors';
import { streamJsonSse } from '../../lib/sse';
import type {
  AgentExecutionTraceSummary,
  ChatCitation,
  ChatMessageStatus,
  ChatStreamEvent,
  UserChatMessage,
  UserChatSessionDetail,
  UserChatSessionSummary,
} from '../../lib/types';
import { AssistantMessageProcessTimeline } from './AssistantMessageProcessTimeline';
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
    resolvedType === 'done' ? 'COMPLETED' : resolvedType === 'error' ? 'FAILED' : resolvedType === 'stopped' ? 'CANCELLED' : null;

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

export function AgentDebugPage() {
  const navigate = useNavigate();
  const { profileCode: profileCodeParam } = useParams<{ profileCode?: string }>();
  const { message } = App.useApp();
  const [input, setInput] = useState('');
  const [sessions, setSessions] = useState<SessionSummaryState[]>([]);
  const [sessionDetails, setSessionDetails] = useState<Record<string, UserChatSessionDetail>>({});
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [loadingSessionId, setLoadingSessionId] = useState<string | null>(null);
  const [streamingSessions, setStreamingSessions] = useState<Record<string, boolean>>({});
  const [stoppingSessions, setStoppingSessions] = useState<Record<string, boolean>>({});
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
  const tracesQuery = useQuery({
    queryKey: ['userDebugChatTraces', selectedProfileCode, activeSessionId],
    queryFn: () => api.userDebugChatTraces(selectedProfileCode!, { sessionId: activeSessionId ?? undefined, page: 1, pageSize: 10 }),
    enabled: Boolean(selectedProfileCode),
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
    const entries = chatOptionsQuery.data?.entries ?? [];
    if (!entries.length) {
      return;
    }
    if (!profileCodeParam || !entries.some((entry) => entry.profileCode === profileCodeParam)) {
      const nextEntry = entries.find((entry) => entry.canStartNewConversation) ?? entries[0];
      navigate(`/agent-debug/${nextEntry.profileCode}`, { replace: true });
    }
  }, [chatOptionsQuery.data?.entries, navigate, profileCodeParam]);

  useEffect(() => {
    setSessions([]);
    setSessionDetails({});
    setActiveSessionId(null);
    setLoadingSessionId(null);
    setStreamingSessions({});
    setStoppingSessions({});
    setInput('');
    for (const controller of streamControllersRef.current.values()) {
      controller.abort();
    }
    streamControllersRef.current.clear();
  }, [selectedProfileCode]);

  useEffect(() => {
    if (!selectedProfileCode || !sessionsQuery.data) {
      return;
    }

    setSessions((current) => {
      const remoteSessions = sessionsQuery.data.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const localDrafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...localDrafts]);
    });
  }, [selectedProfileCode, sessionsQuery.data]);

  useEffect(() => {
    const user = userQuery.data;
    if (!user) {
      return;
    }
    if (!selectedProfileCode) {
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
        const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? null);
        setSessions([draft.summary]);
        setSessionDetails({ [draft.detail.sessionId]: draft.detail });
        setActiveSessionId(draft.detail.sessionId);
        if (selectedProfileCode) {
          setUserDebugLastSessionId(user.id, selectedProfileCode, draft.detail.sessionId);
        }
      }
      return;
    }

    const sessionIds = new Set(sessions.map((session) => session.sessionId));
    if (activeSessionId && sessionIds.has(activeSessionId)) {
      if (selectedProfileCode) {
        setUserDebugLastSessionId(user.id, selectedProfileCode, activeSessionId);
      }
      return;
    }

    const preferredSessionId = selectedProfileCode ? getUserDebugLastSessionId(user.id, selectedProfileCode) : null;
    const nextSessionId =
      (preferredSessionId && sessionIds.has(preferredSessionId) ? preferredSessionId : sessions[0]?.sessionId) ?? null;
    if (nextSessionId) {
      setActiveSessionId(nextSessionId);
      if (selectedProfileCode) {
        setUserDebugLastSessionId(user.id, selectedProfileCode, nextSessionId);
      }
    }
  }, [activeSessionId, chatOptionsQuery.data, selectedProfileCode, sessions, sessionsQuery.data, sessionsQuery.isFetched, userQuery.data]);

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
          selectedChatModel: chatOptionsQuery.data?.defaultChatModel ?? null,
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
    if (!selectedProfileCode) {
      return;
    }
    const latestSessions = await api.userDebugChatSessions(selectedProfileCode);
    setSessions((current) => {
      const remoteSessions = latestSessions.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const drafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...drafts]);
    });
  }

  async function loadSessionDetail(sessionId: string, resumePending = true) {
    if (!selectedProfileCode) {
      return null;
    }
    setLoadingSessionId(sessionId);
    try {
      const detail = await api.userDebugChatSessionDetail(selectedProfileCode, sessionId);
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
      if (resumePending && resumableAssistant && !stoppingSessions[sessionId]) {
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
    if (!selectedProfileCode) {
      return;
    }
    if (sessionDetails[activeSessionId]) {
      if (stoppingSessions[activeSessionId]) {
        return;
      }
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
              null,
            messages: [],
          } satisfies UserChatSessionDetail),
      }));
      return;
    }

    void loadSessionDetail(activeSessionId);
  }, [activeSessionId, chatOptionsQuery.data, selectedProfileCode, sessionDetails, sessions, stoppingSessions]);

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

  function setStopping(sessionId: string, stopping: boolean) {
    setStoppingSessions((current) => {
      if (!stopping) {
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

  function mergePersistedAssistantMessage(sessionId: string, nextAssistant: UserChatMessage) {
    const normalizedAssistant = normalizeMessageCitations([nextAssistant])[0];
    updateSessionDetail(sessionId, (detail) => {
      const nextMessages = [...detail.messages];
      const targetIndex = nextMessages.findIndex((item) => item.messageId === normalizedAssistant.messageId);
      if (targetIndex >= 0) {
        nextMessages[targetIndex] = normalizedAssistant;
      } else {
        nextMessages.push(normalizedAssistant);
      }
      return {
        ...detail,
        selectedChatModel: normalizedAssistant.chatModel ?? detail.selectedChatModel,
        messages: nextMessages,
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
        (normalized.type === 'done'
          ? 'COMPLETED'
          : normalized.type === 'error'
            ? 'FAILED'
            : normalized.type === 'stopped'
              ? 'CANCELLED'
              : previous.status === 'PENDING'
                ? 'STREAMING'
                : previous.status);

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
          : nextStatus === 'CANCELLED'
            ? normalized.errorMessage ?? previous.errorMessage ?? '已停止回答'
            : normalized.errorMessage ?? previous.errorMessage ?? null;

      const completedAt =
        nextStatus === 'COMPLETED' || nextStatus === 'FAILED' || nextStatus === 'CANCELLED'
          ? previous.completedAt ?? new Date().toISOString()
          : null;
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
    if (streamControllersRef.current.has(sessionId) || !selectedProfileCode) {
      return;
    }

    await consumeStream(
      sessionId,
      messageId,
      buildApiUrl(`/api/app/agent-debug/${selectedProfileCode}/sessions/${sessionId}/messages/${messageId}/stream`),
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
    if (!selectedProfileCode) {
      return;
    }
    const placeholderMessageId = `assistant-${clientMessageId}`;
    await consumeStream(
      sessionId,
      placeholderMessageId,
      buildApiUrl('/api/app/agent-debug/messages/stream'),
      {
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
      0,
    );
  }

  function createNewSession() {
    if (!selectedEntry?.canStartNewConversation) {
      message.warning('当前调试入口已下线，不能再创建新对话');
      return;
    }
    const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? null);
    setSessions((current) => sortSessions([draft.summary, ...current]));
    setSessionDetails((current) => ({
      ...current,
      [draft.detail.sessionId]: draft.detail,
    }));
    setActiveSessionId(draft.detail.sessionId);
    if (userQuery.data && selectedProfileCode) {
      setUserDebugLastSessionId(userQuery.data.id, selectedProfileCode, draft.detail.sessionId);
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
      if (userQuery.data && selectedProfileCode) {
        setUserDebugLastSessionId(userQuery.data.id, selectedProfileCode, nextActive);
      }
      return;
    }

    const draft = createDraftSession(chatOptionsQuery.data?.defaultChatModel ?? null);
    setSessions([draft.summary]);
    setSessionDetails({ [draft.detail.sessionId]: draft.detail });
    setActiveSessionId(draft.detail.sessionId);
    if (userQuery.data && selectedProfileCode) {
      setUserDebugLastSessionId(userQuery.data.id, selectedProfileCode, draft.detail.sessionId);
    }
  }

  async function deleteSession(session: SessionSummaryState) {
    const runningController = streamControllersRef.current.get(session.sessionId);
    if (runningController) {
      runningController.abort();
      streamControllersRef.current.delete(session.sessionId);
      setStreaming(session.sessionId, false);
    }
    setStopping(session.sessionId, false);

    if (session.localOnly) {
      removeSessionLocally(session.sessionId);
      message.success('草稿会话已删除');
      return;
    }

    if (!selectedProfileCode) {
      return;
    }
    await api.deleteUserDebugChatSession(selectedProfileCode, session.sessionId);
    removeSessionLocally(session.sessionId);
    message.success('会话已删除');
  }

  async function sendQuery(rawQuery: string) {
    const query = rawQuery.trim();
    if (!query || !activeSessionId) {
      return;
    }
    if (!selectedEntry?.canStartNewConversation) {
      message.warning('当前调试入口已下线，不能再发送新问题');
      return;
    }

    const activeDetail =
      sessionDetails[activeSessionId] ??
      ({
        sessionId: activeSessionId,
        title: '新对话',
        selectedChatModel: chatOptionsQuery.data?.defaultChatModel ?? null,
        messages: [],
      } satisfies UserChatSessionDetail);

    if (hasPendingAssistant(activeDetail.messages) || streamingSessions[activeSessionId] || stoppingSessions[activeSessionId]) {
      message.warning('当前会话仍有回答进行中，请等待完成后再发送新问题');
      return;
    }

    const clientMessageId = crypto.randomUUID();
    const selectedChatModel =
      activeDetail.selectedChatModel ??
      chatOptionsQuery.data?.defaultChatModel ??
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
  const selectedChatModel = activeDetail?.selectedChatModel ?? chatOptionsQuery.data?.defaultChatModel;
  const activeStreaming = Boolean(activeSessionId && streamingSessions[activeSessionId]);
  const activeStopping = Boolean(activeSessionId && stoppingSessions[activeSessionId]);
  const activeResumableAssistant = activeDetail ? findResumableAssistant(activeDetail.messages) : null;
  const composerBusy = activeStreaming || activeStopping || !selectedEntry?.canStartNewConversation;
  const traceItems = tracesQuery.data?.items ?? [];

  function openCitationDetail(citation: ChatCitation) {
    navigate(buildCitationDetailPath(citation));
  }

  async function stopCurrentAnswer() {
    if (!activeSessionId || !activeResumableAssistant) {
      return;
    }

    setStopping(activeSessionId, true);
    const controller = streamControllersRef.current.get(activeSessionId);
    if (controller) {
      controller.abort();
    }

    try {
      if (!selectedProfileCode) {
        return;
      }
      const stoppedMessage = await api.stopUserDebugChatMessage(selectedProfileCode, activeSessionId, activeResumableAssistant.messageId);
      mergePersistedAssistantMessage(activeSessionId, stoppedMessage);
      await reloadSessions();
      await loadSessionDetail(activeSessionId, false);
    } catch (error) {
      try {
        await loadSessionDetail(activeSessionId, true);
      } catch {
        // Keep the original stop error below.
      }
      throw error;
    } finally {
      setStopping(activeSessionId, false);
    }
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

  if (chatOptionsQuery.isSuccess && !(chatOptionsQuery.data?.entries.length ?? 0)) {
    return (
      <div className="chat-shell">
        <Card className="chat-panel chat-card">
          <Empty
            description="当前没有可调试的公开 Entry Agent。请先在管理端把目标 Agent 设为 ENTRY、PUBLISHED，并开启“调试公开”。"
          />
        </Card>
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
                <span className="chat-sidebar-brand-subtitle">Agent Debug Workspace</span>
              </div>
            </div>

            <div className="chat-sidebar-top">
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

              <Button
                type="primary"
                icon={<PlusOutlined />}
                block
                className="chat-sidebar-primary"
                onClick={createNewSession}
                disabled={!selectedEntry?.canStartNewConversation}
              >
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
          <Card className="chat-panel chat-card" title={selectedEntry ? `Agent 调试 · ${selectedEntry.profileName}` : 'Agent 调试'}>
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
                                    reasoningSteps={normalizedReasoningSteps}
                                    toolCalls={item.toolCalls}
                                    status={item.status}
                                    content={item.content}
                                    errorMessage={item.errorMessage}
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
                          <Typography.Text strong>从一个调试问题开始</Typography.Text>
                          <Typography.Text type="secondary">
                            这里会使用当前选中的 Entry Agent 作为入口，并把会话与日志隔离到该 Agent 下。
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
                      disabled={composerBusy || !chatOptionsQuery.data?.models.length}
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
                    调试会话按用户 + Entry Agent 隔离，刷新后会恢复该入口下的最近会话与未完成回答。
                  </Typography.Text>
                </Space>

                <Input.TextArea
                  value={input}
                  rows={4}
                  placeholder="输入你的调试问题，例如：这个入口 Agent 为什么会先调用知识库再决定是否调用子 Agent？"
                  disabled={composerBusy}
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
                <div className="chat-composer-actions">
                  <Button
                    danger
                    icon={<StopOutlined />}
                    disabled={!activeResumableAssistant || activeStopping}
                    loading={activeStopping}
                    onClick={() => {
                      void stopCurrentAnswer().catch((error) => {
                        message.error(buildErrorSummary(error, '停止回答失败，请稍后重试'));
                      });
                    }}
                  >
                    停止回答
                  </Button>
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    loading={activeStreaming}
                    disabled={composerBusy}
                    onClick={() => void sendQuery(input)}
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
