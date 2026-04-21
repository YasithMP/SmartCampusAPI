/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smartcampus.api;

import com.smartcampus.data.DataStore;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
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
@Path("/sensors")
public class SensorResource {

    private final DataStore dataStore = DataStore.getInstance();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSensors() {
        List<Sensor> sensorList = new ArrayList<>(dataStore.getSensors().values());
        return Response.ok(sensorList).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getRoomId() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Room room = dataStore.getRooms().get(sensor.getRoomId());
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        room.getSensors().add(sensor);
        dataStore.getSensors().put(sensor.getId(), sensor);
        
        return Response.status(Response.Status.CREATED).entity(sensor).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensorById(@PathParam("id") String id) {
        Sensor sensor = dataStore.getSensors().get(id);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(sensor).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSensor(@PathParam("id") String id, Sensor updatedSensor) {
        Sensor existingSensor = dataStore.getSensors().get(id);
        if (existingSensor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (updatedSensor.getType() == null) updatedSensor.setType(existingSensor.getType());
        if (updatedSensor.getStatus() == null) updatedSensor.setStatus(existingSensor.getStatus());
        
        String oldRoomId = existingSensor.getRoomId();
        if (updatedSensor.getRoomId() == null) updatedSensor.setRoomId(oldRoomId);

        Room newRoom = dataStore.getRooms().get(updatedSensor.getRoomId());
        if (newRoom == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        updatedSensor.setId(id);
        dataStore.getSensors().put(id, updatedSensor);

        Room oldRoom = dataStore.getRooms().get(oldRoomId);
        if (oldRoom != null) {
            oldRoom.getSensors().removeIf(s -> s.getId().equals(id));
        }
        newRoom.getSensors().add(updatedSensor);

        return Response.ok(updatedSensor).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteSensor(@PathParam("id") String id) {
        Sensor sensor = dataStore.getSensors().remove(id);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Room room = dataStore.getRooms().get(sensor.getRoomId());
        if (room != null) {
            room.getSensors().removeIf(s -> s.getId().equals(id));
        }

        return Response.noContent().build();
    }
}