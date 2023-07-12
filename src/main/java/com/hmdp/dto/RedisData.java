package com.hmdp.dto;

import lombok.Data;

import javax.xml.bind.SchemaOutputResolver;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author 时天晔
 * @data 2023/7/8
 * description:
 */
@Data
public class RedisData {
    private LocalDateTime time;
    private Object data;



}
