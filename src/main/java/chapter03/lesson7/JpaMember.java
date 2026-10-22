package chapter03.lesson7;

import jakarta.persistence.*;

/**
 * 회원 엔티티. jpa_member 테이블 한 행과 대응한다.
 *
 * @Entity: 이 클래스를 JPA가 관리하는 엔티티로 등록한다.
 * new로 만든 직후는 비영속이다. EntityManager가 persist하거나 find한 뒤에야
 * INSERT/UPDATE/DELETE 대상(영속)이 된다.
 *
 * IDENTITY 기본키는 DB가 번호를 만든다. new 직후 id는 null이고,
 * persist 때 INSERT가 나가면 DB가 준 값이 필드에 채워진다.
 */
@Entity
// @Table: 실제 테이블 이름. 생략하면 클래스 이름(JpaMember)을 테이블 이름으로 쓴다.
@Table(name = "jpa_member")
public class JpaMember {

    // @Id: 이 필드를 기본키(PK)로 쓴다. 엔티티를 구분하는 값이다.
    @Id
    // IDENTITY: MySQL AUTO_INCREMENT처럼 DB가 PK를 만든다.
    // 자바에서 new할 때는 id가 null이고, INSERT가 실행된 뒤에 DB가 준 값이 채워진다.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // @Column: 필드와 컬럼을 연결하고, DB 제약을 코드에 적는다.
    // name을 생략하면 필드 이름(nickname)이 컬럼 이름이 된다.
    // loginId처럼 자바는 카멜, DB는 스네이크일 때 name으로 맞춰 준다.
    // nullable = false → NOT NULL, unique = true → 중복 금지, length → VARCHAR 길이
    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    @Column(nullable = false, length = 30)
    private String nickname;

    // JPA는 조회 결과를 객체로 만들 때 이 기본 생성자를 리플렉션으로 호출한다.
    // 앱 코드가 빈 객체를 만들지 못하게 protected로 둔다. public도 되지만 숨기는 편이 안전하다.
    protected JpaMember() {
    }

    // 엔티티를 만드는 공식 입구. DB 제약(NOT NULL, 길이)과 같은 규칙을 자바에서도 먼저 검사한다.
    // DB만 믿으면 잘못된 값이 영속 상태까지 들어가고, flush 시점에야 예외가 난다.
    public JpaMember(String loginId, String nickname) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("loginId required");
        }

        if (loginId.length() > 30) {
            throw new IllegalArgumentException("loginId too long");
        }

        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname required");
        }

        if (nickname.length() > 30) {
            throw new IllegalArgumentException("nickname too long");
        }

        // 검사만 하고 대입하지 않으면 필드는 계속 null이다.
        // 영속화해도 INSERT 값에 이 인자가 들어가지 않는다.
        this.loginId = loginId;
        this.nickname = nickname;
    }

    public Integer getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getNickname() {
        return nickname;
    }

    // 필드를 직접 바꾸지 않고 메서드로만 바꾼다. 바꿀 때도 같은 규칙을 다시 검사한다.
    // 이 객체가 영속 상태이면, setter를 호출하지 않아도
    // 커밋(flush) 때 JPA가 변경을 감지(dirty checking)해서 UPDATE를 만든다.
    public void changeNickname(String nickname) {

        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname required");
        }

        if (nickname.length() > 30) {
            throw new IllegalArgumentException("nickname too long");
        }

        this.nickname = nickname;
    }
}
