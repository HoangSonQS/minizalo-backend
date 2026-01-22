# MiniZalo Backend

Backend service for MiniZalo application (Spring Boot 3.2 + PostgreSQL + DynamoDB + MinIO + WebSocket).

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose

### Run Infrastructure
```bash
docker-compose up -d
```
This starts PostgreSQL (Users/Rooms), MinIO (Files), and DynamoDB (Message History).

### Run Application
```bash
./mvnw spring-boot:run
```
Server starts at `http://localhost:8080`.

## 📡 Realtime Chat (WebSocket)

- **Endpoint**: `ws://localhost:8080/ws`
- **Fallback**: SockJS at `http://localhost:8080/ws`
- **Authentication**: JWT Token required in `Authorization` header during STOMP Connect.

### Topics
- **Individual**: `/topic/user/{user_id}` (Direct messages)
- **Group Messages**: `/topic/group/{group_id}/messages` (Group chat history)
- **Group Events**: `/topic/group/{group_id}/events` (Member joined/left, name changed, etc.)

## 📝 API Reference

### Auth
- `POST /api/auth/signup`
- `POST /api/auth/signin`
- `POST /api/auth/refreshtoken`

### Group Chat
- `POST /api/group`: Create a new group
- `POST /api/group/members`: Add members to group
- `DELETE /api/group/members`: Remove members from group
- `GET /api/group/{groupId}`: Get group details
- `GET /api/group/my-groups`: Get current user's groups
- `POST /api/group/message`: Send a group message (Success returns 200, message is delivered via WebSocket)
- `POST /api/group/leave/{groupId}`: Leave a group

### Message History
- `GET /api/chat/history/{roomId}`: Get paginated history from DynamoDB

## 🧪 Testing
Run unit and integration tests:
```bash
./mvnw test
```
