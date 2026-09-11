package com.first.app.repository;

import com.first.app.TestcontainersConfiguration;
import com.first.app.entity.Post;
import com.first.app.entity.PostStatus;
import com.first.app.entity.Vote;
import com.first.app.entity.VoteType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldFindByStatusOrderByCreatedAtDesc() {
        Post draft = Post.builder()
                .title("Draft Post").content("# Draft").authorId(1L)
                .status(PostStatus.DRAFT).build();
        Post published1 = Post.builder()
                .title("First Published").content("# First").authorId(1L)
                .status(PostStatus.PUBLISHED).build();
        Post published2 = Post.builder()
                .title("Second Published").content("# Second").authorId(2L)
                .status(PostStatus.PUBLISHED).build();

        entityManager.persist(draft);
        entityManager.persist(published1);
        entityManager.persist(published2);
        entityManager.flush();

        List<Post> published = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published.get(0).getTitle()).isEqualTo("Second Published");
        assertThat(published.get(1).getTitle()).isEqualTo("First Published");
    }

    @Test
    void shouldFindByAuthorIdOrderByCreatedAtDesc() {
        Post post1 = Post.builder()
                .title("Author1 Post 1").content("# A1.1").authorId(1L)
                .status(PostStatus.PUBLISHED).build();
        Post post2 = Post.builder()
                .title("Author1 Post 2").content("# A1.2").authorId(1L)
                .status(PostStatus.DRAFT).build();
        Post post3 = Post.builder()
                .title("Author2 Post").content("# A2").authorId(2L)
                .status(PostStatus.PUBLISHED).build();

        entityManager.persist(post1);
        entityManager.persist(post2);
        entityManager.persist(post3);
        entityManager.flush();

        List<Post> author1Posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(1L);

        assertThat(author1Posts).hasSize(2);
        assertThat(author1Posts.get(0).getTitle()).isEqualTo("Author1 Post 2");
        assertThat(author1Posts.get(1).getTitle()).isEqualTo("Author1 Post 1");
    }

    @Test
    void shouldFindLatestBeforeCursor_respectingTimestampAndIdBoundary() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        Post newest = buildPublished("Newest");
        Post sameTimeLowId = buildPublished("Same Time Low Id");
        Post sameTimeHighId = buildPublished("Same Time High Id");
        Post oldest = buildPublished("Oldest");
        Post draft = buildDraft("Draft");

        persistAndFlush(newest, sameTimeLowId, sameTimeHighId, oldest, draft);
        updateCreatedAt(newest.getId(), base);
        updateCreatedAt(sameTimeLowId.getId(), base.minusHours(1));
        updateCreatedAt(sameTimeHighId.getId(), base.minusHours(1));
        updateCreatedAt(oldest.getId(), base.minusHours(2));
        updateCreatedAt(draft.getId(), base.minusMinutes(30));
        entityManager.clear();

        // Cursor at the newest post: same-timestamp posts ordered by id DESC, drafts excluded
        List<Post> firstPage = postRepository.findLatestBeforeCursor(
                PostStatus.PUBLISHED, base, newest.getId(), PageRequest.of(0, 10));
        assertThat(firstPage).extracting(Post::getId)
                .containsExactly(sameTimeHighId.getId(), sameTimeLowId.getId(), oldest.getId());

        // Cursor at the high-id post of the tied timestamp: strict comparison must not repeat it
        List<Post> secondPage = postRepository.findLatestBeforeCursor(
                PostStatus.PUBLISHED, base.minusHours(1), sameTimeHighId.getId(), PageRequest.of(0, 10));
        assertThat(secondPage).extracting(Post::getId)
                .containsExactly(sameTimeLowId.getId(), oldest.getId());

        // Cursor at the low-id post: only strictly older posts remain
        List<Post> thirdPage = postRepository.findLatestBeforeCursor(
                PostStatus.PUBLISHED, base.minusHours(1), sameTimeLowId.getId(), PageRequest.of(0, 10));
        assertThat(thirdPage).extracting(Post::getId)
                .containsExactly(oldest.getId());
    }

    @Test
    void shouldFindIdPageByUpvoteCount_orderByCountDescThenIdDesc() {
        Post twoVotesLowId = buildPublished("Two Votes Low Id");
        Post twoVotesHighId = buildPublished("Two Votes High Id");
        Post noUpVotes = buildPublished("No Up Votes");
        Post draftWithVote = buildDraft("Draft With Vote");
        persistAndFlush(twoVotesLowId, twoVotesHighId, noUpVotes, draftWithVote);

        persistVote(twoVotesLowId.getId(), 11L, VoteType.UP);
        persistVote(twoVotesLowId.getId(), 12L, VoteType.UP);
        persistVote(twoVotesHighId.getId(), 13L, VoteType.UP);
        persistVote(twoVotesHighId.getId(), 14L, VoteType.UP);
        persistVote(noUpVotes.getId(), 15L, VoteType.DOWN);
        persistVote(draftWithVote.getId(), 16L, VoteType.UP);
        entityManager.flush();
        entityManager.clear();

        Page<Long> page = postRepository.findIdPageByUpvoteCount(
                PostStatus.PUBLISHED, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .containsExactly(twoVotesHighId.getId(), twoVotesLowId.getId(), noUpVotes.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldFindByStatusOrderByCommentCountDescIdDesc() {
        Post oneComment = buildPublishedWithCommentCount("One Comment", 1);
        Post threeCommentsLowId = buildPublishedWithCommentCount("Three Comments Low Id", 3);
        Post threeCommentsHighId = buildPublishedWithCommentCount("Three Comments High Id", 3);
        Post draftManyComments = buildDraft("Draft Many Comments");
        draftManyComments.setCommentCount(9);
        persistAndFlush(oneComment, threeCommentsLowId, threeCommentsHighId, draftManyComments);
        entityManager.clear();

        Page<Post> page = postRepository.findByStatusOrderByCommentCountDescIdDesc(
                PostStatus.PUBLISHED, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Post::getId)
                .containsExactly(threeCommentsHighId.getId(), threeCommentsLowId.getId(), oneComment.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldCountByStatus() {
        persistAndFlush(buildPublished("Published 1"), buildPublished("Published 2"), buildDraft("Draft"));
        entityManager.clear();

        assertThat(postRepository.countByStatus(PostStatus.PUBLISHED)).isEqualTo(2);
        assertThat(postRepository.countByStatus(PostStatus.DRAFT)).isEqualTo(1);
    }

    private Post buildPublished(String title) {
        return Post.builder()
                .title(title).content("# " + title).authorId(1L)
                .status(PostStatus.PUBLISHED).build();
    }

    private Post buildPublishedWithCommentCount(String title, int commentCount) {
        Post post = buildPublished(title);
        post.setCommentCount(commentCount);
        return post;
    }

    private Post buildDraft(String title) {
        return Post.builder()
                .title(title).content("# " + title).authorId(1L)
                .status(PostStatus.DRAFT).build();
    }

    private void persistAndFlush(Object... entities) {
        for (Object entity : entities) {
            entityManager.persist(entity);
        }
        entityManager.flush();
    }

    private void persistVote(Long postId, Long userId, VoteType voteType) {
        entityManager.persist(Vote.builder()
                .postId(postId).userId(userId).voteType(voteType)
                .build());
    }

    private void updateCreatedAt(Long id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE posts SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
