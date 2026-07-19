package com.first.app.service;

import com.first.app.dto.CreateUserRequest;
import com.first.app.dto.UpdateUserRequest;
import com.first.app.entity.User;
import com.first.app.exception.DuplicateEmailException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
        if (request.getEmail() != null) {
            existing.setEmail(request.getEmail());
        }

        return userRepository.save(existing);
    }

    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}
