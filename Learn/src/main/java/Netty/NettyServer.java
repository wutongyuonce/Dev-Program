package Netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NettyServer {

    public void start() throws InterruptedException{

        Map<Channel, List<String>> db = new ConcurrentHashMap<>();

        // 创建两个 EventLoopGroup（boss + worker）
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 创建 ServerBootstrap：Netty 服务端启动辅助类
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    // 设置主从 Reactor 线程组：
                    // - 第一个 group 负责接收客户端连接（boss）
                    // - 第二个 group 负责处理已建立连接的 I/O（worker）
                    .group(bossGroup, workerGroup)
                    // 指定服务端 Channel 类型（NIO 非阻塞）
                    .channel(NioServerSocketChannel.class)
                    // 设置每个新连接的 Channel 初始化逻辑
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 添加字符串解码器（将 ByteBuf 转为 String）
                            ch.pipeline()
                                    .addLast(new LineBasedFrameDecoder(1024)) // 按行解析
                                    .addLast(new StringDecoder()) // ← inbound：把 ByteBuf 转成 String（用于读）
                                    .addLast(new StringEncoder()) // ← outbound：把 String 转成 ByteBuf（用于写）
                                    // 添加自定义业务处理器：接收并打印客户端消息
                                    .addLast(new ResponseHandler())
                                    .addLast(new DbHandler(db));
                        }
                    });

            // 绑定端口并同步等待
            ChannelFuture bindFuture = serverBootstrap.bind(8080).sync(); // ⬅️ 阻塞直到绑定成功/失败
            // 获取绑定成功的服务端 Channel
            Channel serverChannel = bindFuture.channel();
            System.out.println("服务器已启动，监听端口 8080");

            // 关键：注册关闭钩子，确保 Ctrl+C 能触发关闭
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("正在关闭服务端...");
                serverChannel.close(); // 触发 closeFuture 完成
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }));

            // serverChannel.closeFuture() 返回一个 Future，它会在 serverChannel 被关闭时完成（例如Ctrl+C通过勾子调用了 serverChannel.close()）
            serverChannel.closeFuture().sync(); // ⬅️ 阻塞主线程直到服务端 channel 被关闭
            System.out.println("服务端关闭");
        } finally {
            // 优雅关闭线程组（释放资源）
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    static class ResponseHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            System.out.println("收到: " + msg.trim());
            ctx.writeAndFlush("OK\n"); // 回复并换行
            ctx.fireChannelRead(msg); // 传给下一个InboundHandler
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx,cause);
            cause.printStackTrace();
            // 出现异常（如客户端强制断开），自动关闭连接
            ctx.close();
        }
    }

    static class DbHandler extends SimpleChannelInboundHandler<String> {
        private Map<Channel, List<String>> db;

        public DbHandler(Map<Channel, List<String>> db) {
            this.db = db;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            List<String> messageList = db.computeIfAbsent(ctx.channel(),
                    k -> new ArrayList<>());
            messageList.add(msg);

        }

        // 无论连接是由客户端还是服务端主动调用 `close()` 关闭的，另一端（对等方）都会收到 `channelInactive()` 事件
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            System.out.println("已断开客户端连接");
            List<String> strings = db.get(ctx.channel());
            System.out.println("客户端传入的消息：" + strings);
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

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            System.out.println("已完成客户端连接");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        NettyServer nettyServer = new NettyServer();
        nettyServer.start();
    }
}
