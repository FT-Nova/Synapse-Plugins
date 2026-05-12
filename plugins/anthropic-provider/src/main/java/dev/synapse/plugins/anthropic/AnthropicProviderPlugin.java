package dev.synapse.plugins.anthropic;

import dev.synapse.plugin.api.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SYNAPSE official model provider for Anthropic Claude.
 *
 * <p>Uses java.net.HttpURLConnection — no external dependencies.
 */
public class AnthropicProviderPlugin implements ModelProvider {

    private PluginContext ctx;
    private String apiKey;
    private String baseUrl;
    private AuthMode authMode;

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override
    public String getId() {
        return "synapse-team/anthropic-provider";
    }

    @Override
    public String getName() {
        return "Anthropic Claude Provider";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getProviderId() {
        return "anthropic";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onLoad(PluginContext context) throws Exception {
        this.ctx = context;
        configure(context);
        ctx.logger().info("{} loaded (authMode={})", getName(), authMode);
    }

    @Override
    public void configure(PluginContext context) throws Exception {
        this.authMode = context.authMode();
        this.baseUrl = context.config().getString("base_url", "https://api.anthropic.com");

        if (authMode == AuthMode.API_KEY) {
            this.apiKey = context.config().getSecret("api_key");
        } else if (authMode == AuthMode.ACP) {
            this.apiKey = context.config().getSecret("acp_subscription_id");
        }
    }

    @Override
    public void onUnload() throws Exception {
        ctx.logger().info("{} unloaded", getName());
    }

    // ── Completion ────────────────────────────────────────────────────────────

    @Override
    public CompletionResponse complete(CompletionRequest request) throws Exception {
        String body = buildMessagesBody(request, false);
        String response = post("/v1/messages", body);
        return parseMessagesResponse(response);
    }

    @Override
    public void stream(CompletionRequest request, StreamHandler handler) throws Exception {
        String body = buildMessagesBody(request, true);

        URI uri = URI.create(baseUrl + "/v1/messages");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6);
                if (data.contains("\"type\":\"message_stop\"")) {
                    handler.onComplete("stop", -1);
                    return;
                }
                String chunk = extractJsonField(data, "\"text\":\"");
                if (chunk != null) {
                    handler.onChunk(unescapeJson(chunk));
                }
            }
            handler.onComplete("stop", -1);
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    // ── Capabilities & Models ─────────────────────────────────────────────────

    @Override
    public ModelCapabilities getCapabilities() {
        return ModelCapabilities.builder()
                .streaming(true)
                .toolCalling(true)
                .vision(true)
                .embeddings(false)
                .maxContextTokens(200_000)
                .build();
    }

    @Override
    public List<ModelInfo> listModels() throws Exception {
        return List.of(
                new ModelInfo("claude-opus-4-7", "Claude Opus 4.7", 200_000, false),
                new ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", 200_000, false),
                new ModelInfo("claude-haiku-4-5", "Claude Haiku 4.5", 200_000, false)
        );
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String post(String path, String body) throws Exception {
        URI uri = URI.create(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    // ── Request/response builders ─────────────────────────────────────────────

    private String buildMessagesBody(CompletionRequest request, boolean stream) {
        StringBuilder messages = new StringBuilder();
        for (CompletionRequest.Message msg : request.getMessages()) {
            if (!messages.isEmpty()) messages.append(",");
            String role = msg.role().equals("system") ? "user" : msg.role();
            messages.append(String.format(
                    "{\"role\":\"%s\",\"content\":\"%s\"}",
                    role,
                    escapeJson(msg.content())
            ));
        }

        return String.format(
                "{\"model\":\"%s\",\"max_tokens\":%d,\"messages\":[%s],\"temperature\":%s,\"stream\":%s}",
                request.getModel(),
                request.getMaxTokens() > 0 ? request.getMaxTokens() : 4096,
                messages,
                request.getTemperature(),
                stream
        );
    }

    private CompletionResponse parseMessagesResponse(String response) {
        String content = "";
        String model = "";
        int promptTokens = -1;
        int completionTokens = -1;
        String finishReason = "stop";

        String contentBlock = extractJsonObject(response, "\"content\":");
        if (contentBlock != null) {
            content = extractJsonField(contentBlock, "\"text\":\"");
            if (content != null) content = unescapeJson(content);
        }

        String usage = extractJsonObject(response, "\"usage\":");
        if (usage != null) {
            String pt = extractJsonField(usage, "\"input_tokens\":");
            if (pt != null) promptTokens = Integer.parseInt(pt);
            String ct = extractJsonField(usage, "\"output_tokens\":");
            if (ct != null) completionTokens = Integer.parseInt(ct);
        }

        model = extractJsonField(response, "\"model\":\"");
        if (model == null) model = "";

        String stopReason = extractJsonField(response, "\"stop_reason\":\"");
        if (stopReason != null) finishReason = stopReason;

        return new CompletionResponse(content != null ? content : "", model, promptTokens, completionTokens, finishReason);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static String extractJsonField(String body, String key) {
        int idx = body.indexOf(key);
        if (idx == -1) return null;
        idx += key.length();
        int end = body.indexOf("\"", idx);
        while (end > idx && body.charAt(end - 1) == '\\') {
            end = body.indexOf("\"", end + 1);
        }
        if (end == -1) {
            end = body.indexOf(",", idx);
            if (end == -1) end = body.indexOf("}", idx);
            if (end == -1) end = body.indexOf("]", idx);
        }
        if (end == -1) return null;
        return body.substring(idx, end);
    }

    private static String extractJsonObject(String body, String key) {
        int idx = body.indexOf(key);
        if (idx == -1) return null;
        idx += key.length();
        int start = body.indexOf("{", idx);
        if (start == -1) {
            start = body.indexOf("[", idx);
            if (start == -1) return null;
        }
        int depth = 0;
        char open = body.charAt(start);
        char close = open == '{' ? '}' : ']';
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == open) depth++;
            else if (c == close) depth--;
            if (depth == 0) return body.substring(start, i + 1);
        }
        return null;
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String raw) {
        return raw.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
