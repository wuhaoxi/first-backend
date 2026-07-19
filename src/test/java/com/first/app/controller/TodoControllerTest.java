package com.first.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.first.app.dto.CreateTodoRequest;
import com.first.app.dto.TodoPriority;
import com.first.app.dto.UpdateTodoRequest;
import com.first.app.entity.Todo;
import com.first.app.exception.ResourceNotFoundException;
import com.first.app.service.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TodoService todoService;

    private Todo buildTodo(Long id, String title) {
        return Todo.builder()
                .id(id).title(title).description("desc")
                .priority(TodoPriority.MEDIUM).completed(false)
                .tags(List.of("work"))
                .createdAt(LocalDateTime.of(2026,7,18,10,0))
                .updatedAt(LocalDateTime.of(2026,7,18,10,0))
                .build();
    }

    @Test
    void create_returns201() throws Exception {
        CreateTodoRequest req = new CreateTodoRequest();
        req.setTitle("Test");
        req.setTags(List.of("tag1"));

        when(todoService.create(any(CreateTodoRequest.class))).thenReturn(buildTodo(1L, "Test"));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.tags[0]").value("work"));
    }

    @Test
    void findAll_returns200() throws Exception {
        when(todoService.findAll(null)).thenReturn(List.of(buildTodo(1L, "A"), buildTodo(2L, "B")));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void findAll_filtered_returns200() throws Exception {
        when(todoService.findAll("active")).thenReturn(List.of(buildTodo(1L, "Active")));

        mockMvc.perform(get("/api/todos").param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void findById_returns200() throws Exception {
        when(todoService.findById(1L)).thenReturn(buildTodo(1L, "Found"));

        mockMvc.perform(get("/api/todos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Found"));
    }

    @Test
    void findById_notFound_returns404() throws Exception {
        when(todoService.findById(99L)).thenThrow(new ResourceNotFoundException("Todo not found with id: 99"));

        mockMvc.perform(get("/api/todos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_returns200() throws Exception {
        UpdateTodoRequest req = new UpdateTodoRequest();
        req.setTitle("Updated");

        when(todoService.update(eq(1L), any(UpdateTodoRequest.class))).thenReturn(buildTodo(1L, "Updated"));

        mockMvc.perform(put("/api/todos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
    }

    @Test
    void toggle_returns200() throws Exception {
        Todo toggled = buildTodo(1L, "Test");
        toggled.setCompleted(true);
        toggled.setCompletedAt(LocalDateTime.of(2026,7,18,12,0));

        when(todoService.toggleComplete(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/todos/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/todos/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Todo not found with id: 99"))
                .when(todoService).delete(99L);

        mockMvc.perform(delete("/api/todos/99"))
                .andExpect(status().isNotFound());
    }
}
