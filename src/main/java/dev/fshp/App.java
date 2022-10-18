package dev.fshp;

import com.aayushatharva.brotli4j.decoder.Decoder;
import com.aayushatharva.brotli4j.decoder.DecoderJNI;
import com.aayushatharva.brotli4j.decoder.DirectDecompress;
import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.*;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.StandardCharsets;

public class App {
    private static void initAndCheckBrotli() throws Throwable {
        Brotli.ensureAvailability();

        byte[] compressed = Encoder.compress("Hello".getBytes(StandardCharsets.UTF_8));
        DirectDecompress dd = Decoder.decompress(compressed);
        byte[] decompressed = dd.getDecompressedData();
        String value = new String(decompressed, StandardCharsets.UTF_8);

        if (dd.getResultStatus() == DecoderJNI.Status.ERROR || !value.equals("Hello")) {
            throw new Exception("Can't init brotli", Brotli.cause());
        }
    }


    public static void main(String[] args) throws Throwable {
        initAndCheckBrotli();

        EventLoopGroup eventLoopGroup = new NioEventLoopGroup(3);

        ChannelFuture server = new ServerBootstrap()
            .group(eventLoopGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel nioServerSocketChannel) throws Exception {
                    nioServerSocketChannel
                        .pipeline()
                        .addLast(new LoggingHandler("OUT TCP", LogLevel.WARN))
                        .addLast(new BrotliDecoder())
//			            .addLast(new JdkZlibDecoder())
                        .addLast(new LengthFieldBasedFrameDecoder(2 << 12, 0, 2, 0, 2))
                        .addLast(new StringDecoder())
                        .addLast(new SimpleChannelInboundHandler<String>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {
                                System.out.print(s);
                            }
                        });
                }
            })
            .bind("127.0.0.1", 13666);

        ChannelFuture client = new Bootstrap()
            .group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                    nioSocketChannel
                        .pipeline()
                        .addLast(new LoggingHandler("IN TCP", LogLevel.WARN))
                        .addLast(new BrotliEncoder())
//			            .addLast(new JdkZlibEncoder())
                        .addLast(new LengthFieldPrepender(2))
                        .addLast(new StringEncoder());
                }
            })
            .connect("127.0.0.1", 13666)
            .addListener((ChannelFutureListener) channelFuture -> {
                    channelFuture.channel().writeAndFlush("Hello");
                }
            );

    }

}
