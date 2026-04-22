# Smart Campus System API

A fully functional RESTful API built for the University of Westminster's **Smart Campus** coursework. The system manages physical **Rooms** and **Sensors**, including nested **Sensor Readings**, with robust exception mapping, request/response logging, and rule-based validation.

---

## Tech Stack

![Java](https://img.shields.io/badge/Java-23-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Jakarta EE API](https://img.shields.io/badge/Jakarta%20EE%20API-11.0.0--M1-4CAF50?style=for-the-badge&logo=java&logoColor=white)
![Jersey](https://img.shields.io/badge/Jersey-3.1.5-2E7D32?style=for-the-badge&logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.6+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Tomcat](https://img.shields.io/badge/Tomcat-Deployment-F8DC75?style=for-the-badge&logo=apachetomcat&logoColor=black)
![JSON-B](https://img.shields.io/badge/JSON--B-via%20Jersey%20JSON%20Binding-yellow?style=for-the-badge&logo=json&logoColor=white)

| Technology | Role in Project |
| --- | --- |
| **Java 23** | Core language used by all API classes |
| **Jakarta EE API 11.0.0-M1 (provided)** | Platform API dependency in `pom.xml` (`jakarta.jakartaee-api`) |
| **Jersey 3.1.5** | Runtime implementation for Jakarta REST |
| **Tomcat (NetBeans deploy target)** | Application server used to run the WAR |
| **Maven** | Dependency management and build lifecycle |
| **JSON-B (Jersey JSON Binding)** | JSON serialization/deserialization via `jersey-media-json-binding` |
| **ConcurrentHashMap** | Thread-safe in-memory storage in singleton `DataStore` |

---

## What This System Does

The API acts as a campus infrastructure backend and supports:

- **Room management**: create, list, fetch, update, and delete rooms
- **Sensor lifecycle management**: register sensors, filter by type, update metadata/status/room assignment, delete sensors
- **Reading history tracking**: nested readings endpoint under each sensor
- **Rule enforcement**:
  - Prevent deleting rooms that still contain sensors
  - Prevent direct sensor value mutation via `PUT /sensors/{id}`
  - Prevent posting readings when sensor status is `MAINTENANCE` or `OFFLINE`
- **Clean error responses**: custom exception mappers return JSON error bodies
- **API observability**: request/response filter logs HTTP method, URI, and response status

---

## System Architecture

```text
HTTP Request
     |
[Tomcat Servlet Container]
     |
[Jersey / Jakarta REST Router]
     |
[Resource Layer]
  - DiscoveryResource
  - RoomResource
  - SensorResource
  - SensorReadingResource (sub-resource)
     |
[DataStore Singleton]
  - rooms: ConcurrentHashMap<String, Room>
  - sensors: ConcurrentHashMap<String, Sensor>
  - sensorReadings: ConcurrentHashMap<String, List<SensorReading>>
     |
[Model Layer]
  - Room
  - Sensor
  - SensorReading
     |
[Exception Mappers + Logging Filter]
```

### Key Design Patterns Used

#### 1. Singleton Pattern (`DataStore`)
`DataStore` is created once and shared across all request-scoped resources.

```java
private static DataStore instance;

public static synchronized DataStore getInstance() {
    if (instance == null) {
        instance = new DataStore();
    }
    return instance;
}
```

#### 2. Sub-Resource Locator Pattern (`SensorResource` -> `SensorReadingResource`)
Nested route delegation keeps reading logic isolated.

```java
@Path("/{id}/readings")
public SensorReadingResource getSensorReadingResource(@PathParam("id") String id) {
    return new SensorReadingResource(id);
}
```

#### 3. Exception Mapper Pattern (`@Provider`)
Business exceptions are translated into safe JSON errors with specific HTTP codes.

#### 4. Filter Pattern (`LoggingFilter`)
Cross-cutting request/response logging is applied without polluting resource methods.

---

## Data Models

### Room

| Field | Type | Description |
| --- | --- | --- |
| `name` | String | Room identifier used in path parameters |
| `location` | String | Human-readable location/building reference |
| `capacity` | int | Max occupancy |
| `sensors` | List<Sensor> | Sensors currently linked to this room |

Example JSON:
```json
{
  "name": "5LA",
  "location": "Level 5",
  "capacity": 160,
  "sensors": []
}
```

### Sensor

| Field | Type | Description |
| --- | --- | --- |
| `id` | String | Unique sensor ID |
| `type` | String | Sensor category (Temperature, CO2, etc.) |
| `status` | String | Operational state (ACTIVE, MAINTENANCE, OFFLINE) |
| `roomId` | String | Parent room key |
| `value` | double | Latest measurement value |

Example JSON:
```json
{
  "id": "TEMP-01",
  "type": "Temperature",
  "status": "ACTIVE",
  "roomId": "5LA",
  "value": 24.3
}
```

### SensorReading

| Field | Type | Description |
| --- | --- | --- |
| `value` | double | Measured value |
| `timestamp` | long | Epoch milliseconds (auto-set to current time when omitted in request body) |

Example JSON:
```json
{
  "timestamp": 1776877483366,
  "value": 10000.0
}
```

---

## API Reference

**Base URL (Tomcat default):** `http://localhost:8080/SmartCampusAPI/api/v1`

All endpoints produce JSON responses.

### Discovery

#### `GET /api/v1/`
Returns API metadata and discoverable links.

Response (`200 OK`):
```json
{
  "version": "v1.0.0",
  "name": "Smart Campus Management API",
  "contact": "admin@westminster.ac.uk",
  "links": {
    "self": "/api/v1",
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

### Rooms

#### `GET /api/v1/rooms`
Returns all rooms.

#### `POST /api/v1/rooms`
Creates a room.

Example request body:
```json
{
  "name": "5LA",
  "location": "5th Floor",
  "capacity": 160
}
```

Validation currently implemented:
- `room` body must exist
- `room.name` must be present and non-empty

Failure:
- `400 Bad Request` when invalid body/name

#### `GET /api/v1/rooms/{name}`
Returns one room by name.

- `404 Not Found` via `RoomNotFoundException` when missing

#### `PUT /api/v1/rooms/{name}`
Updates room fields.

Example request body:
```json
{
  "capacity": 200
}
```

Behavior:
- Preserves existing sensors list
- If `location` is null, keeps previous location
- If `capacity` is `0`, keeps previous capacity
- Forces `name` from path parameter

- `404 Not Found` via `RoomNotFoundException` when target room missing

#### `DELETE /api/v1/rooms/{name}`
Deletes room only when room has no sensors.

Responses:
- `204 No Content` on successful deletion
- `404 Not Found` via `RoomNotFoundException` when room does not exist
- `409 Conflict` via `RoomNotEmptyException` when room still contains sensors

### Sensors

#### `GET /api/v1/sensors`
Returns all sensors.

Optional filter:
- `?type=<value>` (case-insensitive exact type match)

#### `POST /api/v1/sensors`
Creates and links a sensor to a room.

Example request body:
```json
{
  "id": "SN-001",
  "type": "Temperature",
  "status": "Active",
  "roomId": "5LA"
}
```

Validation currently implemented:
- `sensor`, `sensor.id`, and `sensor.roomId` must be non-null
- referenced room must exist

Responses:
- `201 Created`
- `400 Bad Request` for missing required fields
- `422 Unprocessable Entity` via `LinkedResourceNotFoundException` if room does not exist

#### `GET /api/v1/sensors/{id}`
Returns sensor by ID.

- `404 Not Found` via `SensorNotFoundException` when missing

#### `PUT /api/v1/sensors/{id}`
Updates sensor metadata and/or room assignment.

Example request body:
```json
{
  "status": "Active"
}
```

Rule enforcement:
- Direct measurement mutation is blocked through this endpoint.
- If request sends non-zero `value` different from current value, API throws `IllegalSensorUpdateException`.

Additional behavior:
- Preserves `value` from existing sensor
- Null `type`/`status` keep existing values
- Null `roomId` keeps current room
- If `roomId` changes, sensor is moved between room sensor lists

Responses:
- `200 OK`
- `403 Forbidden` via `IllegalSensorUpdateException`
- `404 Not Found` via `SensorNotFoundException` if sensor missing
- `422 Unprocessable Entity` if target room does not exist

#### `DELETE /api/v1/sensors/{id}`
Deletes sensor and removes it from parent room list.

Responses:
- `204 No Content`
- `404 Not Found` via `SensorNotFoundException`

### Sensor Readings

#### `GET /api/v1/sensors/{id}/readings`
Returns reading history list for the sensor key (empty list if none found).

#### `POST /api/v1/sensors/{id}/readings`
Adds a new reading and updates parent sensor `value`.

Behavior:
1. Checks sensor exists
2. Blocks when status is `MAINTENANCE` or `OFFLINE`
3. Appends reading to in-memory list
4. Copies reading value to sensor's latest `value`
5. Returns the created reading object; when `timestamp` is omitted, response includes server-side current timestamp

Responses:
- `201 Created`
- `404 Not Found` with JSON error if sensor missing
- `403 Forbidden` via `SensorUnavailableException` when sensor unavailable

Example request body:
```json
{
  "value": 10000.0
}
```

Example `201 Created` response body:
```json
{
  "timestamp": 1776877883631,
  "value": 10000.0
}
```

---

## Error Handling

### Custom Exception Mappers

| Exception Class | HTTP Status | Trigger |
| --- | --- | --- |
| `RoomNotFoundException` | `404 Not Found` | Room lookup failed on `GET/PUT/DELETE /rooms/{name}` |
| `SensorNotFoundException` | `404 Not Found` | Sensor lookup failed on `GET/PUT/DELETE /sensors/{id}` |
| `RoomNotEmptyException` | `409 Conflict` | Deleting room that still contains sensors |
| `LinkedResourceNotFoundException` | `422 Unprocessable Entity` | Creating/updating sensor with invalid `roomId` |
| `SensorUnavailableException` | `403 Forbidden` | Posting readings to `MAINTENANCE`/`OFFLINE` sensor |
| `IllegalSensorUpdateException` | `403 Forbidden` | Direct sensor value update via `PUT /sensors/{id}` |

### Error Response Format

```json
{
  "error": "Human-readable message"
}
```

---

## Project Structure

```text
SmartCampusAPI/
|-- pom.xml
|-- src/main/java/com/smartcampus/
|   |-- api/
|   |   |-- DiscoveryResource.java
|   |   |-- RoomResource.java
|   |   |-- SensorResource.java
|   |   |-- SensorReadingResource.java
|   |   |-- filters/LoggingFilter.java
|   |   `-- mappers/
|   |       |-- IllegalSensorUpdateMapper.java
|   |       |-- LinkedResourceNotFoundExceptionMapper.java
|   |       |-- RoomNotFoundMapper.java
|   |       |-- RoomNotEmptyMapper.java
|   |       |-- SensorNotFoundMapper.java
|   |       `-- SensorUnavailableExceptionMapper.java
|   |-- config/RestApplication.java
|   |-- data/DataStore.java
|   |-- exceptions/
|   |   |-- IllegalSensorUpdateException.java
|   |   |-- LinkedResourceNotFoundException.java
|   |   |-- RoomNotFoundException.java
|   |   |-- RoomNotEmptyException.java
|   |   |-- SensorNotFoundException.java
|   |   `-- SensorUnavailableException.java
|   `-- model/
|       |-- Room.java
|       |-- Sensor.java
|       `-- SensorReading.java
`-- src/main/webapp/
    |-- index.html
    `-- WEB-INF/web.xml
```

---

## Build and Run (NetBeans + Tomcat)

### Prerequisites
- Java JDK 23 (or compatible with project compiler target)
- Maven 3.6+
- Apache NetBeans
- Apache Tomcat configured in NetBeans

### Steps

1. Open project in NetBeans.
2. Ensure Tomcat is selected as target server.
3. Run `Clean and Build`.
4. Run the project (NetBeans deploys WAR to Tomcat).
5. Access API at:

```text
http://localhost:8080/SmartCampusAPI/api/v1/
```

### Build Verification
A Maven package build has been verified successfully in this environment using the project `pom.xml`.

---

## Testing with curl

All examples assume:

```text
http://localhost:8080/SmartCampusAPI/api/v1
```

### Discovery

```bash
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/
```

### Rooms

```bash
# Create room
curl -X POST http://localhost:8080/SmartCampusAPI/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"5LA\",\"location\":\"Level 3\",\"capacity\":50}"

# List rooms
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/rooms

# Get room by name
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/rooms/5LA

# Update room
curl -X PUT http://localhost:8080/SmartCampusAPI/api/v1/rooms/5LA \
  -H "Content-Type: application/json" \
  -d "{\"location\":\"Main Building\",\"capacity\":60}"

# Delete room (works only if room has no sensors)
curl -X DELETE http://localhost:8080/SmartCampusAPI/api/v1/rooms/5LA
```

### Sensors

```bash
# Create sensor
curl -X POST http://localhost:8080/SmartCampusAPI/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"TEMP-01\",\"type\":\"Temperature\",\"status\":\"ACTIVE\",\"roomId\":\"5LA\"}"

# List all sensors
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/sensors

# Filter sensors by type
curl -X GET "http://localhost:8080/SmartCampusAPI/api/v1/sensors?type=temperature"

# Update sensor metadata (value update blocked here)
curl -X PUT http://localhost:8080/SmartCampusAPI/api/v1/sensors/TEMP-01 \
  -H "Content-Type: application/json" \
  -d "{\"type\":\"Temperature\",\"status\":\"MAINTENANCE\",\"roomId\":\"5LA\"}"

# Delete sensor
curl -X DELETE http://localhost:8080/SmartCampusAPI/api/v1/sensors/TEMP-01
```

### Sensor Readings

```bash
# Get reading history
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/sensors/TEMP-01/readings

# Add reading (updates sensor.value)
curl -X POST http://localhost:8080/SmartCampusAPI/api/v1/sensors/TEMP-01/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":24.5}"
```

### Triggering Rule Errors

```bash
# 409: delete room that still has sensors
curl -X DELETE http://localhost:8080/SmartCampusAPI/api/v1/rooms/5LA

# 422: create sensor with invalid roomId
curl -X POST http://localhost:8080/SmartCampusAPI/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"CO2-01\",\"type\":\"CO2\",\"roomId\":\"NON-EXISTENT\"}"

# 404: get room that does not exist
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/rooms/NO-SUCH-ROOM

# 404: get sensor that does not exist
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/sensors/NO-SUCH-SENSOR

# 403: post reading to a sensor under maintenance
curl -X POST http://localhost:8080/SmartCampusAPI/api/v1/sensors/TEMP-01/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":19.0}"
```

---

## Important Notes

- **In-memory only**: data resets on server restart
- **No authentication layer**: coursework-focused backend
- **Thread safety**: `ConcurrentHashMap` used for shared collections
- **Bidirectional room-sensor link**: sensor create/update/delete also updates parent room sensor list
- **No seeded dataset in current code**: `DataStore` constructor does not preload rooms/sensors/readings

---

## Module Information

- **Module:** 5COSC022W Client-Server Architectures
- **University:** University of Westminster
- **Academic Year:** 2025/26

---

## Conceptual Report - Question Answers

### Part 1.1 - JAX-RS Resource Lifecycle
By default, Jakarta REST resources are request-scoped. A new instance of classes like `RoomResource` or `SensorResource` is created per request and then discarded. Because of this, request classes cannot safely hold durable shared state. This project uses a singleton `DataStore` to persist in-memory state across requests. Since requests may execute concurrently, thread-safe collections (`ConcurrentHashMap`) are used to reduce race-condition risk during multi-user access.

### Part 1.2 - HATEOAS and Hypermedia
The discovery endpoint (`GET /api/v1/`) returns navigable links (`self`, `rooms`, `sensors`). This supports basic hypermedia discoverability: clients can start from one entry point and discover available collections. Compared with hardcoded URL assumptions, this reduces client coupling when route structures evolve.

### Part 2.1 - Returning IDs Only vs Full Objects
Returning only IDs reduces payload size but causes additional round-trips to fetch details (N+1 effect). Returning full objects increases payload size but improves client efficiency for common UI use cases. For a campus-scale in-memory coursework API, full object responses are reasonable and simplify client integration.

### Part 2.2 - Idempotency of DELETE
`DELETE` is idempotent with respect to server state. Example: deleting an existing room once removes it; repeating the same delete keeps the state unchanged (room still absent), even if response codes differ (first success, then 404). Also, repeated delete attempts on non-empty rooms consistently fail without changing server state.

### Part 3.1 - `@Consumes` and Content-Type Mismatch
Endpoints annotated with `@Consumes(MediaType.APPLICATION_JSON)` expect JSON request bodies. If a client sends an unsupported media type (for example `text/plain`), the runtime rejects the request with `415 Unsupported Media Type` before method business logic executes. If media type is JSON but payload is malformed, deserialization failure typically results in a `400` class error.

### Part 3.2 - `@QueryParam` vs Path-Based Filtering
Filtering with query parameters (`/sensors?type=CO2`) is semantically correct because the base collection remains the same while query options refine results. Path-based filter segments quickly cause route explosion and conflict with identifier paths like `/sensors/{id}`. Query parameters remain optional, composable, and standards-friendly.

### Part 4.1 - Sub-Resource Locator Pattern
`SensorResource` delegates `/sensors/{id}/readings` to `SensorReadingResource`, which keeps reading-specific logic isolated and maintains single responsibility. This improves maintainability, testing granularity, and scalability for deeper nested resources.

### Part 5.2 - HTTP 422 vs 404 for Missing `roomId`
When creating/updating a sensor with a non-existent `roomId`, the endpoint itself exists and the request format is understood, so `422 Unprocessable Entity` is more precise than `404`. Here, `404` would incorrectly imply the URL is missing, while `422` correctly indicates semantic payload invalidity.

### Part 5.4 - Security Risk of Exposing Stack Traces
Raw stack traces leak internals such as package structure, library usage, and logic flow. Attackers can use this intelligence for targeted exploitation. This project mitigates exposure by mapping known business exceptions to controlled JSON error responses and keeping detailed technical traces in server-side logs.

---