package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisDate {
    public LocalDateTime localDateTime;
    public Object shop;
}
