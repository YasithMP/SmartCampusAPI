/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api.mappers;

import com.smartcampus.exceptions.IllegalSensorUpdateException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 *
 * @author YasithMP
 */
@Provider
public class IllegalSensorUpdateMapper implements ExceptionMapper<IllegalSensorUpdateException> {
    @Override
    public Response toResponse(IllegalSensorUpdateException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\": \"" + exception.getMessage() + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}