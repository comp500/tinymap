package link.infra.tinymap;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	public final Path basePath;
	private final TileGenerator tileGenerator;

	public HttpServerHandler(Path basePath, TileGenerator tileGenerator) {
		this.tileGenerator = tileGenerator;
		try {
			this.basePath = basePath.toRealPath();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read TinyMap assets", e);
		}
	}

	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_CACHE_SECONDS = 60;

	private FullHttpRequest request;

	private final Pattern tilePattern = Pattern.compile("/tiles/([a-z0-9_-]+:[a-z0-9_-]+)/-?\\d+/(-?\\d+)/(-?\\d+)/tile\\.png");

	// TODO: check all exceptions are handled gracefully
	@Override
	public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		this.request = request;
		if (!request.decoderResult().isSuccess()) {
			sendError(ctx, BAD_REQUEST);
			return;
		}

		if (!GET.equals(request.method())) {
			this.sendError(ctx, METHOD_NOT_ALLOWED);
			return;
		}

		boolean keepAlive = HttpUtil.isKeepAlive(request);
		String uri = request.uri();

		if (uri.equals("/index.html")) {
			sendRedirect(ctx, "/");
			return;
		}

		Matcher tileMatcher = tilePattern.matcher(uri);
		if (tileMatcher.matches()) {
			ByteBuffer buf = tileGenerator.getTile(tileMatcher.group(1), Integer.parseInt(tileMatcher.group(2)), Integer.parseInt(tileMatcher.group(3)), 0);
			if (buf == null) {
				this.sendError(ctx, NOT_FOUND);
			} else {
				HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
				HttpUtil.setContentLength(response, buf.remaining());
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
				setDateAndCacheHeaders(response, new Date());

				if (!keepAlive) {
					response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
				} else if (request.protocolVersion().equals(HTTP_1_0)) {
					response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
				}

				// Write the initial line and the header.
				ctx.write(response);

				// Write the content.
				ctx.write(Unpooled.wrappedBuffer(buf));
				// Write the end marker.
				ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

				// Decide whether to close the connection or not.
				if (!keepAlive) {
					// Close the connection when the whole content is written out.
					lastContentFuture.addListener(ChannelFutureListener.CLOSE);
				}
			}
			return;
		}

		Path path;
		try {
			path = uriToPath(basePath, uri);
		} catch (NoSuchFileException e) {
			this.sendError(ctx, NOT_FOUND);
			return;
		} catch (IOException | URISyntaxException e) {
			this.sendError(ctx, FORBIDDEN);
			return;
		}

		if (Files.isHidden(path)) {
			this.sendError(ctx, NOT_FOUND);
			return;
		}

		if (!Files.isRegularFile(path)) {
			sendError(ctx, FORBIDDEN);
			return;
		}

		// Cache Validation
		String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
		if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
			SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
			Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

			// Only compare up to the second because the datetime format we send to the client
			// does not have milliseconds
			long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
			long fileLastModifiedSeconds = Files.getLastModifiedTime(path).to(TimeUnit.SECONDS);
			if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
				this.sendNotModified(ctx);
				return;
			}
		}

		FileChannel chan = FileChannel.open(path);
		long fileLength = chan.size();

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		HttpUtil.setContentLength(response, fileLength);
		setContentTypeHeader(response, path);
		setDateAndCacheHeaders(response, new Date(Files.getLastModifiedTime(path).toMillis()));

		if (!keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion().equals(HTTP_1_0)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		// Write the initial line and the header.
		ctx.write(response);

		// Write the content.
		ctx.write(new DefaultFileRegion(chan, 0, fileLength), ctx.newProgressivePromise());
		// Write the end marker.
		ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		// Decide whether to close the connection or not.
		if (!keepAlive) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		if (ctx.channel().isActive()) {
			sendError(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	private static final String INDEX = "index.html";

	private static Path uriToPath(Path base, String uri) throws URISyntaxException, IOException {
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		if (uri.charAt(0) != '/') {
			throw new URISyntaxException(uri, "Doesn't start with /");
		}

		Path destRealPath;
		try {
			destRealPath = base.resolve(uri.substring(1)).toRealPath();
		} catch (InvalidPathException e) {
			throw new IOException("Invalid path", e);
		}
		if (!destRealPath.startsWith(base)) {
			throw new IOException("Bad URI, is outside base directory");
		}

		if (Files.isDirectory(destRealPath)) {
			destRealPath = destRealPath.resolve(INDEX);
		}

		return destRealPath;
	}

	private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND, Unpooled.EMPTY_BUFFER);
		response.headers().set(HttpHeaderNames.LOCATION, newUri);

		this.sendAndCleanupConnection(ctx, response);
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(
			HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		this.sendAndCleanupConnection(ctx, response);
	}

	private void sendNotModified(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
		setDateHeader(response);

		this.sendAndCleanupConnection(ctx, response);
	}

	/**
	 * If Keep-Alive is disabled, attaches "Connection: close" header to the response
	 * and closes the connection after the response being sent.
	 */
	private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpResponse response) {
		final FullHttpRequest request = this.request;
		final boolean keepAlive = HttpUtil.isKeepAlive(request);
		HttpUtil.setContentLength(response, response.content().readableBytes());
		if (!keepAlive) {
			// We're going to close the connection as soon as the response is sent,
			// so we should also make it clear for the client.
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion().equals(HTTP_1_0)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		ChannelFuture flushPromise = ctx.writeAndFlush(response);

		if (!keepAlive) {
			// Close the connection as soon as the response is sent.
			flushPromise.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private static void setDateHeader(FullHttpResponse response) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
	}

	private static void setDateAndCacheHeaders(HttpResponse response, Date lastModifiedTime) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		// Date header
		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
		response.headers().set(
			HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(lastModifiedTime));
	}

	private static void setContentTypeHeader(HttpResponse response, Path path) {
		MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(path.getFileName().toString()));
	}
}
