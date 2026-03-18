package com.knowledgebox.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgebox.domain.chat.AgentExecutionStatus;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WebSearchTool {

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final String DIRECT_SEARCH_URL = "https://html.duckduckgo.com/html/?q=%s";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern RESULT_LINK_PATTERN = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern RESULT_SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>|<div[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final HttpClient httpClient;

    public WebSearchTool(ObjectMapper objectMapper, AgentExecutionTraceService agentExecutionTraceService) {
        this.objectMapper = objectMapper;
        this.agentExecutionTraceService = agentExecutionTraceService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool(
            name = "searchWeb",
            description = "Search the public web for up-to-date or external information. Always cite the returned sources in the final answer."
    )
    public String searchWeb(
            @ToolParam(name = "query", description = "The search query rewritten for external web search.") String query,
            @ToolParam(name = "maxResults", required = false, description = "Maximum number of results to return, usually between 3 and 5.") Integer maxResults,
            @ToolParam(name = "searchDepth", required = false, description = "Preferred depth for Tavily, such as basic or advanced.") String searchDepth,
            AgentExecutionTraceContext traceContext,
            ChatExchangeRuntime exchangeRuntime,
            AgentRuntimeEnvironment runtimeEnvironment
    ) {
        int resolvedMaxResults = maxResults == null || maxResults <= 0 ? 5 : Math.min(maxResults, 8);
        String resolvedSearchDepth = StringUtils.hasText(searchDepth) ? searchDepth.trim() : "advanced";
        String callId = startSpan(traceContext, query, resolvedMaxResults, resolvedSearchDepth);
        try {
            SearchResponse response = performSearch(query, resolvedMaxResults, resolvedSearchDepth, runtimeEnvironment, traceContext, callId);
            if (exchangeRuntime != null) {
                exchangeRuntime.recordToolCall("searchWeb");
            }
            endSpan(traceContext, callId, AgentExecutionStatus.COMPLETED, response);
            return render(response);
        } catch (RuntimeException exception) {
            endSpan(traceContext, callId, AgentExecutionStatus.FAILED, exception);
            throw exception;
        }
    }

    private SearchResponse performSearch(
            String query,
            int maxResults,
            String searchDepth,
            AgentRuntimeEnvironment runtimeEnvironment,
            AgentExecutionTraceContext traceContext,
            String parentSpanId
    ) {
        String tavilyApiKey = runtimeEnvironment == null ? null : runtimeEnvironment.get("TAVILY_API_KEY");
        if (StringUtils.hasText(tavilyApiKey)) {
            SearchResponse tavilyResponse = tavilySearch(query, maxResults, searchDepth, tavilyApiKey, traceContext, parentSpanId);
            if (tavilyResponse.status == SearchStatus.FOUND) {
                return tavilyResponse;
            }
            SearchResponse directFallback = directSearch(query, maxResults, traceContext, parentSpanId, tavilyResponse.message);
            if (directFallback.status != SearchStatus.UNAVAILABLE) {
                return directFallback.withFallback("tavily-empty-or-failed");
            }
            return directFallback.withFallback(tavilyResponse.message);
        }
        return directSearch(query, maxResults, traceContext, parentSpanId, "tavily-api-key-missing")
                .withFallback("tavily-api-key-missing");
    }

    private SearchResponse tavilySearch(
            String query,
            int maxResults,
            String searchDepth,
            String apiKey,
            AgentExecutionTraceContext traceContext,
            String parentSpanId
    ) {
        String spanId = startProviderSpan(traceContext, parentSpanId, "tavily", query);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", query);
            payload.put("search_depth", searchDepth);
            payload.put("max_results", maxResults);
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TAVILY_API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                endProviderSpan(traceContext, spanId, AgentExecutionStatus.FAILED, Map.of(
                        "provider", "tavily",
                        "statusCode", response.statusCode()
                ));
                return SearchResponse.unavailable("tavily", "tavily-http-" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<SearchResultItem> items = new ArrayList<>();
            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode itemNode : resultsNode) {
                    String url = text(itemNode, "url");
                    String title = text(itemNode, "title");
                    String content = text(itemNode, "content");
                    if (!StringUtils.hasText(url) && !StringUtils.hasText(title)) {
                        continue;
                    }
                    items.add(new SearchResultItem(
                            title,
                            url,
                            content,
                            "tavily"
                    ));
                    if (items.size() >= maxResults) {
                        break;
                    }
                }
            }
            endProviderSpan(traceContext, spanId, AgentExecutionStatus.COMPLETED, Map.of(
                    "provider", "tavily",
                    "resultCount", items.size()
            ));
            if (items.isEmpty()) {
                return SearchResponse.empty("tavily", "tavily-returned-no-results");
            }
            return SearchResponse.found("tavily", items, null);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            endProviderSpan(traceContext, spanId, AgentExecutionStatus.FAILED, Map.of(
                    "provider", "tavily",
                    "exception", exception.getClass().getSimpleName()
            ));
            return SearchResponse.unavailable("tavily", exception.getClass().getSimpleName());
        }
    }

    private SearchResponse directSearch(
            String query,
            int maxResults,
            AgentExecutionTraceContext traceContext,
            String parentSpanId,
            String reason
    ) {
        String spanId = startProviderSpan(traceContext, parentSpanId, "direct-web", query);
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(DIRECT_SEARCH_URL.formatted(encodedQuery)))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "KnowledgeBoxBot/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                endProviderSpan(traceContext, spanId, AgentExecutionStatus.FAILED, Map.of(
                        "provider", "direct-web",
                        "statusCode", response.statusCode()
                ));
                return SearchResponse.unavailable("direct-web", "direct-web-http-" + response.statusCode());
            }
            List<SearchResultItem> items = parseDuckDuckGoHtml(response.body(), maxResults);
            endProviderSpan(traceContext, spanId, AgentExecutionStatus.COMPLETED, Map.of(
                    "provider", "direct-web",
                    "resultCount", items.size()
            ));
            if (items.isEmpty()) {
                return SearchResponse.unavailable("direct-web", StringUtils.hasText(reason) ? reason : "direct-web-no-results");
            }
            return SearchResponse.found("direct-web", items, reason);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            endProviderSpan(traceContext, spanId, AgentExecutionStatus.FAILED, Map.of(
                    "provider", "direct-web",
                    "exception", exception.getClass().getSimpleName()
            ));
            return SearchResponse.unavailable("direct-web", exception.getClass().getSimpleName());
        }
    }

    private List<SearchResultItem> parseDuckDuckGoHtml(String html, int maxResults) {
        List<SearchResultItem> items = new ArrayList<>();
        List<String> snippets = new ArrayList<>();
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);
        while (snippetMatcher.find()) {
            String snippet = snippetMatcher.group(1);
            if (!StringUtils.hasText(snippet)) {
                snippet = snippetMatcher.group(2);
            }
            snippets.add(cleanHtml(snippet));
        }
        Matcher matcher = RESULT_LINK_PATTERN.matcher(html);
        int index = 0;
        while (matcher.find() && items.size() < maxResults) {
            String url = cleanHtml(matcher.group(1));
            String title = cleanHtml(matcher.group(2));
            String snippet = index < snippets.size() ? snippets.get(index) : "";
            index++;
            if (!StringUtils.hasText(url) && !StringUtils.hasText(title)) {
                continue;
            }
            items.add(new SearchResultItem(title, url, snippet, "direct-web"));
        }
        return items;
    }

    private String render(SearchResponse response) {
        StringBuilder builder = new StringBuilder();
        builder.append("searchStatus: ").append(response.status.name()).append('\n');
        builder.append("providerUsed: ").append(response.provider).append('\n');
        if (StringUtils.hasText(response.fallbackReason)) {
            builder.append("fallbackReason: ").append(response.fallbackReason).append('\n');
        }
        if (StringUtils.hasText(response.message)) {
            builder.append("message: ").append(response.message).append('\n');
        }
        builder.append("resultCount: ").append(response.items.size()).append('\n');
        if (response.items.isEmpty()) {
            builder.append("sources: none\n");
            builder.append("guidance: If no reliable public source is found, explicitly say so in the final answer.");
            return builder.toString();
        }
        builder.append("sources:\n");
        for (int index = 0; index < response.items.size(); index++) {
            SearchResultItem item = response.items.get(index);
            builder.append(index + 1)
                    .append(". title: ").append(defaultText(item.title)).append('\n')
                    .append("   url: ").append(defaultText(item.url)).append('\n')
                    .append("   summary: ").append(defaultText(item.summary)).append('\n');
        }
        builder.append("guidance: Base the final answer on these sources and cite the URLs explicitly.");
        return builder.toString();
    }

    private String startSpan(AgentExecutionTraceContext traceContext, String query, int maxResults, String searchDepth) {
        if (traceContext == null) {
            return null;
        }
        String spanId = agentExecutionTraceService.nextBackendSpanIdValue();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("maxResults", maxResults);
        input.put("searchDepth", searchDepth);
        agentExecutionTraceService.startBackendSpan(
                traceContext,
                traceContext.currentActiveBackendSpanId(),
                spanId,
                "WebSearchTool.searchWeb",
                "TOOL",
                getClass().getSimpleName(),
                "searchWeb",
                input,
                null
        );
        return spanId;
    }

    private void endSpan(AgentExecutionTraceContext traceContext, String spanId, AgentExecutionStatus status, SearchResponse response) {
        if (traceContext == null || spanId == null) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("providerUsed", response.provider);
        output.put("searchStatus", response.status.name());
        output.put("resultCount", response.items.size());
        output.put("fallbackReason", response.fallbackReason);
        agentExecutionTraceService.endBackendSpan(traceContext, spanId, status, output, null);
    }

    private void endSpan(AgentExecutionTraceContext traceContext, String spanId, AgentExecutionStatus status, RuntimeException exception) {
        if (traceContext == null || spanId == null) {
            return;
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("exceptionClass", exception.getClass().getName());
        error.put("message", exception.getMessage());
        agentExecutionTraceService.endBackendSpan(traceContext, spanId, status, Map.of(), error);
    }

    private String startProviderSpan(AgentExecutionTraceContext traceContext, String parentSpanId, String provider, String query) {
        if (traceContext == null) {
            return null;
        }
        String spanId = agentExecutionTraceService.nextBackendSpanIdValue();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("provider", provider);
        input.put("query", query);
        agentExecutionTraceService.startBackendSpan(
                traceContext,
                parentSpanId,
                spanId,
                "WebSearchProvider." + provider,
                "HTTP",
                getClass().getSimpleName(),
                provider,
                input,
                null
        );
        return spanId;
    }

    private void endProviderSpan(
            AgentExecutionTraceContext traceContext,
            String spanId,
            AgentExecutionStatus status,
            Map<String, Object> output
    ) {
        if (traceContext == null || spanId == null) {
            return;
        }
        agentExecutionTraceService.endBackendSpan(traceContext, spanId, status, output, null);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? "" : field.asText("");
    }

    private String cleanHtml(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        cleaned = cleaned.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String defaultText(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.trim();
    }

    private enum SearchStatus {
        FOUND,
        EMPTY,
        UNAVAILABLE
    }

    private record SearchResultItem(String title, String url, String summary, String source) {
    }

    private static final class SearchResponse {
        private final SearchStatus status;
        private final String provider;
        private final List<SearchResultItem> items;
        private final String message;
        private final String fallbackReason;

        private SearchResponse(SearchStatus status, String provider, List<SearchResultItem> items, String message, String fallbackReason) {
            this.status = status;
            this.provider = provider;
            this.items = items == null ? List.of() : List.copyOf(items);
            this.message = message;
            this.fallbackReason = fallbackReason;
        }

        private static SearchResponse found(String provider, List<SearchResultItem> items, String fallbackReason) {
            return new SearchResponse(SearchStatus.FOUND, provider, items, null, fallbackReason);
        }

        private static SearchResponse empty(String provider, String message) {
            return new SearchResponse(SearchStatus.EMPTY, provider, List.of(), message, null);
        }

        private static SearchResponse unavailable(String provider, String message) {
            return new SearchResponse(SearchStatus.UNAVAILABLE, provider, List.of(), message, null);
        }

        private SearchResponse withFallback(String fallbackReason) {
            return new SearchResponse(status, provider, items, message, fallbackReason);
        }
    }
}
