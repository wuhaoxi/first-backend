package com.first.app.service;

import com.first.app.dto.CreateTodoRequest;
import com.first.app.dto.UpdateTodoRequest;
import com.first.app.entity.Todo;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    public Todo create(CreateTodoRequest request) {
        validateDueDate(request.getDueDate());
        List<String> tags = sanitizeTags(request.getTags());
        validateTags(tags);

        Todo todo = Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : com.first.app.dto.TodoPriority.MEDIUM)
                .dueDate(request.getDueDate())
                .tags(tags)
                .build();

        return todoRepository.save(todo);
    }

    public List<Todo> findAll(String status) {
        if (status == null || status.isEmpty() || "all".equals(status)) {
            return todoRepository.findAllByOrderByCreatedAtDesc();
        } else if ("active".equals(status)) {
            return todoRepository.findAllByCompletedOrderByCreatedAtDesc(false);
        } else if ("completed".equals(status)) {
            return todoRepository.findAllByCompletedOrderByCreatedAtDesc(true);
        }
        throw new InvalidRequestException("status must be one of: all, active, completed");
    }

    public Todo findById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Todo not found with id: " + id));
    }

    public Todo update(Long id, UpdateTodoRequest request) {
        Todo todo = findById(id);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new InvalidRequestException("title must not be blank");
            }
            todo.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            todo.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            todo.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            validateDueDate(request.getDueDate());
            todo.setDueDate(request.getDueDate());
        }
        if (request.getTags() != null) {
            List<String> tags = sanitizeTags(request.getTags());
            validateTags(tags);
            todo.setTags(tags);
        }

        return todoRepository.save(todo);
    }

    public Todo toggleComplete(Long id) {
        Todo todo = findById(id);
        if (Boolean.TRUE.equals(todo.getCompleted())) {
            todo.setCompleted(false);
            todo.setCompletedAt(null);
        } else {
            todo.setCompleted(true);
            todo.setCompletedAt(LocalDateTime.now());
        }
        return todoRepository.save(todo);
    }

    public void delete(Long id) {
        if (!todoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Todo not found with id: " + id);
        }
        todoRepository.deleteById(id);
    }

    private void validateDueDate(LocalDateTime dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("dueDate must not be in the past");
        }
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private void validateTags(List<String> tags) {
        if (tags != null) {
            if (tags.size() > 10) {
                throw new InvalidRequestException("tags must not exceed 10 items");
            }
            for (String tag : tags) {
                if (tag.isEmpty()) {
                    throw new InvalidRequestException("each tag must not be blank");
                }
                if (tag.length() > 50) {
                    throw new InvalidRequestException("each tag must not exceed 50 characters");
                }
                if (tag.contains(",")) {
                    throw new InvalidRequestException("tags must not contain commas");
                }
            }
        }
    }
}
