package org.cru.cas.client.integration;

import static org.cru.cas.client.integration.Util.nullToEmpty;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Inflater;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.bind.DatatypeConverter;
import org.jasig.cas.client.Protocol;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.util.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter like {@link org.jasig.cas.client.session.SingleSignOutFilter},
 * except that it uses a logout list strategy instead mapping and eager session inactivation.
 */
public class LogoutListFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutListFilter.class);

    private final static int DECOMPRESSION_FACTOR = 10;


    private String logoutPath;

    private Set<String> logoutStore = ConcurrentHashMap.newKeySet();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logoutPath = Util.getRequiredInitParam(filterConfig, "logoutPath");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        if (isLogoutRequest(request)) {
            storeTicketInLogoutStore(request, response);
            return;
        } else {
            if (isTicketRequest(request)) {
                storeTicketInSession(request);
            } else if (isSessionRequiringLogout(request)) {
                clearSession(request);
            }

            chain.doFilter(request, response);
        }
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return getPath(request).equals(logoutPath);
    }

    private void storeTicketInSession(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String ticket = getTicketParameter(request);
        LOG.debug("storing ticket {} in session", ticket);
        session.setAttribute(getTicketSessionAttribute(), ticket);
    }

    private void storeTicketInLogoutStore(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LOG.debug("storing ticket in logout store");
        String parameter = ConfigurationKeys.LOGOUT_PARAMETER_NAME.getDefaultValue();
        String logoutMessage = request.getParameter(parameter);
        if (logoutMessage == null || logoutMessage.isEmpty()) {
            response.setStatus(400);
            sendText(response, "blank/missing " + parameter + " parameter");
            LOG.debug("no parameter");
            return;
        }

        if (!logoutMessage.contains("SessionIndex")) {
            logoutMessage = uncompressLogoutMessage(logoutMessage);
        }

        LOG.trace("Logout request:\n{}", logoutMessage);
        final String token = XmlUtils.getTextForElement(logoutMessage, "SessionIndex");
        if (token != null && !token.isEmpty()) {
            logoutStore.add(token);
            response.setStatus(200);
            sendText(response, "logout request received\n");
            LOG.debug("...stored");
        } else {
            LOG.warn("no SessionIndex in logout request:\n{}", logoutMessage);
            response.setStatus(400);
            sendText(response, "invalid logout xml");
            LOG.debug("invalid logout xml");
        }
    }

    private void respondOk(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(200);
        sendText(response, "logout request received\n");
    }

    private void sendText(HttpServletResponse response, String body) throws IOException {
        response.addHeader("Content-Type", "text/plain");
        response.getWriter().append(body);
    }


    /**
     * Adapted almost directly from SingleSignOutHandler
     */
    private String uncompressLogoutMessage(final String originalMessage) {
        final byte[] binaryMessage = DatatypeConverter.parseBase64Binary(originalMessage);

        Inflater decompresser = null;
        try {
            // decompress the bytes
            decompresser = new Inflater();
            decompresser.setInput(binaryMessage);
            final byte[] result = new byte[binaryMessage.length * DECOMPRESSION_FACTOR];

            final int resultLength = decompresser.inflate(result);

            // decode the bytes into a String
            return new String(result, 0, resultLength, "UTF-8");
        } catch (final Exception e) {
            LOG.error("Unable to decompress logout message", e);
            throw new RuntimeException(e);
        } finally {
            if (decompresser != null) {
                decompresser.end();
            }
        }
    }


    private boolean isSessionRequiringLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String ticket = getSessionTicket(session);
        return ticket != null && logoutStore.contains(ticket);
    }

    private String getTicketSessionAttribute() {
        return getClass().getName();
    }

    private void clearSession(HttpServletRequest request) {
        HttpSession session = request.getSession(true);

        // get ticket before we clear the session
        String ticket = getSessionTicket(session);
        LOG.debug("clearing session for ticket {}", ticket);

        for (String attribute : Collections.list(session.getAttributeNames())) {
            session.removeAttribute(attribute);
        }

        // remove ticket from logout store afterward, to ensure a concurrent request
        // doesn't get through
        logoutStore.remove(ticket);
    }

    private String getSessionTicket(HttpSession session) {
        return (String) session.getAttribute(getTicketSessionAttribute());
    }


    private boolean isTicketRequest(HttpServletRequest request) {
        return request.getMethod().equals("GET") &&
            getTicketParameter(request) != null;
    }

    private String getTicketParameter(HttpServletRequest request) {
        return request.getParameter(Protocol.CAS2.getArtifactParameterName());
    }

    private String getPath(HttpServletRequest request) {
        return nullToEmpty(request.getServletPath()) + nullToEmpty(request.getPathInfo());
    }

    @Override
    public void destroy() {
    }

}
