package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.AvatarUploadResponse;
import com.first.app.dto.ProfileResponse;
import com.first.app.dto.UpdateProfileRequest;
import com.first.app.entity.UserStatus;
import com.first.app.exception.InvalidRequestException;
import com.first.app.repository.UserRepository;
import com.first.app.security.JwtAuthFilter;
import com.first.app.security.JwtService;
import com.first.app.security.StateCheckFilter;
import com.first.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private StateCheckFilter stateCheckFilter;

    private ProfileResponse buildProfileResponse() {
        return ProfileResponse.builder()
                .id(1L)
                .nickname("TravelCat")
                .email("alice@example.com")
                .avatarUrl("/api/uploads/avatars/1/avatar.jpg")
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 4, 9, 0))
                .build();
    }

    // ========== UPDATE PROFILE ==========

    @Test
    void updateProfile_shouldReturn200WithProfileResponse() throws Exception {
        when(userService.updateProfile(anyLong(), any(UpdateProfileRequest.class)))
                .thenReturn(buildProfileResponse());

        mockMvc.perform(put("/api/users/me/profile")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"TravelCat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("TravelCat"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.avatarUrl").value("/api/uploads/avatars/1/avatar.jpg"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.verificationToken").doesNotExist());
    }

    @Test
    void updateProfile_shouldReturn400_whenNicknameBlank() throws Exception {
        mockMvc.perform(put("/api/users/me/profile")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].message").value("nickname must not be blank"));
    }

    @Test
    void updateProfile_shouldReturn400_whenNicknameTooLong() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .nickname("a".repeat(101))
                .build();

        mockMvc.perform(put("/api/users/me/profile")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nickname"));
    }

    @Test
    void updateProfile_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"TravelCat\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ========== UPLOAD AVATAR ==========

    @Test
    void uploadAvatar_shouldReturn200WithAvatarUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());
        when(userService.uploadAvatar(anyLong(), any()))
                .thenReturn(new AvatarUploadResponse("/api/uploads/avatars/1/avatar.jpg"));

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/uploads/avatars/1/avatar.jpg"));
    }

    @Test
    void uploadAvatar_shouldReturn400_whenInvalidImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "fake-data".getBytes());
        when(userService.uploadAvatar(anyLong(), any()))
                .thenThrow(new InvalidRequestException("Only JPEG and PNG images are allowed"));

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file)
                        .requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG and PNG images are allowed"));
    }

    @Test
    void uploadAvatar_shouldReturn401_whenUnauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(file))
                .andExpect(status().isUnauthorized());
    }
}
