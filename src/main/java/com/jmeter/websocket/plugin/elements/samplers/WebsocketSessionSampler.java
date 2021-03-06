package com.jmeter.websocket.plugin.elements.samplers;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.jmeter.websocket.plugin.endpoint.WebsocketClient;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSampleResult;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.singletonList;

public class WebsocketSessionSampler extends AbstractWebsocketSampler {

    public static final String CONNECT_TIME_OUT = "connectTimeOut";
    private static final Logger log = LoggingManager.getLoggerForClass();

    public WebsocketSessionSampler() {
        setName("Websocket Message Sampler");
    }

    @Override
    public SampleResult sample(Entry entry) {
        HTTPSampleResult sampleResult = new HTTPSampleResult();
        sampleResult.sampleStart();
        sampleResult.setHTTPMethod("GET");
        sampleResult.setSampleLabel(getName());
        try {
            WebsocketClient<String> websocketClient = getWebsocketClient();
            checkNotNull(websocketClient, "WebsocketSessionManager should be added to test plan");
            websocketClient.connect(uri(), getSessionId(), getCookieManager(), headers(), sampleResult, Long.valueOf(getConnectTimeOut()));
        } catch (Exception e) {
            log.error("Error: ", e);
            sampleResult.setResponseMessage(e.getMessage());
            sampleResult.setSuccessful(false);
        } finally {
            sampleResult.sampleEnd();
        }
        return sampleResult;
    }

    @Override
    public void addTestElement(TestElement el) {
        if (el instanceof CookieManager) {
            setCookieManager((CookieManager) el);
        } else if (el instanceof HeaderManager) {
            setHeaderManager((HeaderManager) el);
        } else {
            super.addTestElement(el);
        }
    }

    public String getPath() {
        return getPropertyAsString(PATH);
    }

    public void setPath(String path) {
        setProperty(PATH, path, "");
    }

    public URI uri() throws URISyntaxException {
        return new URI(getProtocol(), null, getServerNameOrIp(), Integer.valueOf(getPortNumber()), getPath(), null, null);
    }

    public String getServerNameOrIp() {
        return getPropertyAsString(SERVER_NAME_OR_IP);
    }

    public void setServerNameOrIp(String serverNameOrIp) {
        setProperty(SERVER_NAME_OR_IP, serverNameOrIp, "");
    }

    public String getPortNumber() {
        return getPropertyAsString(PORT_NUMBER);
    }

    public void setPortNumber(String portNumber) {
        setProperty(PORT_NUMBER, portNumber, "");
    }

    public String getProtocol() {
        return getPropertyAsString(PROTOCOL);
    }

    public void setProtocol(String protocol) {
        setProperty(PROTOCOL, protocol, "");
    }

    private CookieManager getCookieManager() {
        return (CookieManager) getProperty(COOKIE_MANAGER).getObjectValue();
    }

    private void setCookieManager(CookieManager cookieManager) {
        setProperty(new TestElementProperty(COOKIE_MANAGER, cookieManager));
    }

    private HeaderManager getHeaderManager() {
        return (HeaderManager) getProperty(HEADER_MANAGER).getObjectValue();
    }

    private void setHeaderManager(HeaderManager headerManager) {
        setProperty(new TestElementProperty(HEADER_MANAGER, headerManager));
    }

    public String getConnectTimeOut() {
        return getPropertyAsString(CONNECT_TIME_OUT);
    }

    public void setConnectTimeOut(String connectTimeOut) {
        setProperty(CONNECT_TIME_OUT, connectTimeOut, "");
    }

    public Map<String, List<String>> headers() {
        return Optional.fromNullable(getHeaderManager())
                .transform(new Function<HeaderManager, Map<String, List<String>>>() {
                    @Override
                    public Map<String, List<String>> apply(HeaderManager headerManager) {
                        Map<String, List<String>> headers = new HashMap<>();
                        for (int i = 0; i < headerManager.size(); i++) {
                            Header header = headerManager.get(i);
                            headers.put(header.getName(), singletonList(header.getValue()));
                        }
                        return headers;
                    }
                })
                .or(Collections.<String, List<String>>emptyMap());
    }
}
