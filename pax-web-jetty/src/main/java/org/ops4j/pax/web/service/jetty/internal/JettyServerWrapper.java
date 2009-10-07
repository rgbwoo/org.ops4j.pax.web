/* Copyright 2007 Alin Dreghiciu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.jetty.internal;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.SessionIdManager;
import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.HashSessionIdManager;
import org.mortbay.jetty.servlet.SessionHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpContext;
import org.ops4j.pax.swissbox.core.BundleUtils;
import org.ops4j.pax.web.service.WebContainerConstants;
import org.ops4j.pax.web.service.spi.model.Model;
import org.ops4j.pax.web.service.spi.model.ServerModel;

/**
 * Jetty server with a handler collection specific to Pax Web.
 */
class JettyServerWrapper extends Server
{

    private static final Log LOG = LogFactory.getLog( JettyServerWrapper.class );

    private final ServerModel m_serverModel;
    private final Map<HttpContext, Context> m_contexts;
    private Map<String, Object> m_contextAttributes;
    private Integer m_sessionTimeout;
    private String m_sessionCookie;
    private String m_sessionUrl;
    private String m_sessionWorkerName;

    JettyServerWrapper( ServerModel serverModel )
    {
        m_serverModel = serverModel;
        m_contexts = new IdentityHashMap<HttpContext, Context>();
    }

    @Override
    public void addHandler( final Handler handler )
    {
        if( getHandler() == null )
        {
            setHandler( new JettyServerHandlerCollection( m_serverModel ) );
        }
        ( (HandlerCollection) getHandler() ).addHandler( handler );
    }

    /**
     * {@inheritDoc}
     */
    public void configureContext( final Map<String, Object> attributes,
                                  final Integer sessionTimeout,
                                  final String sessionCookie,
                                  final String sessionUrl,
                                  final String sessionWorkerName )
    {
        m_contextAttributes = attributes;
        m_sessionTimeout = sessionTimeout;
        m_sessionCookie = sessionCookie;
        m_sessionUrl = sessionUrl;
        m_sessionWorkerName = sessionWorkerName;
    }

    Context getContext( final HttpContext httpContext )
    {
        return m_contexts.get( httpContext );
    }

    Context getOrCreateContext( final Model model )
    {
        Context context = m_contexts.get( model.getContextModel().getHttpContext() );
        if( context == null )
        {
            context = addContext( model );
            m_contexts.put( model.getContextModel().getHttpContext(), context );
        }
        return context;
    }

    void removeContext( final HttpContext httpContext )
    {
        removeHandler( getContext( httpContext ) );
        m_contexts.remove( httpContext );
    }

    private Context addContext( final Model model )
    {
        Context context = new HttpServiceContext( this, model.getContextModel().getContextParams(),
                                                  getContextAttributes(
                                                      BundleUtils.getBundleContext( model.getContextModel().getBundle()
                                                      )
                                                  ), model
                .getContextModel().getContextName(), model.getContextModel().getHttpContext(), model.getContextModel()
                .getAccessControllerContext()
        );
        context.setClassLoader( model.getContextModel().getClassLoader() );
        Integer sessionTimeout = model.getContextModel().getSessionTimeout();
        if( sessionTimeout == null )
        {
            sessionTimeout = m_sessionTimeout;
        }
        String sessionCookie = model.getContextModel().getSessionCookie();
        if( sessionCookie == null )
        {
            sessionCookie = m_sessionCookie;
        }
        String sessionUrl = model.getContextModel().getSessionUrl();
        if( sessionUrl == null )
        {
            sessionUrl = m_sessionUrl;
        }
        String workerName = model.getContextModel().getSessionWorkerName();
        if( workerName == null )
        {
            workerName = m_sessionWorkerName;
        }
        configureSessionManager( context, sessionTimeout, sessionCookie, sessionUrl, workerName );

        LOG.debug( "Added servlet context: " + context );

        if( isStarted() )
        {
            try
            {
                LOG.debug( "(Re)starting servlet contexts..." );
                // start the server handler if not already started
                Handler serverHandler = getHandler();
                if( !serverHandler.isStarted() && !serverHandler.isStarting() )
                {
                    serverHandler.start();
                }
                // if the server handler is a handler collection, seems like jetty will not automatically
                // start inner handlers. So, force the start of the created context
                if( !context.isStarted() && !context.isStarting() )
                {
                    context.start();
                }
            }
            catch( Exception ignore )
            {
                LOG.error( "Could not start the servlet context for http context ["
                           + model.getContextModel().getHttpContext() + "]", ignore
                );
            }
        }
        return context;
    }

    /**
     * Returns a list of servlet context attributes out of configured properties and attribues containing the bundle
     * context associated with the bundle that created the model (web element).
     *
     * @param bundleContext bundle context to be set as attribute
     *
     * @return context attributes map
     */
    private Map<String, Object> getContextAttributes( final BundleContext bundleContext )
    {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        if( m_contextAttributes != null )
        {
            attributes.putAll( m_contextAttributes );
        }
        attributes.put( WebContainerConstants.BUNDLE_CONTEXT_ATTRIBUTE, bundleContext );
        attributes.put( "org.springframework.osgi.web.org.osgi.framework.BundleContext", bundleContext );
        return attributes;
    }

    /**
     * Configures the session time out by extracting the session handlers->sessionManager for the context.
     *
     * @param context    the context for which the session timeout should be configured
     * @param minutes    timeout in minutes
     * @param cookie     Session cookie name. Defaults to JSESSIONID.
     * @param url        session URL parameter name. Defaults to jsessionid. If set to null or  "none" no URL
     *                   rewriting will be done.
     * @param workerName name appended to session id, used to assist session affinity in a load balancer
     */
    private void configureSessionManager( final Context context,
                                          final Integer minutes,
                                          final String cookie,
                                          final String url,
                                          final String workerName )
    {
        LOG.debug( "configureSessionManager for context [" + context + "] using - timeout:" + minutes
                   + ", cookie:" + cookie + ", url:" + url + ", workerName:" + workerName
        );
        final SessionHandler sessionHandler = context.getSessionHandler();
        if( sessionHandler != null )
        {
            final SessionManager sessionManager = sessionHandler.getSessionManager();
            if( sessionManager != null )
            {
                if( minutes != null )
                {
                    sessionManager.setMaxInactiveInterval( minutes * 60 );
                    LOG.debug( "Session timeout set to " + minutes + " minutes for context [" + context + "]" );
                }
                if( cookie != null )
                {
                    sessionManager.setSessionCookie( cookie );
                    LOG.debug( "Session cookie set to " + cookie + " for context [" + context + "]" );
                }
                if( url != null )
                {
                    sessionManager.setSessionURL( url );
                    LOG.debug( "Session URL set to " + url + " for context [" + context + "]" );
                }
                if( workerName != null )
                {
                    SessionIdManager sessionIdManager = sessionManager.getIdManager();
                    if( sessionIdManager == null )
                    {
                        sessionIdManager = new HashSessionIdManager();
                        sessionManager.setIdManager( sessionIdManager );
                    }
                    if( sessionIdManager instanceof HashSessionIdManager )
                    {
                        HashSessionIdManager s = (HashSessionIdManager) sessionIdManager;
                        s.setWorkerName( workerName );
                        LOG.debug( "Worker name set to " + workerName + " for context [" + context + "]" );
                    }
                }
            }
        }
    }
}