package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @author 时天晔
 * @data 2023/8/16
 * description:
 */

@Data
public class PageDTO {
    private List<?> list;

    private Long minTime;

    private Integer offset;

}
