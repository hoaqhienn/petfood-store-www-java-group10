package vn.edu.dhktpm19att.group10.petfood.account;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class AccountController {
    private final Accounts accounts;
    private final PasswordEncoder passwords;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;
    private final String dummyHash;

    AccountController(Accounts accounts, PasswordEncoder passwords, SecurityContextRepository contexts,
            CsrfTokenRepository csrf) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.contexts = contexts;
        this.csrf = csrf;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/auth/register")
    ResponseEntity<?> register(@Valid @RequestBody Registration request) {
        var account = accounts.register(normalize(request.email()), request.displayName().strip(),
                passwords.encode(request.password()));
        if (account == null) return error(409, "EMAIL_EXISTS");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("user", account.summary(), "emailStatus", "PENDING"));
    }

    @PostMapping("/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody Credentials request, HttpServletRequest http,
            HttpServletResponse response) {
        var account = accounts.byEmail(normalize(request.email()));
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.passwordHash());
        if (account == null || !matches) return error(401, "INVALID_CREDENTIALS");
        if (!"ACTIVE".equals(account.status())) return error(403, "ACCOUNT_LOCKED");

        var session = http.getSession();
        if (session.getAttribute("petfood.cartKey") == null) {
            session.setAttribute("petfood.cartKey", session.getId());
        }
        http.changeSessionId();
        csrf.saveToken(null, http, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                Long.toString(account.id()), null, List.of(new SimpleGrantedAuthority("ROLE_" + account.role()))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, http, response);
        return ResponseEntity.ok(account.summary());
    }

    @GetMapping("/me")
    ResponseEntity<?> me(org.springframework.security.core.Authentication authentication) {
        var account = accounts.byId(Long.parseLong(authentication.getName()));
        if (account == null || !"ACTIVE".equals(account.status())) return error(403, "ACCOUNT_LOCKED");
        return ResponseEntity.ok(account.summary());
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", code,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> invalidInput() {
        return error(422, "INVALID_INPUT");
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE");
    }

    record Registration(@NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 64) String password) {
        @jakarta.validation.constraints.AssertTrue
        public boolean isEmailValid() { return validEmail(email); }
    }

    record Credentials(@NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password) {}

    private static boolean validEmail(String value) {
        return value != null && normalize(value).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }
}
