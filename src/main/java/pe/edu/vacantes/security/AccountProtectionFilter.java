package pe.edu.vacantes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.vacantes.domain.UserAccount;
import pe.edu.vacantes.repository.UserAccountRepository;

public class AccountProtectionFilter extends OncePerRequestFilter {
    private final UserAccountRepository users;
    private final LoginAttempts attempts;

    public AccountProtectionFilter(UserAccountRepository users, LoginAttempts attempts) {
        this.users = users;
        this.attempts = attempts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            UserAccount current = users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
            if (current == null || !current.isEnabled()) {
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?disabled");
                return;
            }
        }
        boolean loginRequest = "/login".equals(request.getServletPath())
                || (request.getContextPath() + "/login").equals(request.getRequestURI());
        if ("POST".equals(request.getMethod()) && loginRequest) {
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            String ip = request.getRemoteAddr();
            if (attempts.isBlocked(ip, username)) {
                response.sendError(429);
                return;
            }
            if (username == null || username.isBlank() || username.length() > 190
                    || password == null || password.isEmpty() || password.length() > 128) {
                attempts.failure(ip, username);
                response.sendRedirect(request.getContextPath() + "/login?error");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
