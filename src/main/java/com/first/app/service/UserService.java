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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private final UserRepository userRepository;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public User create(CreateUserRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new DuplicateEmailException("Email already exists: " + request.getEmail());
        });

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .build();

        return userRepository.save(user);
    }

    public User update(Long id, UpdateUserRequest request) {
        User existing = findById(id);

        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(existing.getEmail())) {
            userRepository.findByEmail(request.getEmail()).ifPresent(other -> {
                throw new DuplicateEmailException("Email already exists: " + request.getEmail());
            });
            existing.setEmail(request.getEmail());
        }

        return userRepository.save(existing);
    }

    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }

    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findById(userId);

        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (nickname.isEmpty()) {
            throw new InvalidRequestException("nickname must not be blank");
        }
        if (nickname.length() > 100) {
            throw new InvalidRequestException("nickname must not exceed 100 characters");
        }

        user.setName(nickname);
        userRepository.save(user);
        return ProfileResponse.from(user);
    }

    public AvatarUploadResponse uploadAvatar(Long userId, MultipartFile file) {
        User user = findById(userId);

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No image file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new InvalidRequestException("Only JPEG and PNG images are allowed");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new InvalidRequestException("Image must not exceed 5MB");
        }

        try {
            String extension = contentType.equals("image/jpeg") ? "jpg" : "png";
            // Tomcat's Part.write() resolves relative paths against its own temp dir,
            // so the transfer target must be absolute regardless of the configured uploadDir.
            Path uploadPath = Paths.get(uploadDir, "avatars", userId.toString()).toAbsolutePath();
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve("avatar." + extension);
            file.transferTo(filePath.toFile());

            String url = "/api/uploads/avatars/" + userId + "/avatar." + extension;
            user.setAvatarUrl(url);
            userRepository.save(user);
            return new AvatarUploadResponse(url);
        } catch (IOException e) {
            throw new InvalidRequestException("Failed to upload avatar: " + e.getMessage());
        }
    }
}
