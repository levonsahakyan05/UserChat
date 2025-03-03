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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat/{email}")
public class ChatWebSocket {
    @Inject
    MessageService messageService;

    @Inject
    JsonWebToken jwt;
    @Inject
    JWTParser jwtParser;

    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();


    @OnOpen
    public void onOpen(@PathParam("email") String email, Session session) {
        String token = getTokenFromSession(session);
        if (!isValidToken(email, token)) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid JWT token"));
            } catch (Exception ignored) {}
            return;
        }
        sessions.put(email, session);
        System.out.println("User " + email + " has joined the chat. Total sessions: " + sessions.size());
        broadcast("User " + email + " has joined the chat.");
    }



    @OnMessage
    public void onMessage(String message, @PathParam("email") String sender) {
        if (message.startsWith("@")) {
            int index = message.indexOf(":");
            if (index > 0) {
                String receiverId = message.substring(1, index).trim();
                String content = message.substring(index + 1).trim();
                new Thread(() -> messageSender(sender, receiverId, content)).start();
                sendPrivateMessage(sender, receiverId, content);
                return;
            }
        }
        new Thread(() -> {
            messageSender(sender, "all", message);
            broadcast(sender + ": " + message);
        }).start();
    }


    @OnClose
    public void onClose(@PathParam("email") String email) {
        sessions.remove(email);
        System.out.println("User " + email + " has left the chat. Total sessions: " + sessions.size());
        broadcast("User " + email + " has left the chat.");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
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
            System.out.println("Invalid token: " + e.getMessage());
            return false;
        }
    }
    @Transactional
    public void messageSender(String sender, String receiver, String message) {
        Message msg = new Message(sender, receiver,message);
        messageService.save(msg);
    }
}

