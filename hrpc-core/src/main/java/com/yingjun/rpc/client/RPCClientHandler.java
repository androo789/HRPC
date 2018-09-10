package com.yingjun.rpc.client;

import com.yingjun.rpc.protocol.RPCRequest;
import com.yingjun.rpc.protocol.RPCResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client  handler
 *
 * @author yingjun
 */
public abstract class RPCClientHandler extends SimpleChannelInboundHandler<RPCResponse> {
    private static final Logger logger = LoggerFactory.getLogger(RPCClientHandler.class);
    private ConcurrentHashMap<String, RPCFuture> pending = new ConcurrentHashMap<String, RPCFuture>();//这是排队的意思吗、？key是RPCRequest的id，value是对应的RPCFuture

    private volatile Channel channel;
    private InetSocketAddress socketAddress;


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        channel = ctx.channel();
        socketAddress = (InetSocketAddress) channel.remoteAddress();//channel连接的远端地址
        handlerCallback(channel.pipeline().get(RPCClientHandler.class), true);
    }

    /**
     * channelInactive是怎么回事？？？只要建立链接以后是不是一直inactive，那是不是不停回调？nonononono
     * inactive里面的in是不的意思，，，整个的意思是	"当前channel不活跃的时候，也就是当前channel到了它生命周期末"
     * 就是结束的时候吧，，就调用这个函数
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        handlerCallback(channel.pipeline().get(RPCClientHandler.class), false);
    }

    /**
     * 如果收到消息，这个消息就应该是服务器端函数运行的结果，返回过来了
     * 接受到的消息就是RPCResponse response
     * TODO 看程序log，应该是我发送消息，然后接收消息，然后强制关闭了连接，关闭连接代码应该在这附近，但是没看见？？？？
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, RPCResponse response) throws Exception {
        String requestId = response.getRequestId();
        RPCFuture rpcFuture = pending.get(requestId);
        if (rpcFuture != null) {
            pending.remove(requestId);
            rpcFuture.done(response);//通过done把接受到的消息，写入RPCFuture
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("client caught exception", cause);
        ctx.close();
    }


    /**
     * 这还是个抽象类，必须在使用的时候override
     *
     * 这个回调函数，是自己写的吧，用于处理channelActive和channelInactive两个信号
     * @param handler
     * @param isActive
     */
    public abstract void handlerCallback(RPCClientHandler handler, boolean isActive);


    /**
     * 之前选好了用哪个handler，现在通过那个handler，发送请求！
     * @param request
     * @return
     */
    public RPCFuture sendRequestBySync(RPCRequest request) {
        RPCFuture rpcFuture = new RPCFuture(request);//new一个RPCRequest实例
        pending.put(request.getRequestId(), rpcFuture);//存入penging
        channel.writeAndFlush(request);//writeAndFlush这函数就是用于发送消息的,,,!!!
        return rpcFuture;
    }

    /**
     * 异步还有个callback函数
     * @param request
     * @param callback
     * @return
     */
    public RPCFuture sendRequestByAsync(RPCRequest request, AsyncRPCCallback callback) {
        RPCFuture rpcFuture = new RPCFuture(request, callback);//异步的RPCFuture构造函数
        pending.put(request.getRequestId(), rpcFuture);
        channel.writeAndFlush(request);//writeAndFlush这函数就是用于发送消息的,,,!!!
        return rpcFuture;
    }

    public void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    /**
     * socketAddress就是返回个变量
     * @return
     */
    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public String toString() {
        return "RPCClientHandler{" +"socketAddress=" + socketAddress + '}';
    }

}
