package com.example.service;
import com.example.repository.UserRepository;
import com.example.entity.User;
import com.example.entity.UserDto;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserService {


    @Inject
    UserRepository userRepository;

    public void register(User user) {
        if(userRepository.findByEmail(user.getEmail()).isPresent()){
          throw new RuntimeException("User already exists");
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        userRepository.persist(user);
    }
    public boolean validateUser(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);
        return user.isPresent() && BCrypt.checkpw(password, user.get().getPassword());
    }

    public List<User> show() {
        return userRepository.listAll();
    }
    public String generateToken(UserDto userDto) {
        return Jwt.issuer("Levon")
                .upn(userDto.getEmail())
                .subject(userDto.getEmail())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .sign();
    }
}
