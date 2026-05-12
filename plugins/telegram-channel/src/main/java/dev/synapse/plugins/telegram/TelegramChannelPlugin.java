package dev.synapse.plugins.telegram;

import dev.synapse.plugin.api.Channel;
import dev.synapse.plugin.api.InboundMessage;
import dev.synapse.plugin.api.OutboundMessage;
import dev.synapse.plugin.api.PluginContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SYNAPSE official channel plugin for Telegram.
 *
 * <p>Supports long-polling (default) and webhook modes.
 * Uses only java.net.http — no external dependencies beyond synapse-plugin-api.
 */
public class TelegramChannelPlugin implements Channel {

    private PluginContext ctx;
    private HttpClient httpClient;
    private String botToken;
    private String webhookUrl;
    private long pollingIntervalMs;
    private volatile boolean running = false;
    private ScheduledFuture<?> pollTask;
    private long lastUpdateId = 0;

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override
    public String getId() {
        return "synapse-team/telegram-channel";
    }

    @Override
    public String getName() {
        return "Telegram Channel";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getChannelId() {
        return "telegram";
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onLoad(PluginContext context) throws Exception {
        this.ctx = context;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.botToken = context.config().getSecret("bot_token");
        this.webhookUrl = context.config().getString("webhook_url", "");
        this.pollingIntervalMs = context.config().getInt("polling_interval_ms", 2000);

        ctx.logger().info("{} loaded (mode={})", getName(),
                webhookUrl.isBlank() ? "long-polling" : "webhook");
    }

    @Override
    public void onInstall() throws Exception {
        if (!webhookUrl.isBlank()) {
            setWebhook(webhookUrl);
        } else {
            startPolling();
        }
        ctx.logger().info("{} installed", getName());
    }

    @Override
    public void onMessage(InboundMessage message) throws Exception {
        ctx.logger().debug("Inbound telegram message from user={}", message.getExternalUserId());
        ctx.routeMessage(message);
    }

    @Override
    public void sendMessage(OutboundMessage message) throws Exception {
        String url = apiUrl("sendMessage");
        String json = String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\"}",
                escapeJson(message.getConversationId()),
                escapeJson(message.getText())
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            ctx.logger().warn("sendMessage failed: {}", response.body());
            throw new IOException("Telegram API returned " + response.statusCode());
        }
    }

    @Override
    public void onUninstall() throws Exception {
        if (!webhookUrl.isBlank()) {
            deleteWebhook();
        }
        stopPolling();
        ctx.logger().info("{} uninstalled", getName());
    }

    @Override
    public void onUnload() throws Exception {
        stopPolling();
        ctx.logger().info("{} unloaded", getName());
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void startPolling() {
        running = true;
        pollTask = ctx.executor().submit(this::pollLoop);
    }

    private void stopPolling() {
        running = false;
        if (pollTask != null) {
            pollTask.cancel(true);
            pollTask = null;
        }
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                pollOnce();
                Thread.sleep(pollingIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                ctx.logger().warn("Polling error: {}", e.getMessage());
                try {
                    Thread.sleep(pollingIntervalMs * 2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void pollOnce() throws Exception {
        String url = apiUrl("getUpdates?offset=" + (lastUpdateId + 1) + "&limit=100");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            ctx.logger().warn("getUpdates failed: {}", response.statusCode());
            return;
        }

        String body = response.body();
        // Minimal JSON parsing without external deps
        int updateIdx = 0;
        while ((updateIdx = body.indexOf("\"update_id\":", updateIdx)) != -1) {
            updateIdx += "\"update_id\":".length();
            int endId = body.indexOf(",", updateIdx);
            if (endId == -1) endId = body.indexOf("}", updateIdx);
            long updateId = Long.parseLong(body.substring(updateIdx, endId).trim());
            if (updateId > lastUpdateId) lastUpdateId = updateId;

            // Extract message
            int msgIdx = body.indexOf("\"message\":", updateIdx);
            if (msgIdx == -1) continue;

            String chatId = extractJsonString(body, "\"chat\":{\"id\":", msgIdx);
            String text = extractJsonString(body, "\"text\":\"", msgIdx);
            String userId = extractJsonString(body, "\"from\":{\"id\":", msgIdx);

            if (chatId != null && text != null) {
                InboundMessage msg = new InboundMessage(
                        getChannelId(),
                        userId != null ? userId : chatId,
                        chatId,
                        text,
                        Map.of("update_id", String.valueOf(updateId))
                );
                ctx.executor().submit(() -> {
                    try {
                        onMessage(msg);
                    } catch (Exception e) {
                        ctx.logger().error("Error handling message", e);
                    }
                });
            }
        }
    }

    // ── Webhook ───────────────────────────────────────────────────────────────

    private void setWebhook(String url) throws Exception {
        String api = apiUrl("setWebhook?url=" + url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void deleteWebhook() throws Exception {
        String api = apiUrl("deleteWebhook");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api))
                .GET()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String apiUrl(String method) {
        return "https://api.telegram.org/bot" + botToken + "/" + method;
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String body, String key, int fromIndex) {
        int idx = body.indexOf(key, fromIndex);
        if (idx == -1) return null;
        idx += key.length();
        int end = body.indexOf("\"", idx);
        if (end == -1) {
            // numeric value
            end = body.indexOf(",", idx);
            if (end == -1) end = body.indexOf("}", idx);
        }
        if (end == -1) return null;
        return body.substring(idx, end);
    }
}
