# MiniZalo Backend

Backend service for MiniZalo application (Spring Boot 3.2 + PostgreSQL + MinIO + WebSocket).

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Docker & Docker Compose

### Run Infrastructure
```bash
docker-compose up -d
```
This starts PostgreSQL, MinIO, and DynamoDB (legacy).

### Run Application
```bash
./mvnw spring-boot:run
```
Server starts at `http://localhost:8080`.

## 📡 Realtime Chat (WebSocket)

- **Endpoint**: `ws://localhost:8080/ws`
- **Fallback**: SockJS at `http://localhost:8080/ws`
- **Authentication**: JWT Token required in `Authorization` header during STOMP Connect.
  - Header: `Authorization: Bearer <your_access_token>`

### Topics
- **Subscribe**: `/topic/user/{your_user_id}` (Receives `ChatMessageResponse`)
- **Send**: `/app/chat.send` (Payload: `ChatMessageRequest`)

## 📝 API Reference

### Auth
- `POST /api/auth/signup`
- `POST /api/auth/login`

### Chat
- `GET /api/messages?userId={targetId}&page=0&size=20` (Get history)
- `POST /messages/recall` (Recall message)

## 🧪 Testing
Run unit and integration tests:
```bash
./mvnw test
```
