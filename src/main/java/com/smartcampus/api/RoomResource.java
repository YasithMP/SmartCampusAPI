/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api;

import com.smartcampus.data.DataStore;
import com.smartcampus.model.Room;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author YasithMP
 */
@Path("/rooms")
public class RoomResource {

    private final DataStore dataStore = DataStore.getInstance();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRooms() {
        List<Room> roomList = new ArrayList<>(dataStore.getRooms().values());
        return Response.ok(roomList).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addRoom(Room room) {
        if (room == null || room.getName() == null || room.getName().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        dataStore.getRooms().put(room.getName(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomByName(@PathParam("name") String name) {
        Room room = dataStore.getRooms().get(name);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(room).build();
    }

    @PUT
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateRoom(@PathParam("name") String name, Room updatedRoom) {
        Room existingRoom = dataStore.getRooms().get(name);
        if (existingRoom == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (updatedRoom.getLocation() == null) updatedRoom.setLocation(existingRoom.getLocation());
        if (updatedRoom.getCapacity() == 0) updatedRoom.setCapacity(existingRoom.getCapacity());
        
        updatedRoom.setSensors(existingRoom.getSensors());

        updatedRoom.setName(name);
        dataStore.getRooms().put(name, updatedRoom);
        return Response.ok(updatedRoom).build();
    }

    @DELETE
    @Path("/{name}")
    public Response deleteRoom(@PathParam("name") String name) {
        if (dataStore.getRooms().remove(name) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }
}