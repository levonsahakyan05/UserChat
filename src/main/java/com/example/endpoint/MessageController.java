package com.example.endpoint;

import com.example.repository.MessageRepository;
import com.example.entity.Message;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
public class MessageController {

    @Inject
    MessageRepository messageRepository;

    @GET
    @Path("/history")
    @Authenticated
    public List<Message> history(){
        return messageRepository.listAll();
    }

}
