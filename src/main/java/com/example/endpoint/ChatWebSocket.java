package com.example.endpoint;
import com.example.entity.Message;
import com.example.service.MessageService;
import io.smallrye.jwt.auth.principal.DefaultJWTCallerPrincipal;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat/{email}")
public class ChatWebSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatWebSocket.class);
    @Inject
    MessageService messageService;

    @Inject
    JsonWebToken jwt;
    @Inject
    JWTParser jwtParser;

    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<Session>> groups = new ConcurrentHashMap<>();


    @OnOpen
    public void onOpen(@PathParam("email") String email, Session session) {
        String token = getTokenFromSession(session);
        if (!isValidToken(email, token)) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid JWT token"));
                LOGGER.warn("User {} failed authentication with an invalid token.", email);
            } catch (Exception ignored) {
            }
            return;
        }
        sessions.put(email, session);
        LOGGER.info("User {} has joined the chat. Total sessions: {}", email, sessions.size());
        broadcast("User " + email + " has joined the chat.");
    }



    @OnMessage
    public void onMessage(String message, @PathParam("email") String sender) {
        try {
            if (message.startsWith("@")) {
                int index = message.indexOf(":");
                if (index > 0) {
                    String receiver = message.substring(1, index).trim();
                    String content = message.substring(index + 1).trim();
                    new Thread(() -> messageSender(sender, receiver, content)).start();
                    sendPrivateMessage(sender, receiver, content);
                    return;
                }
            }
            if (message.startsWith("group")) {
                int groupIndex = message.indexOf(":");
                if (groupIndex > 0) {
                    String groupName = message.substring(5, groupIndex).trim();
                    String content = message.substring(groupIndex + 1).trim();
                    new Thread(() -> messageSender(sender, groupName, content)).start();
                    sendGroupMessage(sender, groupName, content);
                    return;
                }
            }
            if (message.startsWith("!join")) {
                String groupName = message.substring(6).trim();
                addToGroup(sender, groupName);
                Session senderSession = sessions.get(sender);
                if (senderSession != null) {
                    senderSession.getAsyncRemote().sendText("You have joined group: " + groupName);
                }
                return;
            }
            new Thread(() -> {
                messageSender(sender, "all", message);
                broadcast(sender + ": " + message);
            }).start();
        }catch(Exception e) {
            LOGGER.error("Error handling message from {}: {}", sender, e.getMessage());
        }
    }

    @OnClose
    public void onClose(@PathParam("email") String email) {
        try {
            sessions.remove(email);
            for (Set<Session> group : groups.values()) {
                group.remove(sessions.get(email));
            }
            LOGGER.info("User {} has left the chat. Total sessions: {}", email, sessions.size());
            broadcast("User " + email + " has left the chat.");
        }catch(Exception e) {
            LOGGER.error("Error handling user disconnect for {}: {}", email, e.getMessage());
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        LOGGER.error("Error with session {}: {}", session.getId(), throwable.getMessage(), throwable);
        if (session != null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Unexpected error"));
            } catch (Exception closeEx) {
                LOGGER.error("Error closing session {}: {}", session.getId(), closeEx.getMessage());
            }
        }
    }

    private void broadcast(String message) {
        sessions.values().forEach(session -> {
            session.getAsyncRemote().sendText(message, sendResult -> {
                if (sendResult.getException() != null) {
                    sendResult.getException().printStackTrace();
                }
            });
        });
    }

    private void sendPrivateMessage(String sender, String receiverId, String content) {
        Session receiverSession = sessions.get(receiverId);
        if (receiverSession != null) {
            receiverSession.getAsyncRemote().sendText("[Private] " + sender + ": " + content);
        } else {
            Session senderSession = sessions.get(sender);
            if (senderSession != null) {
                senderSession.getAsyncRemote().sendText("User " + receiverId + " is not online.");
            }
        }
    }
    private void addToGroup(String email, String groupName) {
        groups.computeIfAbsent(groupName, k -> new HashSet<>()).add(sessions.get(email));
    }
    private void sendGroupMessage(String sender, String groupName, String content) {
        Set<Session> group = groups.get(groupName);
        if (group != null) {
            for (Session session : group) {
                session.getAsyncRemote().sendText("[Group: " + groupName + "] " + sender + ": " + content);
            }
        } else {
            Session senderSession = sessions.get(sender);
            if (senderSession != null) {
                senderSession.getAsyncRemote().sendText("Group " + groupName + " does not exist.");
            }
        }
    }

    private String getTokenFromSession(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        return (params.containsKey("token") && !params.get("token").isEmpty())
                ? params.get("token").get(0)
                : null;
    }
    private boolean isValidToken(String email, String token) {
        try {
            DefaultJWTCallerPrincipal jwtPrincipal = (DefaultJWTCallerPrincipal) jwtParser.parse(token);
            String subject = jwtPrincipal.getSubject();
            return email.equals(subject);
        } catch (Exception e) {
            LOGGER.warn("Invalid token for user {}: {}", email, e.getMessage());
            return false;
        }
    }
    @Transactional
    public void messageSender(String sender, String receiver, String message) {
        Message msg = new Message(sender, receiver,message);
        messageService.save(msg);
    }
}

