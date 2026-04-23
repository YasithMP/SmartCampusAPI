/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api.filters;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author YasithMP
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();
        String uri = requestContext.getUriInfo().getRequestUri().toString();
        
        LOGGER.log(Level.INFO, ">>> [REQUEST] Method: {0} | URI: {1}", new Object[]{method, uri});
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        int status = responseContext.getStatus();
        
        if (status >= 400 && status < 500) {
            LOGGER.log(Level.WARNING, "!!! [CLIENT ERROR] Status: {0} | Entity: {1}", new Object[]{status, responseContext.getEntity()});
        } else if (status >= 500) {
            LOGGER.log(Level.SEVERE, "!!! [SERVER ERROR] Status: {0} | Entity: {1}", new Object[]{status, responseContext.getEntity()});
        }
        
        LOGGER.log(Level.INFO, "<<< [RESPONSE] Status: {0}", status);
    }
}