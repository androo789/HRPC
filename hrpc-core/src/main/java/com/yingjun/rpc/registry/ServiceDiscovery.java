package com.yingjun.rpc.registry;

import com.yingjun.rpc.manage.ConnectManage;
import com.yingjun.rpc.utils.Config;
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
 */
public class ServiceDiscovery {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDiscovery.class);

    private CountDownLatch latch = new CountDownLatch(1);
    private String address;
    private ZooKeeper zookeeper;
    //客户端订阅的接口
    private List<String> interfaces;

    /**
     *
     * 需要看看zk zpi才好懂,,涉及到watch
     *
     *
     * 构造函数，，，先连接zk
     * 再,,
     * @param address
     * @param interfaces
     */
    public ServiceDiscovery(String address, List<String> interfaces) {
        this.address = address;
        this.interfaces = interfaces;
        zookeeper = connectServer();
        if (zookeeper != null) {
            watchNode();
        }
    }


    private Watcher rootWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent watchedEvent) {
            logger.info("#######" + watchedEvent.getPath() + " Watcher " + " process: " + watchedEvent);
            if (watchedEvent.getType() == Event.EventType.NodeChildrenChanged) {//节点的孩子变化
                watchNode();//就重复注册所有节点的watcher？？？每次得到child变化通知，是不知道是哪个child变化了，仅仅是一通知
                //所以就需要注册所有的节点
            }
        }
    };

    private Watcher childdrenWatcher = new Watcher() {
        @Override
        public void process(WatchedEvent watchedEvent) {
            logger.info("#######" + watchedEvent.getPath() + " Watcher " + " process: " + watchedEvent);
            if (watchedEvent.getType() == Event.EventType.NodeChildrenChanged) {
                watchNode();
            }
        }
    };

    /**
     *
     */
    private void watchNode() {
        try {
            logger.info("invoke watchNode() ");
            List<String> interfaceList = zookeeper.getChildren(Config.ZK_ROOT_PATH, rootWatcher);//interfaceList这个是所有zk上的服务接口
            Set<String> dataSet = new HashSet<String>();//所有我需要的接口的，所有的地址的，集合，，，这是干啥的？
            Map<String, Set<InetSocketAddress>> interfaceAndServerMap = new HashMap<String, Set<InetSocketAddress>>();//key是接口名称，value是这个接口的所有地址
            for (final String face : interfaceList) {
                if (interfaces.contains(face)) {//我只处理我这个cilent需要的接口
                    List<String> addressList = zookeeper.getChildren(Config.ZK_ROOT_PATH + "/" + face, childdrenWatcher);//每一次服务接口可能有多个"地址孩子节点",所以是一个list
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
     * 连接zookeeper
     *
     * @return
     */
    private ZooKeeper connectServer() {
        ZooKeeper zk = null;
        try {
            zookeeper = new ZooKeeper(address, Config.ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    logger.info("#######default Watcher" + Config.ZK_ROOT_PATH + " watched node process: " + event);
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        latch.countDown();
                    }
                }
            });
            latch.await();//在这里等待，等countDown减1一直到0，然后再往下运行
        } catch (IOException e) {
            logger.error("Exception", e);
        } catch (InterruptedException ex) {
            logger.error("Exception", ex);
        }
        return zookeeper;
    }


    public void stop() {
        if (zookeeper != null) {
            try {
                zookeeper.close();
            } catch (InterruptedException e) {
                logger.error("", e);
            }
        }
    }

}
