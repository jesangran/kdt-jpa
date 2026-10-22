package chapter03.lesson7;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.List;

/**
 * 댓글이 가리키는 게시글·회원과, 게시글 상태 조건을 한 영속성 컨텍스트에서 조회한다.
 * 값을 바꾸지 않으므로 begin/commit이 없다.
 * Hibernate는 EntityManager가 열려 있으면 트랜잭션 없이도 SELECT를 실행하고,
 * 엔티티 결과는 이 EM의 영속성 컨텍스트에 영속 상태로 올린다.
 *
 * JpaComment는 연관의 주인이다. 댓글 테이블에 post_id, member_id가 있고
 * 둘 다 ManyToOne + LAZY다. JpaPost 쪽에는 댓글 컬렉션이 없다.
 * 지연 로딩이라 댓글만 읽으면 post와 member 자리에는 프록시만 들어간다.
 * getLoginId()나 게시글 제목처럼 실제 필드에 손대면, 컨텍스트가 살아 있을 때 추가 SELECT가 나간다.
 *
 * 1번 쿼리의 join fetch는 그 추가 SELECT를 처음 SQL 한 번으로 합친다.
 * fetch로 읽은 댓글, 회원, 게시글은 모두 같은 1차 캐시에 PK로 등록된다.
 * 같은 게시글 id를 가리키는 댓글 여러 건은 서로 다른 JpaPost가 아니라 캐시 안의 그 인스턴스 하나를 공유한다.
 * 이 파일은 조회 사이에 em.clear()를 호출하지 않는다. 1번에서 올린 엔티티는 3번까지 영속으로 남아 있다.
 *
 * JPQL의 c.post.id, p.status는 테이블 컬럼이 아니라 필드 경로다.
 * c.post.id는 댓글의 post_id(FK)와 같고, p.status는 @Enumerated(EnumType.STRING)이라 DB에는 "DRAFT" 같은 문자로 있다.
 */
public class JpaRelationQuery {
    public static void main(String[] args) {

        // jpa_comment.post_id와 비교할 게시글 PK. 시드와 다르면 실제 id로 바꾼다.
        int postId = 1;
        // 2번 count가 본문 전체와 비교할 값. 1번의 전체 댓글 수와 다를 수 있다.
        String content = "first comment";

        // 팩토리는 "kdt" 유닛을 읽고, EM 하나가 이 세 조회가 공유하는 영속성 컨텍스트를 만든다.
        // 닫는 순서는 역순이라 em.close()가 먼저다. 닫히면 올려 둔 댓글·회원·게시글이 모두 준영속이 된다.
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("kdt");
             EntityManager em = emf.createEntityManager()) {
            // 1. postId 게시글의 댓글을, 작성자와 게시글까지 한 SQL로 가져온다.
            // select 대상은 c(JpaComment)다. 결과 타입도 JpaComment.class다.
            // join fetch c.member, join fetch c.post는 inner join이면서 두 연관의 컬럼까지 같이 SELECT한다.
            // 그래서 결과 댓글의 member, post는 빈 프록시가 아니라 필드가 채워진 영속 엔티티다.
            // 연관이 둘 다 ManyToOne이라 댓글 한 건이 회원 한 명, 게시글 한 건과만 만난다.
            // 컬렉션 fetch join이 아니므로 댓글 행이 결과에서 중복되지 않는다.
            // where c.post.id = :postId는 댓글이 가리키는 게시글 PK다. SQL에서는 jpa_comment.post_id = ? 가 된다.
            // 실행은 getResultList()다. 이 호출 때 SELECT가 나가고, 댓글·회원·게시글이 1차 캐시에 등록된다.
            List<JpaComment> comments = em.createQuery(
                            "select c from JpaComment c "
                                    + "join fetch c.member join fetch c.post "
                                    + "where c.post.id = :postId order by c.id",
                            JpaComment.class)
                    .setParameter("postId", postId)
                    .getResultList();
            // 이 게시글에 달린 댓글 전체 건수다. 내용이 "first comment"인 것만 세지 않는다.
            System.out.println("commentRows=" + comments.size());

            for (JpaComment comment : comments) {
                // fetch로 이미 채워져 있으므로 getLoginId(), getId()에서 회원·게시글 SELECT가 추가되지 않는다.
                // 이 리스트의 getPost()는 전부 캐시에 있는 같은 JpaPost 인스턴스다.
                System.out.println(comment.getId() + "|"
                        + comment.getMember().getLoginId() + "|"
                        + comment.getPost().getId() + "|" + comment.getContent());
            }
            // 2. 같은 게시글이면서 내용이 content와 완전히 같은 댓글만 집계한다.
            // select count(c)는 엔티티가 아니라 숫자 하나다. JpaComment를 생성하거나 1차 캐시에 올리지 않는다.
            // 1번에서 이미 영속인 댓글이 있어도 count는 그 메모리를 세지 않고 DB로 SELECT count(...)를 보낸다.
            // 결과 타입이 Long.class인 이유다. 집계는 0건이어도 행이 하나(값 0)라 getSingleResult()가 예외를 던지지 않는다.
            // c.post.id는 역시 post_id 컬럼 비교다. fetch join이 없어 회원·게시글 테이블을 읽지 않는다.
            // 조건이 1번보다 좁으므로 matchingContentCount는 commentRows보다 작거나 같다.

            Long count = em.createQuery(
                            "select count(c) from JpaComment c "
                                    + "where c.post.id = :postId and c.content = :content",
                            Long.class)
                    .setParameter("postId", postId)
                    .setParameter("content", content)
                    .getSingleResult();
            System.out.println("matchingContentCount=" + count);

            // 3. 상태가 DRAFT가 아닌 게시글만 id 오름차순으로 조회한다.
            // PostStatus는 EnumType.STRING이라 파라미터 DRAFT는 정수 0이 아니라 문자열 "DRAFT"로 바인딩된다.
            // SQL은 status <> 'DRAFT' 이고, PUBLISHED와 ARCHIVED가 남는다.
            // 컬럼이 nullable = false라 상태가 비어 있는 행은 없다. SQL에서 NULL <> 'DRAFT'는 참이 아니므로,
            // 널이 허용됐다면 그 행은 이 조건에 포함되지 않는다.
            // select가 p뿐이고 join fetch가 없다. 새로 읽는 게시글의 member는 지연 프록시다.
            // 여기서는 size만 출력해서 getMember()를 호출하지 않으므로 회원 SELECT는 나가지 않는다.
            // 1번 fetch로 이미 캐시에 있는 같은 id의 게시글은 새 객체가 아니라 그 영속 인스턴스가 결과에 들어간다.
            // 이 메서드는 필드를 고치지 않았으므로 캐시 값과 DB 값이 같다.
            List<JpaPost> notDraft = em.createQuery(
                            "select p from JpaPost p "
                                    + "where p.status <> :excludedStatus order by p.id",
                            JpaPost.class)
                    .setParameter("excludedStatus", PostStatus.DRAFT)
                    .getResultList();
            System.out.println("notDraftCount=" + notDraft.size());


            /*1. select c from JpaComment c join fetch c.member join fetch c.post where c.post.id = :postId order by c.id
            *2. select count(c) from JpaComment c where c.post.id = :postId and c.content = :content
            * 3. select p from JpaPost p where p.status <> :excludedStatus order by p.id
            * */


        }
    }
}