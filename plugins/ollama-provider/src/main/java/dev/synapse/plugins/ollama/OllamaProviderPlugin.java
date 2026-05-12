package dev.synapse.plugins.ollama;

import dev.synapse.plugin.api.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SYNAPSE official model provider for Ollama.
 *
 * <p>Connects to a local or remote Ollama server for self-hosted inference.
 * Uses only java.net — no external dependencies.
 */
public class OllamaProviderPlugin implements ModelProvider {

    private PluginContext ctx;
    private String baseUrl;
    private String defaultModel;

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override
    public String getId() {
        return "synapse-team/ollama-provider";
    }

    @Override
    public String getName() {
        return "Ollama Provider";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getProviderId() {
        return "ollama";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onLoad(PluginContext context) throws Exception {
        this.ctx = context;
        configure(context);
        ctx.logger().info("{} loaded (baseUrl={})", getName(), baseUrl);
    }

    @Override
    public void configure(PluginContext context) throws Exception {
        this.baseUrl = context.config().getString("base_url", "http://localhost:11434");
        this.defaultModel = context.config().getString("default_model", "llama3.2");
    }

    @Override
    public void onUnload() throws Exception {
        ctx.logger().info("{} unloaded", getName());
    }

    // ── Completion ────────────────────────────────────────────────────────────

    @Override
    public CompletionResponse complete(CompletionRequest request) throws Exception {
        String model = request.getModel() != null ? request.getModel() : defaultModel;
        String body = buildRequestBody(model, request, false);

        String response = post("/api/generate", body);
        return parseGenerateResponse(response, model);
    }

    @Override
    public void stream(CompletionRequest request, StreamHandler handler) throws Exception {
        String model = request.getModel() != null ? request.getModel() : defaultModel;
        String body = buildRequestBody(model, request, true);

        URI uri = URI.create(baseUrl + "/api/generate");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(120000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder fullResponse = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String chunk = extractJsonField(line, "\"response\":\"");
                if (chunk != null) {
                    handler.onChunk(unescapeJson(chunk));
                    fullResponse.append(unescapeJson(chunk));
                }
                if (line.contains("\"done\":true")) {
                    break;
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
                .toolCalling(false)
                .vision(false)
                .embeddings(true)
                .maxContextTokens(128_000)
                .build();
    }

    @Override
    public List<ModelInfo> listModels() throws Exception {
        String response = get("/api/tags");
        List<ModelInfo> models = new ArrayList<>();

        int idx = 0;
        while ((idx = response.indexOf("\"name\":\"", idx)) != -1) {
            idx += "\"name\":\"".length();
            int end = response.indexOf("\"", idx);
            if (end == -1) break;
            String name = response.substring(idx, end);
            models.add(new ModelInfo(name, name, 128_000, false));
        }

        if (models.isEmpty()) {
            models.add(new ModelInfo(defaultModel, defaultModel, 128_000, false));
        }
        return models;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String get(String path) throws Exception {
        URI uri = URI.create(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String post(String path, String body) throws Exception {
        URI uri = URI.create(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
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

    private String buildRequestBody(String model, CompletionRequest request, boolean stream) {
        StringBuilder messages = new StringBuilder();
        for (CompletionRequest.Message msg : request.getMessages()) {
            if (!messages.isEmpty()) messages.append("\\n\\n");
            messages.append(capitalize(msg.role())).append(": ").append(msg.content());
        }

        return String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":%s,\"options\":{\"temperature\":%s}}",
                model,
                escapeJson(messages.toString()),
                stream,
                request.getTemperature()
        );
    }

    private CompletionResponse parseGenerateResponse(String response, String model) {
        String text = extractJsonField(response, "\"response\":\"");
        if (text == null) text = "";
        return new CompletionResponse(unescapeJson(text), model, -1, -1, "stop");
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
        if (end == -1) return null;
        return body.substring(idx, end);
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
