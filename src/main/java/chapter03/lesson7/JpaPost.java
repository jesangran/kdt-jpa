package chapter03.lesson7;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 게시글 엔티티. 회원(JpaMember) 한 명에게 여러 글이 달린다. 글이 "다(N)" 쪽이다.
 *
 * [@ManyToOne vs @OneToMany]
 * - @ManyToOne: "다(N)" → "하나(1)". 단일 참조 필드. FK는 보통 이쪽(@JoinColumn)이 관리한다.
 *   예) 이 글의 member → jpa_post.member_id
 * - @OneToMany: "하나(1)" → "다(N)". 컬렉션 필드. mappedBy면 반대편(@ManyToOne)이 FK 주인이다.
 *   예) 이 글의 comments → jpa_comment.post_id 는 JpaComment.post 가 쓴다.
 * - 단방향 ManyToOne만 있어도 FK 매핑은 충분하다. OneToMany는 반대 방향 탐색·cascade/orphanRemoval용이다.
 *
 * [cascade vs orphanRemoval vs DB ON DELETE CASCADE]
 * - cascade: 부모(글)에 대한 JPA 작업(persist/merge/remove 등)을 자식(댓글)에도 그대로 전파한다.
 *   CascadeType.ALL 이면 글만 persist/remove 해도 연관 댓글까지 같이 INSERT/DELETE 된다.
 *   FK를 누가 쓰는지(@JoinColumn)와는 별개다. "작업 전파" 설정이다.
 * - orphanRemoval: 영속 부모의 컬렉션에서 빠진 자식을 "고아"로 보고 DELETE 한다.
 *   removeComment(...) 후 flush → 그 댓글 행이 삭제된다. cascade=REMOVE 와 비슷해 보이지만,
 *   부모 삭제뿐 아니라 "컬렉션에서 제거"만으로도 삭제가 난다는 점이 다르다.
 * - DB ON DELETE CASCADE: DB 제약. 부모 행이 DELETE될 때 FK로 자식 행을 DB가 지운다.
 *   JPA cascade/orphanRemoval과 역할이 겹칠 수 있지만, 동작 주체가 DB라서 별개 개념이다.
 *
 * [왜 FetchType.LAZY인가]
 * - 필요할 때만 연관(회원)을 읽기 위해서다.
 * - EAGER면 글을 조회할 때마다 회원까지 항상 같이 가져와서, 제목만 필요한 경우에도 쿼리·데이터가 늘어난다.
 * - LAZY면 글만 먼저 가져오고, getMember()처럼 실제로 쓸 때 회원을 읽는다. 안 쓰면 회원 SELECT가 안 나간다.
 * - 글을 find할 때는 회원 SELECT 없이 프록시만 넣고, getMember() 호출 때 jpa_member를 읽는다.
 * - 목록에서 작성자를 매번 쓰면 LAZY + getMember()가 N+1이 될 수 있다 → 그때는 JPQL join fetch로 한 번에 가져온다.
 * - 트랜잭션이 끝난 뒤(준영속)에 아직 안 읽은 프록시를 getMember()하면 LazyInitializationException이 날 수 있다.
 *
 * edit는 영속 객체의 title/body만 바꾼다. em.update()는 없다.
 * flush 때 스냅샷과 달라진 필드를 보고 UPDATE가 나가는 것이 dirty checking이다.
 *
 * [@Version — 낙관적 락 (Optimistic Lock)]
 * - 풀려는 문제: 두 트랜잭션이 같은 글을 읽고 각자 고친 뒤 저장하면, 나중에 커밋한 쪽이
 *   먼저 커밋한 쪽의 변경을 통째로 덮어쓸 수 있다 → lost update(갱신 유실).
 * - "낙관적"인 이유: 충돌이 드물다고 가정하고, 읽을 때는 DB 행을 잠그지 않는다.
 *   대신 저장(UPDATE) 순간에 "내가 읽었을 때의 version 과 DB 가 같은지"로 검사한다.
 * - @Version 필드가 그 검사용 숫자다. JPA 가 INSERT/UPDATE 때 값을 올리고 WHERE 에 넣는다.
 * - 검사 실패(갱신 0건) → OptimisticLockException. 앱은 다시 조회 후 수정·재시도하면 된다.
 *
 * [낙관적 vs 비관적 — "조회를 막느냐"]
 * - 핵심은 "모든 SELECT를 막느냐"가 아니라, 읽을 때 행을 잠그느냐 / 안 잠그느냐다.
 * - 낙관적(@Version): 조회할 때 잠금 없음 → 여러 트랜잭션이 같이 읽어도 됨.
 *   막는 시점은 저장(UPDATE). version 이 다르면 거절.
 * - 비관적(SELECT ... FOR UPDATE 등): 조회 시점에 행을 잠금 → 보통 다른 쪽의 수정(쓰기)을
 *   막거나 기다리게 함. (DB에 따라 일반 SELECT 스냅샷 읽기는 가능할 수 있음)
 * - 낙관적: 대기·데드락이 적고 충돌이 날 때만 예외. 비관적: 읽는 동안도 바꾸면 안 될 때.
 */
@Entity
@Table(name = "jpa_post")
public class JpaPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "body", length = 1000, nullable = false)
    private String body;

    /*
     * [@ManyToOne] N:1 — 게시글(N)이 회원(1)을 가리킨다. 필드 타입은 단건(JpaMember).
     * - FK 위치: "다" 쪽 테이블 jpa_post.member_id. 이 필드가 관계의 주인(@JoinColumn).
     * - 회원 쪽에는 posts 컬렉션이 없어도 된다 → 단방향 ManyToOne.
     *
     * fetch = LAZY (지연 로딩) — lesson7에서 LAZY를 선언한 곳.
     * - 글을 SELECT할 때 회원 행은 안 읽고, member 자리에 프록시만 둔다.
     * - getMember().getLoginId()처럼 내용에 접근할 때야 jpa_member SELECT가 나간다.
     * - 이유: 회원 정보가 필요 없는 조회까지 항상 JOIN/추가 SELECT 하지 않기 위함.
     * - 대비: @ManyToOne 기본값은 EAGER라서, 명시하지 않으면 글을 읽을 때마다 회원을 같이 읽기 쉽다.
     * - 주의: 글 N건 목록 + 각 글 getMember() → N+1. 작성자를 목록에서 같이 쓰면 join fetch.
     * - 주의: EM/트랜잭션이 닫힌 뒤 미초기화 프록시에 접근 → LazyInitializationException.
     *
     * optional = false: 회원 없는 글은 JPA 수준에서도 허용하지 않는다. (NOT NULL과 같은 뜻)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    /* @JoinColumn: jpa_post 테이블의 FK 컬럼 이름. 자바 필드명 member와 달라도 된다. */
    @JoinColumn(name = "member_id", nullable = false)
    private JpaMember member;

    /*
     * @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
     * → "글 ↔ 댓글 목록"을 아래처럼 묶는다.
     *
     * 1) mappedBy = "post"  — FK는 댓글이 씀
     *    - jpa_comment.post_id 는 JpaComment.post (@ManyToOne + @JoinColumn)가 관리한다.
     *    - 이 comments 컬렉션은 FK를 INSERT/UPDATE하지 않는다 (반대편 탐색용).
     *    - 컬렉션에 add만 하고 comment.post 가 null이면 post_id가 비게 된다.
     *    - 그래서 addComment에서 comment.getPost() == this 인지 검증한다.
     *
     * 2) cascade = CascadeType.ALL  — 글 작업을 댓글에도 전파
     *    - em.persist(post) → comments 안 새 댓글도 같이 persist (별도 em.persist(comment) 불필요).
     *    - em.remove(post)  → 연관 댓글도 같이 remove → DELETE.
     *    - cascade는 "부모(글)에 대한 작업이 있을 때"만 움직인다.
     *      컬렉션에서 빼기만 한 경우에는 관여하지 않는다.
     *
     * 3) orphanRemoval = true  — 목록에서 빼면 댓글 행 삭제
     *    - 글을 안 지워도, 영속 글의 comments에서 빠진 댓글은 고아 → DELETE.
     *    - removeComment(comment) 후 flush/commit → 그 댓글만 DELETE (글은 그대로).
     *    - cascade=REMOVE ≈ "글을 지울 때 댓글도"
     *      orphanRemoval     ≈ "목록에서 빼면 댓글 삭제"
     *    - 둘 다 JPA 동작. DB ON DELETE CASCADE와는 별개다.
     *
     * 한 줄 흐름: FK는 Comment.post / 글 persist·remove는 cascade / 목록에서 빼기는 orphanRemoval
     */
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JpaComment> comments = new ArrayList<>();

    /*
     * 게시글 상태(초안/공개/보관). private 이라 바깥에서는 직접 post.status 로 못 읽고,
     * getStatus() / publish() / archive() 같은 메서드로만 다룬다.
     * = PostStatus.DRAFT : new JpaPost(...) 할 때 필드 초기값. 생성자에 status 인자가 없어도 DRAFT로 시작한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.DRAFT; /* 기본값 */

    /*
     * [@Version] 낙관적 락용 버전 번호. JPA가 자동 관리한다. (앱에서 setVersion 하지 않는다)
     *
     * [생명주기]
     * - INSERT: 보통 0(또는 구현체에 따라 1)으로 시작. 컬럼은 DB jpa_post.version 에 있어야 한다.
     * - 엔티티를 읽으면 그 시점의 version 이 영속 객체·스냅샷에 남는다.
     * - dirty checking 으로 UPDATE 가 나갈 때 JPA가 version 을 +1 하고,
     *   WHERE 에 "읽었을 때 version"을 붙인다.
     *   예) UPDATE jpa_post SET title=?, version=1 WHERE id=? AND version=0
     * - 성공하면 DB·엔티티 version 이 함께 올라간다. 실패(0건)면 OptimisticLockException.
     *
     * [동시 수정 시나리오 — lost update 방지]
     * 1) A가 글 조회 → version=0, 제목 "old"
     * 2) B가 같은 글 조회 → version=0
     * 3) A가 제목을 "A"로 고치고 flush/commit
     *    → UPDATE ... version=1 WHERE id=? AND version=0  성공. DB version=1, title="A"
     * 4) B가 본문만 고치고 flush/commit (B가 가진 version은 여전히 0)
     *    → UPDATE ... WHERE id=? AND version=0
     *    → 이미 DB version=1 이라 매칭 0건 → OptimisticLockException
     *    → B의 저장이 A의 제목 변경을 덮어쓰지 못한다.
     * 5) B 쪽 처리: 예외 catch → 글을 다시 find → 최신 내용을 보고 수정 → 다시 저장(재시도).
     *
     * [낙관적 vs 비관적 — 조회를 막느냐?]
     * - "모든 조회를 무조건 막는다"기보다는: 읽을 때부터 잠그느냐(비관적) / 안 잠그고 쓸 때 검사하느냐(낙관적).
     * - 낙관적(@Version): 조회 시 잠금 없음 → 여러 명이 같이 읽어도 됨.
     *   막는 대상은 조회가 아니라, 저장 시 version 불일치(다른 쪽이 이미 수정한 경우).
     * - 비관적(LockModeType.PESSIMISTIC_WRITE 등): SELECT ... FOR UPDATE 로 읽자마자 행 잠금.
     *   보통 다른 트랜잭션의 수정(쓰기)·추가 잠금을 막거나 기다리게 함.
     *   DB에 따라 일반 SELECT(스냅샷 읽기)는 잠긴 행도 볼 수 있는 경우가 있다.
     *   데이터 정합성↑
     * - 낙관적: 충돌이 드물 때 유리(대기·데드락 적음). 비관적: 충돌이 잦거나 읽는 동안도 바꾸면 안 될 때.
     *
     * [@JdbcTypeCode(SqlTypes.BIGINT)]
     * - Hibernate에게 version 컬럼 JDBC/SQL 타입을 BIGINT 로 맞추라는 힌트.
     * - 낙관적 락 "동작" 자체는 @Version 이 담당한다.
     *
     * 낙관적 락은 동시성 문제를 다루는 방식이다.
     * 수정(저장) 시점에 "내가 읽은 뒤 다른 트랜잭션이 이미 바꿨는지"를 version으로 확인하고,
     * 바뀌었으면 저장을 거절해서 lost update(한쪽 변경이 덮어씌워짐)를 막는다.
     */
    @Version
    @Column(name = "version")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Long version;

    /*
     * JPA가 관리하는 version 조회용. 값을 올려서 락을 "흉내" 내는 용도가 아니다.
     * 디버깅·로그·클라이언트에 ETag처럼 현재 버전을 보여줄 때 읽기만 한다.
     */
    public Long getVersion() {
        return version;
    }

    /*
     * JpaStateCheck 의 post.getStatus() 가 호출하는 메서드.
     * JPA가 자동 생성하지 않는다. getTitle() 처럼 우리가 직접 두는 getter 다.
     * 하는 일은 private status 필드의 현재 값을 그대로 돌려주는 것뿐이다.
     */
    public PostStatus getStatus() {
        return status;
    }

    public void publish() {
        if(status != PostStatus.DRAFT) {
            throw new IllegalStateException("only draft posts can be published");
        }

        status = PostStatus.PUBLISHED;
    }

    public void archive() {
        /* 이미 보관된 글만 거절해야 한다. (!= 로 쓰면 DRAFT/PUBLISHED 일 때 바로 예외가 난다.) */
        if (status == PostStatus.ARCHIVED) {
            throw new IllegalStateException("already archived");
        }

        status = PostStatus.ARCHIVED;
    }

    public boolean canReceiveComments() {
        return status == PostStatus.PUBLISHED;
    }

    /* JPA가 조회 결과를 채울 때 쓰는 기본 생성자. 앱에서는 아래 생성자만 쓴다. */
    protected JpaPost() {
    }

    /*
     * 글을 만들 때 작성자는 필수다. member가 null이면 DB까지 가기 전에 거절한다.
     * 이때 member는 보통 이미 영속인 회원을 넘긴다. 비영속 회원을 넣으면 FK INSERT가 실패하기 쉽다.
     */
    public JpaPost(JpaMember member, String title, String body) {
        if (member == null) {
            throw new IllegalArgumentException("member required");
        }
        validate(title, body);
        this.member = member;
        this.title = title;
        this.body = body;
    }

    /* 생성과 수정이 같은 규칙을 쓰도록 한곳에 모은다. */
    private static void validate(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title required");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title too long");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body required");
        }
        if (body.length() > 1000) {
            throw new IllegalArgumentException("body too long");
        }
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    /*
     * LAZY 필드의 입구. 아직 안 읽힌 프록시면, 여기서(또는 loginId 접근 때) 회원 SELECT가 나간다.
     * EM이 열린 영속 상태에서 호출해야 한다. 닫힌 뒤·준영속이면 LazyInitializationException 가능.
     * join fetch로 이미 채워졌다면 추가 SELECT 없이 메모리의 회원을 반환한다.
     */
    public JpaMember getMember() {
        return member;
    }

    /*
     * 제목/본문만 바꾼다. 작성자(member)는 여기서 바꾸지 않는다.
     * 영속 상태의 글이면 필드 대입만으로 충분하고, flush 때 UPDATE가 나간다.
     * 준영속이면 이 대입만으로는 DB가 안 바뀐다. 그때는 merge가 필요하다.
     */
    public void edit(String title, String body) {
        validate(title, body);
        this.title = title;
        this.body = body;
    }

    /*
     * Collections.unmodifiableList(comments)
     * - 원본 comments 를 감싼 "읽기 전용 뷰"를 반환한다. (새 리스트로 전부 복사하는 것이 아님)
     * - get / size / 순회는 되고, add·remove·clear 를 호출하면 UnsupportedOperationException.
     * - 바깥에서 list.add(...) 로 직접 못 고치게 해서, 변경은 addComment / removeComment 로만 유도한다.
     * - 뷰이므로 안에서 comments 가 바뀌면, 이미 받아 둔 목록에도 그 내용이 보인다.
     */
    public List<JpaComment> getComments() {
        return Collections.unmodifiableList(comments);
    }

    /*
     * mappedBy라서 FK는 comment.post가 쓴다. 컬렉션 add만으로는 post_id가 안 채워지므로 post 일치 검증.
     * cascade=ALL이면 영속 글에 새 댓글을 넣고 persist/flush 할 때 댓글 INSERT도 같이 나간다.
     */
    public void addComment(JpaComment comment) {
        if(comment == null || comment.getPost() != this) {
            throw new IllegalArgumentException("comment post mismatch");
        }

        comments.add(comment);
    }

    /* orphanRemoval=true 이면 여기서 컬렉션에서 빠진 뒤 flush 때 해당 댓글 DELETE가 나간다. */
    public void removeComment(JpaComment comment) {
        comments.remove(comment);
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
