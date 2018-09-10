package com.yingjun.rpc.registry;

import com.yingjun.rpc.utils.Config;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * RPC服务注册中心
 * <p>
 * TODO 现在这个注册也有问题，，，，，，就是如果长时间不操作，就会自己断开，，log上写什么超时 ,,????,
 * 断开的意思就是下面的那些ip节点就没有了
 * <p>
 * zk改curator
 * 1
 * 连接集群
 * 2
 * 建立根节点，持久
 * 3
 * 建立接口节点，持久
 * 4
 * 建立ip port地址节点，临时
 *
 * @author yingjun
 */
public class ServiceRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);
    private CountDownLatch latch = new CountDownLatch(1);
    private String address;//注册中心地址
    //    private ZooKeeper zooKeeper;
    private CuratorFramework client;


    /**
     * 这是构造函数
     *
     * @param address
     */
    public ServiceRegistry(String address) {
        this.address = address;
        //连接zookeeper
        connectServer();
        //创建根节点
//        if (client != null) {
//            setRootNode();
//        }
    }

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
     * 创建服务接口地址节点
     * PERSISTENT：创建后只要不删就永久存在
     * EPHEMERAL：会话结束 ,之后，结点自动被删除，EPHEMERAL结点不允许有子节点
     * SEQUENTIAL：节点名末尾会自动追加一个10位数的单调递增的序号，同一个节点的所有子节点序号是单调递增的
     * PERSISTENT_SEQUENTIAL：结合PERSISTENT和SEQUENTIAL
     * EPHEMERAL_SEQUENTIAL：结合EPHEMERAL和SEQUENTIAL
     *
     * @param interfaceName
     * @param serverAddress
     */
    public void createInterfaceAddressNode(String interfaceName, String serverAddress) {

        try {
            String pathStr = Config.ZK_ROOT_PATH + "/" + interfaceName + "/" + serverAddress;
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(pathStr);
            //按照curator规则，根节点和接口节点都是持久节点，是自动递归建立的，，这也是curator方便的一点
            logger.info("create zookeeper interface address node (path:{})", pathStr);
        } catch (Exception e) {
            logger.error("", e);
        }
    }
}