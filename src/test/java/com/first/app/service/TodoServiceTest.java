package com.first.app.service;

import com.first.app.dto.CreateTodoRequest;
import com.first.app.dto.TodoPriority;
import com.first.app.dto.UpdateTodoRequest;
import com.first.app.entity.Todo;
import com.first.app.exception.InvalidRequestException;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoService todoService;

    private Todo buildTodo(String title) {
        return Todo.builder()
                .id(1L)
                .title(title)
                .description("desc")
                .priority(TodoPriority.MEDIUM)
                .completed(false)
                .tags(List.of("work"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void create_success() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Buy groceries");
        req.setPriority(TodoPriority.HIGH);
        req.setTags(List.of("shopping"));

        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> {
            Todo t = inv.getArgument(0);
            t.setId(1L);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            return t;
        });

        Todo result = todoService.create(req);
        assertEquals("Buy groceries", result.getTitle());
        assertEquals(TodoPriority.HIGH, result.getPriority());
        assertFalse(result.getCompleted());
        assertEquals(List.of("shopping"), result.getTags());
    }

    @Test
    void create_pastDueDate_throws() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setDueDate(LocalDateTime.now().minusHours(1));

        assertThrows(InvalidRequestException.class, () -> todoService.create(req));
    }

    @Test
    void create_tagTooLong_throws() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(List.of("a".repeat(51)));

        assertThrows(InvalidRequestException.class, () -> todoService.create(req));
    }

    @Test
    void create_tooManyTags_throws() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(Arrays.asList("t1","t2","t3","t4","t5","t6","t7","t8","t9","t10","t11"));

        assertThrows(InvalidRequestException.class, () -> todoService.create(req));
    }

    @Test
    void create_tagWithComma_throws() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(List.of("work,urgent"));

        assertThrows(InvalidRequestException.class, () -> todoService.create(req));
    }

    @Test
    void create_blankTagsFiltered() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(List.of("  ", "work", ""));

        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        Todo result = todoService.create(req);
        assertEquals(List.of("work"), result.getTags());
    }

    @Test
    void create_tagsTrimmed() {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(List.of("  work  ", " urgent "));

        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        Todo result = todoService.create(req);
        assertEquals(List.of("work", "urgent"), result.getTags());
    }

    @Test
    void findAll_all() {
        when(todoRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(buildTodo("A"), buildTodo("B")));

        List<Todo> result = todoService.findAll(null);
        assertEquals(2, result.size());
    }

    @Test
    void findAll_active() {
        when(todoRepository.findAllByCompletedOrderByCreatedAtDesc(false))
                .thenReturn(List.of(buildTodo("Active")));

        List<Todo> result = todoService.findAll("active");
        assertEquals(1, result.size());
    }

    @Test
    void findAll_completed() {
        when(todoRepository.findAllByCompletedOrderByCreatedAtDesc(true))
                .thenReturn(Collections.emptyList());

        List<Todo> result = todoService.findAll("completed");
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_invalidStatus_throws() {
        assertThrows(InvalidRequestException.class, () -> todoService.findAll("invalid"));
    }

    @Test
    void findById_exists() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(buildTodo("Found")));

        Todo result = todoService.findById(1L);
        assertEquals("Found", result.getTitle());
    }

    @Test
    void findById_notFound_throws() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> todoService.findById(99L));
    }

    @Test
    void update_success() {
        Todo existing = buildTodo("Old");
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTitle("Updated");
        req.setTags(List.of("new-tag"));

        Todo result = todoService.update(1L, req);
        assertEquals("Updated", result.getTitle());
        assertEquals(List.of("new-tag"), result.getTags());
    }

    @Test
    void update_notFound_throws() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> todoService.update(99L, new UpdateTodoRequest()));
    }

    @Test
    void update_tagTooLong_throws() {
        Todo existing = buildTodo("Old");
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTags(List.of("a".repeat(51)));

        assertThrows(InvalidRequestException.class, () -> todoService.update(1L, req));
    }

    @Test
    void update_tagWithComma_throws() {
        Todo existing = buildTodo("Old");
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTags(List.of("work,urgent"));

        assertThrows(InvalidRequestException.class, () -> todoService.update(1L, req));
    }

    @Test
    void update_tagsTrimmedAndFiltered() {
        Todo existing = buildTodo("Old");
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTags(List.of("  work  ", "", " urgent "));

        Todo result = todoService.update(1L, req);
        assertEquals(List.of("work", "urgent"), result.getTags());
    }

    @Test
    void toggle_toComplete() {
        Todo existing = buildTodo("Test");
        existing.setCompleted(false);
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        Todo result = todoService.toggleComplete(1L);
        assertTrue(result.getCompleted());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void toggle_toIncomplete() {
        Todo existing = buildTodo("Test");
        existing.setCompleted(true);
        existing.setCompletedAt(LocalDateTime.now());
        when(todoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(todoRepository.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        Todo result = todoService.toggleComplete(1L);
        assertFalse(result.getCompleted());
        assertNull(result.getCompletedAt());
    }

    @Test
    void toggle_notFound_throws() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> todoService.toggleComplete(99L));
    }

    @Test
    void delete_success() {
        when(todoRepository.existsById(1L)).thenReturn(true);

        todoService.delete(1L);
        verify(todoRepository).deleteById(1L);
    }

    @Test
    void delete_notFound_throws() {
        when(todoRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> todoService.delete(99L));
    }
}
