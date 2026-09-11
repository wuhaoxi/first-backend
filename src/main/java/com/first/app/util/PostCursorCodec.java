package com.first.app.util;

import com.first.app.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class PostCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String INVALID_CURSOR = "Invalid cursor";

    private PostCursorCodec() {
    }

    public static String encode(LocalDateTime createdAt, Long id) {
        String payload = createdAt + "|" + id;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidRequestException(INVALID_CURSOR);
        }
        try {
            String payload = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InvalidRequestException(INVALID_CURSOR);
            }
            LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new InvalidRequestException(INVALID_CURSOR);
            }
            return new Cursor(createdAt, id);
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidRequestException(INVALID_CURSOR);
        }
    }

    public record Cursor(LocalDateTime createdAt, Long id) {
    }
}
