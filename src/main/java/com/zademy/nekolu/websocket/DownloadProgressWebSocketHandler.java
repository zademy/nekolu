/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.websocket;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * WebSocket handler that pushes real-time download progress events to connected clients.
 * <p>
 * Clients connect to {@code /ws/download-progress} and send a subscription message:
 * <pre>{"fileId": 12345}</pre>
 * The handler then pushes progress JSON payloads until the download completes or the session closes.
 */
@Component
public class DownloadProgressWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProgressWebSocketHandler.class);

    private final Map<Long, List<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public DownloadProgressWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            var node = objectMapper.readTree(message.getPayload());
            if (node.has("fileId")) {
                long fileId = node.get("fileId").asLong();
                subscriptions.computeIfAbsent(fileId, k -> new CopyOnWriteArrayList<>()).add(session);
                logger.debug("WS session {} subscribed to fileId {}", session.getId(), fileId);
            }
        } catch (Exception e) {
            logger.warn("Invalid WS subscription message from {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.values().forEach(sessions -> sessions.remove(session));
        logger.debug("WS session {} closed: {}", session.getId(), status);
    }

    /**
     * Broadcasts a progress update to all sessions subscribed to the given fileId.
     *
     * @param fileId the file being downloaded
     * @param progress the progress payload (will be serialized to JSON)
     */
    public void broadcastProgress(long fileId, Object progress) {
        List<WebSocketSession> sessions = subscriptions.get(fileId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(progress);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        logger.warn("Failed to send WS progress to {}: {}", session.getId(), e.getMessage());
                        sessions.remove(session);
                    }
                } else {
                    sessions.remove(session);
                }
            }
        } catch (Exception e) {
            logger.error("Error broadcasting WS progress for fileId {}: {}", fileId, e.getMessage());
        }
    }

    /**
     * Notifies all subscribed sessions that the download is complete, then removes subscriptions.
     *
     * @param fileId the completed file ID
     * @param completionPayload the final payload to send
     */
    public void broadcastCompletion(long fileId, Object completionPayload) {
        broadcastProgress(fileId, completionPayload);
        subscriptions.remove(fileId);
    }
}
