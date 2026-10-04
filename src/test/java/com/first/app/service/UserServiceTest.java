package com.first.app.service;

import com.first.app.dto.AvatarUploadResponse;
import com.first.app.dto.CreateUserRequest;
import com.first.app.dto.ProfileResponse;
import com.first.app.dto.UpdateProfileRequest;
import com.first.app.dto.UpdateUserRequest;
import com.first.app.entity.User;
import com.first.app.entity.UserStatus;
import com.first.app.exception.DuplicateEmailException;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
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

    // ========== UPDATE PROFILE ==========

    private User buildProfileUser() {
        return User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashed")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void updateProfile_shouldUpdateNicknameAndReturnProfileResponse() {
        User existing = buildProfileUser();
        UpdateProfileRequest request = UpdateProfileRequest.builder().nickname("TravelCat").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse result = userService.updateProfile(1L, request);

        assertThat(result.getNickname()).isEqualTo("TravelCat");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(existing.getName()).isEqualTo("TravelCat");
        verify(userRepository).save(existing);
    }

    @Test
    void updateProfile_shouldTrimSurroundingWhitespace() {
        User existing = buildProfileUser();
        UpdateProfileRequest request = UpdateProfileRequest.builder().nickname("  TravelCat  ").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse result = userService.updateProfile(1L, request);

        assertThat(result.getNickname()).isEqualTo("TravelCat");
    }

    @Test
    void updateProfile_shouldThrowInvalidRequest_whenNicknameBlank() {
        User existing = buildProfileUser();
        UpdateProfileRequest request = UpdateProfileRequest.builder().nickname("   ").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("nickname must not be blank");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_shouldThrowInvalidRequest_whenNicknameExceeds100Characters() {
        User existing = buildProfileUser();
        UpdateProfileRequest request = UpdateProfileRequest.builder().nickname("a".repeat(101)).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateProfile(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("nickname must not exceed 100");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateProfileRequest request = UpdateProfileRequest.builder().nickname("TravelCat").build();

        assertThatThrownBy(() -> userService.updateProfile(999L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ========== UPLOAD AVATAR ==========

    @Test
    void uploadAvatar_shouldStoreJpegAndReturnUrl(@TempDir Path tempDir) throws Exception {
        ReflectionTestUtils.setField(userService, "uploadDir", tempDir.toString());

        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());

        AvatarUploadResponse result = userService.uploadAvatar(1L, file);

        assertThat(result.getAvatarUrl()).isEqualTo("/api/uploads/avatars/1/avatar.jpg");
        assertThat(existing.getAvatarUrl()).isEqualTo("/api/uploads/avatars/1/avatar.jpg");
        assertThat(tempDir.resolve("avatars/1/avatar.jpg")).exists();
        verify(userRepository).save(existing);
    }

    @Test
    void uploadAvatar_shouldUsePngExtension_forPngContentType(@TempDir Path tempDir) throws Exception {
        ReflectionTestUtils.setField(userService, "uploadDir", tempDir.toString());

        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "fake-image-data".getBytes());

        AvatarUploadResponse result = userService.uploadAvatar(1L, file);

        assertThat(result.getAvatarUrl()).isEqualTo("/api/uploads/avatars/1/avatar.png");
        assertThat(tempDir.resolve("avatars/1/avatar.png")).exists();
    }

    @Test
    void uploadAvatar_shouldResolveUploadTargetToAbsolutePath() throws Exception {
        // Tomcat's Part.write() resolves relative paths against the container temp dir,
        // so the transfer target must be absolute regardless of the configured uploadDir.
        ReflectionTestUtils.setField(userService, "uploadDir", "./target/rel-upload-test");

        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MultipartFile file = spy(new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes()));
        doNothing().when(file).transferTo(any(File.class));

        AvatarUploadResponse result = userService.uploadAvatar(1L, file);

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(file).transferTo(captor.capture());
        assertThat(captor.getValue().isAbsolute()).isTrue();
        assertThat(result.getAvatarUrl()).isEqualTo("/api/uploads/avatars/1/avatar.jpg");
    }

    @Test
    void uploadAvatar_shouldRejectNullFile() {
        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.uploadAvatar(1L, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No image file provided");
    }

    @Test
    void uploadAvatar_shouldRejectEmptyFile() {
        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No image file provided");
    }

    @Test
    void uploadAvatar_shouldRejectNonImageType() {
        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "fake-data".getBytes());

        assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only JPEG and PNG images are allowed");
    }

    @Test
    void uploadAvatar_shouldRejectOversizedImage() {
        User existing = buildProfileUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        byte[] largeData = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", largeData);

        assertThatThrownBy(() -> userService.uploadAvatar(1L, file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Image must not exceed 5MB");
    }

    @Test
    void uploadAvatar_shouldThrowNotFound_whenUserMissing() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());

        assertThatThrownBy(() -> userService.uploadAvatar(999L, file))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
