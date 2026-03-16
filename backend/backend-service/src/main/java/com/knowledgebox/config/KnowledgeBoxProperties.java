package com.knowledgebox.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowledge-box")
public class KnowledgeBoxProperties {

    private final Admin admin = new Admin();
    private final Auth auth = new Auth();
    private final Mail mail = new Mail();
    private final Redis redis = new Redis();
    private final Storage storage = new Storage();
    private final Chat chat = new Chat();
    private final Retrieval retrieval = new Retrieval();
    private final Document document = new Document();
    private final Integration integration = new Integration();
    private final Observability observability = new Observability();
    private final Web web = new Web();

    public Admin getAdmin() {
        return admin;
    }

    public Storage getStorage() {
        return storage;
    }

    public Auth getAuth() {
        return auth;
    }

    public Mail getMail() {
        return mail;
    }

    public Redis getRedis() {
        return redis;
    }

    public Chat getChat() {
        return chat;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Document getDocument() {
        return document;
    }

    public Integration getIntegration() {
        return integration;
    }

    public Observability getObservability() {
        return observability;
    }

    public Web getWeb() {
        return web;
    }

    public static class Admin {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Auth {
        private String jwtSecret;
        private String issuer = "knowledge-box";
        private Duration tokenTtl = Duration.ofDays(7);
        private Duration verificationCodeTtl = Duration.ofMinutes(10);
        private Duration sendCooldown = Duration.ofSeconds(60);

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public Duration getVerificationCodeTtl() {
            return verificationCodeTtl;
        }

        public void setVerificationCodeTtl(Duration verificationCodeTtl) {
            this.verificationCodeTtl = verificationCodeTtl;
        }

        public Duration getSendCooldown() {
            return sendCooldown;
        }

        public void setSendCooldown(Duration sendCooldown) {
            this.sendCooldown = sendCooldown;
        }

    }

    public static class Mail {
        private String fromAddress;
        private String fromPersonal;

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public String getFromPersonal() {
            return fromPersonal;
        }

        public void setFromPersonal(String fromPersonal) {
            this.fromPersonal = fromPersonal;
        }
    }

    public static class Redis {
        private final Keys keys = new Keys();

        public Keys getKeys() {
            return keys;
        }
    }

    public static class Keys {
        private final AuthKeys auth = new AuthKeys();
        private final ChatKeys chat = new ChatKeys();
        private final RateLimitKeys rateLimit = new RateLimitKeys();

        public AuthKeys getAuth() {
            return auth;
        }

        public ChatKeys getChat() {
            return chat;
        }

        public RateLimitKeys getRateLimit() {
            return rateLimit;
        }
    }

    public static class AuthKeys {
        private String verificationCode = "knowledge-box:auth:verification-code";
        private String verificationCooldown = "knowledge-box:auth:verification-cooldown";

        public String getVerificationCode() {
            return verificationCode;
        }

        public void setVerificationCode(String verificationCode) {
            this.verificationCode = verificationCode;
        }

        public String getVerificationCooldown() {
            return verificationCooldown;
        }

        public void setVerificationCooldown(String verificationCooldown) {
            this.verificationCooldown = verificationCooldown;
        }
    }

    public static class ChatKeys {
        private String sessionState = "knowledge-box:chat:session-state";
        private String streamState = "knowledge-box:chat:stream-state";

        public String getSessionState() {
            return sessionState;
        }

        public void setSessionState(String sessionState) {
            this.sessionState = sessionState;
        }

        public String getStreamState() {
            return streamState;
        }

        public void setStreamState(String streamState) {
            this.streamState = streamState;
        }
    }

    public static class RateLimitKeys {
        private String authSendCode = "knowledge-box:rate-limit:auth-send-code";
        private String publicChatSubmit = "knowledge-box:rate-limit:public-chat-submit";
        private String appToolExecute = "knowledge-box:rate-limit:app-tool-execute";

        public String getAuthSendCode() {
            return authSendCode;
        }

        public void setAuthSendCode(String authSendCode) {
            this.authSendCode = authSendCode;
        }

        public String getPublicChatSubmit() {
            return publicChatSubmit;
        }

        public void setPublicChatSubmit(String publicChatSubmit) {
            this.publicChatSubmit = publicChatSubmit;
        }

        public String getAppToolExecute() {
            return appToolExecute;
        }

        public void setAppToolExecute(String appToolExecute) {
            this.appToolExecute = appToolExecute;
        }
    }

    public static class Storage {
        private String provider = "local";
        private String localBasePath = "backend/uploads";
        private String publicBaseUrl = "http://localhost:8080/uploads";
        private final Oss oss = new Oss();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getLocalBasePath() {
            return localBasePath;
        }

        public void setLocalBasePath(String localBasePath) {
            this.localBasePath = localBasePath;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public Oss getOss() {
            return oss;
        }
    }

    public static class Oss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        private String publicBaseUrl;
        private String pathPrefix = "knowledge-box";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getPathPrefix() {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }
    }

    public static class Chat {
        private int topK = 6;
        private int historyTurns = 12;
        private boolean stubResponses = false;
        private Duration streamDelay = Duration.ofMillis(150);
        private RetrievalTriggerMode retrievalTriggerMode = RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE;
        private final KnowledgeBaseRouting knowledgeBaseRouting = new KnowledgeBaseRouting();
        private final DashScopeCompatible dashScopeCompatible = new DashScopeCompatible();

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getHistoryTurns() {
            return historyTurns;
        }

        public void setHistoryTurns(int historyTurns) {
            this.historyTurns = historyTurns;
        }

        public boolean isStubResponses() {
            return stubResponses;
        }

        public void setStubResponses(boolean stubResponses) {
            this.stubResponses = stubResponses;
        }

        public Duration getStreamDelay() {
            return streamDelay;
        }

        public void setStreamDelay(Duration streamDelay) {
            this.streamDelay = streamDelay;
        }

        public RetrievalTriggerMode getRetrievalTriggerMode() {
            return retrievalTriggerMode;
        }

        public void setRetrievalTriggerMode(RetrievalTriggerMode retrievalTriggerMode) {
            this.retrievalTriggerMode = retrievalTriggerMode == null
                    ? RetrievalTriggerMode.ALWAYS_PRE_RETRIEVE
                    : retrievalTriggerMode;
        }

        public KnowledgeBaseRouting getKnowledgeBaseRouting() {
            return knowledgeBaseRouting;
        }

        public DashScopeCompatible getDashScopeCompatible() {
            return dashScopeCompatible;
        }
    }

    public enum RetrievalTriggerMode {
        ALWAYS_PRE_RETRIEVE,
        MODEL_ROUTED
    }

    public static class DashScopeCompatible {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private List<String> forceModelRegexes = defaultCompatibleModelRegexes();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public List<String> getForceModelRegexes() {
            return forceModelRegexes;
        }

        public void setForceModelRegexes(List<String> forceModelRegexes) {
            this.forceModelRegexes = forceModelRegexes;
        }

        private static List<String> defaultCompatibleModelRegexes() {
            List<String> patterns = new ArrayList<>();
            // qwen3.5-* models are served by DashScope compatible-mode or multimodal endpoints.
            // AgentScope 1.0.9 DashScopeHttpClient endpoint heuristics do not cover this naming.
            patterns.add("(?i)^qwen3\\.5-.*$");
            return patterns;
        }
    }

    public static class KnowledgeBaseRouting {
        private boolean enabled = true;

        /**
         * Regexes that force enabling knowledge base tool/fallback for the query.
         * Evaluated before {@link #forceDisableRegexes} and before model-based routing.
         * Keep rules explicit and business-oriented to reduce false negatives.
         */
        private List<String> forceEnableRegexes = new ArrayList<>();

        /**
         * Regexes that force disabling knowledge base tool/fallback for the query.
         * Keep this list small and intention-revealing. It should only short-circuit
         * obvious generic questions, while uncertain queries should fall back to model routing.
         */
        private List<String> forceDisableRegexes = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getForceEnableRegexes() {
            return forceEnableRegexes;
        }

        public void setForceEnableRegexes(List<String> forceEnableRegexes) {
            this.forceEnableRegexes = forceEnableRegexes;
        }

        public List<String> getForceDisableRegexes() {
            return forceDisableRegexes;
        }

        public void setForceDisableRegexes(List<String> forceDisableRegexes) {
            this.forceDisableRegexes = forceDisableRegexes;
        }
    }

    public static class Retrieval {
        private int topK = 6;
        private double similarityThreshold = 0.2D;
        private int chunkSize = 900;
        private int embeddingBatchSize = 10;
        private boolean vectorEnabled = true;
        private int embeddingDimensions = 1024;
        private String vectorTable = "kb_vector_store";
        private String vectorSchema = "public";

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getEmbeddingBatchSize() {
            return embeddingBatchSize;
        }

        public void setEmbeddingBatchSize(int embeddingBatchSize) {
            this.embeddingBatchSize = embeddingBatchSize;
        }

        public boolean isVectorEnabled() {
            return vectorEnabled;
        }

        public void setVectorEnabled(boolean vectorEnabled) {
            this.vectorEnabled = vectorEnabled;
        }

        public int getEmbeddingDimensions() {
            return embeddingDimensions;
        }

        public void setEmbeddingDimensions(int embeddingDimensions) {
            this.embeddingDimensions = embeddingDimensions;
        }

        public String getVectorTable() {
            return vectorTable;
        }

        public void setVectorTable(String vectorTable) {
            this.vectorTable = vectorTable;
        }

        public String getVectorSchema() {
            return vectorSchema;
        }

        public void setVectorSchema(String vectorSchema) {
            this.vectorSchema = vectorSchema;
        }
    }

    public static class Document {
        private final Taxonomy taxonomy = new Taxonomy();
        private final Bootstrap bootstrap = new Bootstrap();

        public Taxonomy getTaxonomy() {
            return taxonomy;
        }

        public Bootstrap getBootstrap() {
            return bootstrap;
        }
    }

    public static class Taxonomy {
        private String model = "qwen-plus";
        private int maxTags = 5;
        private int maxTokens = 256;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTags() {
            return maxTags;
        }

        public void setMaxTags(int maxTags) {
            this.maxTags = maxTags;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Bootstrap {
        private boolean enabled = false;
        private String seedFile = "";
        private String seedDirectory = "";
        private String seedDirectoryPattern = "*.json";
        private boolean seedDirectoryRecursive = true;
        private boolean failFast = false;
        private String operatorUsername = "admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSeedFile() {
            return seedFile;
        }

        public void setSeedFile(String seedFile) {
            this.seedFile = seedFile;
        }

        public String getSeedDirectory() {
            return seedDirectory;
        }

        public void setSeedDirectory(String seedDirectory) {
            this.seedDirectory = seedDirectory;
        }

        public String getSeedDirectoryPattern() {
            return seedDirectoryPattern;
        }

        public void setSeedDirectoryPattern(String seedDirectoryPattern) {
            this.seedDirectoryPattern = seedDirectoryPattern;
        }

        public boolean isSeedDirectoryRecursive() {
            return seedDirectoryRecursive;
        }

        public void setSeedDirectoryRecursive(boolean seedDirectoryRecursive) {
            this.seedDirectoryRecursive = seedDirectoryRecursive;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        public String getOperatorUsername() {
            return operatorUsername;
        }

        public void setOperatorUsername(String operatorUsername) {
            this.operatorUsername = operatorUsername;
        }
    }

    public static class Web {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Integration {
        private final Crypto crypto = new Crypto();
        private String skillCacheDir = "backend/uploads/skills/cache";
        private String skillPackageCategory = "agent-skills";

        public Crypto getCrypto() {
            return crypto;
        }

        public String getSkillCacheDir() {
            return skillCacheDir;
        }

        public void setSkillCacheDir(String skillCacheDir) {
            this.skillCacheDir = skillCacheDir;
        }

        public String getSkillPackageCategory() {
            return skillPackageCategory;
        }

        public void setSkillPackageCategory(String skillPackageCategory) {
            this.skillPackageCategory = skillPackageCategory;
        }
    }

    public static class Crypto {
        private String masterKey = "";

        public String getMasterKey() {
            return masterKey;
        }

        public void setMasterKey(String masterKey) {
            this.masterKey = masterKey;
        }
    }

    public static class Observability {
        private final AgentTrace agentTrace = new AgentTrace();

        public AgentTrace getAgentTrace() {
            return agentTrace;
        }
    }

    public static class AgentTrace {
        private boolean enabled = true;
        private Duration retention = Duration.ofDays(30);
        private Duration cleanupInterval = Duration.ofHours(24);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRetention() {
            return retention;
        }

        public void setRetention(Duration retention) {
            this.retention = retention;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }
    }
}
