package org.cru.cas.client.integration;

import static org.cru.cas.client.integration.Util.nullToEmpty;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jasig.cas.client.session.SessionMappingStorage;
import org.jasig.cas.client.session.SingleSignOutFilter;

/**
 * A filter that behaves the same as {@link SingleSignOutFilter} except that
 * it does not consume the entity input stream unless the path matches
 * the configured logout path.
 *
 * This assumes that the ticket is sent in a GET request, which might be wrong for SAML
 * (I'm not sure),
 * but is fine for the native CAS protocol.
 *
 * Works around https://github.com/apereo/java-cas-client/issues/210.
 */
public class EntityPreservingSingleSignOutFilter implements Filter {

    private final SingleSignOutFilter delegate = new SingleSignOutFilter();
    private String logoutPath;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        delegate.init(filterConfig);
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
        if (safeToDelegate(request)) {
            delegate.doFilter(request, response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean safeToDelegate(HttpServletRequest request) {
        return request.getMethod().equals("GET") ||
            getPath(request).equals(logoutPath);
    }

    private String getPath(HttpServletRequest request) {
        return nullToEmpty(request.getServletPath()) + nullToEmpty(request.getPathInfo());
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    public void setArtifactParameterName(String name) {
        delegate.setArtifactParameterName(name);
    }

    public void setLogoutParameterName(String name) {
        delegate.setLogoutParameterName(name);
    }

    public void setRelayStateParameterName(String name) {
        delegate.setRelayStateParameterName(name);
    }

    public void setCasServerUrlPrefix(String casServerUrlPrefix) {
        delegate.setCasServerUrlPrefix(casServerUrlPrefix);
    }

    public void setSessionMappingStorage(SessionMappingStorage storage) {
        delegate.setSessionMappingStorage(storage);
    }

    public void setIgnoreInitConfiguration(boolean ignoreInitConfiguration) {
        delegate.setIgnoreInitConfiguration(ignoreInitConfiguration);
    }
}
