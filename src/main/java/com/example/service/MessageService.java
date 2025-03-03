package com.example.service;

import com.example.repository.MessageRepository;
import com.example.entity.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MessageService {
    @Inject
    MessageRepository messageRepository;

    public void save(Message message){
        messageRepository.persist(message);
    }
}
