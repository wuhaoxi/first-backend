package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.CreateUserRequest;
import com.first.app.dto.UpdateUserRequest;
import com.first.app.entity.User;
import com.first.app.exception.DuplicateEmailException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void findAll_shouldReturn200WithUserList() throws Exception {
        List<User> users = List.of(
                User.builder().id(1L).name("Alice").email("alice@example.com").build(),
                User.builder().id(2L).name("Bob").email("bob@example.com").build()
        );
        when(userService.findAll()).thenReturn(users);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    @Test
    void findById_shouldReturn200WithUser() throws Exception {
        User user = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void findById_shouldReturn404_whenUserNotFound() throws Exception {
        when(userService.findById(999L)).thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id: 999"));
    }

    @Test
    void create_shouldReturn201WithCreatedUser() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("Alice")
                .email("alice@example.com")
                .build();
        User saved = User.builder().id(1L).name("Alice").email("alice@example.com").build();
        when(userService.create(any(CreateUserRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("")
                .email("alice@example.com")
                .build();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_shouldReturn409_whenDuplicateEmail() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("Bob")
                .email("alice@example.com")
                .build();
        when(userService.create(any(CreateUserRequest.class)))
                .thenThrow(new DuplicateEmailException("Email already exists: alice@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists: alice@example.com"));
    }

    @Test
    void update_shouldReturn200WithUpdatedUser() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder().name("Alice Updated").build();
        User updated = User.builder().id(1L).name("Alice Updated").email("alice@example.com").build();
        when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }
}
