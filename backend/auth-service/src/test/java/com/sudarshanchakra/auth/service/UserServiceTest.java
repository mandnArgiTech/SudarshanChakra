package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findAll_mapsToResponses() {
        UUID id = UUID.randomUUID();
        User u = User.builder()
                .id(id)
                .farmId(UUID.randomUUID())
                .username("a")
                .email("a@x.com")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(u));

        var list = userService.findAll();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUsername()).isEqualTo("a");
    }

    @Test
    void findById_found() {
        UUID id = UUID.randomUUID();
        User u = User.builder()
                .id(id)
                .farmId(UUID.randomUUID())
                .username("u")
                .passwordHash("h")
                .role(Role.ADMIN)
                .active(true)
                .build();
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        assertThat(userService.findById(id).getUsername()).isEqualTo("u");
        assertThat(userService.findById(id).getRole()).isEqualTo("admin");
    }

    @Test
    void findById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void findByUsername_notFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.findByUsername("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void updateMqttClientId_saves() {
        User u = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("bob")
                .passwordHash("h")
                .role(Role.MANAGER)
                .active(true)
                .build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(u));

        userService.updateMqttClientId("bob", "mqtt-123");

        verify(userRepository).save(u);
        assertThat(u.getMqttClientId()).isEqualTo("mqtt-123");
    }
}
