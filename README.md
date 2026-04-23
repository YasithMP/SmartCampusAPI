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
- **API observability**: request/response filter logs HTTP method, URI, response status, and escalates failed responses to warning/severe levels
- **Global server-failure handling**: uncaught runtime errors are mapped to a sanitized `500` JSON response

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
  - CrashTestResource
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

### Crash Test

#### `GET /api/v1/crash`
Deliberately triggers an unhandled runtime failure for testing global exception handling.

Behavior:
- Throws a `NullPointerException` intentionally inside the endpoint
- Is handled by `GlobalExceptionMapper`
- Returns a sanitized `500 Internal Server Error` JSON body instead of exposing stack traces

Example response (`500 Internal Server Error`):
```json
{
  "error": "An unexpected internal server error occurred. Please try again later."
}
```

---

## Error Handling

### Custom Exception Mappers

| Exception Class | HTTP Status | Trigger |
| --- | --- | --- |
| `Throwable` (via `GlobalExceptionMapper`) | `500 Internal Server Error` | Any uncaught exception not handled by specific mappers |
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
|   |   |-- CrashTestResource.java
|   |   |-- DiscoveryResource.java
|   |   |-- RoomResource.java
|   |   |-- SensorResource.java
|   |   |-- SensorReadingResource.java
|   |   |-- filters/LoggingFilter.java
|   |   `-- mappers/
|   |       |-- GlobalExceptionMapper.java
|   |       |-- IllegalSensorUpdateMapper.java
|   |       |-- LinkedResourceNotFoundExceptionMapper.java
|   |       |-- RoomNotFoundMapper.java
|   |       |-- RoomNotEmptyMapper.java
|   |       |-- SensorNotFoundMapper.java
|   |       `-- SensorUnavailableExceptionMapper.java
|   |-- config/RestApplication.java
|   |-- data/DataStore.java
|   |-- exceptions/
|   |   |-- GlobalException.java
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

### Expert Setup Evidence
- Maven packaging is `war`, suitable for servlet-container deployment.
- JAX-RS/Jakarta REST setup is provided by Jersey dependencies: `jersey-container-servlet`, `jersey-hk2`, and `jersey-media-json-binding`.
- Jakarta EE API is declared as `provided`, which matches container-managed runtime behavior.
- API base path is centrally configured using:

```java
@ApplicationPath("/api/v1")
public class RestApplication extends Application {
}
```

This guarantees that all resource URIs are rooted at `/api/v1` and keeps URI versioning explicit.

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

# 500: trigger intentional crash endpoint
curl -X GET http://localhost:8080/SmartCampusAPI/api/v1/crash
```

---

## Important Notes

- **In-memory only**: data resets on server restart
- **No authentication layer**: coursework-focused backend
- **Thread safety**: `ConcurrentHashMap` used for shared collections
- **Bidirectional room-sensor link**: sensor create/update/delete also updates parent room sensor list
- **No seeded dataset in current code**: `DataStore` constructor does not preload rooms/sensors/readings
- **`/crash` endpoint is test-only**: designed to validate `500` handling and logging, not for production use

---

## Module Information

- **Module:** 5COSC022W Client-Server Architectures
- **University:** University of Westminster
- **Academic Year:** 2025/26

---

## Conceptual Report - Question Answers

### Part 1.1 - JAX-RS Resource Lifecycle
In JAX-RS, resource classes are request-scoped unless you do something different yourself. So for each HTTP request, Jersey creates a fresh resource object and throws it away after the response. That keeps things stateless, but it also means you should not store shared state inside the resource class.

In my API, I keep shared state in a singleton `DataStore`, and I keep request logic inside the resource classes. The maps inside `DataStore` are `ConcurrentHashMap`, so normal map operations are safe across concurrent requests.

What this already gives me:
- Safe concurrent `get/put/remove` operations on core maps.
- Clear place for domain checks (for example, preventing invalid room-sensor transitions).

If this system had to scale further, I would harden synchronisation with:
- `compute` / `computeIfPresent` style updates for atomic multi-step changes.
- Lock-per-room (or `ReadWriteLock`) for cross-collection invariants.
- Strict pre-validation before mutation so partial updates cannot happen.
- Read snapshots for heavy read endpoints.

### Part 1.2 - HATEOAS and Hypermedia
My discovery endpoint (`GET /api/v1/`) returns version info, contact info, and links to main resources. In practice, this acts like a live API index.

Why I think this is useful:
- Clients can start from one known URL and discover routes instead of hardcoding everything.
- If paths change later, the runtime response is still the source of truth.
- Tooling can test discoverability directly from the root response.
- Version metadata in the same response helps with safer client upgrades.

### Part 2.1 - Returning IDs Only vs Full Objects
If I return only IDs, each response is smaller, but clients usually have to make extra calls to get details (classic N+1 problem). If I return full objects, payloads are bigger, but clients can do more with fewer requests.

So the trade-off is basically:
- IDs only: lower payload per call, more client-side orchestration.
- Full objects: bigger payloads, fewer round-trips.

For this coursework API, I chose full objects because entities are not huge and it keeps client code simple. If the dataset got much larger, a hybrid response shape would make more sense.

### Part 2.2 - Idempotency of DELETE
`DELETE` here is idempotent from a state perspective, even if the status code can change across retries.

For `DELETE /rooms/{name}`:
1. If the room exists and is empty, first call deletes it (`204`).
2. Calling it again gives `404`, but nothing changes on the server.
3. If the room still has sensors, call returns `409` and the room stays untouched.
4. Repeating that blocked call still returns `409` with no state change.

So repeated identical DELETE calls do not keep changing server state after the first transition (or after a blocked transition), which is the key idempotency requirement.

### Part 3.1 - `@Consumes` and Content-Type Mismatch
`@Consumes(MediaType.APPLICATION_JSON)` means the endpoint is expecting JSON. If a client sends `text/plain` (or another unsupported media type), the JAX-RS/Jersey layer rejects it with `415 Unsupported Media Type`.

That matters because:
- Content-type problems are handled at framework level.
- Bad media types never reach business validation.
- If content type is JSON but the JSON is malformed, the failure typically happens at deserialization and returns `400 Bad Request`.

### Part 3.2 - `@QueryParam` vs Path-Based Filtering
For filtering a collection, query params are the right fit. `/sensors?type=CO2` still represents the sensors collection, just filtered. Path params are better for identity, like `/sensors/{id}`.

Why query params work better for this:
- Filters stay optional.
- It is easy to combine multiple filters later.
- It avoids route explosion.
- It is a familiar pattern for API consumers and tools.

### Part 4.1 - Sub-Resource Locator Pattern
`SensorResource` delegates `/{id}/readings` to a separate `SensorReadingResource` class. This keeps nested reading logic out of top-level sensor CRUD logic.

Why this design helps:
- Better separation of concerns.
- Easier targeted testing for readings.
- Cleaner growth path if readings need pagination/analytics later.
- URI structure stays clean and intuitive.

### Part 4.2 - Consistency Between Readings and Current Sensor Value
In `POST /sensors/{id}/readings`, I append the new reading and then update the parent sensor's current value in the same request flow.

So both views stay in sync:
- Reading history stores the event log.
- Sensor current value reflects the latest accepted reading.
- Clients get consistent data whether they read the sensor or its readings.

### Part 5.1 - Leak-Proof Exception Mapping (409, 422, 403)
Exception mappers give consistent JSON error responses for business-rule failures:
- `409 Conflict` when deleting a room that still contains sensors.
- `422 Unprocessable Entity` when payload references a missing linked resource (`roomId`).
- `403 Forbidden` when updates violate domain policy (direct value updates or unavailable sensor readings).

Because mapping is centralized, clients see predictable status codes and a stable response shape.

### Part 5.2 - HTTP 422 vs 404 for Missing `roomId`
If `roomId` does not exist during sensor create/update, the endpoint itself is still valid and reachable. The problem is with request content, not URL routing.

That is why `422 Unprocessable Entity` is more accurate than `404 Not Found`.

Client impact:
- `404` can mislead debugging toward path mistakes.
- `422` tells the client to fix payload semantics.
- Retry logic is cleaner because route errors and data errors are separated.

### Part 5.4 - Security Risk of Exposing Stack Traces
Raw stack traces in API responses leak useful internal details (package structure, framework clues, call flow), and that can help attackers profile the system.

In this project:
- Specific mappers convert known domain failures into controlled JSON responses.
- A catch-all `ExceptionMapper<Throwable>` guarantees an intentionally generic `500` payload.
- Detailed diagnostics are retained in server logs (for maintainers) rather than exposed to remote clients.

Result: clients get clean and minimal errors, while debugging detail stays server-side.

---