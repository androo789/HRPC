package com.yingjun.rpc.codec;

import com.yingjun.rpc.utils.SerializationUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * decoder
 * 先看解码类，，，解码就是byte转换消息
 *
 * ByteToMessageDecoder是抽像类，需要override  decode
 *
 * @author yingjun
 */
public class RPCDecoder extends ByteToMessageDecoder {

    private Class<?> genericClass;

    public RPCDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }

    @Override
    public final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {//为什么是4？？？因为要和 LengthFieldBasedFrameDecoder的输入参数一直，那个也是4,,,,,
            // 4就是最小用4个字节表示消息的长度，，，4就是消息的头
            return;
        }
        in.markReaderIndex();
        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[dataLength];//
        in.readBytes(data);//从in中，读出数据，放入byte数组
        Object obj = SerializationUtil.deserialize(data, genericClass);//最终还是用byte数组，转object类型，是在这里就是二进制转RPCRequest类型，
        out.add(obj);
    }

}
