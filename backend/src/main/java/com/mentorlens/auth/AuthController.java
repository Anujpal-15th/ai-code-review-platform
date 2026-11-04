package com.mentorlens.auth;

import com.mentorlens.NoNul;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    record Credentials(@NotBlank @NoNul @Email @Size(max = 254) String email,
                       @NotBlank @NoNul @Size(min = 8, max = 72) String password,
                       Boolean consent) {}

    // login must not apply registration password rules
    record Login(@NotBlank @NoNul @Size(max = 254) String email, @NotBlank @NoNul String password) {}

    private final UserRepository users;
    private final JwtEncoder encoder;
    private final String allowedDomain; // blank: registration is open
    private final PasswordEncoder hasher = new BCryptPasswordEncoder();
    // checked when the email is unknown, so both login failures cost the same time
    private final String dummyHash = hasher.encode("not-a-real-password");

    AuthController(UserRepository users, JwtEncoder encoder,
                   @Value("${ALLOWED_EMAIL_DOMAIN:}") String allowedDomain) {
        this.allowedDomain = allowedDomain.trim().toLowerCase();
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/register")
    Map<String, String> register(@Valid @RequestBody Credentials c) {
        if (!Boolean.TRUE.equals(c.consent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consent is required");
        }
        if (c.password().getBytes(StandardCharsets.UTF_8).length > 72) { // BCrypt limit is bytes
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is too long");
        }
        String email = normalize(c.email());
        if (!allowedDomain.isEmpty() && !email.substring(email.lastIndexOf('@') + 1).equals(allowedDomain)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email domain is not allowed");
        }
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }
        User u = new User(email, hasher.encode(c.password()));
        u.setConsentedAt(Instant.now());
        try {
            u = users.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) { // concurrent duplicate hits the unique constraint
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
        }
        return Map.of("token", token(u));
    }

    @PostMapping("/login")
    Map<String, String> login(@Valid @RequestBody Login c) {
        User u = users.findByEmail(normalize(c.email())).orElse(null);
        boolean ok = c.password().getBytes(StandardCharsets.UTF_8).length <= 72
            && hasher.matches(c.password(), u == null ? dummyHash : u.passwordHash);
        if (u == null || !ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return Map.of("token", token(u));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private String token(User u) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(u.getId().toString())
            .issuedAt(now).expiresAt(now.plus(12, ChronoUnit.HOURS)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
}
