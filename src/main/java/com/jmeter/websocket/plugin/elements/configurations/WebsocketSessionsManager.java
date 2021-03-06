package com.jmeter.websocket.plugin.elements.configurations;

import com.google.common.base.Supplier;
import com.jmeter.websocket.plugin.endpoint.WebsocketClient;
import com.jmeter.websocket.plugin.endpoint.comsumers.CsvFileWriter;
import com.jmeter.websocket.plugin.endpoint.jetty.JettyWebsocketEndpoint;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.nio.file.Paths;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Suppliers.memoize;
import static com.jmeter.websocket.plugin.endpoint.comsumers.CsvFileWriter.csvFileWriterSupplier;

public class WebsocketSessionsManager extends ConfigTestElement implements TestStateListener {

    private static final String FILE = "websocket.data.output.file";
    private static final Logger log = LoggingManager.getLoggerForClass();
    private static final Supplier<WebsocketClient<String>> websocketClientSupplier = websocketClientSupplier();
    private static Supplier<CsvFileWriter> csvFileWriterSupplier;

    private static Supplier<WebsocketClient<String>> websocketClientSupplier() {
        return memoize(new Supplier<WebsocketClient<String>>() {
            @Override
            public WebsocketClient<String> get() {
                return new JettyWebsocketEndpoint();
            }
        });
    }

    public static WebsocketClient<String> getWebsocketClient() {
        return websocketClientSupplier.get();
    }

    public String getFile() {
        return getPropertyAsString(FILE, "");
    }

    public void setFile(String filename) {
        setProperty(FILE, filename);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("websocketClient", getWebsocketClient())
                .add("file", getFile())
                .toString();
    }

    @Override
    public void testStarted() {
        csvFileWriterSupplier = csvFileWriterSupplier(Paths.get(getFile()));
        getWebsocketClient().registerStaticMessageConsumer(csvFileWriterSupplier.get());
        getWebsocketClient().start();
        log.info("Test started: " + this);
    }

    @Override
    public void testStarted(String host) {
        log.info("Test started: " + this + ". Host: " + host + ".");
    }

    @Override
    public void testEnded() {
        getWebsocketClient().stop();
        csvFileWriterSupplier.get().stop();
        getWebsocketClient().unregisterStaticMessageConsumer(csvFileWriterSupplier.get());
        log.info("Test ended: " + this);
    }

    @Override
    public void testEnded(String host) {
        log.info("Test ended: " + this + ". Host: " + host + ".");
    }
}
