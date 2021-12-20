package link.infra.tinymap;

import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.nio.file.Path;

public class HttpServer {
	private static final Logger LOGGER = LogManager.getLogger();

	public static Javalin start(int port, Path basePath, TileGenerator tileGenerator) {
		Javalin app = Javalin.create(config -> {
			config.enableWebjars();
			config.server(() -> {
				// Use 2 - (numCPUs-1) threads
				QueuedThreadPool pool = new QueuedThreadPool(Runtime.getRuntime().availableProcessors() - 1, 2, 60000);
				// Use daemon threads, so the server can exit properly
				pool.setDaemon(true);
				pool.setName("Javalin-pool");

				Server server = new Server(pool);

				// Serve static files from the basePath
				ResourceHandler staticFileHandler = new ResourceHandler();
				staticFileHandler.setBaseResource(new PathResource(basePath));

				HandlerList handlers = new HandlerList();
				handlers.addHandler(staticFileHandler);
				server.setHandler(handlers);

				return server;
			});
			config.showJavalinBanner = false;
		});

		app.events(event -> {
			event.serverStarted(() -> {
				LOGGER.info("Javalin activated by: tinymap");
			});
		});

		app.get("/tiles/{dim}/{zoom}/{x}/{z}/tile.png", ctx -> {
			byte[] tile = tileGenerator.getTile(
				ctx.pathParam("dim"),
				ctx.pathParamAsClass("x", Integer.class).get(),
				ctx.pathParamAsClass("z", Integer.class).get(),
				0);
			if (tile != null) {
				ctx.contentType("image/png");
				ctx.result(tile);
			} else {
				throw new NotFoundResponse();
			}
		});

		app.start(port);

		return app;
	}
}
