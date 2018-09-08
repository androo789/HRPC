package com.yingjun.rpc.manage;

import com.yingjun.rpc.client.RPCClientHandler;
import com.yingjun.rpc.codec.RPCDecoder;
import com.yingjun.rpc.codec.RPCEncoder;
import com.yingjun.rpc.protocol.RPCRequest;
import com.yingjun.rpc.protocol.RPCResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 管理所有的服务连接
 *
 * @author yingjun
 */
public class ConnectManage {

    private static final Logger logger = LoggerFactory.getLogger(ConnectManage.class);
    private volatile static ConnectManage connectManage;
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);

    private CopyOnWriteArrayList<RPCClientHandler> connectedHandlerList = new CopyOnWriteArrayList<RPCClientHandler>();
    private Map<InetSocketAddress, RPCClientHandler> connectedHandlerMap = new ConcurrentHashMap<InetSocketAddress, RPCClientHandler>();
    private Map<String, SameInterfaceRPCHandlers> interfaceAndHandlersMap = new ConcurrentHashMap<String, SameInterfaceRPCHandlers>();
    //key是接口名字，注意value是handlers是复数的，是一个服务器下面好几个地址，每一个地址搞一个连接就是一个handler，所有handler集中起来，放入SameInterfaceRPCHandlers类里面

    private final int reconnectTime = 5000;//重连时间
    private final int connecntTimeOut = 6000;//连接超时时间

    //线程控制
    private CountDownLatch countDownLatch;
    //可重入锁
    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();

    protected long connectTimeoutMillis = 6000;
    private AtomicInteger roundRobin = new AtomicInteger(0);
    private volatile boolean isRuning = true;

    private ConnectManage() {//单例
    }


    /**
     * connectManage是  volatile
     * 这是懒汉模式双重加锁
     * 为什么使用单例？？？？连接管理器只能有一个？？  因为要处理全局的事情，所以只能有一个
     * @return
     */
    public static ConnectManage getInstance() {
        if (connectManage == null) {
            synchronized (ConnectManage.class) {
                if (connectManage == null) {
                    connectManage = new ConnectManage();
                }
            }
        }
        return connectManage;
    }

    /**
     * 服务更新操作???更新啥？？？？
     *
     * @param newServerAddress  所有需要服务的所有ip
     * @param interfaceAndServerMap  key是服务，value是服务的地址
     */
    public void updateConnectedServer(Set<String> newServerAddress,
                                                   Map<String, Set<InetSocketAddress>> interfaceAndServerMap) {

        if (newServerAddress != null) {

            //整理出需要连接的服务地址，，，就是string类型set转为InetSocketAddress类型set
            Set<InetSocketAddress> newServerNodeSet = new HashSet<InetSocketAddress>();
            for (String newServerAddres : newServerAddress) {
                String[] array = newServerAddres.split(":");
                if (array.length == 2) {
                    String host = array[0];
                    int port = Integer.parseInt(array[1]);
                    InetSocketAddress socketAddress = new InetSocketAddress(host, port);
                    newServerNodeSet.add(socketAddress);
                }
            }

            //删除无效的服务
            for (int i = 0; i < connectedHandlerList.size(); i++) {//connectedHandlerList这是哪来的？？？
                RPCClientHandler handler = connectedHandlerList.get(i);//得到handler
                InetSocketAddress socketAddress = handler.getSocketAddress();//从handler里面得到地址
                if (!newServerNodeSet.contains(socketAddress)) {//如果没有就是需要删除的
                    logger.info("remove and close invalid server node: " + socketAddress);
                    handler.close();
                    connectedHandlerList.remove(handler);
                    connectedHandlerMap.remove(socketAddress);
                }
            }

            //若发现新的未创建连接的服务，则去创建连接,,,需要建立的连接的数量
            int needToConnectNum = 0;
            for (InetSocketAddress serverNodeAddress : newServerNodeSet) {
                RPCClientHandler handler = connectedHandlerMap.get(serverNodeAddress);
                if (handler == null) {//如果得到的竟然是null，就说明这个需要建立
                    needToConnectNum++;
                }
            }
            if (needToConnectNum > 0) {
                countDownLatch = new CountDownLatch(needToConnectNum);
                for (InetSocketAddress serverNodeAddress : newServerNodeSet) {
                    RPCClientHandler handler = connectedHandlerMap.get(serverNodeAddress);
                    if (handler == null) {
                        connectServerNode(serverNodeAddress);//和远端的服务器端建立连接
                    }
                }
            }
            try {
                if(countDownLatch!=null){
                    countDownLatch.await(connecntTimeOut, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            //更新 interfaceAndHandlersMap
            for (String key : interfaceAndServerMap.keySet()) {//遍历所有的接口名字
                SameInterfaceRPCHandlers handlers = new SameInterfaceRPCHandlers();//SameInterfaceRPCHandlers是自己实现的类，handlers是一个新的，现在有0个元素
                Set<InetSocketAddress> set = interfaceAndServerMap.get(key);//某一个接口名字下面所有的地址
                for (InetSocketAddress inetSocketAddress : set) {//遍历所有的地址
                    RPCClientHandler handler = connectedHandlerMap.get(inetSocketAddress);//得到这个地址对应的handler
                    if (handler != null) {
                        handlers.addHandler(handler);//添加一个handler
                    }
                }
                interfaceAndHandlersMap.put(key, handlers);//key是接口名字，value是所有的handler
            }

            logger.info("current connectedHandlerList: {}", connectedHandlerList);
            logger.info("current connectedHandlerMap: {}", connectedHandlerMap);
            logger.info("current interfaceAndHandlersMap: {}", interfaceAndHandlersMap);


        } else {
            logger.error("no available server node. all server nodes are down !!!");
            for (RPCClientHandler handler : connectedHandlerList) {
                logger.info("remove invalid server node: " + handler.getSocketAddress());
                handler.close();//关闭和服务器的连接
            }
            connectedHandlerList.clear();
            connectedHandlerMap.clear();
            interfaceAndHandlersMap.clear();
            logger.error("connectedHandlerList connectedHandlerMap interfaceAndHandlersMap has bean cleared!!!");
        }
    }


    /**
     * 创建各个服务的连接（基于netty）
     *
     *  是Bootstrap，而不是ServerBootstrap所以这是客户端的netty程序
     *
     * 就是我客户端，要一个个和远端的地址建立连接
     * @param remote  就是远端那个服务的地址（ip和port），，
     */
    private void connectServerNode(final InetSocketAddress remote) {
        logger.info("start connect to remote server: {}", remote);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connecntTimeOut)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline cp = socketChannel.pipeline();
                        cp.addLast(new RPCEncoder(RPCRequest.class));
                        cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
                        //我觉得这个4应该写到配置文件里面去，要不然以后不好改
                        cp.addLast(new RPCDecoder(RPCResponse.class));
                        cp.addLast(new RPCClientHandler() {
                            @Override
                            public void handlerCallback(RPCClientHandler handler, boolean isActive) {
                                if (isActive) {//如果连接成功!
                                    logger.info("Active: " + handler.getSocketAddress());
                                    connectedHandlerList.add(handler);//存放所有的handleer
                                    connectedHandlerMap.put(handler.getSocketAddress(), handler);//用map存地址和handler的对应关系
                                    countDownLatch.countDown();
                                } else {
                                    logger.error("Inactive: " + handler.getSocketAddress());
                                }
                            }
                        });
                    }
                });

        bootstrap.connect(remote).addListener(new ChannelFutureListener() {//bootstrap发起连接，指定我这个客户端连接到哪个远端地址上！！？？？？应该是吧，我猜这种写法没见过
            @Override
            public void operationComplete(final ChannelFuture channelFuture) throws Exception {//表示建立连接的情况!!
                if (channelFuture.isSuccess()) {
                    logger.info("success connect to remote server: {}", remote);
                } else {
                    //不停的重连，，，，按照某个重试次数和，超时时间
                    logger.info("failed connect to remote server: {} will reconnect {} millseconds later", remote, reconnectTime);
                    channelFuture.channel().eventLoop().schedule(new Runnable() {
                        @Override
                        public void run() {
                            connectServerNode(remote);
                        }
                    }, reconnectTime, TimeUnit.MILLISECONDS);
                }
            }
        });
    }


    /**
     * 根据接口名字选一个handler出来，，然后进行负载均衡
     * @param face
     * @return
     */
    public RPCClientHandler chooseHandler(String face) {
        SameInterfaceRPCHandlers handlers = interfaceAndHandlersMap.get(face);
        if (handlers != null) {
            return handlers.getSLBHandler();
        } else {
            return null;
        }

    }

    public void stop() {
        isRuning = false;
        for (int i = 0; i < connectedHandlerList.size(); ++i) {
            RPCClientHandler handler = connectedHandlerList.get(i);
            handler.close();
        }
        eventLoopGroup.shutdownGracefully();
    }
}
