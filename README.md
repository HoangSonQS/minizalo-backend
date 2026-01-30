# MiniZalo Backend 📱

[![Java 17+](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/17/)
[![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.2.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![AWS DynamoDB](https://img.shields.io/badge/DynamoDB-Scalable-4053D6?style=for-the-badge&logo=amazondynamodb&logoColor=white)](https://aws.amazon.com/dynamodb/)

**MiniZalo Backend** is a high-performance, real-time messaging engine built with **Spring Boot 3**. It features a modern hybrid architecture designed for lightning-fast message delivery, scalable media storage, and intelligent AI companion features.

---

## 🏗️ Technical Architecture

The project follows a **Modified Modular Monolith** approach, optimizing for both developer velocity and system performance:

- **Relational Integrity (PostgreSQL)**: Handles Users, Relationships, and Account Metadata with strict ACID compliance.
- **High-Velocity Streams (DynamoDB)**: Offloads chat history and audit logs to NoSQL to ensure sub-millisecond persistence even under heavy load.
- **Distributed Caching (Redis)**: Optimizes presence tracking and real-time session management.
- **S3-Compatible Storage (MinIO)**: Manages unstructured media data (images, documents, avatars) with secure pre-signed URL access.

---

## ✨ Premium Features

### ⚡ Real-Time Engine
- **STOMP over WebSocket**: Low-latency, full-duplex communication.
- **Micro-Interactions**: Real-time typing indicators, read receipts, and multi-user reactions.
- **Advanced Controls**: Message recall (24h window), reply-to threading, and seamless forwarding.

### 🤖 Smart Intelligence
- **AI Companion**: Integrated **OpenAI GPT-4o** powered chatbots available for every user.
- **Contextual Responses**: The bot maintains conversation context for helpful interactions.
- **Analytics Dashboard**: Real-time tracking of message volume, user growth, and engagement metrics via the Analytics API.

### 🔐 Zero-Trust Security
- **Dual-Token System**: Secure JWT authentication with refresh token rotation.
- **Strict Authorization**: Multi-level roles (ADMIN, MEMBER) for group security and privacy.
- **Encrypted Storage**: Bcrypt password hashing and secure environment variable management.

---

## 🛠️ Technology Stack

| Layer | System |
| :--- | :--- |
| **Language** | Java 17 (LTS) |
| **Backend Core** | Spring Boot 3.2.1, Spring Data JPA, Spring Security |
| **Messaging** | Spring WebSocket (STOMP), Firebase Cloud Messaging |
| **Persistence** | PostgreSQL 13, AWS DynamoDB (Local/Cloud) |
| **Caching** | Redis (Alpine) |
| **Media** | MinIO (S3 Compatible Storage) |
| **DevOps** | Docker, Docker Compose, GitHub Actions CI |

---

## 🚀 Quick Start (Local Development)

### Prerequisites
- Docker Desktop
- Java 17 (optional for local run, mandatory for build)

### 1. Simple Setup (Docker)
The easiest way to get started is using the hardened Docker environment:

```bash
# 1. Clone & Enter
git clone https://github.com/HoangSonQS/minizalo-backend.git
cd minizalo-backend

# 2. Add Firebase Credentials
# Place firebase-service-account.json in src/main/resources/

# 3. Spin up the entire stack
docker-compose up -d --build
```

### 2. Access Points
- **Swagger Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui/index.html)
- **MinIO Media Console**: `http://localhost:9001` (User/Pass: `minioadmin`)
- **DynamoDB Admin**: `http://localhost:8000`

---

## 🧪 Testing & Validation

We maintain high code quality through various testing tiers:

```bash
# Run all Unit & Integration Tests
./mvnw test

# Run Specific Integration Test (Full Flow)
./mvnw test -Dtest=UserJourneyIntegrationTest
```

- **Unit Tests**: Coverage for core business services and models.
- **Full-Flow Integration Tests**: Automated verification of the Signup -> Login -> Chat -> Analytics journey.

---

## 📂 Project Structure

```bash
src/main/java/iuh/fit/se/minizalobackend
├── config          # Infrastructure (Security, S3, WS, DB)
├── controllers     # REST & WebSocket Handlers
├── dtos            # API Request/Response Models
├── models          # JPA Entities & Dynamo Icons
├── repository      # PostgreSQL & DynamoDB Access
├── services        # Core Business Logic
└── utils           # AppConstants & Utilities
```

---

## 📝 License
This project is private and intended for educational purposes at IUH.

---
*Built with ❤️ by the MiniZalo Team.*
