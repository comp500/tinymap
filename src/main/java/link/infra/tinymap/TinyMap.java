package link.infra.tinymap;

import io.javalin.Javalin;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class TinyMap implements ModInitializer {
	private static Properties CONFIG = null;

	private static Javalin httpServer = null;
	private static TileGenerator tileGenerator = null;

	private static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void onInitialize() {
		Path basePath = FabricLoader.getInstance().getModContainer("tinymap").get().getPath("web");
		loadConfig(Paths.get(FabricLoader.getInstance().getConfigDir().toString(), "tinymap.properties"));
		String port = CONFIG.getProperty("web_port");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			tileGenerator = new TileGenerator(server);
			if (httpServer != null) {
				try {
					httpServer.stop();
				} catch (Exception e) {
					LOGGER.error("Failed to stop Javalin server", e);
				}
			}
			httpServer = HttpServer.start(Integer.parseInt(port), basePath, tileGenerator);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			tileGenerator = null;
			if (httpServer != null) {
				try {
					httpServer.stop();
				} catch (Exception e) {
					LOGGER.error("Failed to stop Javalin server", e);
				}
			}
			httpServer = null;
		});
	}

	public void loadConfig(Path configPath) {
		Properties props = new Properties();

		if (!configPath.toFile().exists()) {
			try {
				Files.copy(TinyMap.class.getResourceAsStream("/config.properties"), configPath);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		try {
			props.load(new FileInputStream(configPath.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		CONFIG = props;
	}

}
