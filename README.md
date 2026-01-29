# MiniZalo Backend 📱

![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)
![Build](https://img.shields.io/badge/Build-Maven-red)

**MiniZalo Backend** is a scalable, real-time messaging application backend inspired by Zalo. Built with **Spring Boot 3**, it utilizes a **Hybrid Database Architecture** to balance relational data integrity with high-throughput message handling.

## 🏗️ Architecture Highlights

The system leverages a hybrid approach to data storage to optimize performance:
*   **PostgreSQL (Relational):** Manages structured data requiring ACID transactions such as Users, Friendships, Group Memberships, and Auth Credentials.
*   **DynamoDB (NoSQL):** Handles the high-volume, write-heavy workload of Chat History and Message Logs, ensuring horizontal scalability.

## ✨ Key Features

### 🔐 Authentication & Security
*   **Secure Access:** JWT-based authentication (Access Token + Refresh Token rotation).
*   **User Management:** Register, Login, Logout, Profile updates.
*   **Role-Based Access Control (RBAC):** Admin, Deputy, and Member roles for group management.

### ⚡ Real-time Communication
*   **WebSocket Protocol:** Powered by **STOMP** over WebSocket for low-latency delivery.
*   **Instant Messaging:** 1-on-1 and Group chats delivered in real-time.
*   **Interactive Features:**
    *   Typing indicators ("User is typing...").
    *   Read receipts (Seen status).
    *   Message Reactions (❤️, 👍, 😂).
    *   Message Recall (Unsend).
    *   Reply & Forward capabilities.
*   **User Presence:** Real-time Online/Offline status tracking.

### 📂 Media & Storage
*   **Object Storage:** Integration with **MinIO** (S3 Compatible) for scalable storage of avatars, images, and file attachments.
*   **Secure Uploads:** Uses Presigned URLs for secure, direct client-to-storage uploads.

### � Notifications
*   **Push Notifications:** Integration with **Firebase Cloud Messaging (FCM)** to deliver messages even when the app is in the background.

## 🛠️ Tech Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Java 17 |
| **Framework** | Spring Boot 3.2.1, Spring Security, Spring WebSocket |
| **Relational DB** | PostgreSQL |
| **NoSQL DB** | DynamoDB (Local for Dev, AWS for Prod) |
| **Storage** | MinIO |
| **Build Tool** | Maven |
| **Infrastructure** | Docker & Docker Compose |

## 🚀 Getting Started

### Prerequisites
*   Java 17+ via JDK
*   Docker Desktop (running)
*   git

### Installation

1.  **Clone the repository**
    ```bash
    git clone https://github.com/HoangSonQS/minizalo-backend.git
    cd minizalo-backend
    ```

2.  **Configure Environment**
    > **⚠️ CRITICAL:** You must provide your own Firebase credential file.
    *   Place your `firebase-service-account.json` file inside `src/main/resources/`.
    *   *Note: This file is sensitive and is ignored by Git.*

3.  **Run with Docker Compose** (Recommended)
    This command builds the backend image and starts all services (Postgres, DynamoDB, MinIO).
    ```bash
    # Build package skipping tests (tests require running containers)
    ./mvnw clean package -DskipTests
    
    # Start infrastructure
    docker-compose up -d --build
    ```

4.  **Access the Application**
    *   **API Server:** `http://localhost:8080`
    *   **Swagger API Docs:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
    *   **MinIO Console:** `http://localhost:9001` (User: `minioadmin`, Pass: `minioadmin`)

## ⚙️ Configuration (Environment Variables)

The application moves key configurations to `application.properties` or Docker environment variables.

| Variable | Description | Default (Docker) |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | PostgreSQL Connection URL | `jdbc:postgresql://minizalo-db:5432/minizalodb` |
| `APP_JWT_SECRET` | Secret key for signing JWTs | *(Set in docker-compose.yml)* |
| `AWS_DYNAMODB_ENDPOINT` | Endpoint for DynamoDB | `http://dynamodb-local:8000` |
| `MINIO_ENDPOINT` | MinIO Server URL | `http://minio:9000` |
| `MINIO_ACCESS_KEY` | MinIO Username | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO Password | `minioadmin` |

## 📂 Project Structure

```
src/main/java/iuh/fit/se/minizalobackend
├── config          # App configurations (Security, WebSocket, MinIO, etc.)
├── controllers     # REST API Controllers
├── dtos            # Data Transfer Objects (Requests/Responses)
├── models          # JPA and DynamoDB Entities
├── repository      # Data Access Layer
├── security        # JWT Auth filters and UserDetails logic
├── services        # Business Logic Layer
└── utils           # Helper classes
```

## 🤝 Contributing
Contributions are welcome! Please fork the repository and submit a Pull Request.
