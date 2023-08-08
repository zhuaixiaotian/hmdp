package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
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
        Boolean flag = redisTemplate.opsForSet().isMember(key, user.getId().toString());
        blog.setIsLike(BooleanUtil.isTrue(flag));
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
        Boolean flag = redisTemplate.opsForSet().isMember(key, user.getId().toString());
        if (BooleanUtil.isFalse(flag)) {
            // 未点赞 点赞数+1 用户存入redis
            update().setSql("liked = liked + 1").eq("id", id).update();
            redisTemplate.opsForSet().add(key, user.getId().toString());
        } else {
            // 已点赞反之
            update().setSql("liked = liked - 1").eq("id", id).update();
            redisTemplate.opsForSet().remove(key, user.getId().toString());
        }
        return Result.ok();
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
