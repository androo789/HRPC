package com.yingjun.rpc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author yingjun
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:spring-server.xml")
public class Server1Test {

    /**
     * 这个test竟然不会退出，竟然是一直运行的？？？？为什么
     */
    @Test
    public void startServer1(){

    }

}
