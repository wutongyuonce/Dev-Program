package Netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class NettyClient {

    public void start() {

        NioEventLoopGroup group = new NioEventLoopGroup();

        try {
            // 创建客户端启动辅助类
            Bootstrap bootstrap = new Bootstrap()
                    // 设置 I/O 线程组（客户端只需一个 EventLoopGroup）
                    .group(group)
                    // 指定客户端 Channel 类型（NIO 非阻塞）
                    .channel(NioSocketChannel.class)
                    // 初始化客户端对应连接的 Channel Pipeline
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new StringDecoder())
                                    .addLast(new StringEncoder())
                                    .addLast(new ClientHandler());
                        }
                    });

            // 异步连接到服务器（localhost:8080）
            System.out.println("正在连接服务器...");
            ChannelFuture connectFuture = bootstrap.connect("localhost", 8080).sync(); // 阻塞直到连接成功（或失败）

            // 获取连接成功的 Channel
            Channel channel = connectFuture.channel();

            // 由客户端或服务端业务处理器调用 ctx.close() 关闭连接，如果不调用 ctx.close() 关闭连接，这行会导致客户端一直挂起阻塞
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 优雅关闭线程组
            group.shutdownGracefully();
        }
    }


    static class ClientHandler extends SimpleChannelInboundHandler<String> { // 入站处理器
        int replyCount = 0;
        final int totalReplies = 3;

        // 当客户端与服务器的 TCP 连接成功建立后，自动触发此方法，并在此发送第一条消息
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("已完成客户端连接，开始发送多条消息...");
            // 连续发送 3 条消息（模拟快速发送）
            ctx.writeAndFlush("Msg1\n");
            ctx.writeAndFlush("Msg2\n");
            ctx.writeAndFlush("Msg3\n");
            // 在“发完即走”场景中，通常不等待服务端关闭连接或者等待服务端响应结果，直接自己 ctx.close() 关闭channel 即可
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            System.out.println("收到回复: " + msg);
            replyCount++;
            if (replyCount >= totalReplies) {
                System.out.println("已收完所有回复，开始关闭连接...");
                // 判断已经拿到所有服务端响应结果，自己关闭客户端连接
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            System.out.println("已断开客户端连接");
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            super.channelRegistered(ctx);
            System.out.println("客户端连接完成了注册");
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            super.channelUnregistered(ctx);
            System.out.println("客户端连接解除了注册");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        NettyClient nettyClient = new NettyClient();
        nettyClient.start();
    }
}
