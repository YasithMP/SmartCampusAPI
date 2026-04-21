/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author YasithMP
 */
@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response discover() {
        Map<String, Object> discoveryData = new HashMap<>();

        discoveryData.put("version", "v1.0.0");
        discoveryData.put("name", "Smart Campus Management API");
        discoveryData.put("contact", "admin@westminster.ac.uk");

        Map<String, String> links = new HashMap<>();
        links.put("self", "/api/v1");
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");

        discoveryData.put("links", links);

        return Response.ok(discoveryData).build();
    }
    
}