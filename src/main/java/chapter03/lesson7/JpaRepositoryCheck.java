package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.List;

public class JpaRepositoryCheck {

    public static void main(String[] args) {
        int postId = 1; // 실제 게시글 ID로 바꿉니다.
        String keyword = "JPA";

        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt");
             EntityManager em = emf.createEntityManager()) {

            // 1. 이미 만든 EntityManager를 전달해 리포지토리가 같은 조회 환경을 사용하게 합니다.
            PostRepository posts = new JpaPostRepository(em);

            // 2. ID로 한 건을 조회하고, 없으면 orElseThrow로 중단하여 null을 사용하지 않습니다.
            JpaPost post = posts.findById(postId)
                    .orElseThrow(() -> new IllegalArgumentException("post not found"));
            System.out.println("found=" + post.getTitle()); //found=JPA Start

            // 3. 단건 조회와 별도로 제목 검색 목록을 조회해 두 조건의 결과를 구분합니다.
            List<JpaPost> result = posts.findByTitleContaining(keyword);
            System.out.println("searchCount=" + result.size()); //searchCount=1
        }
    }
}
