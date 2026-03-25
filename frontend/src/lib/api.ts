import {
  AboutReleaseNote,
  BatchDocumentReviewActionResult,
  AgentProfileImportCommitRequest,
  AgentProfileImportCommitResult,
  AgentProfileImportPreview,
  AgentExecutionTraceDetail,
  AgentExecutionTracePage,
  AgentExecutionTraceSummary,
  AgentProfileVersionBindings,
  AgentProfileVersionMcpBinding,
  AgentProfileVersion,
  AppToolCatalogItem,
  AppToolDefinition,
  AppToolExecutionLogPage,
  AppToolExecutionResult,
  AppToolRateLimitScope,
  ChatMessageStatus,
  DocumentCategory,
  DocumentColumn,
  DocumentDuplicateCleanupPreview,
  DocumentDuplicateCleanupResult,
  DocumentDuplicateKeepStrategy,
  DocumentIndexRebuildJob,
  DocumentReviewRequestDetail,
  DocumentReviewRequestPage,
  DocumentReviewRequestSummary,
  DocumentReviewStatus,
  DocumentTag,
  ChatStreamEvent,
  ChatResponse,
  ConfigBundleImportCommitRequest,
  ConfigBundleImportCommitResult,
  ConfigBundleImportPreview,
  DashboardStats,
  IngestionJob,
  InlineImageUploadResult,
  KnowledgeDocument,
  KnowledgeIngestionDraft,
  KnowledgeIngestionOptions,
  KnowledgeIngestionTask,
  KnowledgeIngestionTaskDocumentDetail,
  KnowledgeIngestionUploadResult,
  McpServer,
  ModelCatalog,
  RuntimeEnvRequirement,
  AgentRuntimeEnvVar,
  PublicDocumentFacet,
  PublicDocumentPage,
  PublicChatOptions,
  SkillBinding,
  ToolDefinition,
  UserAuthResponse,
  UserChatMessage,
  UserChatSessionDetail,
  UserChatSessionSummary,
  UserDebugChatOptions,
  UserView,
  UploadResult,
  WebhookSubscription,
} from './types';
import {
  buildAdminAuthToken,
  clearAdminAuthToken,
  clearUserAuthSession,
  getAdminAuthToken,
  getUserAccessToken,
} from './auth';
import { ApiRequestError, isApiErrorPayload } from './errors';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

type UpdateProfileVersionPayload = {
  agentType: AgentProfileVersion['agentType'];
  chatModel: string;
  embeddingModel: string;
  rerankModel?: string | null;
  temperature: number;
  retrievalTopK: number;
  reasoningBudget: number;
  publicDebug?: boolean;
  systemPrompt?: string | null;
};

type CreateProfilePayload = {
  profileCode: string;
  profileName: string;
  description?: string;
  agentType: AgentProfileVersion['agentType'];
  chatModel: string;
  embeddingModel: string;
  rerankModel?: string | null;
  temperature: number;
  retrievalTopK: number;
  reasoningBudget: number;
  publicDebug?: boolean;
  systemPrompt?: string | null;
};

type UpdateProfileVersionBindingsPayload = {
  toolCodes: string[];
  skillCodes: string[];
  mcpBindings: AgentProfileVersionMcpBinding[];
  childAgentVersionIds: number[];
  envVars: AgentRuntimeEnvVar[];
};

type CreateToolPayload = {
  code: string;
  name: string;
  className: string;
  beanName?: string | null;
  configJson?: string;
  runtimeEnvRequirements: RuntimeEnvRequirement[];
  enabled: boolean;
};

type UpdateToolPayload = Omit<CreateToolPayload, 'code'>;

type CreateAppToolPayload = {
  code: string;
  name: string;
  summary: string;
  descriptionMarkdown: string;
  categoryCode: string;
  iconKey: string;
  tags: string[];
  displayOrder?: number | null;
  enabled: boolean;
  executionMode: 'CLIENT' | 'SERVER';
  rendererCode: string;
  handlerCode: string;
  inputSchemaJson: string;
  defaultValuesJson?: string;
  resultSchemaJson?: string;
  serverConfigJson?: string;
  timeoutMs?: number | null;
  rateLimitScope: AppToolRateLimitScope;
  rateLimitMaxRequests?: number | null;
  rateLimitWindowSeconds?: number | null;
  auditEnabled: boolean;
  payloadLimitBytes?: number | null;
};

type UpdateAppToolPayload = Omit<CreateAppToolPayload, 'code'>;

type CreateMcpServerPayload = {
  code: string;
  transportType: string;
  target: string;
  headers?: Record<string, string>;
  queryParams?: Record<string, string>;
  runtimeEnvRequirements: RuntimeEnvRequirement[];
  timeoutMs?: number | null;
  initializationTimeoutMs?: number | null;
  enabled: boolean;
};

type UpdateMcpServerPayload = Omit<CreateMcpServerPayload, 'code'>;

type UploadSkillPayload = {
  code?: string;
  name?: string;
  description?: string;
  enabled?: boolean;
  zip?: File;
  files?: File[];
  paths?: string[];
  replace?: boolean;
};

type UpdateSkillPayload = {
  name: string;
  description?: string;
  runtimeEnvRequirements: RuntimeEnvRequirement[];
  enabled: boolean;
};

type AuthMode = 'admin' | 'user' | 'none';

type BlobResponse = {
  blob: Blob;
  fileName: string | null;
};

type RawKnowledgeIngestionUploadResult = {
  mode: 'SYNC_DRAFT' | 'ASYNC_TASK';
  draft?: { id: number } | null;
  task?: { id: number } | null;
};

type RawKnowledgeIngestionTaskStage = {
  id: number;
  stageCode: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  progressPercent: number;
  message: string | null;
  createdAt: string;
  updatedAt: string;
};

type RawKnowledgeIngestionTaskDocumentSummary = {
  id: number;
  documentCode: string;
  segmentIndex: number;
  pageFromNumber: number | null;
  pageToNumber: number | null;
  status: 'PLANNED' | 'GENERATING' | 'PENDING_REVIEW_CREATED' | 'FAILED' | 'CANCELLED';
  suggestedTitle: string | null;
  suggestedCategoryName: string | null;
  summaryText: string | null;
  errorMessage: string | null;
  reviewRequestCode: string | null;
  createdAt: string;
  updatedAt: string;
};

type RawKnowledgeIngestionTask = {
  id: number;
  taskCode: string;
  sourceFilename: string;
  sourceFileUrl: string | null;
  sourcePageCount: number | null;
  status: KnowledgeIngestionTask['status'];
  stage: string;
  progressPercent: number;
  cancelRequested: boolean;
  summaryText: string | null;
  errorMessage: string | null;
  stages: RawKnowledgeIngestionTaskStage[];
  documents: RawKnowledgeIngestionTaskDocumentSummary[];
  createdAt: string;
  updatedAt: string;
};

type RawKnowledgeIngestionTaskDocumentDetail = RawKnowledgeIngestionTaskDocumentSummary & {
  suggestedTagsJson: string;
  analysisReasoning: string | null;
  generatedMarkdown: string | null;
};

const ingestionStageNameMap: Record<string, string> = {
  UPLOAD_STORED: '保存原文件',
  PAGE_SCAN: '扫描页数',
  TEXT_EXTRACTION: '提取文本',
  SEGMENT_PLANNING: '规划拆解',
  DOCUMENT_GENERATION: '生成文档',
  FINALIZING: '收尾汇总',
};

function formatIngestionPageRange(pageFromNumber: number | null, pageToNumber: number | null) {
  if (pageFromNumber == null && pageToNumber == null) {
    return null;
  }
  if (pageFromNumber != null && pageToNumber != null) {
    return pageFromNumber === pageToNumber ? `第 ${pageFromNumber} 页` : `第 ${pageFromNumber}-${pageToNumber} 页`;
  }
  return `第 ${pageFromNumber ?? pageToNumber} 页`;
}

function normalizeKnowledgeIngestionUploadResult(raw: RawKnowledgeIngestionUploadResult): KnowledgeIngestionUploadResult {
  if (raw.mode === 'ASYNC_TASK') {
    return {
      mode: 'task',
      taskId: raw.task?.id ?? 0,
    };
  }
  return {
    mode: 'draft',
    draftId: raw.draft?.id ?? 0,
  };
}

function normalizeKnowledgeIngestionTaskDocumentSummary(
  raw: RawKnowledgeIngestionTaskDocumentSummary,
): KnowledgeIngestionTaskDocumentDetail {
  return {
    id: raw.id,
    documentCode: raw.documentCode,
    title: raw.suggestedTitle ?? `文档 ${raw.segmentIndex}`,
    categoryName: raw.suggestedCategoryName,
    pageRange: formatIngestionPageRange(raw.pageFromNumber, raw.pageToNumber),
    status: raw.status,
    stage: raw.status,
    summary: raw.summaryText,
    createdAt: raw.createdAt,
    generatedMarkdown: null,
    confirmedReviewRequestCode: raw.reviewRequestCode,
    errorMessage: raw.errorMessage,
  };
}

function normalizeKnowledgeIngestionTask(raw: RawKnowledgeIngestionTask): KnowledgeIngestionTask {
  return {
    id: raw.id,
    taskCode: raw.taskCode,
    sourceFilename: raw.sourceFilename,
    sourceFileUrl: raw.sourceFileUrl,
    pageCount: raw.sourcePageCount,
    status: raw.status,
    stage: raw.stage,
    progressPercent: raw.progressPercent,
    summaryText: raw.summaryText,
    cancelRequested: raw.cancelRequested,
    failureReason: raw.errorMessage,
    stages: (raw.stages ?? []).map((stage) => ({
      id: stage.id,
      name: ingestionStageNameMap[stage.stageCode] ?? stage.stageCode,
      status: stage.status,
      progressPercent: stage.progressPercent,
      message: stage.message,
      startedAt: stage.createdAt,
      finishedAt: ['COMPLETED', 'FAILED', 'CANCELLED'].includes(stage.status) ? stage.updatedAt : null,
    })),
    documents: (raw.documents ?? []).map((document) => normalizeKnowledgeIngestionTaskDocumentSummary(document)),
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
}

function normalizeKnowledgeIngestionTaskDocumentDetail(
  raw: RawKnowledgeIngestionTaskDocumentDetail,
): KnowledgeIngestionTaskDocumentDetail {
  return {
    ...normalizeKnowledgeIngestionTaskDocumentSummary(raw),
    stage: raw.status,
    generatedMarkdown: raw.generatedMarkdown,
    confirmedReviewRequestCode: raw.reviewRequestCode,
    errorMessage: raw.errorMessage,
  };
}

export function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  if (!API_BASE_URL) {
    return normalizedPath;
  }
  if (API_BASE_URL.endsWith('/api') && normalizedPath.startsWith('/api')) {
    return `${API_BASE_URL.slice(0, -4)}${normalizedPath}`;
  }
  return `${API_BASE_URL}${normalizedPath}`;
}

export function buildUserAuthHeaders(headers?: HeadersInit) {
  const resolvedHeaders = new Headers(headers);
  const accessToken = getUserAccessToken();
  if (!accessToken) {
    throw new ApiRequestError('请先登录后再继续操作', {
      status: 401,
      code: 'UNAUTHORIZED',
    });
  }
  resolvedHeaders.set('Authorization', `Bearer ${accessToken}`);
  return resolvedHeaders;
}

async function requestJson<T>(path: string, init?: RequestInit, authMode: AuthMode = 'none'): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  if (!(init?.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (authMode === 'admin') {
    const token = getAdminAuthToken();
    if (!token) {
      throw new Error('请先登录管理后台');
    }
    headers.set('Authorization', `Basic ${token}`);
  }
  if (authMode === 'user') {
    const accessToken = getUserAccessToken();
    if (!accessToken) {
      throw new ApiRequestError('请先登录后再继续操作', {
        status: 401,
        code: 'UNAUTHORIZED',
        path,
      });
    }
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(buildApiUrl(path), {
    ...init,
    headers,
  });

  const responseText = await response.text();
  let responseBody: unknown = undefined;
  if (responseText) {
    try {
      responseBody = JSON.parse(responseText) as unknown;
    } catch {
      responseBody = responseText;
    }
  }

  if (authMode === 'admin' && response.status === 401) {
    clearAdminAuthToken();
    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
      window.location.replace('/admin/login');
    }
    const unauthorizedMessage = isApiErrorPayload(responseBody)
      ? responseBody.message ?? '管理员认证失败，请重新登录，并确认账号密码与后端配置一致'
      : '管理员认证失败，请重新登录，并确认账号密码与后端配置一致';
    throw new ApiRequestError(unauthorizedMessage, {
      status: response.status,
      code: isApiErrorPayload(responseBody) ? responseBody.code : 'UNAUTHORIZED',
      path: isApiErrorPayload(responseBody) ? responseBody.path : path,
      fieldErrors: isApiErrorPayload(responseBody) ? responseBody.fieldErrors : undefined,
    });
  }

  if (authMode === 'user' && response.status === 401) {
    clearUserAuthSession();
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.replace('/login');
    }
    const unauthorizedMessage = isApiErrorPayload(responseBody)
      ? responseBody.message ?? '登录状态已失效，请重新登录'
      : '登录状态已失效，请重新登录';
    throw new ApiRequestError(unauthorizedMessage, {
      status: response.status,
      code: isApiErrorPayload(responseBody) ? responseBody.code : 'UNAUTHORIZED',
      path: isApiErrorPayload(responseBody) ? responseBody.path : path,
      fieldErrors: isApiErrorPayload(responseBody) ? responseBody.fieldErrors : undefined,
    });
  }

  if (!response.ok) {
    if (isApiErrorPayload(responseBody)) {
      throw new ApiRequestError(responseBody.message ?? `Request failed: ${response.status}`, {
        status: responseBody.status ?? response.status,
        code: responseBody.code,
        path: responseBody.path,
        fieldErrors: responseBody.fieldErrors,
      });
    }
    if (typeof responseBody === 'string' && responseBody.trim()) {
      throw new ApiRequestError(responseBody, { status: response.status, path });
    }
    throw new ApiRequestError(`Request failed: ${response.status}`, { status: response.status, path });
  }
  return responseBody as T;
}

function buildRequestHeaders(init: RequestInit | undefined, authMode: AuthMode, path: string) {
  const headers = new Headers(init?.headers);
  if (authMode === 'admin') {
    const token = getAdminAuthToken();
    if (!token) {
      throw new ApiRequestError('请先登录管理后台', {
        status: 401,
        code: 'UNAUTHORIZED',
        path,
      });
    }
    headers.set('Authorization', `Basic ${token}`);
  }
  if (authMode === 'user') {
    const accessToken = getUserAccessToken();
    if (!accessToken) {
      throw new ApiRequestError('请先登录后再继续操作', {
        status: 401,
        code: 'UNAUTHORIZED',
        path,
      });
    }
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return headers;
}

function extractFileNameFromDisposition(disposition: string | null) {
  if (!disposition) {
    return null;
  }
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const quotedMatch = disposition.match(/filename="([^"]+)"/i);
  if (quotedMatch?.[1]) {
    return quotedMatch[1];
  }
  const plainMatch = disposition.match(/filename=([^;]+)/i);
  return plainMatch?.[1]?.trim() ?? null;
}

async function requestBlob(path: string, init?: RequestInit, authMode: AuthMode = 'none'): Promise<BlobResponse> {
  const headers = buildRequestHeaders(init, authMode, path);
  const response = await fetch(buildApiUrl(path), {
    ...init,
    headers,
  });

  if (authMode === 'admin' && response.status === 401) {
    clearAdminAuthToken();
    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
      window.location.replace('/admin/login');
    }
    throw new ApiRequestError('管理员认证失败，请重新登录，并确认账号密码与后端配置一致', {
      status: 401,
      code: 'UNAUTHORIZED',
      path,
    });
  }

  if (authMode === 'user' && response.status === 401) {
    clearUserAuthSession();
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.replace('/login');
    }
    throw new ApiRequestError('登录状态已失效，请重新登录', {
      status: 401,
      code: 'UNAUTHORIZED',
      path,
    });
  }

  if (!response.ok) {
    const responseText = await response.text();
    let responseBody: unknown = undefined;
    if (responseText) {
      try {
        responseBody = JSON.parse(responseText) as unknown;
      } catch {
        responseBody = responseText;
      }
    }
    if (isApiErrorPayload(responseBody)) {
      throw new ApiRequestError(responseBody.message ?? `Request failed: ${response.status}`, {
        status: responseBody.status ?? response.status,
        code: responseBody.code,
        path: responseBody.path ?? path,
        fieldErrors: responseBody.fieldErrors,
      });
    }
    if (typeof responseBody === 'string' && responseBody.trim()) {
      throw new ApiRequestError(responseBody, { status: response.status, path });
    }
    throw new ApiRequestError(`Request failed: ${response.status}`, { status: response.status, path });
  }

  return {
    blob: await response.blob(),
    fileName: extractFileNameFromDisposition(response.headers.get('content-disposition')),
  };
}

export const api = {
  async publicChatOptions() {
    return requestJson<PublicChatOptions>('/api/public/chat/options');
  },
  async sendEmailCode(email: string) {
    return requestJson<{ message: string }>(
      '/api/public/auth/send-code',
      {
        method: 'POST',
        body: JSON.stringify({ email }),
      },
      'none',
    );
  },
  async registerByEmail(email: string, password: string, verificationCode: string) {
    return requestJson<UserAuthResponse>(
      '/api/public/auth/register',
      {
        method: 'POST',
        body: JSON.stringify({ email, password, verificationCode }),
      },
      'none',
    );
  },
  async loginByEmailCode(email: string, verificationCode: string) {
    return requestJson<UserAuthResponse>(
      '/api/public/auth/login/code',
      {
        method: 'POST',
        body: JSON.stringify({ email, verificationCode }),
      },
      'none',
    );
  },
  async loginByPassword(email: string, password: string) {
    return requestJson<UserAuthResponse>(
      '/api/public/auth/login/password',
      {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      },
      'none',
    );
  },
  async currentUser() {
    return requestJson<UserView>('/api/app/me', undefined, 'user');
  },
  async aboutReleaseNotes() {
    return requestJson<AboutReleaseNote[]>('/api/app/about/release-notes', undefined, 'user');
  },
  async appToolsCatalog() {
    return requestJson<AppToolCatalogItem[]>('/api/app/tools', undefined, 'user');
  },
  async executeAppTool(code: string, input: Record<string, unknown>) {
    return requestJson<AppToolExecutionResult>(
      `/api/app/tools/${encodeURIComponent(code)}/execute`,
      {
        method: 'POST',
        body: JSON.stringify({ input }),
      },
      'user',
    );
  },
  async userDocumentDetail(id: number) {
    return requestJson<KnowledgeDocument>(`/api/app/documents/${id}`, undefined, 'user');
  },
  async publicDocumentFacets() {
    return requestJson<PublicDocumentFacet>('/api/public/documents/facets');
  },
  async publicDocuments(params?: {
    categoryId?: number | null;
    tagIds?: number[];
    page?: number;
    pageSize?: number;
  }) {
    const query = new URLSearchParams();
    if (params?.categoryId) {
      query.set('categoryId', String(params.categoryId));
    }
    for (const tagId of params?.tagIds ?? []) {
      query.append('tagId', String(tagId));
    }
    if (params?.page) {
      query.set('page', String(params.page));
    }
    if (params?.pageSize) {
      query.set('pageSize', String(params.pageSize));
    }
    const path = query.toString() ? `/api/public/documents?${query.toString()}` : '/api/public/documents';
    return requestJson<PublicDocumentPage>(path);
  },
  async publicDocumentDetail(id: number) {
    return requestJson<KnowledgeDocument>(`/api/public/documents/${id}`);
  },
  async userChatOptions() {
    return requestJson<PublicChatOptions>('/api/app/chat/options', undefined, 'user');
  },
  async userChatSessions() {
    return requestJson<UserChatSessionSummary[]>('/api/app/chat/sessions', undefined, 'user');
  },
  async userChatSessionDetail(sessionId: string) {
    return requestJson<UserChatSessionDetail>(`/api/app/chat/sessions/${sessionId}`, undefined, 'user');
  },
  async userDebugChatOptions() {
    return requestJson<UserDebugChatOptions>('/api/app/agent-debug/options', undefined, 'user');
  },
  async knowledgeIngestionOptions() {
    return requestJson<KnowledgeIngestionOptions>('/api/app/knowledge-ingestion/options', undefined, 'user');
  },
  async createKnowledgeIngestionUploadDraft(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    const result = await requestJson<RawKnowledgeIngestionUploadResult>(
      '/api/app/knowledge-ingestion/uploads',
      {
        method: 'POST',
        body: formData,
      },
      'user',
    );
    return normalizeKnowledgeIngestionUploadResult(result);
  },
  async createKnowledgeIngestionInlineDraft(payload: {
    content: string;
    sourceFilename?: string;
  }) {
    const result = await requestJson<RawKnowledgeIngestionUploadResult>(
      '/api/app/knowledge-ingestion/inline',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'user',
    );
    return normalizeKnowledgeIngestionUploadResult(result);
  },
  async knowledgeIngestionDraftDetail(draftId: number) {
    return requestJson<KnowledgeIngestionDraft>(`/api/app/knowledge-ingestion/drafts/${draftId}`, undefined, 'user');
  },
  async confirmKnowledgeIngestionDraft(
    draftId: number,
    payload: {
      title?: string;
      categoryName?: string;
      columnName?: string;
      tags?: string[];
    },
  ) {
    return requestJson<KnowledgeIngestionDraft>(
      `/api/app/knowledge-ingestion/drafts/${draftId}/confirm`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'user',
    );
  },
  async listKnowledgeIngestionTasks() {
    const result = await requestJson<RawKnowledgeIngestionTask[]>('/api/app/knowledge-ingestion/tasks', undefined, 'user');
    return result.map((item) => normalizeKnowledgeIngestionTask(item));
  },
  async knowledgeIngestionTaskDetail(taskId: number) {
    const result = await requestJson<RawKnowledgeIngestionTask>(`/api/app/knowledge-ingestion/tasks/${taskId}`, undefined, 'user');
    return normalizeKnowledgeIngestionTask(result);
  },
  async knowledgeIngestionTaskDocument(taskId: number, documentId: number) {
    const result = await requestJson<RawKnowledgeIngestionTaskDocumentDetail>(
      `/api/app/knowledge-ingestion/tasks/${taskId}/documents/${documentId}`,
      undefined,
      'user',
    );
    return normalizeKnowledgeIngestionTaskDocumentDetail(result);
  },
  async cancelKnowledgeIngestionTask(taskId: number) {
    return requestJson<void>(
      `/api/app/knowledge-ingestion/tasks/${taskId}/cancel`,
      {
        method: 'POST',
      },
      'user',
    );
  },
  async userDebugChatSessions(profileCode: string) {
    return requestJson<UserChatSessionSummary[]>(
      `/api/app/agent-debug/${encodeURIComponent(profileCode)}/sessions`,
      undefined,
      'user',
    );
  },
  async userDebugChatSessionDetail(profileCode: string, sessionId: string) {
    return requestJson<UserChatSessionDetail>(
      `/api/app/agent-debug/${encodeURIComponent(profileCode)}/sessions/${encodeURIComponent(sessionId)}`,
      undefined,
      'user',
    );
  },
  async userDebugChatTraces(profileCode: string, params?: { sessionId?: string; page?: number; pageSize?: number }) {
    const query = new URLSearchParams();
    if (params?.sessionId) {
      query.set('sessionId', params.sessionId);
    }
    if (typeof params?.page === 'number') {
      query.set('page', String(params.page));
    }
    if (typeof params?.pageSize === 'number') {
      query.set('pageSize', String(params.pageSize));
    }
    return requestJson<AgentExecutionTracePage>(
      `/api/app/agent-debug/${encodeURIComponent(profileCode)}/traces${query.toString() ? `?${query.toString()}` : ''}`,
      undefined,
      'user',
    );
  },
  async stopUserChatMessage(sessionId: string, messageId: string) {
    return requestJson<UserChatMessage>(
      `/api/app/chat/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/stop`,
      {
        method: 'POST',
      },
      'user',
    );
  },
  async deleteUserChatSession(sessionId: string) {
    return requestJson<void>(
      `/api/app/chat/sessions/${sessionId}`,
      {
        method: 'DELETE',
      },
      'user',
    );
  },
  async stopUserDebugChatMessage(profileCode: string, sessionId: string, messageId: string) {
    return requestJson<UserChatMessage>(
      `/api/app/agent-debug/${encodeURIComponent(profileCode)}/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/stop`,
      {
        method: 'POST',
      },
      'user',
    );
  },
  async deleteUserDebugChatSession(profileCode: string, sessionId: string) {
    return requestJson<void>(
      `/api/app/agent-debug/${encodeURIComponent(profileCode)}/sessions/${encodeURIComponent(sessionId)}`,
      {
        method: 'DELETE',
      },
      'user',
    );
  },
  async chat(query: string, sessionId?: string, chatModel?: string) {
    return requestJson<ChatResponse>('/api/public/chat', {
      method: 'POST',
      body: JSON.stringify({ query, sessionId, chatModel }),
    });
  },
  async dashboard() {
    return requestJson<DashboardStats>('/api/admin/dashboard', undefined, 'admin');
  },
  async verifyAdminLogin(username: string, password: string) {
    const token = buildAdminAuthToken(username, password);
    return requestJson<{ username: string; role: string }>(
      '/api/admin/me',
      {
        headers: {
          Authorization: `Basic ${token}`,
        },
      },
      'none',
    );
  },
  async changeAdminPassword(currentPassword: string, newPassword: string) {
    return requestJson<{ message: string }>(
      '/api/admin/me/password',
      {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword }),
      },
      'admin',
    );
  },
  async profileVersions() {
    return requestJson<AgentProfileVersion[]>('/api/admin/profile-versions', undefined, 'admin');
  },
  async exportProfileVersions() {
    return requestBlob('/api/admin/profile-versions/export', undefined, 'admin');
  },
  async previewProfileVersionImport(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return requestJson<AgentProfileImportPreview>(
      '/api/admin/profile-versions/import/preview',
      {
        method: 'POST',
        body: formData,
      },
      'admin',
    );
  },
  async commitProfileVersionImport(payload: AgentProfileImportCommitRequest) {
    return requestJson<AgentProfileImportCommitResult>(
      '/api/admin/profile-versions/import/commit',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async exportConfigBundle() {
    return requestBlob('/api/admin/config-bundles/export', undefined, 'admin');
  },
  async previewConfigBundleImport(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return requestJson<ConfigBundleImportPreview>(
      '/api/admin/config-bundles/import/preview',
      {
        method: 'POST',
        body: formData,
      },
      'admin',
    );
  },
  async commitConfigBundleImport(payload: ConfigBundleImportCommitRequest) {
    return requestJson<ConfigBundleImportCommitResult>(
      '/api/admin/config-bundles/import/commit',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async createProfile(payload: CreateProfilePayload) {
    return requestJson<AgentProfileVersion>(
      '/api/admin/profile-versions',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async updateProfileVersion(id: number, payload: UpdateProfileVersionPayload) {
    return requestJson<AgentProfileVersion>(
      `/api/admin/profile-versions/${id}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async deleteProfileVersion(id: number) {
    return requestJson<{ message: string }>(
      `/api/admin/profile-versions/${id}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async getProfileVersionBindings(id: number) {
    return requestJson<AgentProfileVersionBindings>(`/api/admin/profile-versions/${id}/bindings`, undefined, 'admin');
  },
  async updateProfileVersionBindings(id: number, payload: UpdateProfileVersionBindingsPayload) {
    return requestJson<AgentProfileVersionBindings>(
      `/api/admin/profile-versions/${id}/bindings`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async modelCatalogs() {
    return requestJson<ModelCatalog[]>('/api/admin/model-catalogs', undefined, 'admin');
  },
  async createModelCatalog(payload: {
    code: string;
    displayName: string;
    provider: string;
    modelType: ModelCatalog['modelType'];
    description?: string;
    enabled: boolean;
    publicSelectable: boolean;
    defaultForPublic: boolean;
  }) {
    return requestJson<ModelCatalog>(
      '/api/admin/model-catalogs',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async updateModelCatalog(
    id: number,
    payload: {
      displayName: string;
      provider: string;
      description?: string;
      enabled: boolean;
      publicSelectable: boolean;
      defaultForPublic: boolean;
    },
  ) {
    return requestJson<ModelCatalog>(
      `/api/admin/model-catalogs/${id}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async documents() {
    return requestJson<KnowledgeDocument[]>('/api/admin/documents', undefined, 'admin');
  },
  async documentDetail(id: number) {
    return requestJson<KnowledgeDocument>(`/api/admin/documents/${id}`, undefined, 'admin');
  },
  async updateDocumentSource(
    id: number,
    payload: {
      title: string;
      sourceFilename: string;
      visibilityType: KnowledgeDocument['visibilityType'];
      sourceMarkdown: string;
      extensionJson?: string;
    },
  ) {
    return requestJson<DocumentReviewRequestSummary>(
      `/api/admin/documents/${id}/source`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async documentCategories() {
    return requestJson<DocumentCategory[]>('/api/admin/document-categories', undefined, 'admin');
  },
  async documentColumns() {
    return requestJson<DocumentColumn[]>('/api/admin/document-columns', undefined, 'admin');
  },
  async documentTags() {
    return requestJson<DocumentTag[]>('/api/admin/document-tags', undefined, 'admin');
  },
  async previewDocumentDuplicates(params?: {
    visibilityType?: KnowledgeDocument['visibilityType'];
    status?: KnowledgeDocument['status'];
    keepStrategy?: DocumentDuplicateKeepStrategy;
    limit?: number;
  }) {
    const query = new URLSearchParams();
    if (params?.visibilityType) {
      query.set('visibilityType', params.visibilityType);
    }
    if (params?.status) {
      query.set('status', params.status);
    }
    if (params?.keepStrategy) {
      query.set('keepStrategy', params.keepStrategy);
    }
    if (params?.limit != null) {
      query.set('limit', String(params.limit));
    }
    const queryString = query.toString();
    const path = queryString ? `/api/admin/document-duplicates?${queryString}` : '/api/admin/document-duplicates';
    return requestJson<DocumentDuplicateCleanupPreview>(path, undefined, 'admin');
  },
  async cleanupDocumentDuplicates(payload: {
    visibilityType?: KnowledgeDocument['visibilityType'];
    status?: KnowledgeDocument['status'];
    keepStrategy?: DocumentDuplicateKeepStrategy;
    limit?: number;
    triggerIndexRebuild?: boolean;
  }) {
    return requestJson<DocumentDuplicateCleanupResult>(
      '/api/admin/document-duplicates/cleanup',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async documentReviews(params?: {
    status?: DocumentReviewStatus;
    page?: number;
    pageSize?: number;
  }) {
    const query = new URLSearchParams();
    if (params?.status) {
      query.set('status', params.status);
    }
    if (params?.page != null) {
      query.set('page', String(params.page));
    }
    if (params?.pageSize != null) {
      query.set('pageSize', String(params.pageSize));
    }
    const queryString = query.toString();
    const path = queryString ? `/api/admin/document-reviews?${queryString}` : '/api/admin/document-reviews';
    return requestJson<DocumentReviewRequestPage>(path, undefined, 'admin');
  },
  async documentReviewDetail(id: number) {
    return requestJson<DocumentReviewRequestDetail>(`/api/admin/document-reviews/${id}`, undefined, 'admin');
  },
  async updateDocumentReviewTaxonomy(
    id: number,
    payload: {
      categoryName: string;
      columnName?: string | null;
      tags: string[];
    },
  ) {
    return requestJson<DocumentReviewRequestSummary>(
      `/api/admin/document-reviews/${id}/taxonomy`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async approveDocumentReview(id: number, reason?: string) {
    return requestJson<DocumentReviewRequestSummary>(
      `/api/admin/document-reviews/${id}/approve`,
      {
        method: 'POST',
        body: JSON.stringify({ reason: reason ?? '' }),
      },
      'admin',
    );
  },
  async batchApproveDocumentReviews(reviewIds: number[], reason?: string) {
    return requestJson<BatchDocumentReviewActionResult>(
      '/api/admin/document-reviews/batch/approve',
      {
        method: 'POST',
        body: JSON.stringify({ reviewIds, reason: reason ?? '' }),
      },
      'admin',
    );
  },
  async rejectDocumentReview(id: number, reason?: string) {
    return requestJson<DocumentReviewRequestSummary>(
      `/api/admin/document-reviews/${id}/reject`,
      {
        method: 'POST',
        body: JSON.stringify({ reason: reason ?? '' }),
      },
      'admin',
    );
  },
  async triggerDocumentIndexRebuild() {
    return requestJson<DocumentIndexRebuildJob>(
      '/api/admin/documents/index-rebuilds',
      {
        method: 'POST',
      },
      'admin',
    );
  },
  async latestDocumentIndexRebuild() {
    return requestJson<DocumentIndexRebuildJob | null>(
      '/api/admin/documents/index-rebuilds/latest',
      undefined,
      'admin',
    );
  },
  async ingestionJobs() {
    return requestJson<IngestionJob[]>('/api/admin/ingestion-jobs', undefined, 'admin');
  },
  async appTools() {
    return requestJson<AppToolDefinition[]>('/api/admin/app-tools', undefined, 'admin');
  },
  async createAppTool(payload: CreateAppToolPayload) {
    return requestJson<AppToolDefinition>(
      '/api/admin/app-tools',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async updateAppTool(code: string, payload: UpdateAppToolPayload) {
    return requestJson<AppToolDefinition>(
      `/api/admin/app-tools/${encodeURIComponent(code)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async deleteAppTool(code: string) {
    return requestJson<{ message: string }>(
      `/api/admin/app-tools/${encodeURIComponent(code)}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async appToolExecutions(params?: { toolCode?: string; status?: string; userId?: number; page?: number; pageSize?: number }) {
    const query = new URLSearchParams();
    if (params?.toolCode) {
      query.set('toolCode', params.toolCode);
    }
    if (params?.status) {
      query.set('status', params.status);
    }
    if (params?.userId != null && Number.isFinite(params.userId)) {
      query.set('userId', String(params.userId));
    }
    if (params?.page != null) {
      query.set('page', String(params.page));
    }
    if (params?.pageSize != null) {
      query.set('pageSize', String(params.pageSize));
    }
    const queryString = query.toString();
    const path = queryString ? `/api/admin/app-tool-executions?${queryString}` : '/api/admin/app-tool-executions';
    return requestJson<AppToolExecutionLogPage>(path, undefined, 'admin');
  },
  async tools() {
    return requestJson<ToolDefinition[]>('/api/admin/tools', undefined, 'admin');
  },
  async createTool(payload: CreateToolPayload) {
    return requestJson<ToolDefinition>(
      '/api/admin/tools',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async updateTool(code: string, payload: UpdateToolPayload) {
    return requestJson<ToolDefinition>(
      `/api/admin/tools/${encodeURIComponent(code)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async deleteTool(code: string) {
    return requestJson<{ message: string }>(
      `/api/admin/tools/${encodeURIComponent(code)}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async mcpServers() {
    return requestJson<McpServer[]>('/api/admin/mcp-servers', undefined, 'admin');
  },
  async createMcpServer(payload: CreateMcpServerPayload) {
    return requestJson<McpServer>(
      '/api/admin/mcp-servers',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async updateMcpServer(code: string, payload: UpdateMcpServerPayload) {
    return requestJson<McpServer>(
      `/api/admin/mcp-servers/${encodeURIComponent(code)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async deleteMcpServer(code: string) {
    return requestJson<{ message: string }>(
      `/api/admin/mcp-servers/${encodeURIComponent(code)}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async skills() {
    return requestJson<SkillBinding[]>('/api/admin/skills', undefined, 'admin');
  },
  async uploadSkill(payload: UploadSkillPayload) {
    const formData = new FormData();
    if (payload.code) {
      formData.append('code', payload.code);
    }
    if (payload.name) {
      formData.append('name', payload.name);
    }
    if (payload.description) {
      formData.append('description', payload.description);
    }
    if (typeof payload.enabled === 'boolean') {
      formData.append('enabled', String(payload.enabled));
    }
    if (typeof payload.replace === 'boolean') {
      formData.append('replace', String(payload.replace));
    }
    if (payload.zip) {
      formData.append('zip', payload.zip);
    }
    if (payload.files?.length) {
      payload.files.forEach((file) => formData.append('files', file));
    }
    if (payload.paths?.length) {
      payload.paths.forEach((path) => formData.append('paths', path));
    }
    return requestJson<SkillBinding>(
      '/api/admin/skills/upload',
      {
        method: 'POST',
        body: formData,
      },
      'admin',
    );
  },
  async updateSkill(code: string, payload: UpdateSkillPayload) {
    return requestJson<SkillBinding>(
      `/api/admin/skills/${encodeURIComponent(code)}`,
      {
        method: 'PUT',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async deleteSkill(code: string) {
    return requestJson<{ message: string }>(
      `/api/admin/skills/${encodeURIComponent(code)}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async hooks() {
    return requestJson<WebhookSubscription[]>('/api/admin/hooks', undefined, 'admin');
  },
  async traces(params?: {
    traceId?: string;
    status?: AgentExecutionTraceSummary['status'] | 'ALL';
    sessionCode?: string;
    userId?: number;
    queryKeyword?: string;
    page?: number;
    pageSize?: number;
  }) {
    const searchParams = new URLSearchParams();
    if (params?.traceId) {
      searchParams.set('traceId', params.traceId);
    }
    if (params?.status && params.status !== 'ALL') {
      searchParams.set('status', params.status);
    }
    if (params?.sessionCode) {
      searchParams.set('sessionCode', params.sessionCode);
    }
    if (typeof params?.userId === 'number' && !Number.isNaN(params.userId)) {
      searchParams.set('userId', String(params.userId));
    }
    if (params?.queryKeyword) {
      searchParams.set('queryKeyword', params.queryKeyword);
    }
    if (typeof params?.page === 'number') {
      searchParams.set('page', String(params.page));
    }
    if (typeof params?.pageSize === 'number') {
      searchParams.set('pageSize', String(params.pageSize));
    }
    const query = searchParams.toString();
    return requestJson<AgentExecutionTracePage>(`/api/admin/traces${query ? `?${query}` : ''}`, undefined, 'admin');
  },
  async traceDetail(traceId: string) {
    return requestJson<AgentExecutionTraceDetail>(`/api/admin/traces/${encodeURIComponent(traceId)}`, undefined, 'admin');
  },
  async deleteTrace(traceId: string) {
    return requestJson<{ message: string }>(
      `/api/admin/traces/${encodeURIComponent(traceId)}`,
      {
        method: 'DELETE',
      },
      'admin',
    );
  },
  async uploadDocument(
    markdown: File,
    assets: File[],
    options?: {
      title?: string;
      visibilityType?: KnowledgeDocument['visibilityType'];
      extensionJson?: string;
    },
  ) {
    const formData = new FormData();
    formData.append('markdown', markdown);
    assets.forEach((asset) => formData.append('assets', asset));
    if (options?.title) {
      formData.append('title', options.title);
    }
    if (options?.visibilityType) {
      formData.append('visibilityType', options.visibilityType);
    }
    if (options?.extensionJson) {
      formData.append('extensionJson', options.extensionJson);
    }
    return requestJson<UploadResult>(
      '/api/admin/documents/upload',
      {
        method: 'POST',
        body: formData,
      },
      'admin',
    );
  },
  async createDocumentReview(payload: {
    title: string;
    sourceFilename: string;
    visibilityType: KnowledgeDocument['visibilityType'];
    sourceMarkdown: string;
    extensionJson?: string;
  }) {
    return requestJson<UploadResult>(
      '/api/admin/documents/upload-json',
      {
        method: 'POST',
        body: JSON.stringify(payload),
      },
      'admin',
    );
  },
  async uploadDocumentInlineImage(image: File) {
    const formData = new FormData();
    formData.append('image', image);
    return requestJson<InlineImageUploadResult>(
      '/api/admin/documents/paste-image',
      {
        method: 'POST',
        body: formData,
      },
      'admin',
    );
  },
};
