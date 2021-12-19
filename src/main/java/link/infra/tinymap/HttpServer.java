package link.infra.tinymap;

import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

public class HttpServer {
	public static Server start(int port, Path basePath, TileGenerator tileGenerator) {
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
		}).start(port);

		app.get("/tiles/{dim}/{zoom}/{x}/{z}/tile.png", ctx -> {
			ByteBuffer buf = tileGenerator.getTile(
				ctx.pathParam("dim"),
				ctx.pathParamAsClass("x", Integer.class).get(),
				ctx.pathParamAsClass("z", Integer.class).get(),
				0);
			if (buf != null) {
				ctx.contentType("image/png");
				ctx.result(buf.array()); // TODO: is this efficient?
			} else {
				throw new NotFoundResponse();
			}
		});

		return Objects.requireNonNull(app.jettyServer()).server();
	}
}
