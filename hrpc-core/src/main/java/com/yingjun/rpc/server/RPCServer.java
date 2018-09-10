package com.yingjun.rpc.server;

import com.yingjun.rpc.annotation.HRPCService;
import com.yingjun.rpc.codec.RPCDecoder;
import com.yingjun.rpc.codec.RPCEncoder;
import com.yingjun.rpc.protocol.RPCRequest;
import com.yingjun.rpc.protocol.RPCResponse;
import com.yingjun.rpc.registry.ServiceRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RPC Server
 *
 * 这个类在server模块的的xml文件中被使用，还传入了响应的参数，，，
 *
 *
 * InitializingBean是spring框架里面的，，InitializingBean中只有一个方法
 *
 *  BeanNameAware, BeanFactoryAware, ApplicationContextAware这三个接口，都跟感知有关系，就是aware，就是
 *  BeanNameAware:实现该接口的Bean能够在初始化时知道自己在BeanFactory中对应的名字。
 * BeanFactoryAware:实现该接口的Bean能够在初始化时知道自己所在的BeanFactory的名字。
 *
 *
 * @author yingjun
 */
public class RPCServer implements BeanNameAware, BeanFactoryAware, ApplicationContextAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(RPCServer.class);
    private ServiceRegistry serviceRegistry;
    private String serverAddress;

    //存放服务名与服务对象之间的映射关系
    private Map<String, Object> serviceBeanMap = new ConcurrentHashMap<String, Object>();
    //采用线程池，提高接口调用性能
    private static ExecutorService threadPoolExecutor;

    /**
     * 构造函数，，这个构造函数是服务端的 入口
     * ，，进入构造函数，然后就是注册这个服务，，，
     * @param serverAddress
     * @param zookeeper
     */
    public RPCServer(String serverAddress, String zookeeper) {
        this.serverAddress = serverAddress;
        serviceRegistry = new ServiceRegistry(zookeeper);
    }

    @Override
    /**
     * 这个函数也可能是某种自动调用具体还不清楚？？运行一遍就清楚了，String s会被自动注入
     * 根据调试的log知道了，setBeanName>setBeanFactory>setApplicationContext>afterPropertiesSet
     */
    public void setBeanName(String s) {
        logger.info("setBeanName() {}", s);
    }

    @Override
    /**
     * beanFactory这个参数会被，spring自动注入
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        logger.info("setBeanFactory()");
    }

    @Override
    /**
     * 这个在哪里被调用？？？？会因为ApplicationContextAware而自动调用？？
     * ApplicationContext ctx会被 spring自动注入
     *
     * 这个函数是spring框架里面的，是server端的入口函数之一
     */
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        logger.info("setApplicationContext()");
        //扫描含有@RPCService的注解类
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(HRPCService.class);//这个key和value分别是什么？bean的名字是key，bean的实例是value，所以是object对象
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                //获取接口名称
                String interfaceName = serviceBean.getClass().getAnnotation(HRPCService.class).value().getName();
                //getAnnotation(HRPCService.class)表示这个类上如果有HRPCService.class这个注解，就返回这个注解，
                //然后.value()就是注解接口，里面的方法，这里就是 Class<?> value();，，
                //然后.getName();就是得到那个类的名字，是一个string，这里就比如@HRPCService(OrderService.class)里面的OrderService这个名字
                logger.info("@HRPCService:" + interfaceName);
                //在zookeeper上注册该接口服务
                serviceRegistry.createInterfaceAddressNode(interfaceName, serverAddress);
                //本地保存该接口服务,,,
                this.serviceBeanMap.put(interfaceName, serviceBean);//暂时还不知道，为什么这么做？？？
            }
        }
    }

    @Override
    //在实例被创建时执行，后续即是init-method
    //创建netty服务
    /**
     * 按照他的说法afterPropertiesSet继承自InitializingBean类，会在创建实例后运行，，这是一个spring的机制
     *
     * 这里相当于建立了netty的服务器端，通过ServerBootstrap看出来的，它带一个server单词
     */
    public void afterPropertiesSet() throws Exception {
        logger.info("afterPropertiesSet()");
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(65536,0,4,0,0))  //基于消息头指定消息长度进行粘包拆包处理的。
                                    .addLast(new RPCDecoder(RPCRequest.class)) //这是服务器端，我接收的是RPCRequest，所有我要从二进制中解码出来，RPCRequest
                                    .addLast(new RPCEncoder(RPCResponse.class))//我给的回应是RPCResponse，所以编码是RPCResponse
                                    .addLast(new RPCServerHandler(serviceBeanMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    // 通过NoDelay禁用Nagle,使消息立即发出去，不用等待到一定的数据量才发出去
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            String[] array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);

            ChannelFuture future = bootstrap.bind(host, port).sync();

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }


    /**
     * 这个在哪里被调用？ 在netty接收到数据的回调函数里面使用
     * @param task
     */
    public static void submit(Runnable task) {
        if (threadPoolExecutor == null) {
            synchronized (RPCServer.class) {
                if (threadPoolExecutor == null) {
                    threadPoolExecutor = Executors.newFixedThreadPool(16);
                }
            }
        }
        threadPoolExecutor.submit(task);
    }

}
