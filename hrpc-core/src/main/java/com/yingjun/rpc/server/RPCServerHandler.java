package com.yingjun.rpc.server;

import com.yingjun.rpc.protocol.RPCRequest;
import com.yingjun.rpc.protocol.RPCResponse;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * RPC request processor
 *
 * SimpleChannelInboundHandler<RPCRequest>在继承的时候，指定了要处理的数据类型，表示每次接收到的数据，都是一个request
 * @author yingjun
 */
public class RPCServerHandler extends SimpleChannelInboundHandler<RPCRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RPCServerHandler.class);
    private final Map<String, Object> serviceBeanMap;


    /**
     * 构造函数，map的key是bean的名字，value是bean的对象
     * @param serviceBeanMap
     */
    public RPCServerHandler(Map<String, Object> serviceBeanMap) {
        this.serviceBeanMap = serviceBeanMap;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("======rpc server channel active：" + ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("======rpc server channel inactive：" + ctx.channel().remoteAddress());
    }


    /**
     * 收到数据
     * 每次接收到的数据都是参数里面final RPCRequest request，
     * 按照继承的原因，是可以这样写
     *
     * TODO 每次接收到消息，放到线程池里面去处理，，，如果不用线程池会怎么样？？？？并发阻塞？？？netty里面是怎么处理并发的？？？
     */
    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final RPCRequest request) throws Exception {
        logger.info("======rpc server channelRead0：" + ctx.channel().remoteAddress());//remoteAddress我猜表示远端的ip地址
        RPCServer.submit(new Runnable() {
            @Override
            public void run() {
                logger.info("receive request:" + request.getRequestId() +
                        " className:" + request.getClassName() +
                        " methodName:" + request.getMethodName());
                RPCResponse response = new RPCResponse();
                response.setRequestId(request.getRequestId());
                try {
                    //通过反射原理找到对应的服务类和方法
                    String className = request.getClassName();
                    Object serviceBean = serviceBeanMap.get(className);

                    String methodName = request.getMethodName();
                    Class<?>[] parameterTypes = request.getParameterTypes();
                    Object[] parameters = request.getParameters();

                    // JDK reflect
                    /*Method method = serviceClass.getMethod(methodName, parameterTypes);
                    method.setAccessible(true);
                    Object result=method.invoke(serviceBean, parameters);*/

                    // 避免使用 Java 反射带来的性能问题，我们使用 CGLib 提供的反射 API
                    // 有什么问题？按照网上的说法，cglib调用快，创建慢，但是性能上我看起来也差不多
                    FastClass serviceFastClass = FastClass.create(serviceBean.getClass());
                    FastMethod serviceFastMethod = serviceFastClass.getMethod(methodName, parameterTypes);
                    Object result = serviceFastMethod.invoke(serviceBean, parameters);

                    response.setResult(result);//怎么获取返回值类型？？应该是两边规定好，直接就是知道的
                } catch (Exception e) {
                    response.setError(e.getMessage());//如果出错了，才设置错误
                    logger.error("Exception", e);
                }
                ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        logger.info("send response for request: " + request.getRequestId());
                    }
                });
            }
        });
    }


    /**
     *  TODO   /127.0.0.1:59888|远程主机强迫关闭了一个现有的连接。
     *
     * 看了这个log，，，，这是为什么？？？调用完成以后的log
     * 可能是断开连接？？为什么是异常？？？
     *
     * cause.getMessage()竟然还说中文？？神奇？？
     *
     * 客户端没有指定关闭连接吧？？？？我没看见这个代码吧？？？？客户端每次连接以后就关闭连接？？？
     * @param ctx
     * @param cause
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("rpc server caught exception: " + ctx.channel().remoteAddress() + "|" + cause.getMessage());
        ctx.close();//这个close是什么意思？？关闭连接？？？？看函数的英语解释确实是，，但是更进一步的原来还不知道
    }
}
