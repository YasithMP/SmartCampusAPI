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

/**
 *
 * @author YasithMP
 */
@Path("/crash")
public class CrashTestResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerCrash() {
        String brokenString = null;
        brokenString.length(); 
        
        return Response.ok().build();
    }
}