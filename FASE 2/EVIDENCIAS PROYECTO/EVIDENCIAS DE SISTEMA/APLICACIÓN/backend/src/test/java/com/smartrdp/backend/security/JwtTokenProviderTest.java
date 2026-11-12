package com.smartrdp.backend.security;

import com.smartrdp.backend.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0cy0yMDI2LXNtYXJ0cmRw");
        props.setExpirationMs(86400000L);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateToken_whenValidUser_thenReturnsNonBlankToken() {
        UserDetails user = User.withUsername("jorge@rdp.cl")
                .password("pass").authorities("ROLE_BODEGUERO").build();
        String token = provider.generateToken(user);
        assertThat(token).isNotBlank();
    }

    @Test
    void validateToken_whenValidToken_thenReturnsTrue() {
        UserDetails user = User.withUsername("jorge@rdp.cl")
                .password("pass").authorities("ROLE_BODEGUERO").build();
        String token = provider.generateToken(user);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void getUsername_whenValidToken_thenReturnsEmail() {
        UserDetails user = User.withUsername("jorge@rdp.cl")
                .password("pass").authorities("ROLE_BODEGUERO").build();
        String token = provider.generateToken(user);
        assertThat(provider.getUsername(token)).isEqualTo("jorge@rdp.cl");
    }
}
