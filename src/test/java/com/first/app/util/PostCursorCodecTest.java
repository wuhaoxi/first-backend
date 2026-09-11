package com.first.app.util;

import com.first.app.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostCursorCodecTest {

    @Test
    void encode_thenDecode_roundTripsCreatedAtAndId() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 3, 12, 0, 0, 123_000_000);

        String cursor = PostCursorCodec.encode(createdAt, 42L);

        PostCursorCodec.Cursor decoded = PostCursorCodec.decode(cursor);
        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @Test
    void encode_producesUrlSafeOutput() {
        String cursor = PostCursorCodec.encode(LocalDateTime.of(2026, 9, 3, 12, 0, 0), 1L);

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void decode_rejectsMalformedCursors() {
        List<String> malformed = List.of(
                "not-base64!!",
                "abc",
                "a|b",
                "-1",
                cursorOf("x|y"),
                cursorOf("2026-09-03T12:00:00|0"),
                cursorOf("2026-09-03T12:00:00|-5"),
                cursorOf("2026-09-03T12:00:00|abc"),
                cursorOf("2026-09-03T12:00:00|42|extra"));

        for (String cursor : malformed) {
            assertThatThrownBy(() -> PostCursorCodec.decode(cursor))
                    .as("cursor: %s", cursor)
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    private static String cursorOf(String payload) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
