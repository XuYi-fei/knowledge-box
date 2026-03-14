import {
  AboutReleaseNote,
  AgentExecutionTraceDetail,
  AgentExecutionTracePage,
  AgentExecutionTraceSummary,
  AgentProfileVersionBindings,
  AgentProfileVersionMcpBinding,
  AgentProfileVersion,
  ChatMessageStatus,
  DocumentCategory,
  DocumentIndexRebuildJob,
  DocumentReviewRequestDetail,
  DocumentReviewRequestPage,
  DocumentReviewRequestSummary,
  DocumentReviewStatus,
  DocumentTag,
  ChatStreamEvent,
  ChatResponse,
  DashboardStats,
  IngestionJob,
  InlineImageUploadResult,
  KnowledgeDocument,
  McpServer,
  ModelCatalog,
  PublicChatOptions,
  SkillBinding,
  ToolDefinition,
  UserAuthResponse,
  UserChatSessionDetail,
  UserChatSessionSummary,
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

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

type UpdateProfileVersionPayload = {
  chatModel: string;
  routingModel?: string | null;
  embeddingModel: string;
  rerankModel?: string | null;
  temperature: number;
  retrievalTopK: number;
  reasoningBudget: number;
};

type UpdateProfileVersionBindingsPayload = {
  toolCodes: string[];
  skillCodes: string[];
  mcpBindings: AgentProfileVersionMcpBinding[];
};

type CreateToolPayload = {
  code: string;
  name: string;
  className: string;
  beanName?: string | null;
  configJson?: string;
  enabled: boolean;
};

type UpdateToolPayload = Omit<CreateToolPayload, 'code'>;

type CreateMcpServerPayload = {
  code: string;
  transportType: string;
  target: string;
  headers?: Record<string, string>;
  queryParams?: Record<string, string>;
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
  enabled: boolean;
};

type AuthMode = 'admin' | 'user' | 'none';

export function buildApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
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
  async userChatOptions() {
    return requestJson<PublicChatOptions>('/api/app/chat/options', undefined, 'user');
  },
  async userChatSessions() {
    return requestJson<UserChatSessionSummary[]>('/api/app/chat/sessions', undefined, 'user');
  },
  async userChatSessionDetail(sessionId: string) {
    return requestJson<UserChatSessionDetail>(`/api/app/chat/sessions/${sessionId}`, undefined, 'user');
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
  async documentTags() {
    return requestJson<DocumentTag[]>('/api/admin/document-tags', undefined, 'admin');
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
