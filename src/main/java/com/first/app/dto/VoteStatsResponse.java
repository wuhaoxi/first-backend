package com.first.app.dto;

import com.first.app.entity.VoteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteStatsResponse {

    private long upCount;
    private long downCount;
    private VoteType userVote;
}
