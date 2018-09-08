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
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private CountDownLatch latch = new CountDownLatch(1);
    private String address;
    private CuratorFramework client;
    //客户端订阅的接口
    private List<String> interfaces;

    //
    PathChildrenCacheListener pathChildrenCacheListener;

    /**
     * 需要看看zk zpi才好懂,,涉及到watch
     * <p>
     * <p>
     * 构造函数，，，先连接zk
     * 再,,
     *
     * @param address
     * @param interfaces
     */
    public ServiceDiscovery(String address, List<String> interfaces) {
        this.address = address;
        this.interfaces = interfaces;
        connectServer();


        //new一个监听器
        pathChildrenCacheListener = new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent event) throws Exception {
                switch (event.getType()) {
                    case CHILD_ADDED://应该仅仅是加入和删除两个事件会发生
                        logger.info("CHILD_ADDED," + event.getData().getPath());
                        watchNode();
                        break;
                    case CHILD_UPDATED://这个在程序里应该不会出现
                        logger.info("CHILD_UPDATED," + event.getData().getPath());
                        watchNode();
                        break;
                    case CHILD_REMOVED:
                        logger.info("CHILD_REMOVED," + event.getData().getPath());
                        watchNode();
                        break;
                    default:
                        break;
                }
            }
        };
        //必须先new，再注册监听器
        registerWatcher4Node(Config.ZK_ROOT_PATH);//为根节点注册监听器


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
            Set<String> dataSet = new HashSet<String>();//所有我需要的接口的，所有的地址的，集合，，，这是干啥的？
            Map<String, Set<InetSocketAddress>> interfaceAndServerMap = new HashMap<String, Set<InetSocketAddress>>();//key是接口名称，value是这个接口的所有地址
            for (final String face : interfaceList) {
                if (interfaces.contains(face)) {//我只处理我这个cilent需要的接口
                    registerWatcher4Node(Config.ZK_ROOT_PATH + "/" + face);

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
            logger.info("node data: {}", dataSet);
            logger.info("interfaceAndServerMap data: {}", interfaceAndServerMap);
            logger.info("Service discovery triggered updating connected server node");
            //更新连接服务
            ConnectManage.getInstance().updateConnectedServer(dataSet, interfaceAndServerMap);
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }


    /**
     * 注册监听器，为了监听孩子节点的变化
     *
     * @param pathString
     */
    private void registerWatcher4Node(String pathString) {
        PathChildrenCache cache = new PathChildrenCache(client, pathString, true);//我暂时不需要缓存节点里面的数据????存疑
        try {
            cache.start(StartMode.NORMAL);
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        cache.getListenable().addListener(pathChildrenCacheListener);

    }


    /**
     * 连接zookeeper
     *
     * @return
     */
    private void connectServer() {

        client = CuratorFrameworkFactory.builder()
                .connectString(address)
                .sessionTimeoutMs(Config.ZK_SESSION_TIMEOUT)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();

        client.start();

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
