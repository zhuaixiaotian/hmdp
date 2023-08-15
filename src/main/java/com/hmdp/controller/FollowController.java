package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followedId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followedId,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followedId) {
        return followService.isFollow(followedId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long followedId) {
        return followService.common(followedId);
    }
}
