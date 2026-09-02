package com.first.app.dto;

import com.first.app.entity.VoteType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteRequest {

    @NotNull(message = "voteType must not be null")
    private VoteType voteType;
}
