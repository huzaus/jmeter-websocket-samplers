package com.jmeter.websocket.plugin.endpoint.jetty;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.jmeter.websocket.plugin.endpoint.SessionsManager;
import com.jmeter.websocket.plugin.endpoint.WebsocketClient;
import com.jmeter.websocket.plugin.endpoint.comsumers.WebsocketMessageConsumer;
import com.jmeter.websocket.plugin.endpoint.jetty.converters.CookieManagerToCookieStoreConverter;
import com.jmeter.websocket.plugin.endpoint.jetty.converters.HeadersToClientUpgradeRequestConverter;
import com.jmeter.websocket.plugin.endpoint.jetty.converters.SampleResultToUpgradeListenerConverter;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.io.UpgradeListener;

import java.io.IOException;
import java.net.CookieStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static java.lang.Integer.toHexString;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class JettyWebsocketEndpoint implements WebsocketClient {

    private static final Logger log = LoggingManager.getLoggerForClass();

    private static Supplier<WebSocketClient> webSocketClientSupplier = webSocketClientSupplier();
    private Function<CookieManager, CookieStore> cookieManagerToCookieStoreConverter = new CookieManagerToCookieStoreConverter();
    private Function<Map<String, List<String>>, ClientUpgradeRequest> headersToClientUpgradeRequestConverter = new HeadersToClientUpgradeRequestConverter();
    private Function<SampleResult, UpgradeListener> sampleResultToUpgradeListenerConverter = new SampleResultToUpgradeListenerConverter();
    private Collection<WebsocketMessageConsumer> websocketMessageConsumers = new ArrayList<>();
    private Supplier<JettyWebsocket> jettyWebsocketSupplier = jettyWebsocketSupplier();
    private SessionsManager<String, Session> sessionsManager = new JettySessionManager();

    private static Supplier<WebSocketClient> webSocketClientSupplier() {
        return memoize(new Supplier<WebSocketClient>() {
            @Override
            public WebSocketClient get() {
                return new WebSocketClient(sslContextFactory(), newCachedThreadPool());
            }
        });
    }

    private static SslContextFactory sslContextFactory() {
        return new SslContextFactory(true);
    }

    @Override
    public void connect(URI uri, String sessionId, CookieManager cookieManager, Map<String, List<String>> headers, SampleResult result, long timeOut) throws
            Exception {
        checkArgument(!sessionsManager.hasOpenSession(sessionId), "Session is already open");
        log.info("Opening " + uri + " connection." +
                " Session ID: " + sessionId +
                " CookieManager: " + cookieManager +
                " Headers: " + headers);
        WebSocketClient webSocketClient = webSocketClientSupplier.get();
        Future<Session> promise;
        synchronized (webSocketClient) {
            webSocketClient.setCookieStore(cookieManagerToCookieStoreConverter.apply(cookieManager));
            promise = webSocketClient
                    .connect(jettyWebsocketSupplier.get(),
                            uri,
                            headersToClientUpgradeRequestConverter.apply(headers),
                            sampleResultToUpgradeListenerConverter.apply(result));
        }
        sessionsManager.registerSession(sessionId, promise.get(timeOut, MILLISECONDS));
        log.info("Connected to: " + uri + " .");
    }

    @Override
    public void sendMessage(String sessionId, String message) throws IOException {
        Session session = sessionsManager.getOpenSession(sessionId);
        checkNotNull(session, sessionId + " session is not open. Session: " + session);
        log.info("sendMessage() message: " + message + " to " + sessionId + " using session:" + session);
        session.getRemote().sendString(message);
        for (WebsocketMessageConsumer consumer : websocketMessageConsumers) {
            consumer.onMessageSend(toHexString(session.hashCode()), message);
        }
    }

    @Override
    public void registerMessageConsumer(WebsocketMessageConsumer consumer) {
        websocketMessageConsumers.add(consumer);
    }

    @Override
    public void unregisterMessageConsumer(WebsocketMessageConsumer consumer) {
        websocketMessageConsumers.remove(consumer);
    }

    @Override
    public void start() {
        try {
            log.info("Starting websocket client.");
            webSocketClientSupplier.get().start();
        } catch (Exception e) {
            log.error("Failed to start WebSocketClient: " + e);
        }
    }

    @Override
    public void stop() {
        try {
            log.info("Stopping websocket client.");
            WebSocketClient webSocketClient = webSocketClientSupplier.get();
            synchronized (webSocketClient) {
                sessionsManager.closeSessions();
                webSocketClient.stop();
            }
        } catch (Exception e) {
            log.error("Failed to start WebSocketClient: " + e);
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("hash", toHexString(hashCode()))
                .add("websocketMessageConsumers", websocketMessageConsumers)
                .add("sessionsManager", sessionsManager)
                .toString();
    }

    private Supplier<JettyWebsocket> jettyWebsocketSupplier() {
        return new Supplier<JettyWebsocket>() {
            @Override
            public JettyWebsocket get() {
                JettyWebsocket jettyWebsocket = new JettyWebsocket();
                for (WebsocketMessageConsumer consumer : websocketMessageConsumers) {
                    jettyWebsocket.registerWebsocketMessageConsumer(consumer);
                }
                return jettyWebsocket;
            }
        };
    }
}
