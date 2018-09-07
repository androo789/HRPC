package com.yingjun.rpc.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * RPC annotation for RPC test
 *
 * 这个注解加载服务器端，需要发布的服务类上面
 *
 * @author yingjun
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface HRPCService {
    Class<?> value();
}
