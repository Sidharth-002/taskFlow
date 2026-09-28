package com.flowdesk.security;

import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Used exclusively by the login flow: {@code AuthenticationManager} calls
 * this (via the auto-configured {@code DaoAuthenticationProvider}) to load
 * the account and check the submitted password against
 * {@link User#getPasswordHash()} with {@code PasswordEncoder}.
 *
 * <p>This is a one-time, per-login database hit and is unrelated to how
 * subsequent authenticated requests are authorized - those go through
 * {@link JwtAuthenticationFilter}, which validates the JWT's signature
 * directly and never calls back into this service.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                // Same exception Spring Security's DaoAuthenticationProvider
                // raises for a wrong password, so the two cases are
                // indistinguishable to the caller (see GlobalExceptionHandler).
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
