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
  profileName: string;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  published: boolean;
  agentType: 'MAIN' | 'ENTRY' | 'ORCHESTRATOR' | 'ATOMIC';
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
  columnName: string | null;
  tags: string;
  columnDocuments: DocumentColumnDocument[];
  createdAt: string;
  updatedAt: string;
};

export type DocumentColumnDocument = {
  id: number;
  title: string;
  createdAt: string;
};

export type DocumentDuplicateKeepStrategy = 'OLDEST' | 'NEWEST';

export type DocumentDuplicateCleanupItem = {
  keepDocumentId: number;
  keepSourceFilename: string;
  keepImportKey: string | null;
  duplicateDocumentId: number;
  duplicateSourceFilename: string;
  duplicateImportKey: string | null;
  categoryName: string | null;
  title: string;
  contentFingerprint: string;
  chunkCount: number;
  assetCount: number;
  tagCount: number;
  sourceReviewRefCount: number;
  publishedReviewRefCount: number;
  ingestionRefCount: number;
};

export type DocumentDuplicateCleanupPreview = {
  items: DocumentDuplicateCleanupItem[];
  previewCount: number;
  visibilityType: KnowledgeDocument['visibilityType'];
  status: KnowledgeDocument['status'];
  keepStrategy: DocumentDuplicateKeepStrategy;
  limit: number;
};

export type DocumentDuplicateCleanupResult = {
  duplicateDocumentsDeleted: number;
  mergedTagBindings: number;
  rewiredSourceReviews: number;
  rewiredPublishedReviews: number;
  rewiredIngestionJobs: number;
  deletedTagBindings: number;
  deletedAssets: number;
  deletedChunks: number;
  vectorRowsDeleted: number;
  refreshedKeeperTags: number;
  deletedDocuments: number;
  indexRebuildJob: DocumentIndexRebuildJob | null;
};

export type PublicDocumentSummary = {
  id: number;
  title: string;
  categoryName: string | null;
  tags: string[];
  excerpt: string;
  updatedAt: string;
};

export type PublicDocumentPage = {
  items: PublicDocumentSummary[];
  total: number;
  page: number;
  pageSize: number;
};

export type PublicDocumentTagFacet = {
  id: number;
  name: string;
  documentCount: number;
};

export type PublicDocumentCategoryFacet = {
  id: number;
  name: string;
  documentCount: number;
  tags: PublicDocumentTagFacet[];
};

export type PublicDocumentFacet = {
  totalDocumentCount: number;
  categories: PublicDocumentCategoryFacet[];
  allTags: PublicDocumentTagFacet[];
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

export type AppToolExecutionMode = 'CLIENT' | 'SERVER';

export type AppToolRateLimitScope = 'NONE' | 'USER' | 'USER_AND_IP';

export type AppToolCatalogItem = {
  code: string;
  name: string;
  summary: string;
  descriptionMarkdown: string;
  categoryCode: string;
  iconKey: string;
  tags: string[];
  displayOrder: number;
  executionMode: AppToolExecutionMode;
  rendererCode: string;
  handlerCode: string;
  inputSchemaJson: string;
  defaultValuesJson: string;
  resultSchemaJson: string;
};

export type AppToolDefinition = {
  id: number;
  code: string;
  name: string;
  summary: string;
  descriptionMarkdown: string;
  categoryCode: string;
  iconKey: string;
  tags: string[];
  displayOrder: number;
  enabled: boolean;
  executionMode: AppToolExecutionMode;
  rendererCode: string;
  handlerCode: string;
  inputSchemaJson: string;
  defaultValuesJson: string;
  resultSchemaJson: string;
  serverConfigJson: string;
  timeoutMs: number | null;
  rateLimitScope: AppToolRateLimitScope;
  rateLimitMaxRequests: number | null;
  rateLimitWindowSeconds: number | null;
  auditEnabled: boolean;
  payloadLimitBytes: number | null;
  createdAt: string;
  updatedAt: string;
};

export type AppToolExecutionResult = {
  toolCode: string;
  executionMode: AppToolExecutionMode;
  resultType: string;
  result: unknown;
  resultPreview: string;
  durationMs: number;
  executionId: string;
};

export type AppToolExecutionLog = {
  executionId: string;
  toolCode: string;
  userId: number;
  status: 'SUCCESS' | 'FAILED' | 'RATE_LIMITED';
  durationMs: number | null;
  requestSummaryJson: string;
  responseSummaryJson: string;
  errorCode: string | null;
  errorMessage: string | null;
  clientIpMasked: string | null;
  createdAt: string;
};

export type AppToolExecutionLogPage = {
  items: AppToolExecutionLog[];
  total: number;
  page: number;
  pageSize: number;
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

export type AgentProfileVersionAgentBinding = {
  profileVersionId: number;
  profileCode: string;
  profileName: string;
  versionNumber: number;
  agentType: AgentProfileVersion['agentType'];
  published: boolean;
};

export type AgentProfileVersionBindings = {
  profileVersionId: number;
  toolCodes: string[];
  skillCodes: string[];
  mcpBindings: AgentProfileVersionMcpBinding[];
  childAgentBindings: AgentProfileVersionAgentBinding[];
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

export type AgentExecutionTimelineItem = {
  itemId: string;
  itemType: string;
  sourceType: 'SPAN' | 'EVENT' | (string & {});
  title: string;
  status: AgentExecutionTraceStatus | 'INFO' | string;
  sequenceNo: number;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  inputJson: string | null;
  outputJson: string | null;
  payloadJson: string | null;
  relatedSpanId: string | null;
  relatedEventId: number | null;
};

export type AgentExecutionBackendSpan = {
  callId: string;
  parentCallId: string | null;
  callName: string;
  callType: string;
  serviceClass: string | null;
  methodName: string | null;
  status: AgentExecutionTraceStatus;
  sequenceNo: number;
  attemptNo: number;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  inputJson: string | null;
  outputJson: string | null;
  errorJson: string | null;
  relatedSpanId: string | null;
};

export type AgentExecutionReadableNode = {
  nodeId: string;
  nodeType: string;
  title: string;
  badge: string | null;
  technicalLabel: string | null;
  plainSummary: string | null;
  inputExplanation: string | null;
  outputExplanation: string | null;
  status: AgentExecutionTraceStatus | string | null;
  sequenceNo: number;
  startedAt: string;
  endedAt: string | null;
  durationMs: number | null;
  rawRefType: string | null;
  rawRefId: string | null;
  children: AgentExecutionReadableNode[];
};

export type AgentExecutionTraceDetail = {
  trace: AgentExecutionTraceSummary;
  agentTimeline: AgentExecutionTimelineItem[];
  readableAgentTimeline: AgentExecutionReadableNode[];
  readableBackendTimeline: AgentExecutionReadableNode[];
  backendSpans: AgentExecutionBackendSpan[];
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

export type DocumentColumn = {
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
  selectedColumnName: string | null;
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

export type BatchDocumentReviewActionResult = {
  processedCount: number;
  items: DocumentReviewRequestSummary[];
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
  selectedColumnName: string | null;
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
