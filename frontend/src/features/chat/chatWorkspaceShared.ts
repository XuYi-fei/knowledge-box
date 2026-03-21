import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState } from 'react';
import { buildErrorSummary } from '../../lib/errors';
import { streamJsonSse } from '../../lib/sse';
import type {
  ChatCitation,
  ChatMessageStatus,
  ChatProcessDetail,
  ChatStreamEvent,
  PublicChatModelOption,
  UserChatMessage,
  UserChatSessionDetail,
  UserChatSessionSummary,
} from '../../lib/types';

export type SessionSummaryState = UserChatSessionSummary & { localOnly?: boolean };

type NormalizedStreamEvent = {
  type: string;
  sessionId: string | null;
  messageId: string | null;
  content: string | null;
  reasoningSteps: string[] | null;
  processDetails: ChatProcessDetail[] | null;
  citations: ChatCitation[] | null;
  toolCalls: string[] | null;
  status: ChatMessageStatus | null;
  chatModel: string | null;
  errorMessage: string | null;
  extraReasoningStep: string | null;
};

type StreamRequest = {
  input: RequestInfo | URL;
  init: RequestInit;
};

type ChatWorkspaceControllerConfig = {
  workspaceKey: string;
  workspaceReady: boolean;
  sessionsData?: UserChatSessionSummary[];
  sessionsFetched: boolean;
  defaultChatModel?: string | null;
  availableModels: PublicChatModelOption[];
  currentUserId?: number | null;
  messageApi: MessageInstance;
  canStartNewConversation: boolean;
  createBlockedMessage?: string | null;
  sendBlockedMessage?: string | null;
  getStoredSessionId: (userId: number) => string | null;
  setStoredSessionId: (userId: number, sessionId: string) => void;
  loadSessions: () => Promise<UserChatSessionSummary[]>;
  loadSessionDetail: (sessionId: string) => Promise<UserChatSessionDetail>;
  deleteSession: (sessionId: string) => Promise<void>;
  stopMessage: (sessionId: string, messageId: string) => Promise<UserChatMessage>;
  buildResumeRequest: (sessionId: string, messageId: string) => StreamRequest;
  buildStartRequest: (sessionId: string, clientMessageId: string, query: string, chatModel?: string | null) => StreamRequest;
};

export type ChatWorkspaceController = ReturnType<typeof useChatWorkspaceController>;

export function buildSessionTitle(query: string) {
  return query.length > 22 ? `${query.slice(0, 22)}...` : query;
}

export function dedupeReasoningSteps(steps: string[]) {
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

export function buildCitationDetailPath(citation: ChatCitation) {
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
    processDetails: [],
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
    reasoningSteps: [],
    processDetails: [],
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

function sameProcessDetails(left: ChatProcessDetail[], right: ChatProcessDetail[]) {
  if (left.length !== right.length) {
    return false;
  }
  return left.every((value, index) => {
    const candidate = right[index];
    return (
      value.kind === candidate.kind &&
      value.summary === candidate.summary &&
      value.detail === candidate.detail &&
      value.statusLabel === candidate.statusLabel &&
      value.statusTone === candidate.statusTone
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
    sameProcessDetails(left.processDetails, right.processDetails) &&
    sameStringArray(left.toolCalls, right.toolCalls) &&
    sameCitations(left.citations, right.citations)
  );
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
        ? (raw as { content: string }).content
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
    processDetails: Array.isArray(raw.processDetails)
      ? raw.processDetails.filter(
          (item): item is ChatProcessDetail =>
            Boolean(item) &&
            typeof item === 'object' &&
            typeof item.kind === 'string' &&
            typeof item.summary === 'string' &&
            typeof item.detail === 'string' &&
            typeof item.statusLabel === 'string' &&
            typeof item.statusTone === 'string',
        )
      : null,
    citations: Array.isArray(raw.citations) ? mergeCitationsByDocument(raw.citations as ChatCitation[]) : null,
    toolCalls: Array.isArray(raw.toolCalls) ? raw.toolCalls.filter((item) => typeof item === 'string') : null,
    status: raw.status ?? statusFromType,
    chatModel: raw.chatModel ?? null,
    errorMessage: raw.errorMessage ?? null,
    extraReasoningStep,
  };
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

export function useChatWorkspaceController(config: ChatWorkspaceControllerConfig) {
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
  }, [config.workspaceKey]);

  useEffect(() => {
    if (!config.workspaceReady || !config.sessionsData) {
      return;
    }

    setSessions((current) => {
      const remoteSessions = config.sessionsData!.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const localDrafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...localDrafts]);
    });
  }, [config.workspaceReady, config.sessionsData]);

  useEffect(() => {
    if (!config.workspaceReady || !config.currentUserId || !config.sessionsFetched) {
      return;
    }

    if (!sessions.length) {
      if ((config.sessionsData?.length ?? 0) > 0) {
        return;
      }
      if (!activeSessionId) {
        const draft = createDraftSession(config.defaultChatModel ?? null);
        setSessions([draft.summary]);
        setSessionDetails({ [draft.detail.sessionId]: draft.detail });
        setActiveSessionId(draft.detail.sessionId);
        config.setStoredSessionId(config.currentUserId, draft.detail.sessionId);
      }
      return;
    }

    const sessionIds = new Set(sessions.map((session) => session.sessionId));
    if (activeSessionId && sessionIds.has(activeSessionId)) {
      config.setStoredSessionId(config.currentUserId, activeSessionId);
      return;
    }

    const preferredSessionId = config.getStoredSessionId(config.currentUserId);
    const nextSessionId =
      (preferredSessionId && sessionIds.has(preferredSessionId) ? preferredSessionId : sessions[0]?.sessionId) ?? null;
    if (nextSessionId) {
      setActiveSessionId(nextSessionId);
      config.setStoredSessionId(config.currentUserId, nextSessionId);
    }
  }, [
    activeSessionId,
    config,
    sessions,
  ]);

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
          selectedChatModel: config.defaultChatModel ?? null,
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
    if (!config.workspaceReady) {
      return;
    }
    const latestSessions = await config.loadSessions();
    setSessions((current) => {
      const remoteSessions = latestSessions.map((session) => ({ ...session, localOnly: false }));
      const remoteIds = new Set(remoteSessions.map((session) => session.sessionId));
      const drafts = current.filter((session) => session.localOnly && !remoteIds.has(session.sessionId));
      return sortSessions([...remoteSessions, ...drafts]);
    });
  }

  async function loadSessionDetail(sessionId: string, resumePending = true) {
    if (!config.workspaceReady) {
      return null;
    }
    setLoadingSessionId(sessionId);
    try {
      const detail = await config.loadSessionDetail(sessionId);
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
    if (!activeSessionId || !config.workspaceReady) {
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
            selectedChatModel: activeSummary?.selectedChatModel ?? config.defaultChatModel ?? null,
            messages: [],
          } satisfies UserChatSessionDetail),
      }));
      return;
    }

    void loadSessionDetail(activeSessionId);
  }, [activeSessionId, config.workspaceReady, config.defaultChatModel, sessionDetails, sessions, stoppingSessions]);

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
      const nextProcessDetails = normalized.processDetails ?? previous.processDetails;
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
        processDetails: nextProcessDetails,
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
      config.messageApi.error(latestFailedMessage);
      return;
    }

    if (fallbackError) {
      config.messageApi.error(fallbackError);
    }
  }

  async function resumeStream(sessionId: string, messageId: string, recoveryAttempt = 0) {
    if (streamControllersRef.current.has(sessionId) || !config.workspaceReady) {
      return;
    }

    const request = config.buildResumeRequest(sessionId, messageId);
    await consumeStream(sessionId, messageId, request.input, request.init, recoveryAttempt);
  }

  async function startStream(sessionId: string, clientMessageId: string, query: string, chatModel?: string | null) {
    if (!config.workspaceReady) {
      return;
    }
    const placeholderMessageId = `assistant-${clientMessageId}`;
    const request = config.buildStartRequest(sessionId, clientMessageId, query, chatModel);
    await consumeStream(sessionId, placeholderMessageId, request.input, request.init, 0);
  }

  function createNewSession() {
    if (!config.canStartNewConversation) {
      if (config.createBlockedMessage) {
        config.messageApi.warning(config.createBlockedMessage);
      }
      return;
    }
    const draft = createDraftSession(config.defaultChatModel ?? null);
    setSessions((current) => sortSessions([draft.summary, ...current]));
    setSessionDetails((current) => ({
      ...current,
      [draft.detail.sessionId]: draft.detail,
    }));
    setActiveSessionId(draft.detail.sessionId);
    if (config.currentUserId) {
      config.setStoredSessionId(config.currentUserId, draft.detail.sessionId);
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
      if (config.currentUserId) {
        config.setStoredSessionId(config.currentUserId, nextActive);
      }
      return;
    }

    const draft = createDraftSession(config.defaultChatModel ?? null);
    setSessions([draft.summary]);
    setSessionDetails({ [draft.detail.sessionId]: draft.detail });
    setActiveSessionId(draft.detail.sessionId);
    if (config.currentUserId) {
      config.setStoredSessionId(config.currentUserId, draft.detail.sessionId);
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
      config.messageApi.success('草稿会话已删除');
      return;
    }

    await config.deleteSession(session.sessionId);
    removeSessionLocally(session.sessionId);
    config.messageApi.success('会话已删除');
  }

  async function sendQuery(rawQuery: string) {
    const query = rawQuery.trim();
    if (!query || !activeSessionId) {
      return;
    }
    if (!config.canStartNewConversation) {
      if (config.sendBlockedMessage) {
        config.messageApi.warning(config.sendBlockedMessage);
      }
      return;
    }

    const activeDetail =
      sessionDetails[activeSessionId] ??
      ({
        sessionId: activeSessionId,
        title: '新对话',
        selectedChatModel: config.defaultChatModel ?? null,
        messages: [],
      } satisfies UserChatSessionDetail);

    if (hasPendingAssistant(activeDetail.messages) || streamingSessions[activeSessionId] || stoppingSessions[activeSessionId]) {
      config.messageApi.warning('当前会话仍有回答进行中，请等待完成后再发送新问题');
      return;
    }

    const clientMessageId = crypto.randomUUID();
    const selectedChatModel = activeDetail.selectedChatModel ?? config.defaultChatModel ?? null;
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
  const selectedChatModel = activeDetail?.selectedChatModel ?? config.defaultChatModel ?? null;
  const activeStreaming = Boolean(activeSessionId && streamingSessions[activeSessionId]);
  const activeStopping = Boolean(activeSessionId && stoppingSessions[activeSessionId]);
  const activeResumableAssistant = activeDetail ? findResumableAssistant(activeDetail.messages) : null;
  const composerBusy = activeStreaming || activeStopping || !config.canStartNewConversation;

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
      const stoppedMessage = await config.stopMessage(activeSessionId, activeResumableAssistant.messageId);
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
  }, [activeDetail?.messages, activeDetail, activeSessionId, activeStreaming]);

  return {
    input,
    setInput,
    sessions,
    activeSessionId,
    setActiveSessionId,
    activeDetail,
    loadingSessionId,
    selectedChatModel,
    activeStreaming,
    activeStopping,
    activeResumableAssistant,
    composerBusy,
    hasConversation: Boolean(activeDetail?.messages.length),
    availableModels: config.availableModels,
    messagesContainerRef,
    messagesEndRef,
    isComposingRef,
    createNewSession,
    deleteSession,
    sendQuery,
    stopCurrentAnswer,
    updateSelectedChatModel(value: string) {
      if (!activeSessionId) {
        return;
      }
      updateSessionDetail(activeSessionId, (detail) => ({
        ...detail,
        selectedChatModel: value,
      }));
    },
  };
}
