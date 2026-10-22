package chapter03.lesson7;

import jakarta.persistence.EntityManager;

public class PostService {
    private final EntityManager em;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public PostService(EntityManager em, MemberRepository memberRepository, PostRepository postRepository, CommentRepository commentRepository) {
        this.em = em;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    public void publish(int postId){
        em.getTransaction().begin();

        try{
          JpaPost post =  postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("post not found"));
          post.publish();
          em.getTransaction().commit();
        } catch (RuntimeException e) {
           if(em.getTransaction().isActive()){
               em.getTransaction().rollback();
           }

           em.clear();
           throw e;
        }
    }

    public void addComment(int postId, int memberId, String content){
        em.getTransaction().begin();

        try {
            JpaPost post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("post not found"));
            JpaMember member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("member not found"));
            JpaComment comment = new JpaComment(post, member, content);

            commentRepository.save(comment);

            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if(em.getTransaction().isActive()){
                em.getTransaction().rollback();
            }

            em.clear();
            throw e;
        }


    }
}
