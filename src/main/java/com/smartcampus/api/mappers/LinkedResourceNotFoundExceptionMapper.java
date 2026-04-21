/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api.mappers;

import com.smartcampus.exceptions.LinkedResourceNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 *
 * @author YasithMP
 */
@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        String jsonError = String.format("{\"error\": \"%s\"}", exception.getMessage());
        
        // Using 422 explicitly as JAX-RS Response.Status enum does not include Unprocessable Entity
        return Response.status(422)
                .entity(jsonError)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}