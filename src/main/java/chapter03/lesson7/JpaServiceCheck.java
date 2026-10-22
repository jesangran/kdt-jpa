package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JpaServiceCheck {
    public static void main(String[] args) {
        int postId = 1;   // 댓글 없는 DRAFT 게시글의 실제 ID로 바꿉니다.
        int memberId = 1; // DB에 존재하는 회원의 실제 ID로 바꿉니다.

        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt")) {
            try (EntityManager em = emf.createEntityManager()) {
                // 1. 서비스와 세 리포지토리에 같은 EntityManager를 전달해 작업 환경을 공유합니다.
                PostService service = new PostService(em,
                        new JpaMemberRepository(em), new JpaPostRepository(em),
                        new JpaCommentRepository(em));

                // 2. 첫 번째 서비스 트랜잭션에서 게시글을 공개하고 별도로 커밋합니다.
                service.publish(postId);

                // 3. 공개 성공 후 두 번째 트랜잭션으로 댓글을 저장합니다. 실패해도 앞의 공개는 유지됩니다.
                service.addComment(postId, memberId, "service check");
            }

            // 4. 작업용 EntityManager를 닫은 뒤 새 조회 환경으로 DB의 최종 상태와 댓글 수를 확인합니다.
            try (EntityManager verifyEm = emf.createEntityManager()) {
                PostRepository posts = new JpaPostRepository(verifyEm);
                CommentRepository comments = new JpaCommentRepository(verifyEm);

                JpaPost post = posts.findById(postId)
                        .orElseThrow(() -> new IllegalStateException("post row missing"));
                System.out.println(post.getStatus() + "|" + comments.countByPostId(postId));
            }
        }
    }
}
