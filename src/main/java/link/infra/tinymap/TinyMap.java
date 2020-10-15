package link.infra.tinymap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class TinyMap implements ModInitializer {
	private static final int PORT = 8080;

	private static EventLoopGroup managerGroup = null;
	private static EventLoopGroup workerGroup = null;
	private static TileGenerator tileGenerator = null;

	@Override
	public void onInitialize() {
		Path basePath = FabricLoader.getInstance().getModContainer("tinymap").get().getPath("web");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			managerGroup = new NioEventLoopGroup(1, new DefaultThreadFactory(NioEventLoopGroup.class, true));
			workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory(NioEventLoopGroup.class, true));
			tileGenerator = new TileGenerator(server);
			ServerBootstrap b = new ServerBootstrap();
			b.group(managerGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) {
						ChannelPipeline pipeline = ch.pipeline();
						pipeline.addLast(new HttpServerCodec());
						pipeline.addLast(new HttpObjectAggregator(65536));
						pipeline.addLast(new ChunkedWriteHandler());
						pipeline.addLast(new HttpServerHandler(basePath, tileGenerator));
					}
				});
			b.bind(PORT);

			System.out.println("Open your web browser and navigate to http://127.0.0.1:" + PORT + '/');
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (managerGroup != null) {
				managerGroup.shutdownGracefully();
			}
			if (workerGroup != null) {
				workerGroup.shutdownGracefully();
			}
			managerGroup = null;
			workerGroup = null;
			tileGenerator = null;
		});
	}

}
