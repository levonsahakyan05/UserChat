package com.example.endpoint;
import com.example.entity.UserDto;
import com.example.service.UserService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import com.example.entity.User;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserController {

    @Inject
    UserService userService;

    @Authenticated
    @GET
    public List<User> getUsers() {
        return userService.show();
    }
    @POST
    @Path("/register")
    @PermitAll
    @Transactional
    public Response register(User user) {
        userService.register(user);
        return Response.ok().build();
    }
    @POST
    @Path("/login")
    @PermitAll
    @Transactional
    public Response login(UserDto userDto) {
        if(userService.validateUser(userDto.getEmail(), userDto.getPassword())) {
            String token = userService.generateToken(userDto);
            return Response.ok().entity(token).build();
        }
        return Response.status(401).build();
    }
}
