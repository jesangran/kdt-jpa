package chapter03.lesson7;

import jakarta.persistence.*;

@Entity //식별자 반드시 필요
@Table(name = "jpa_comment")
public class JpaComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private JpaPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private JpaMember member;

    protected JpaComment(){}

    public JpaComment(JpaPost post, JpaMember member, String content) {
        if (post == null) {
            throw new IllegalArgumentException("post required");
        }
        if (member == null) {
            throw new IllegalArgumentException("member required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("content too long");
        }
        if (!post.canReceiveComments()) {
            throw new IllegalStateException("post is not published");
        }
        this.post = post;
        this.member = member;
        this.content = content;
    }

    public Integer getId() { return id; }
    public JpaPost getPost() { return post; }
    public JpaMember getMember() { return member; }
    public String getContent() { return content; }

    public void edit(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("content too long");
        }
        this.content = content;
    }

}
