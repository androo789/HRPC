package com.yingjun.rpc.registry;

import com.yingjun.rpc.manage.ConnectManage;
import com.yingjun.rpc.utils.Config;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * 服务发现
 * <p>
 * 需要的功能
 * 1
 * 连接zk集群
 * 2
 * 关闭对zk的连接
 * 3
 * 注册孩子监听器，获取孩子列表
 *
 * zk改curator以后，代码量并没有减少，还踩了一个curator的坑，但是代码结构上更明确了，，但是可能延时很慢，很有可能
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private CountDownLatch latch = new CountDownLatch(1);
    private String address;
    private CuratorFramework client;

    private List<String> interfaces;//我这个客户端需要的接口

    /**
     * 需要看看zk zpi才好懂,,涉及到watch
     * 构造函数，，，先连接zk
     * 再注册监听器
     *
     * @param address
     * @param interfaces
     */
    public ServiceDiscovery(String address, List<String> interfaces) {
        this.address = address;
        this.interfaces = interfaces;
        connectServer();

        registerWatcher4Node();//为根节点和接口节点注册监听器

        if (client != null) {
            watchNode();
        }
    }

    /**
     *
     */
    private void watchNode() {
        try {
            logger.info("invoke watchNode() ");
            List<String> interfaceList = client.getChildren().forPath(Config.ZK_ROOT_PATH);//interfaceList这个是所有zk上的服务接口
//            List<String> interfaceList = zookeeper.getChildren(Config.ZK_ROOT_PATH, rootWatcher);//interfaceList这个是所有zk上的服务接口
            Set<String> dataSet = new HashSet<String>();//所有我需要的！！！接口的，所有的地址的，集合，，，这是干啥的？
            Map<String, Set<InetSocketAddress>> interfaceAndServerMap = new HashMap<String, Set<InetSocketAddress>>();//key是接口名称，value是这个接口的所有地址
            for (final String face : interfaceList) {
                if (interfaces.contains(face)) {//我只处理我这个cilent需要的接口
//                    registerWatcher4Node(Config.ZK_ROOT_PATH + "/" + face);//这里逻辑上过不去，需要反复注册监听器？？？？？

                    List<String> addressList = client.getChildren().forPath(Config.ZK_ROOT_PATH + "/" + face);//每一次服务接口可能有多个"地址孩子节点",所以是一个list
//                    List<String> addressList = zookeeper.getChildren(Config.ZK_ROOT_PATH + "/" + face, childdrenWatcher);//每一次服务接口可能有多个"地址孩子节点",所以是一个list
                    Set<InetSocketAddress> set = new HashSet<InetSocketAddress>();//InetSocketAddress是ip和port的合体
                    for (String s : addressList) {//某个服务下面，所有的地址，地址用ip和port表示
                        dataSet.add(s);
                        String[] array = s.split(":");
                        if (array.length == 2) {
                            String host = array[0];
                            int port = Integer.parseInt(array[1]);
                            set.add(new InetSocketAddress(host, port));
                        }
                    }
                    interfaceAndServerMap.put(face, set);
                }
            }
            logger.info("node data 所有我需要的！！！接口的，所有的地址的，集合: {}", dataSet);
            logger.info("interfaceAndServerMap data，这是需要的接口的map: {}", interfaceAndServerMap);
            logger.info("Service discovery triggered updating connected server node");
            //更新连接服务
            ConnectManage.getInstance().updateConnectedServer(dataSet, interfaceAndServerMap);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }


    /**
     * 一次函数，一次性搞定，不需要反复注册watcher
     * 注册监听器，为了监听孩子节点的变化,,对于根或者对于接口
     *
     * 对于每一个节点，都有一个专属的监听器，在代码里表现出来就是，，  new匿名内部类的实例,,,而不是只用一个监听器，，不知道用一个监听器行不行??可能会有并发问题？？
     */
    private void registerWatcher4Node() {
        //监听root
        PathChildrenCache cacheOfRoot = new PathChildrenCache(client, Config.ZK_ROOT_PATH, false);//我暂时不需要缓存节点里面的数据????存疑
        try {
            cacheOfRoot.start(StartMode.BUILD_INITIAL_CACHE);//只有这种模式是阻塞的,会阻塞到这里直到缓存保存完了，再往下运行
            logger.info("root缓存保存ok");
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        cacheOfRoot.getListenable().addListener(new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent event) throws Exception {
                switch (event.getType()) {
                    case CHILD_ADDED://应该仅仅是加入和删除两个事件会发生
                        logger.info("root根");
                        logger.info("CHILD_ADDED," + event.getData().getPath());
                        watchNode();
                        break;
                    case CHILD_UPDATED://这个在程序里应该不会出现
                        logger.info("CHILD_UPDATED," + event.getData().getPath());
                        watchNode();
                        break;
                    case CHILD_REMOVED:
                        logger.info("root根");
                        logger.info("CHILD_REMOVED," + event.getData().getPath());
                        watchNode();
                        break;
                    default:
                        logger.info("default情况");
                        break;
                }
            }
        } );


        //监听root的孩子，就是所有我这个clint需要的节点
        PathChildrenCache cacheOfFace[]=new PathChildrenCache[interfaces.size()];

        for (int i=0;i<interfaces.size();i++ ) {
            String face=interfaces.get(i);
            cacheOfFace[i] = new PathChildrenCache(client, Config.ZK_ROOT_PATH + "/"+ face, false);//我暂时不需要缓存节点里面的数据????存疑
            try {
                cacheOfFace[i].start(StartMode.BUILD_INITIAL_CACHE);
                logger.info("接口 {} 缓存保存ok",face);
            } catch (Exception e) {
                logger.error("Exception", e);
            }

            cacheOfFace[i].getListenable().addListener(new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent event) throws Exception {
                    switch (event.getType()) {
                        case CHILD_ADDED://应该仅仅是加入和删除两个事件会发生
                            logger.info("face接口");
                            logger.info("CHILD_ADDED," + event.getData().getPath());
                            watchNode();
                            break;
                        case CHILD_UPDATED://这个在程序里应该不会出现
                            logger.info("CHILD_UPDATED," + event.getData().getPath());
                            watchNode();
                            break;
                        case CHILD_REMOVED:
                            logger.info("face接口");
                            logger.info("CHILD_REMOVED," + event.getData().getPath());
                            watchNode();
                            break;
                        default:
                            logger.info("default情况");
                            break;
                    }
                }
            } );
        }
    }


    /**
     * 连接zookeeper
     * @return
     */
    private void connectServer() {

        client = CuratorFrameworkFactory.builder()
                .connectString(address)
                .sessionTimeoutMs(Config.ZK_SESSION_TIMEOUT)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();

        client.start();
        logger.info("连接zk集群，ok了");

    }

    /**
     * 这个函数也一直没用上？？？看错了，用上了，被RPCClient类调用
     */
    public void stop() {
        if (client != null) {
            client.close();
        }
    }
}
