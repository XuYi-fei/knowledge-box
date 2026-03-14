export type ChatCitation = {
  documentId: number;
  documentTitle: string;
  headingPath: string;
  anchor: string;
  snippet: string;
};

export type UserView = {
  id: number;
  email: string;
};

export type AboutReleaseNote = {
  id: number;
  versionLabel: string;
  title: string;
  summary: string;
  contentMarkdown: string;
  publishedAt: string;
  highlighted: boolean;
};

export type UserAuthAction = 'REGISTERED' | 'AUTO_REGISTERED' | 'LOGGED_IN';

export type UserAuthResponse = {
  accessToken: string;
  expiresAt: string;
  user: UserView;
  authAction: UserAuthAction;
  message: string;
};

export type ChatResponse = {
  sessionId: string;
  answer: string;
  citations: ChatCitation[];
  toolCalls: string[];
  chatModel: string;
};

export type PublicChatModelOption = {
  code: string;
  displayName: string;
  provider: string;
  description: string | null;
};

export type PublicChatOptions = {
  activeChatModel: string;
  defaultChatModel: string | null;
  models: PublicChatModelOption[];
};

export type ChatMessageStatus = 'PENDING' | 'STREAMING' | 'COMPLETED' | 'FAILED';

export type UserChatMessage = {
  messageId: string;
  clientMessageId: string | null;
  role: 'user' | 'assistant';
  content: string;
  status: ChatMessageStatus;
  reasoningSteps: string[];
  citations: ChatCitation[];
  toolCalls: string[];
  chatModel: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
};

export type UserChatSessionSummary = {
  sessionId: string;
  title: string;
  selectedChatModel: string | null;
  messageCount: number;
  lastMessagePreview: string;
  pending: boolean;
  updatedAt: string;
};

export type UserChatSessionDetail = {
  sessionId: string;
  title: string;
  selectedChatModel: string | null;
  messages: UserChatMessage[];
};

export type ChatStreamEventType = 'snapshot' | 'thinking' | 'delta' | 'done' | 'error' | (string & {});

// Backend may add new event types / fields over time.
// Keep this shape backward compatible: older required fields remain, newer fields are optional.
export type ChatStreamEvent = {
  type?: ChatStreamEventType;
  sessionId?: string;
  messageId?: string;
  delta?: string;
  fullContent?: string;
  reasoningSteps?: string[];
  citations?: ChatCitation[];
  toolCalls?: string[];
  status?: ChatMessageStatus;
  chatModel?: string | null;
  errorMessage?: string | null;
  [key: string]: unknown;
};

export type DashboardStats = {
  profileCount: number;
  documentCount: number;
  activeHookCount: number;
  recentTraceCount: number;
};

export type AgentProfileVersion = {
  id: number;
  profileCode: string;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  published: boolean;
  chatModel: string;
  routingModel: string | null;
  embeddingModel: string;
  rerankModel: string | null;
  temperature: number;
  retrievalTopK: number;
  reasoningBudget: number;
};

export type ModelType = 'CHAT' | 'EMBEDDING' | 'RERANK';

export type ModelCatalog = {
  id: number;
  code: string;
  displayName: string;
  provider: string;
  modelType: ModelType;
  description: string | null;
  enabled: boolean;
  publicSelectable: boolean;
  defaultForPublic: boolean;
};

export type KnowledgeDocument = {
  id: number;
  title: string;
  sourceFilename: string;
  status: 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'ARCHIVED';
  visibilityType: 'PUBLIC' | 'AGENT_ONLY';
  uploaderType: 'ADMIN';
  uploaderUserId: number | null;
  normalizedMarkdownPath: string;
  sourceMarkdown: string;
  extensionJson: string;
  vectorConfigJson: string;
  categoryName: string | null;
  tags: string;
  createdAt: string;
  updatedAt: string;
};

export type IngestionJob = {
  id: number;
  documentId: number;
  documentTitle: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  jobType: string;
  detail: string;
};

export type ToolDefinition = {
  id: number;
  code: string;
  name: string;
  className: string;
  beanName: string | null;
  configJson: string;
  enabled: boolean;
};

export type McpServer = {
  id: number;
  code: string;
  transportType: string;
  target: string;
  headersJson?: string;
  headersMaskedJson: string;
  queryParamsJson: string;
  timeoutMs: number | null;
  initializationTimeoutMs: number | null;
  enabled: boolean;
};

export type SkillBinding = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  sourceType: string | null;
  ossObjectKey: string | null;
  checksumMd5: string | null;
  enabled: boolean;
};

export type AgentProfileVersionMcpBinding = {
  mcpCode: string;
  enableTools: string[];
  disableTools: string[];
};

export type AgentProfileVersionBindings = {
  profileVersionId: number;
  toolCodes: string[];
  skillCodes: string[];
  mcpBindings: AgentProfileVersionMcpBinding[];
};

export type WebhookSubscription = {
  id: number;
  eventType: string;
  targetUrl: string;
  secretMasked: string;
  enabled: boolean;
};

export type AgentExecutionTraceStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export type AgentExecutionTraceSummary = {
  traceId: string;
  userId: number | null;
  sessionCode: string | null;
  assistantMessageCode: string | null;
  clientMessageId: string | null;
  profileCode: string | null;
  chatModelCode: string | null;
  requestQueryMasked: string | null;
  status: AgentExecutionTraceStatus;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  attemptCount: number;
  errorCode: string | null;
  errorMessage: string | null;
};

export type AgentExecutionTracePage = {
  items: AgentExecutionTraceSummary[];
  total: number;
  page: number;
  pageSize: number;
};

export type AgentExecutionSpan = {
  spanId: string;
  parentSpanId: string | null;
  spanName: string | null;
  spanType: 'REQUEST' | 'ROUTING' | 'STREAM' | 'TOOL' | 'FINALIZE' | (string & {});
  status: AgentExecutionTraceStatus;
  sequenceNo: number;
  attemptNo: number;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  inputJson: string | null;
  outputJson: string | null;
  tagsJson: string | null;
  errorJson: string | null;
};

export type AgentExecutionEvent = {
  id: number;
  spanId: string | null;
  eventType: string | null;
  sequenceNo: number;
  occurredAt: string;
  payloadJson: string | null;
};

export type AgentExecutionTraceDetail = {
  trace: AgentExecutionTraceSummary;
  spans: AgentExecutionSpan[];
  events: AgentExecutionEvent[];
};

export type UploadResult = {
  title: string;
  sourceFilename: string;
  normalizedMarkdownPath: string | null;
  rewrittenAssets: string[];
  reviewRequestId: number | null;
  reviewRequestCode: string | null;
};

export type InlineImageUploadResult = {
  url: string;
  provider: string;
  objectKey: string | null;
  md5: string;
  contentType: string | null;
  contentLength: number | null;
};

export type DocumentCategory = {
  id: number;
  name: string;
  source: 'SYSTEM' | 'AGENT' | 'MANUAL';
};

export type DocumentTag = {
  id: number;
  name: string;
  source: 'SYSTEM' | 'AGENT' | 'MANUAL';
};

export type DocumentReviewStatus = 'CREATED' | 'PROCESSING' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'FAILED';

export type DocumentReviewRequestSummary = {
  id: number;
  requestCode: string;
  sourceDocumentId: number | null;
  publishedDocumentId: number | null;
  title: string;
  sourceFilename: string;
  uploaderType: 'ADMIN';
  uploaderUserId: number | null;
  visibilityType: 'PUBLIC' | 'AGENT_ONLY';
  status: DocumentReviewStatus;
  stage: string;
  progressPercent: number;
  suggestedCategoryName: string | null;
  suggestedTagsJson: string;
  selectedCategoryName: string | null;
  selectedTagsJson: string;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};

export type DocumentReviewRequestPage = {
  items: DocumentReviewRequestSummary[];
  total: number;
  page: number;
  pageSize: number;
};

export type DocumentReviewAsset = {
  id: number;
  originalPath: string;
  storedUrl: string;
  provider: string;
  objectKey: string | null;
  contentType: string | null;
  contentLength: number | null;
};

export type DocumentReviewChunk = {
  id: number;
  chunkIndex: number;
  headingPath: string;
  anchor: string;
  content: string;
  metadataJson: string;
};

export type DocumentReviewRequestDetail = {
  id: number;
  requestCode: string;
  sourceDocumentId: number | null;
  publishedDocumentId: number | null;
  title: string;
  sourceFilename: string;
  uploaderType: 'ADMIN';
  uploaderUserId: number | null;
  visibilityType: 'PUBLIC' | 'AGENT_ONLY';
  status: DocumentReviewStatus;
  stage: string;
  progressPercent: number;
  sourceMarkdown: string;
  normalizedMarkdownPath: string | null;
  extensionJson: string;
  vectorConfigJson: string;
  suggestedCategoryName: string | null;
  suggestedTagsJson: string;
  selectedCategoryName: string | null;
  selectedTagsJson: string;
  taxonomyReasoning: string | null;
  reviewReason: string | null;
  reviewedByUserId: number | null;
  reviewedAt: string | null;
  errorMessage: string | null;
  assets: DocumentReviewAsset[];
  chunks: DocumentReviewChunk[];
  createdAt: string;
  updatedAt: string;
};

export type DocumentIndexRebuildJob = {
  id: number;
  jobCode: string;
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  triggeredByUserId: number | null;
  sourceVectorTable: string;
  targetVectorTable: string;
  detailJson: string;
  startedAt: string;
  finishedAt: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};
