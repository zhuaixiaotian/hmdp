package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (ObjectUtils.isEmpty(blog)) {
            return Result.fail("笔记不存在");
        }
        setUserInfo(blog);
        isLiked(blog);
        return Result.ok(blog);
    }

    private void isLiked(Blog blog) {
        String key = "blog:" + blog.getId();
        UserDTO user = UserHolder.getUser();
        // 判断是否点赞
        Double score = redisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setUserInfo(blog);
            isLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        String key = "blog:" + id;
        UserDTO user = UserHolder.getUser();
        // 判断是否点赞
        Double score = redisTemplate.opsForZSet().score(key, user.getId().toString());
        if (ObjectUtils.isEmpty(score)) {
            // 未点赞 点赞数+1 用户存入redis
            update().setSql("liked = liked + 1").eq("id", id).update();
            redisTemplate.opsForZSet().add(key, user.getId().toString(), System.currentTimeMillis());
        } else {
            // 已点赞反之
            update().setSql("liked = liked - 1").eq("id", id).update();
            redisTemplate.opsForZSet().remove(key, user.getId().toString());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:" + id;
        // 从redis中取出用户id
        Set<String> range = redisTemplate.opsForZSet().range(key, 0, 4);
        if (ObjectUtils.isEmpty(range)) {
            return Result.ok(Collections.emptyList());
        }
        Set<Long> collect = range.stream().map(Long::valueOf).collect(Collectors.toSet());
        String join = StrUtil.join(",", collect);
        List<User> users = userService.query().in("id", collect).last("order by field (id," + join + " )").list();
        List<UserDTO> collect1 = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect1);

        // 数据库中查询
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean flag = save(blog);
        if (flag) {
            // 查询当前用户粉丝
            List<Follow> fans = followService.query().eq("follow_user_id", user.getId()).list();
            for (Follow fan : fans) {
                // 收件箱为sortedset
                Long userId = fan.getUserId();
                String key = "fans:" + userId;
                redisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            }
        }

        return Result.ok(blog.getId());
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
