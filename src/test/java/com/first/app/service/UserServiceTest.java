package com.first.app.service;

import com.first.app.dto.CreateUserRequest;
import com.first.app.dto.UpdateUserRequest;
import com.first.app.entity.User;
import com.first.app.exception.DuplicateEmailException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void create_shouldReturnSavedUser() {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .build();

        User savedUser = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        User result = userService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void create_shouldThrowDuplicateEmailException_whenEmailExists() {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("Bob")
                .email("alice@example.com")
                .build();

        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("alice@example.com");
    }

    @Test
    void findById_shouldReturnUser_whenExists() {
        User user = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void findById_shouldThrowNotFoundException_whenNotExists() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void findAll_shouldReturnListOfUsers() {
        List<User> users = List.of(
                User.builder().id(1L).name("Alice").email("alice@example.com").build(),
                User.builder().id(2L).name("Bob").email("bob@example.com").build()
        );
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void update_shouldReturnUpdatedUser() {
        User existing = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        UpdateUserRequest request = UpdateUserRequest.builder().name("Alice Updated").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Alice Updated");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void update_shouldThrowDuplicateEmailException_whenEmailAlreadyInUse() {
        User existing = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        UpdateUserRequest request = UpdateUserRequest.builder().name("Alice").email("bob@example.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail("bob@example.com"))
                .thenReturn(Optional.of(User.builder().id(2L).name("Bob").email("bob@example.com").build()));

        assertThatThrownBy(() -> userService.update(1L, request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("bob@example.com");
    }

    @Test
    void update_shouldAllowKeepingSameEmail() {
        User existing = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        UpdateUserRequest request = UpdateUserRequest.builder().name("Alice Updated").email("alice@example.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Alice Updated");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void delete_shouldSoftDeleteBySettingStatusToDeleted() {
        User user = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));

        userService.delete(1L);

        assertThat(user.getStatus()).isEqualTo(com.first.app.entity.UserStatus.DELETED);
        verify(userRepository).save(user);
    }
}
