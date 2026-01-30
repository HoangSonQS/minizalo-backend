# Báo Cáo Hoàn Thiện Backend MiniZalo

## 1. Mục Tiêu Chung
Hoàn thiện backend của dự án MiniZalo để đáp ứng đầy đủ yêu cầu đồ án: quản lý tài khoản & nhóm, chat 1‑1 & group (text, image, video, document, emotion), chatbot AI, báo cáo/thống kê hoạt động người dùng, cùng pipeline deploy & test.

## 2. Tổng Quan Hiện Trạng
### Đã Triển Khai
- **Authentication**: JWT, Security Config.
- **Quản lý Group/ChatRoom**: Basic CRUD, GroupChatController.
- **Message Core**: DynamoDB storage, recall/read/reaction/pin basics.
- **Realtime**: STOMP over WebSocket.
- **Storage**: MinIO configuration (local & mock).
- **Docs**: README architecture.

### Cần Bổ Sung
1. **Media**: Upload image/video/doc, xử lý metadata, hiển thị trong tin nhắn.
2. **Chatbot AI**: Tích hợp OpenAI/LLM, flow chat với bot.
3. **Analytics**: API thống kê (messages/day, active users).
4. **Tests & CI**: Integration tests, GitHub Actions pipeline.
5. **Deploy**: Docker-compose hoàn chỉnh, hướng dẫn Cloud deploy.
6. **Documentation**: Mapping requirement -> code, báo cáo tiến độ.

---

## 3. Danh Sách Công Việc (Tasks)

### A. Media (Ưu tiên: Cao)
- **Task A1: API Upload File**
    - Endpoint: `POST /api/files/upload`
    - Validation: Mime-type, Size limit (20MB).
    - Storage: MinIO.
    - Output: Presigned URL / Public URL.
- **Task A2: Attachments trong Message**
    - Cập nhật `MessageDynamo`: Thêm field attachments (URL, type, thumb).
    - Logic `MessageService`: Handling type `IMAGE`, `VIDEO`, `DOCUMENT`.
- **Task A3: Video Metadata** (Optional)
    - Generate thumbnail, duration.

### B. Chatbot AI (Ưu tiên: Cao)
- **Task B1: BotService Adapter**
    - Integrate OpenAI API.
    - Config: `APP_OPENAI_KEY`.
- **Task B2: Bot Integration Flow**
    - Bot join group hoặc command `/bot`.
    - Bot reply -> Save to DynamoDB -> Broadcast WebSocket.
- **Task B3: Demo & Test**
    - Mock OpenAI response for testing.

### C. Analytics (Ưu tiên: Cao)
- **Task C1: AnalyticsService**
    - Metrics: messages/day, active_users.
    - Data source: Aggregation from DynamoDB/Postgres.
- **Task C2: Reporting API**
    - `GET /api/analytics/messages?from=...&to=...`

### D. Tests & CI (Ưu tiên: Cao)
- **Task D1: Unit Tests**
    - Coverage services chính.
- **Task D2: Integration Tests**
    - WebSocket flow, File upload flow.
- **Task D3: GitHub Actions**
    - Build -> Test -> Docker Build.

### E. Deploy & Release (Ưu tiên: Trung)
- **Task E1: Docker Compose**
    - App, Postgres, DynamoDB-Local, MinIO, Redis.
- **Task E2: Cloud Deploy Guide**
    - Docs for DigitalOcean/AWS.
- **Task E3: Scaling**
    - Redis Broker for WebSocket.

### F. Tài Liệu (Ưu tiên: Cao)
- **Task F1: Final Report**
    - Diagrams, Screenshots, Traceability Matrix.
- **Task F2: Project Management**
    - Log tiến độ, phân công thành viên.

### G. Hardening (Ưu tiên: Trung)
- Rate limiting, Error handling, Logging.

---

## 4. Timeline Dự Kiến (4 Tuần)

| Tuần | Sprint | Mục Tiêu Chính | Nội Dung Chi Tiết |
| :--- | :--- | :--- | :--- |
| **1** | Sprint 1 | **Media Core** | File Controller, MinIO upload, Message Attachments. |
| **2** | Sprint 2 | **Bot & Analytics** | OpenAI Integration, Analytics API basics. |
| **3** | Sprint 3 | **CI/CD & Tests** | GitHub Actions, Integration Tests, Docker Compose. |
| **4** | Sprint 4 | **Hardening & Report** | Final Polish, Documentation, Bug fixes.

## 5. Phân Công Nhân Sự (Gợi ý nhóm 4)
- **Backend Core (2)**: MessageService, File Upload, Group Logic.
- **AI & Analytics (1)**: BotService, Stats API.
- **Infra (1)**: Docker, CI/CD, Deploy.
- **Lead/QA**: Doc, Review, Demo Script.

---

## 6. Resources & Deliverables
- **Repo**: MiniZalo_Backend
- **Artifacts**: `PROJECT_REPORT.md` (Design & Plan), `docker-compose.yml` (Runtime), `GitHub Issues` (Tracking).
