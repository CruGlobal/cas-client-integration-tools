package org.cru.cas.client.integration;

import static org.cru.cas.client.integration.Util.getRequiredInitParam;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter to broadcast logout requests to other nodes operating behind a load balancer.
 *
 * This behaves more like a servlet than a filter,
 * but it is a filter instead of a servlet so that it can run before the
 * {@link org.jasig.cas.client.session.SingleSignOutFilter} filter.
 */
public class LogoutBroadcastFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutBroadcastFilter.class);

    private Set<URI> recipientHosts;
    private String recipientPath;

    @Override
    public void init(FilterConfig config) throws ServletException {
        this.recipientPath = getRequiredInitParam(config, "recipientPath");
        String hostsParameter = getRequiredInitParam(config, "recipientHosts");

        recipientHosts = Arrays.stream(hostsParameter.split("\\s+"))
            .filter(string -> !string.isEmpty())
            .map(LogoutBroadcastFilter::createUri)
            .collect(Collectors.toSet());
    }

    private static URI createUri(String host) {
        try {
            return new URI(host);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid target host: " + host, e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            service((HttpServletRequest) request, (HttpServletResponse) response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void service(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        broadcastRequest(request);
        respondWithOk(response);
    }

    private void broadcastRequest(HttpServletRequest request) throws IOException {
        ServletInputStream inputStream = request.getInputStream();
        BufferedInputStream bufferedStream = inputStream == null ? null : new BufferedInputStream(inputStream);
        if (bufferedStream != null) {
            bufferedStream.mark(1_000_000);
        }

        for (URI host : recipientHosts) {
            try {
                if (bufferedStream != null) {
                    bufferedStream.reset();
                }
                forwardRequest(host.toURL(), request, bufferedStream);
            } catch (IOException e) {
                LOG.error("Unable to send request to " + host, e);
            }
        }
    }

    // adapted from Brian White's answer at https://stackoverflow.com/a/22572736/1174749
    private void forwardRequest(URL target, HttpServletRequest req, InputStream entityStream) throws IOException {
        final URL url = new URL(target, recipientPath + buildQueryString(req));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(req.getMethod());

        addHeaders(req, conn);

        final boolean requestHasEntity = entityStream != null;

        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(requestHasEntity);
        conn.connect();

        if (requestHasEntity) {
            forwardRequestEntity(conn, entityStream);
        }

        try (InputStream stream = conn.getInputStream()) {
            // ignore it, but make sure it gets closed
        }
    }

    private String buildQueryString(HttpServletRequest req) {
        return req.getQueryString() != null ? "?" + req.getQueryString() : "";
    }

    private void addHeaders(HttpServletRequest req, HttpURLConnection conn) {
        final Enumeration<String> headers = req.getHeaderNames();
        while (headers.hasMoreElements()) {
            final String header = headers.nextElement();
            final Enumeration<String> values = req.getHeaders(header);
            while (values.hasMoreElements()) {
                final String value = values.nextElement();
                conn.addRequestProperty(header, value);
            }
        }
    }

    private void forwardRequestEntity(HttpURLConnection conn, InputStream entity) throws IOException {
        final byte[] buffer = new byte[16384];
        while (true) {
            final int read = entity.read(buffer);
            if (read <= 0) break;
            conn.getOutputStream().write(buffer, 0, read);
        }
    }

    private void respondWithOk(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain");
        response.getWriter().write("logout request received");
    }

    @Override
    public void destroy() {
    }
}
