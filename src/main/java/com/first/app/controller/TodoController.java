package com.first.app.controller;

import com.first.app.dto.CreateTodoRequest;
import com.first.app.dto.UpdateTodoRequest;
import com.first.app.entity.Todo;
import com.first.app.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Todo create(@Valid @RequestBody CreateTodoRequest request) {
        return todoService.create(request);
    }

    @GetMapping
    public List<Todo> findAll(@RequestParam(required = false) String status) {
        return todoService.findAll(status);
    }

    @GetMapping("/{id}")
    public Todo findById(@PathVariable Long id) {
        return todoService.findById(id);
    }

    @PutMapping("/{id}")
    public Todo update(@PathVariable Long id, @Valid @RequestBody UpdateTodoRequest request) {
        return todoService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    public Todo toggleComplete(@PathVariable Long id) {
        return todoService.toggleComplete(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        todoService.delete(id);
    }
}
