package com.yingjun.rpc.manage;

import com.yingjun.rpc.client.RPCClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 相同接口handler？？？？？
 *
 * 负载均衡在这里用的是轮询，我可以改成加权或者hash，，就算是一个小的点也行啊， TODO
 *
 * @author yingjun
 */
public class SameInterfaceRPCHandlers {

    private List<RPCClientHandler> handlers;
    private AtomicInteger number = new AtomicInteger(0);

    public SameInterfaceRPCHandlers() {
        this.handlers = new ArrayList<RPCClientHandler>();
    }


    public RPCClientHandler getSLBHandler() {
        if (handlers == null || handlers.size() < 1) {
            return null;
        }
        int num = number.getAndIncrement() % handlers.size();
        return handlers.get(num);
    }


    public void addHandler(RPCClientHandler handler) {
        handlers.add(handler);
    }

    @Override
    public String toString() {
        String value="";
        for (RPCClientHandler handler : handlers) {
            value+=handler.getSocketAddress();
        }
        return "SameInterfaceRPCHandlers[" +value+"]";
    }
}
