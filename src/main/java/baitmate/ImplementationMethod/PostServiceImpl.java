package baitmate.ImplementationMethod;

import baitmate.DTO.PostDto;
import baitmate.Repository.FishingLocationRepository;
import baitmate.Repository.PostRepository;
import baitmate.Repository.UserRepository;
import baitmate.Service.PostService;
import baitmate.converter.PostConverter;
import baitmate.converter.UserConverter;
import baitmate.model.Image;
import baitmate.model.Post;
import baitmate.model.User;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FishingLocationRepository fishingLocationRepository;

    @Autowired
    private PostConverter postConverter;
    @Autowired
    private UserConverter userConverter;

    @Autowired
    private DataSource dataSource;

    public List<PostDto> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        Collectors Collectors = null;
        return posts.stream()
                .map(postConverter::toDto)
                .collect(Collectors.toList());
    }

    public PostDto createPost(PostDto postDto) {
        // 1. dto -> entity
        Post post = postConverter.toEntity(postDto);

        // 2. 若前端传了 user 信息
        if (postDto.getUser() != null && postDto.getUser().getId() != null) {
            User user = userRepository.findById(postDto.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            post.setUser(user);
        }
        // 2.1 你也可设置 postTime = LocalDateTime.now() 等
        post.setPostTime(LocalDateTime.now());
        post.setLikeCount(0);
        post.setSavedCount(0);

        post.setPostTitle(postDto.getPostTitle());
        post.setPostContent(postDto.getPostContent());

        // c. 如果前端上传了 fishingLocationId
//        if (postDto.getFishingLocationId() != null) {
//            FishingLocation location = fishingLocationRepository
//                    .findById(postDto.getFishingLocationId())
//                    .orElseThrow(() -> new RuntimeException("Location not found"));
//            post.setFishingLocation(location);
//        }

        // 3. 处理 images
        List<Image> imageEntities = postConverter.toImageEntityList(postDto.getImages(), post);
        post.setImages(imageEntities);

        // 4. save
        Post saved = postRepository.save(post);

        // 5. entity -> dto
        return postConverter.toDto(saved);
    }

    public PostDto updatePost(Long postId, PostDto postDto) {
        // 1. 找到原post
        Post existing = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        // 2. 更新字段
        existing.setPostTitle(postDto.getPostTitle());
        existing.setPostContent(postDto.getPostContent());
        // ... 其他字段

        // 3. 更新 images
        //    先把旧的 images 清空或删除，再 set 新的？
        //    看业务需求，这里示例直接清空再重新设置
        existing.getImages().clear();

        List<Image> newImages = postConverter.toImageEntityList(postDto.getImages(), existing);
        existing.getImages().addAll(newImages);

        // 4. save
        Post updated = postRepository.save(existing);

        // 5. 返回
        return postConverter.toDto(updated);
    }

    // 2. 删除 Post
    public void deletePost(Long postId, Long userId) {
        // 只有发帖人可以删除
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!post.getUser().getId().equals(userId)) {
            throw new RuntimeException("You are not the owner of this post.");
        }
        postRepository.delete(post);
    }

    public PostDto getPostById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return postConverter.toDto(post);
    }

    public PostDto toggleLikePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> likedPosts = user.getLikedPosts();
        if (likedPosts.contains(post)) {
            // 已点赞 -> 取消点赞
            likedPosts.remove(post);
            post.getLikedByUsers().remove(user);
            post.setLikeCount(post.getLikeCount() - 1);
        } else {
            // 未点赞 -> 点赞
            likedPosts.add(post);
            post.getLikedByUsers().add(user);
            post.setLikeCount(post.getLikeCount() + 1);
        }

        postRepository.save(post);
        userRepository.save(user);

        // 这里需要一个带 currentUserId 的 toDto
        return postConverter.toDto(post, userId);
    }


    public PostDto toggleSavePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> savedPosts = user.getSavedPosts();

        if (savedPosts.contains(post)) {
            // 已收藏 -> 取消收藏
            savedPosts.remove(post);
            post.getSavedByUsers().remove(user);
            post.setSavedCount(post.getSavedCount() - 1);
        } else {
            // 未收藏 -> 收藏
            savedPosts.add(post);
            post.getSavedByUsers().add(user);
            post.setSavedCount(post.getSavedCount() + 1);
        }

        // 保存
        postRepository.save(post);
        userRepository.save(user);

        return postConverter.toDto(post);
    }

    @Transactional
    public byte[] getImageDataByOid(Long oid) {
        try (Connection conn = dataSource.getConnection()) {
            // 注：Spring @Transactional 默认已关了 autoCommit，
            // 但仍可手动 setAutoCommit(false) 以防万一
            conn.setAutoCommit(false);

            PGConnection pgConn = conn.unwrap(PGConnection.class);
            LargeObjectManager lobj = pgConn.getLargeObjectAPI();

            // 打开 OID 对应的大对象 (只读)
            LargeObject lo = lobj.open(oid, LargeObjectManager.READ);
            int size = (int) lo.size();
            byte[] data = new byte[size];
            lo.read(data, 0, size);
            lo.close();

            // 不要忘了提交事务
            conn.commit();

            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read large object OID=" + oid, e);
        }
    }
}
