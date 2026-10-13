package com.first.app.service;

/**
 * Published inside the create/update/delete transaction whenever a post changes. Consumers run
 * after commit (the knowledge-index listener) so they observe the committed state by post id.
 */
public record PostChangedEvent(long postId) {
}
