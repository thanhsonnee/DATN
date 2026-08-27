# PROJECT SCOPE — Hệ thống Quản lý Phòng Gym tích hợp Chatbot Tư vấn

> Soạn theo cấu trúc **Phiếu giao nhiệm vụ ĐATN (Mẫu ĐATN 02)**, tham chiếu phiếu đã được cô Vũ Thị Hương Giang duyệt.
> **Phạm vi đã chốt: quản lý MỘT phòng gym** (không đa chi nhánh, không multi-tenant).
> Mục ⚠️ cuối tài liệu liệt kê những chỗ tôi phải suy đoán — **cần bạn xác nhận trước khi nộp**.

---

# ⚡ RÀ SOÁT CÔNG NGHỆ TRƯỚC KHI CODE

> Kết quả rà lại mục 3.1 và 3.2. Chỉ liệt kê **những chỗ cần sửa**, phần không nhắc tới là giữ nguyên.

## A. Cần cắt bớt — quá nhiều so với sức một người

| # | Mục 3.2 hiện viết | Sửa thành | Lý do |
|---|---|---|---|
| 1 | **12 công cụ kiểm thử** (JUnit, jqwik, Testcontainers, REST Assured, Playwright, Maestro, k6, ZAP, Dependency-Check, gitleaks, RAGAS, ArchUnit) | **Giữ 6:** JUnit · **jqwik** · Testcontainers · REST Assured · Playwright · script đánh giá RAG.<br>**Bỏ:** Maestro, ZAP, Dependency-Check, gitleaks, ArchUnit. k6 chỉ chạy 1 kịch bản đơn giản | Một người không dựng nổi 12 bộ kiểm thử. `jqwik` phải giữ vì là cách chứng minh bất biến sổ cái — đóng góp học thuật |
| 2 | Dịch **~870 bài tập** sang tiếng Việt rồi **rà soát thủ công** | Chỉ dịch và rà soát **100–150 bài phổ biến nhất** | 870 bài rà tay là vài chục giờ. 150 bài đã phủ hết nhu cầu giáo án |
| 3 | Nguồn **bảng thành phần thực phẩm Việt Nam** *(⚠️10)* | **Bỏ.** Chatbot chỉ tư vấn nguyên tắc chung (thặng dư/thâm hụt calo, nhu cầu protein), **không tra cứu món ăn cụ thể** | Nguồn chưa xác minh được giấy phép, và tư vấn món ăn cụ thể là phần rủi ro pháp lý nhất |
| 4 | **Reranking** *(⚠️11)* | **Bỏ** | Đã so sánh 3 phương pháp truy xuất là đủ đóng góp thực nghiệm |
| 5 | **WebSocket + STOMP** | **Bỏ** nếu chọn Hướng A cho check-in (lễ tân quét mã trên máy hội viên) | Hướng A trả kết quả thẳng trong response HTTP. WebSocket chỉ cần cho Hướng B |

## B. Cần chốt trước khi code

| # | Điểm treo | Đề xuất chốt |
|---|---|---|
| 6 | **Mô hình LLM đối chiếu** *(⚠️8)* | So sánh **Claude Haiku 4.5** với một **mô hình mở chạy cục bộ qua Ollama** (Qwen2.5 hoặc Llama 3.1). Miễn phí, và so sánh mô hình đóng với mô hình mở có giá trị học thuật hơn so sánh hai mô hình cùng nhà |
| 7 | **Neo4j** *(⚠️3)* | **Giữ** — nhưng chỉ nạp tri thức tĩnh về bài tập. Nếu đến giai đoạn cuối không kịp, phương án lùi là cột `exercises.contraindications` JSONB đã có sẵn |
| 8 | **Mô hình embedding tiếng Việt** *(⚠️9)* | Chốt sớm để không phải nạp lại kho tri thức nhiều lần — mỗi lần đổi mô hình là phải sinh lại toàn bộ vector |

## C. Cần lưu ý về hạ tầng

**Một VPS 4 vCPU / 8GB RAM phải chạy:** PostgreSQL + Redis + MinIO + Neo4j + Spring Boot + FastAPI = **6 tiến trình**. Neo4j một mình đã muốn 2GB. Cần giới hạn bộ nhớ trong `docker-compose.yml` cho từng service, và cân nhắc chỉ bật Neo4j khi demo.

**Sửa nhỏ:** `expo-barcode-scanner` đã bị gộp vào `expo-camera` từ SDK 51 — bỏ khỏi danh sách phụ thuộc.

## D. Rủi ro lớn nhất

**Bốn codebase cho một người:** backend + AI service + web + mobile. Thứ tự làm bắt buộc:

```
1. Backend (nghiệp vụ cốt lõi + 3 đóng góp học thuật)   ← không có thì không có gì cả
2. Web (màn hình quầy, kế toán)                          ← đủ để demo toàn bộ nghiệp vụ
3. AI Service (chatbot)                                   ← đóng góp học thuật thứ 3
4. Mobile                                                 ← làm sau cùng, cắt được nếu thiếu thời gian
```

---

# 📁 CODE BASE DỰ KIẾN

```
DATN/
├── backend/            Spring Boot 3 — modular monolith (Java 21)
├── ai-service/         Python FastAPI — chatbot, RAG, Knowledge Graph
├── web/                React + Vite + TypeScript
├── mobile/             React Native + Expo
├── shared/             openapi.yaml + TypeScript types sinh tự động
├── db/                 tài liệu CSDL (đã có)
├── docs/               tài liệu đồ án (đã có)
└── docker-compose.yml
```

## 1. `backend/` — chia module theo miền nghiệp vụ

```
backend/src/main/java/com/gym/
├── common/          config · security (JWT, RBAC) · exception · audit
├── identity/        persons · users · employees · members · auth
├── membership/      memberships · registrations · registration_freezes
├── checkin/         check_ins
├── training/        pt_sessions · session_credit_ledger ⭐ · classes
├── billing/         invoices · payments · cash_shifts
├── finance/         revenue_schedules ⭐ · expenses · payroll
├── crm/             leads
├── fitness/         exercises · workout_plans · body_metrics
├── feedback/        feedbacks · equipment
└── platform/        system_settings · file storage (MinIO) · scheduler
```

Mỗi module có cùng cấu trúc bên trong: `api/` (controller + DTO) · `domain/` (entity + enum) · `repository/` · `service/`.

> **Vì sao modular monolith:** module gọi nhau qua interface `service`, không gọi thẳng repository của module khác. Nhờ vậy các bất biến tài chính nằm gọn trong một transaction, mà sau này vẫn tách được thành dịch vụ riêng.

## 2. `ai-service/` — **RAG và Knowledge Graph nằm ở đây, không nằm trong backend**

```
ai-service/app/
├── main.py
├── router/          Phân loại ý định: số liệu → tool, tri thức → RAG, an toàn → graph
├── tools/           Tool Calling — gọi NGƯỢC về backend API, không đụng CSDL
├── rag/             ⭐ KNOWLEDGE BASE
│   ├── ingest.py       nạp tài liệu
│   ├── chunker.py      cắt đoạn
│   ├── embedder.py     sinh vector
│   └── retriever.py    BM25 · dense · hybrid
├── graph/           ⭐ KNOWLEDGE GRAPH
│   ├── schema.py       định nghĩa đỉnh và cạnh
│   ├── sync.py         đồng bộ từ PostgreSQL sang Neo4j
│   └── query.py        truy vấn Cypher (chống chỉ định, thiết bị, lộ trình)
├── guardrail/       luật an toàn sức khỏe
├── llm/             client · prompt · caching · ghi log chi phí
└── eval/            bộ câu hỏi chuẩn + tính Recall@k, MRR
```

**Ba nguyên tắc quan trọng:**
1. **Vector lưu trong PostgreSQL** qua extension `pgvector` — không dựng thêm vector database riêng
2. **Neo4j chỉ là bản chiếu**, đồng bộ một chiều từ PostgreSQL. PostgreSQL vẫn là nguồn sự thật duy nhất
3. **AI service không truy cập thẳng CSDL** — mọi dữ liệu hội viên lấy qua API nội bộ của backend, để phân quyền chỉ phải viết một lần

## 3. `web/` và `mobile/`

```
web/src/                            mobile/
├── api/      client + types sinh   ├── app/           expo-router
├── features/                       │   ├── (auth)/
│   ├── admin/                      │   ├── (member)/  gói · QR · đặt lịch · giáo án · chatbot
│   ├── sale/                       │   └── (trainer)/ lịch dạy · xác nhận buổi · lương
│   ├── reception/                  ├── api/
│   └── accountant/                 ├── components/
├── components/ui/  shadcn          ├── hooks/
├── hooks/ lib/ stores/             └── lib/           qr.ts (HMAC-TOTP offline)
└── routes/
```

## 4. `shared/` — nối backend với hai frontend

Backend sinh `openapi.yaml` (springdoc) → script sinh TypeScript types → **web và mobile dùng chung**. Backend đổi DTO thì cả hai frontend báo lỗi biên dịch ngay tại chỗ sai, không phải chờ chạy mới phát hiện.

---

## 1. Tên đề tài *(cần chốt — xem mục ⚠️1)*

**Phương án đề xuất:**
> *Xây dựng hệ thống quản lý phòng gym tích hợp chatbot tư vấn gói tập và chế độ luyện tập dựa trên RAG và Knowledge Graph*

## 2. Lĩnh vực đề tài *(cần chốt — xem mục ⚠️2)*

- Lựa chọn 1: **Phần mềm doanh nghiệp**
- Lựa chọn 2: **Trí tuệ nhân tạo ứng dụng**

---

# 3. MỤC TIÊU CỦA ĐATN

## 3.1. Kiến thức sinh viên thu thập được

### 3.1.1. Kiến thức nghiệp vụ

**Hiện trạng vận hành phòng gym tại Việt Nam**
- Mô hình kinh doanh phòng gym tầm trung: nguồn thu chỉ đến từ hội viên (bán gói tập, bán buổi PT, vé lẻ), cơ cấu chi phí (thuê mặt bằng, lương, điện nước, khấu hao thiết bị, marketing).
- Thực trạng quản lý thủ công bằng Excel + Zalo và các điểm yếu: thất thoát doanh thu, sai lệch tính công huấn luyện viên, không có bức tranh lãi/lỗ theo kỳ.

**Quy trình nghiệp vụ theo 6 vai trò**
- **Admin (chủ phòng gym):** quản lý gói tập, nhân sự, chính sách giá và chiết khấu; giám sát doanh thu, đánh giá của hội viên về PT và cơ sở vật chất.
- **Member (hội viên):** đăng ký/gia hạn/bảo lưu gói, check-in, đặt lịch với PT, theo dõi giáo án và chỉ số cơ thể.
- **Personal Trainer:** quản lý lịch dạy, xác nhận buổi tập đã dạy để được tính công, soạn giáo án, theo dõi tiến độ học viên.
- **Sale:** quản lý khách tiềm năng (lead), pipeline bán hàng, tạo hợp đồng theo bảng giá và chương trình khuyến mãi công khai, danh sách hội viên sắp hết hạn.
- **Receptionist (lễ tân):** xác minh danh tính khi check-in, thu tiền nhiều hình thức, **mở/đóng ca và đối soát tiền mặt cuối ca**.
- **Accountant (kế toán):** ghi nhận doanh thu, quản lý chi phí, chạy bảng lương, lập báo cáo tài chính.

**Vòng đời hợp đồng gói tập**
- Máy trạng thái đầy đủ: soạn thảo → chờ thanh toán → đang hiệu lực → bảo lưu → hoàn thành / hủy / hoàn tiền.
- Quy tắc **bảo lưu** (điều kiện, số ngày tối đa, ảnh hưởng tới ngày hết hạn) và **chuyển nhượng gói**.
- **Snapshot điều khoản thương mại** tại thời điểm ký hợp đồng: vì sao thay đổi giá gói không được làm thay đổi giá trị hợp đồng đã ký.

**Nghiệp vụ tài chính — kế toán phòng gym**
- Phân biệt **dòng tiền thực thu (cash flow)** và **doanh thu ghi nhận (revenue recognition)**: bán gói 12 tháng thu tiền một lần nhưng doanh thu phải phân bổ theo kỳ phục vụ.
- Khái niệm **doanh thu chưa thực hiện (deferred revenue)** — nghĩa vụ dịch vụ còn nợ hội viên.
- Hai phương pháp phân bổ: **theo thời gian** (gói theo tháng) và **theo lượng tiêu dùng** (gói theo buổi PT).
- Phân bổ chi phí có kỳ và khấu hao thiết bị theo phương pháp đường thẳng.
- Cấu trúc **lương huấn luyện viên nhiều tầng**: lương cứng + tiền công theo từng loại buổi tập + hoa hồng bán gói theo bậc + thưởng KPI − khấu trừ.

**Kiểm soát thất thoát và gian lận**
- Các hình thức thất thoát phổ biến: dùng chung thẻ tập, hội viên hết hạn vẫn vào tập, huấn luyện viên khai khống buổi tập.
- Nguyên tắc **sổ cái chỉ ghi thêm (append-only ledger)** áp dụng cho quản lý số buổi tập, và các bất biến dữ liệu kiểm chứng được.
- Cơ chế **xác nhận hai chiều** giữa huấn luyện viên và hội viên khi kết thúc buổi tập.
- Quy trình đối soát tiền mặt theo ca làm việc của lễ tân.

**Kiến thức lĩnh vực thể hình — phục vụ chatbot tư vấn**
- Các chỉ số cơ thể: BMI (theo **ngưỡng WHO Asia-Pacific**, khác ngưỡng quốc tế), BMR (công thức Mifflin-St Jeor), TDEE, tỷ lệ eo/hông.
- Phân loại bài tập theo nhóm cơ chính/phụ, thiết bị sử dụng, mức độ khó.
- Nguyên tắc xây dựng chương trình tập theo mục tiêu (tăng cơ, giảm mỡ, sức bền) và theo trình độ.
- **Chống chỉ định bài tập** đối với người có chấn thương hoặc bệnh nền — cơ sở để hệ thống loại trừ bài tập không an toàn.
- Nguyên tắc dinh dưỡng cơ bản phục vụ mục tiêu tăng/giảm cân: thặng dư/thâm hụt calo, nhu cầu protein theo khối lượng cơ thể.

**Ranh giới pháp lý và đạo đức**
- **Nghị định 13/2023/NĐ-CP** về bảo vệ dữ liệu cá nhân: dữ liệu sinh trắc học thuộc nhóm nhạy cảm — yêu cầu về sự đồng ý, tối thiểu hóa dữ liệu, quyền xóa.
- Ranh giới của tư vấn sức khỏe bằng AI: **cung cấp thông tin tham khảo, không chẩn đoán, không kê chế độ điều trị**; các tình huống bắt buộc chuyển sang chuyên gia người thật.
- Tuân thủ PCI-DSS ở mức cơ bản: không lưu trữ số thẻ đầy đủ.

---

### 3.1.2. Kiến thức về LLM / RAG / Chatbot tư vấn

> Chatbot của đề tài là **chatbot tư vấn gói tập và chế độ luyện tập — dinh dưỡng**, không phải chatbot bán hàng thương mại điện tử. Đặc thù: câu trả lời phải chính xác về số liệu tài chính, an toàn về mặt sức khỏe, và cá nhân hóa theo dữ liệu thật của hội viên trong hệ thống.

**Nền tảng mô hình ngôn ngữ lớn**
- Cơ chế hoạt động của LLM, cửa sổ ngữ cảnh, cách tính token và chi phí.
- Hiện tượng **hallucination** và các kỹ thuật kiểm soát: grounding vào nguồn dữ liệu, bắt buộc dẫn nguồn, structured output, guardrails. (nên tập trung vào hiện tượng nào sẽ giúp ích cho đồ án của tôi?)
- **Prompt engineering**: system prompt, few-shot, phân tách vai trò, prompt caching để giảm chi phí.

**Retrieval-Augmented Generation (RAG)**
- Kiến trúc pipeline RAG: thu thập → làm sạch → chunking → embedding → indexing → retrieval → generation.
- Các phương pháp truy xuất và so sánh: **keyword retrieval (TF-IDF/BM25)**, **dense retrieval (vector embedding)**, **hybrid retrieval**, và **reranking**.
- Chiến lược chunking: kích thước đoạn, độ chồng lấn (overlap), giữ ngữ cảnh không bị cắt đứt giữa chừng.
- Embedding cho **tiếng Việt** và bài toán truy xuất văn bản tiếng Việt có dấu/không dấu.
- Quy trình **rebuild knowledge base** khi dữ liệu nguồn thay đổi.

**Ranh giới giữa RAG và Tool Calling — nội dung kỹ thuật trọng tâm**
- Nhận diện **hai loại câu hỏi cần hai cơ chế khác nhau**:
  - Câu hỏi về **dữ liệu có cấu trúc** (giá gói tập, số buổi còn lại, chỉ số BMI của hội viên) → phải dùng **Tool Calling / Function Calling** truy vấn trực tiếp cơ sở dữ liệu.
  - Câu hỏi về **tri thức dạng văn bản** (nguyên tắc dinh dưỡng, cách thực hiện bài tập) → dùng **RAG**.
- Lý do **không được dùng RAG cho bảng giá**: retrieval trả về đoạn văn bản chứa số, LLM có thể đọc sai, cộng sai hoặc bịa ra gói tập không tồn tại. Đây là lỗi thiết kế phổ biến khi làm chatbot trên dữ liệu số.
- **Structured Outputs** (JSON Schema): ràng buộc đầu ra của LLM, kiểm chứng mọi định danh (mã gói tập, mã bài tập) phải tồn tại thật trong cơ sở dữ liệu.

**Knowledge Graph và GraphRAG**
- Mô hình hóa tri thức lĩnh vực thể hình thành đồ thị: `Bài tập —TÁC ĐỘNG→ Nhóm cơ`, `Bài tập —YÊU CẦU→ Thiết bị`, `Bài tập —CHỐNG CHỈ ĐỊNH VỚI→ Tình trạng sức khỏe`, `Bài tập —TIẾN LÊN→ Bài tập`.
- Bài toán mà **vector search làm kém còn graph traversal làm tốt**: hội viên đau lưng dưới muốn tập chân — vector search trả về Squat/Deadlift (đúng ngữ nghĩa nhưng **nguy hiểm**), graph query loại được bài chống chỉ định.
- **GraphRAG**: kết hợp truy xuất đồ thị con và truy xuất văn bản để tạo câu trả lời có cả ràng buộc quan hệ lẫn kiến thức nền.
- Ngôn ngữ truy vấn đồ thị **Cypher** và mô hình dữ liệu property graph.

**Hội thoại đa lượt có trạng thái**
- Quản lý ngữ cảnh hội thoại nhiều lượt, ghi nhớ thông tin đã trao đổi.
- Theo dõi và cập nhật trạng thái tư vấn: **ngân sách, mục tiêu tập luyện, tình trạng chấn thương, khung giờ rảnh**.
- Xử lý các tình huống thực tế: người dùng **thay đổi ngân sách**, **đổi mục tiêu** giữa chừng, **từ chối gợi ý** và yêu cầu phương án khác.
- Cá nhân hóa dựa trên dữ liệu thật trong hệ thống (gói đang dùng, lịch sử check-in, chỉ số cơ thể) thay vì chỉ dựa vào nội dung hội thoại.

**Kiểm soát an toàn cho tư vấn sức khỏe (guardrails)**
- Luật cứng chặn tư vấn khi phát hiện tình huống rủi ro: BMI ngoài ngưỡng an toàn, mục tiêu giảm cân phi thực tế, hội viên khai báo bệnh nền.
- Cơ chế **chuyển giao sang người thật** (handoff): gợi ý đặt buổi đánh giá thể trạng với huấn luyện viên.
- Bắt buộc kèm khuyến cáo tham vấn chuyên môn y tế; không sử dụng từ ngữ mang tính chẩn đoán hay điều trị.

**Đánh giá chất lượng hệ thống RAG**
- Chỉ số đánh giá **retrieval**: Recall@k, MRR, Precision@k trên bộ câu hỏi benchmark tự xây dựng.
- Chỉ số đánh giá **chất lượng sinh câu trả lời**: faithfulness (câu trả lời có bám vào ngữ cảnh truy xuất không), answer relevancy, context precision.
- Phương pháp so sánh nhiều cấu hình: TF-IDF vs dense vs hybrid; so sánh **ít nhất 2 mô hình LLM**.
- **Quản trị chi phí LLM**: định tuyến mô hình theo độ khó tác vụ, prompt caching, batch API, ghi log token và đặt hạn mức ngân sách.

---

## 3.2. Công nghệ sinh viên thu thập được

### 3.2.1. Công nghệ xây dựng backend nghiệp vụ

**Mục tiêu:** xây dựng backend quản lý hội viên, hợp đồng, check-in, buổi tập, thanh toán, tài chính và phân quyền cho 6 vai trò; cung cấp API cho web và mobile.

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Ngôn ngữ | **Java 21 (LTS)** | Records, pattern matching, virtual threads |
| Framework | **Spring Boot 3.3+** | Khung ứng dụng, REST API |
| Web layer | Spring Web (MVC) | Xử lý HTTP request/response |
| Bảo mật | **Spring Security** | **Tự cài đặt JWT**: access token + refresh token rotation, phát hiện tái sử dụng token, RBAC 6 vai trò + phân quyền cấp bản ghi |
| Truy cập dữ liệu | Spring Data JPA + Hibernate | Tầng ORM |
| Migration | **Flyway** | Phiên bản hóa schema, nguồn sự thật của cấu trúc CSDL |
| Kiểm tra đầu vào | Spring Boot Validation | Ràng buộc dữ liệu API |
| Mapping | MapStruct + Lombok | Entity ↔ DTO, giảm boilerplate |
| Tài liệu API | springdoc-openapi | Sinh OpenAPI 3.1 + Swagger UI |
| Realtime | Spring WebSocket (STOMP) | Đẩy sự kiện check-in lên màn hình quầy lễ tân |
| Lập lịch | Spring Scheduler | Job ghi nhận doanh thu hàng đêm, tính khấu hao |

**Cơ sở dữ liệu và hạ tầng lưu trữ**

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| CSDL quan hệ | **PostgreSQL 16** | Toàn bộ dữ liệu giao dịch. Dùng `NUMERIC(14,2)` cho tiền, `EXCLUDE` constraint chống trùng lịch, partial index, window function cho báo cáo |
| Cache & khóa | **Redis 7** | Chống replay mã QR, rate limit, cache, khóa phân tán cho job đêm |
| Object storage | **MinIO** (S3-compatible) | Ảnh hồ sơ, ảnh check-in, media bài tập, chứng từ, báo cáo PDF |

---

### 3.2.2. Công nghệ xây dựng AI Service và xử lý hội thoại

**Mục tiêu:** xây dựng service xử lý hội thoại đa lượt, kết hợp Tool Calling, RAG và Knowledge Graph để sinh câu trả lời tư vấn có căn cứ và an toàn.

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Ngôn ngữ | **Python 3.11** | Hệ sinh thái AI/ML |
| Framework | **FastAPI** | API service cho tầng AI |
| ASGI server | Uvicorn | Chạy FastAPI |
| Điều phối hội thoại | Tự cài đặt (không dùng framework agent) | Router phân loại ý định → gọi Tool / RAG / Graph → tổng hợp |
| Gọi LLM | **Anthropic SDK (Python)** | Tool Calling, Structured Outputs, prompt caching |
| Mô hình chính | **Claude Haiku 4.5** | Tác vụ khối lượng lớn, chi phí thấp (1 USD/1M token input) |
| Mô hình nâng cao | **Claude Sonnet 5** | Tác vụ cần suy luận sâu (3 USD/1M token input) |
| Mô hình đối chiếu | *(cần chốt — xem ⚠️8)* | Yêu cầu so sánh ≥ 2 mô hình LLM |

**Các kỹ thuật LLM triển khai**
- **Tool Calling**: khai báo tập công cụ truy vấn dữ liệu thật (tìm gói tập theo ngân sách, tra số buổi còn lại, lấy chỉ số cơ thể, xem lịch PT).
- **Structured Outputs (JSON Schema)**: ràng buộc định dạng đầu ra; hậu kiểm mọi `exercise_id`, `membership_id` trả về phải tồn tại trong CSDL.
- **Prompt caching**: cache phần system prompt cố định, giảm ~90% chi phí cho phần được cache.
- **Guardrails**: bộ luật kiểm tra trước và sau khi sinh câu trả lời cho các tình huống rủi ro sức khỏe.
- **Cost-aware routing**: định tuyến mô hình theo độ khó; ghi log token và chi phí từng lượt gọi; hạn mức ngân sách theo người dùng và theo tháng.

---

### 3.2.3. Công nghệ xây dựng Knowledge Base và Retrieval

**Mục tiêu:** xây dựng kho tri thức về bài tập, dinh dưỡng và quy định phòng gym; hỗ trợ truy xuất phục vụ pipeline RAG và GraphRAG.

**Nguồn dữ liệu**

| Nguồn | Nội dung | Giấy phép |
|---|---|---|
| **Free Exercise DB** (`yuhonas/free-exercise-db`) | ~870 bài tập kèm ảnh minh họa, nhóm cơ, thiết bị | **Unlicense (public domain)** |
| **wger** (`wger.de`) — nguồn bổ sung | Bài tập cộng đồng | CC-BY-SA / AGPL |
| Bảng thành phần thực phẩm Việt Nam *(cần xác minh — ⚠️10)* | Dữ liệu dinh dưỡng | *(cần kiểm tra)* |
| Tài liệu nội bộ | Nội quy, chính sách, FAQ phòng gym | Tự biên soạn |

**Xử lý dữ liệu**
- Chuẩn hóa dữ liệu bài tập về schema thống nhất; **dịch tên và hướng dẫn sang tiếng Việt bằng LLM theo lô (Batch API, giảm 50% chi phí)**, sau đó **rà soát thủ công** — bài chưa được duyệt không hiển thị cho hội viên.
- **Chunking**: chia văn bản thành đoạn có kích thước và độ chồng lấn cấu hình được, giữ nguyên ngữ cảnh.
- Quy trình **rebuild knowledge base** khi dữ liệu nguồn được cập nhật.

**Embedding và Retrieval**

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Sinh embedding | `sentence-transformers` | Mô hình cụ thể **cần chốt** — xem ⚠️9 |
| Vector store | **`pgvector`** (extension của PostgreSQL) | **Không dùng Pinecone/Qdrant** — quy mô ~2.000 đoạn, tận dụng PostgreSQL sẵn có, không thêm dịch vụ phải vận hành |
| Baseline retriever | TF-IDF / BM25 (`scikit-learn` hoặc PostgreSQL FTS) | Làm đường cơ sở so sánh |
| Dense retriever | Truy vấn vector cosine similarity | |
| Hybrid retriever | Kết hợp keyword + dense, hợp nhất kết quả | |
| Reranking | *(tùy chọn — xem ⚠️11)* | |
| Đánh giá retrieval | `scikit-learn` | Recall@k, MRR, Precision@k |

**Knowledge Graph**

| Thành phần | Công nghệ | Ghi chú |
|---|---|---|
| Graph database | **Neo4j** *(cần chốt — xem ⚠️3)* | **Chỉ chứa tri thức tĩnh** (bài tập, nhóm cơ, thiết bị, chống chỉ định, thực phẩm). Toàn bộ dữ liệu giao dịch vẫn ở PostgreSQL → **không có bài toán đồng bộ hai chiều** |
| Ngôn ngữ truy vấn | Cypher | |
| Phương án thay thế | PostgreSQL + `WITH RECURSIVE` | Rẻ hơn nhưng khó trình bày như "công nghệ lõi" |

---

### 3.2.4. Công nghệ xây dựng ứng dụng Web và Mobile

> *Thay cho mục "Công nghệ tích hợp kênh tương tác" trong phiếu mẫu — đề tài này **không** tích hợp Messenger/Telegram.*

**Ứng dụng Web** — cho Admin, Sale, Lễ tân, Kế toán

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Framework | **React 18 + TypeScript** | |
| Build tool | **Vite** | Dev server nhanh |
| Server state | **TanStack Query** | Cache, revalidate, optimistic update |
| Client state | Zustand | Trạng thái xác thực và giao diện |
| UI | **Tailwind CSS + shadcn/ui** | Component copy vào repo, tùy biến tự do |
| Biểu đồ | **Recharts** | Báo cáo tài chính, biểu đồ chỉ số cơ thể |
| Bảng dữ liệu | TanStack Table | Màn hình kế toán nhiều dòng |
| Form & validate | React Hook Form + Zod | Schema dùng chung giữa web và mobile |
| Realtime | WebSocket (STOMP) | Màn hình quầy lễ tân |

**Ứng dụng Mobile** — cho Hội viên và Huấn luyện viên

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| Framework | **React Native + Expo (SDK 51+)** | Một codebase, rẽ nhánh giao diện theo vai trò |
| Lý do chọn | Chia sẻ TypeScript types và logic với web; **EAS Build cho phép build iOS không cần máy Mac** | |
| Camera / QR | `expo-camera`, `expo-barcode-scanner` | Quét mã check-in, chụp phiếu InBody |
| Lưu trữ an toàn | `expo-secure-store` | Lưu token và khóa sinh mã QR trong Keychain/Keystore |
| Sinh mã QR động | `expo-crypto` | **HMAC-TOTP hoạt động offline** — quan trọng vì sóng trong phòng tập thường yếu |
| Biểu đồ | `react-native-svg-charts` | Biểu đồ tiến trình cơ thể |

**Chia sẻ code giữa Web và Mobile**
- Sinh **TypeScript types tự động từ OpenAPI spec** của backend → backend đổi DTO thì cả web và mobile báo lỗi biên dịch ngay tại chỗ sai.

---

### 3.2.5. Công nghệ triển khai, kiểm thử và công cụ phát triển

**Triển khai**

| Thành phần | Công nghệ |
|---|---|
| Container | **Docker + Docker Compose** — orchestrate PostgreSQL, Redis, MinIO, backend Spring Boot, AI service FastAPI, Neo4j |
| Reverse proxy | **Caddy** — TLS tự động (Let's Encrypt) |
| CI/CD | **GitHub Actions** — build, test, quét bảo mật, deploy |
| Môi trường | 1 VPS (4 vCPU / 8GB RAM) |

**Kiểm thử**

| Loại | Công nghệ | Trọng tâm |
|---|---|---|
| Unit | JUnit 5 + AssertJ | Công thức phân bổ doanh thu, tính lương, BMI/BMR/TDEE |
| **Property-based** | **jqwik** | **Kiểm chứng bất biến của sổ cái buổi tập và ghi nhận doanh thu với hàng nghìn chuỗi thao tác sinh ngẫu nhiên** |
| Integration | **Testcontainers** | PostgreSQL + Redis thật trong Docker; kiểm thử transaction và xử lý đồng thời |
| API & phân quyền | REST Assured | Ma trận `endpoint × vai trò × quyền sở hữu` sinh tự động — phát hiện lỗ hổng IDOR |
| E2E Web | **Playwright** | Kịch bản nghiệp vụ đầu-cuối |
| E2E Mobile | **Maestro** | |
| Hiệu năng | **k6** | Giờ cao điểm 18h–20h |
| Bảo mật | OWASP ZAP, Dependency-Check, gitleaks | |
| **Đánh giá RAG** | **RAGAS** hoặc script tự viết | Faithfulness, answer relevancy, context precision |
| Kiến trúc | **ArchUnit** | Cưỡng chế ranh giới giữa các module |

**Công cụ phát triển**
IntelliJ IDEA (backend Java) · VS Code (frontend + AI service) · Postman (kiểm thử API) · pgAdmin / DBeaver (CSDL) · dbdiagram.io (vẽ ERD)

---

## 3.3. Kỹ năng sinh viên phát triển được

- Kỹ năng khảo sát thực tế và **giới hạn phạm vi** cho hệ thống nhiều thành phần, đảm bảo đủ độ khó nhưng khả thi trong phạm vi đồ án cử nhân.
- Kỹ năng phân tích nghiệp vụ đa vai trò và chuyển hóa thành use case, luồng nghiệp vụ và máy trạng thái.
- Kỹ năng **thiết kế cơ sở dữ liệu cho nghiệp vụ tài chính**: sổ cái append-only, snapshot điều khoản, bất biến dữ liệu kiểm chứng được.
- Kỹ năng thiết kế kiến trúc **modular monolith** với ranh giới module được cưỡng chế tự động, và tách AI service theo lý do kỹ thuật chính đáng.
- Kỹ năng tự cài đặt **xác thực và phân quyền** (JWT, refresh token rotation, RBAC + phân quyền cấp bản ghi) thay vì dùng giải pháp đóng gói sẵn.
- Kỹ năng xây dựng **pipeline dữ liệu cho knowledge base**: thu thập → làm sạch → chunking → embedding → indexing → rebuild.
- Kỹ năng xây dựng **chatbot đa lượt có trạng thái**, kết hợp Tool Calling, RAG và Knowledge Graph, có kiểm soát an toàn.
- Kỹ năng xây dựng **bộ benchmark và phân tích kết quả đánh giá** để lựa chọn phương án retrieval và mô hình LLM.
- Kỹ năng **kiểm thử dựa trên thuộc tính (property-based testing)** cho nghiệp vụ tài chính — chứng minh tính đúng đắn thay vì chỉ kiểm tra vài ví dụ.
- Kỹ năng phát triển ứng dụng **đa nền tảng** (web + mobile) chia sẻ kiểu dữ liệu.
- Kỹ năng **quản trị chi phí vận hành AI** trong sản phẩm thực tế.
- Kỹ năng đóng gói, triển khai hệ thống nhiều service bằng Docker; viết tài liệu kỹ thuật, chuẩn bị dữ liệu demo, video demo và báo cáo.

---

## 3.4. Sản phẩm kỳ vọng

### 3.4.1. Phần mềm hệ thống

Xây dựng hệ thống quản lý phòng gym tích hợp chatbot tư vấn, gồm **4 thành phần**:

1. **Backend nghiệp vụ** (Spring Boot) — quản lý hội viên, hợp đồng, check-in, buổi tập, thanh toán, tài chính, phân quyền.
2. **AI service** (FastAPI) — xử lý hội thoại, truy xuất tri thức, sinh câu trả lời tư vấn.
3. **Ứng dụng web** — cho Admin, Sale, Lễ tân, Kế toán.
4. **Ứng dụng di động** — cho Hội viên và Huấn luyện viên.

**Hệ thống phục vụ 6 vai trò với các nhóm chức năng chính:**

| Vai trò | Chức năng chính |
|---|---|
| **Admin** | Dashboard doanh thu · CRUD gói tập và nhân sự · xem đánh giá về PT và cơ sở vật chất · cấu hình chính sách (bảo lưu, chiết khấu, đơn giá công) · duyệt yêu cầu vượt hạn mức |
| **Member** | Xem/đăng ký/gia hạn/bảo lưu gói · **QR check-in động** · đặt lịch với PT · xem giáo án và đánh dấu hoàn thành · nhập và theo dõi chỉ số cơ thể · thanh toán · gửi phản hồi · **chat với trợ lý tư vấn** |
| **Personal Trainer** | Lịch dạy · duyệt yêu cầu đặt lịch · **xác nhận buổi tập hai chiều** · quản lý học viên phụ trách · soạn giáo án · **xem bảng lương chi tiết từng buổi** |
| **Sale** | Quản lý lead và pipeline · đặt lịch tập thử · tạo hợp đồng theo bảng giá và khuyến mãi công khai · danh sách hội viên sắp hết hạn · theo dõi KPI và hoa hồng |
| **Receptionist** | **Màn hình quầy realtime** (hiện ảnh hồ sơ khi quẹt thẻ) · xử lý ngoại lệ check-in · thu tiền nhiều hình thức · **mở/đóng ca và đối soát tiền mặt** · đăng ký hội viên mới |
| **Accountant** | Sổ doanh thu ghi nhận và doanh thu chưa thực hiện · quản lý chi phí · **chạy bảng lương** · đối soát dòng tiền · **báo cáo P&L, dòng tiền — có biểu đồ, xuất PDF/Excel** |

**Chatbot tư vấn cung cấp 4 chế độ hoạt động:**

| Chế độ | Cơ chế | Ví dụ câu hỏi |
|---|---|---|
| **1. Tư vấn chọn gói tập** | **Tool Calling** → truy vấn CSDL | *"Tôi có 3 triệu, nên chọn gói nào?"* |
| **2. Tư vấn tập luyện & dinh dưỡng** | **GraphRAG** (Knowledge Graph + RAG) | *"BMI tôi 19, muốn tăng lên 68kg thì tập và ăn thế nào?"* |
| **3. Hỏi đáp thông tin cá nhân** | **Tool Calling** | *"Gói của tôi còn mấy buổi PT? Hết hạn khi nào?"* |
| **4. Hỏi đáp quy định phòng gym** | **RAG** | *"Bảo lưu gói tập cần điều kiện gì?"* |

**Hệ thống hỗ trợ tối thiểu 10 kịch bản hội thoại đa lượt:**

1. Hỏi gói tập phù hợp theo ngân sách và mục tiêu
2. **Thay đổi ngân sách** trong quá trình trao đổi
3. So sánh nhiều gói tập theo nhiều tiêu chí
4. Tư vấn chế độ tập luyện theo mục tiêu tăng cơ, dựa trên BMI hiện tại
5. Tư vấn chế độ dinh dưỡng theo mục tiêu và chỉ số cơ thể
6. **Đổi mục tiêu** giữa chừng (từ giảm mỡ sang tăng cơ)
7. **Có chấn thương/bệnh nền** → loại trừ bài tập chống chỉ định
8. Từ chối gợi ý và yêu cầu phương án khác
9. Hỏi thông tin cá nhân: số buổi còn lại, ngày hết hạn, lịch tập với PT
10. Hỏi quy định phòng gym: giờ mở cửa, điều kiện bảo lưu, chính sách hoàn tiền
11. **Tình huống rủi ro sức khỏe** → từ chối tư vấn, chuyển sang huấn luyện viên

**So sánh với tối thiểu 2 mô hình LLM** *(theo yêu cầu trong phiếu mẫu — cấu hình cụ thể xem ⚠️8)*.

---

### 3.4.2. Service / API

Hệ thống cung cấp tối thiểu **8 nhóm API**:

| Nhóm API | Nội dung |
|---|---|
| **1. Xác thực & phân quyền** | Đăng nhập, làm mới token, đăng xuất, thông tin tài khoản hiện tại |
| **2. Hội viên & hợp đồng** | CRUD hội viên, gói tập, hợp đồng; bảo lưu; gia hạn |
| **3. Check-in** | Xác thực mã QR động, ghi nhận lượt vào/ra, xử lý ngoại lệ, WebSocket đẩy sự kiện lên màn hình quầy |
| **4. Buổi tập PT & sổ cái** | Xem lịch, đặt lịch, duyệt, xác nhận hai chiều, truy vấn sổ cái buổi tập |
| **5. Thanh toán** | Tạo hóa đơn, ghi nhận thanh toán (có `Idempotency-Key`), sinh mã VietQR, webhook đối soát, mở/đóng ca |
| **6. Tài chính & báo cáo** | Chi phí, chạy bảng lương, báo cáo P&L / dòng tiền / doanh thu chưa thực hiện, xuất PDF-Excel |
| **7. Hội thoại chatbot** | Tạo phiên chat, gửi tin nhắn, lấy lịch sử hội thoại, streaming câu trả lời |
| **8. Quản lý tri thức** | Thêm nguồn dữ liệu, rebuild knowledge base, kiểm tra trạng thái index |

**Yêu cầu kỹ thuật:**
- Request/response dạng JSON; API versioned (`/api/v1/...`).
- Định dạng lỗi thống nhất theo **RFC 7807 Problem Details**.
- Phân trang chuẩn; `Idempotency-Key` bắt buộc trên mọi API tạo giao dịch tiền.
- Tài liệu **OpenAPI 3.1** sinh tự động, kiểm thử được bằng Postman và Swagger UI.

---

### 3.4.3. Giao diện và tương tác

**Giao diện Web (4 nhóm màn hình theo vai trò)**

| Nhóm | Màn hình chính |
|---|---|
| **Admin** | Dashboard tổng quan có biểu đồ · quản lý gói tập và giá · quản lý nhân sự · xem đánh giá · cấu hình chính sách |
| **Sale** | Pipeline dạng kanban · danh sách lead · tạo hợp đồng · danh sách sắp hết hạn · KPI cá nhân |
| **Lễ tân** | **Màn hình quầy chế độ kiosk** — hiện ảnh hồ sơ cỡ lớn khi quẹt thẻ · thu tiền và in phiếu · quản lý ca làm việc |
| **Kế toán** | Sổ doanh thu · quản lý chi phí · chạy bảng lương với chi tiết từng dòng · báo cáo có biểu đồ |

**Ứng dụng di động (2 vai trò trong 1 codebase)**

| Vai trò | Màn hình chính |
|---|---|
| **Hội viên** | Gói của tôi · **QR check-in** · đặt lịch PT · giáo án hôm nay · chỉ số cơ thể và biểu đồ · thanh toán · phản hồi · **màn hình chat tư vấn** |
| **Huấn luyện viên** | Lịch dạy · duyệt đặt lịch · **xác nhận buổi tập** · danh sách học viên · soạn giáo án · **bảng lương của tôi** |

---

### 3.4.4. Dữ liệu

**Dữ liệu vận hành mô phỏng (golden dataset)**
- Mô phỏng **6 tháng vận hành** một phòng gym quy mô ~1.000 hội viên hoạt động.
- Bao gồm: ~1.400 hợp đồng · ~5.400 buổi tập PT · ~62.000 lượt check-in · 8 huấn luyện viên · các nghiệp vụ bảo lưu, hoàn tiền, chuyển nhượng.
- **Gài sẵn ~50 tình huống bất thường** (gian lận check-in, khai khống buổi tập, nghiệp vụ kế toán phức tạp) để kiểm chứng khả năng phát hiện của hệ thống.
- Bộ tham số sinh dữ liệu (bảng giá, cơ cấu chi phí, tỷ lệ hành vi) được xây dựng **có căn cứ và giải thích được**, không lấy tùy tiện.

**Knowledge base cho RAG**
- Tối thiểu **~2.000 đoạn văn bản**, gồm: hướng dẫn ~870 bài tập · tài liệu nguyên tắc tập luyện · tài liệu dinh dưỡng · nội quy và FAQ phòng gym.
- Mỗi đoạn lưu kèm metadata: nguồn, giấy phép, chủ đề, trạng thái đã rà soát.

**Knowledge graph**
- ~870 nút bài tập · các nút nhóm cơ, thiết bị, tình trạng sức khỏe, mục tiêu, thực phẩm.
- Các loại quan hệ: tác động lên nhóm cơ, yêu cầu thiết bị, **chống chỉ định**, tiến trình từ dễ đến khó, bài tập thay thế.

**Dataset đánh giá**
- Tối thiểu **100 câu hỏi benchmark** cho retrieval, có nhãn đoạn đúng.
- Tối thiểu **30–50 kịch bản hội thoại kiểm thử**, bao gồm các tình huống thay đổi ngân sách, đổi mục tiêu, từ chối gợi ý, có chấn thương, và **tình huống rủi ro sức khỏe cần từ chối tư vấn**.
- Bộ dữ liệu đối chiếu (oracle) cho nghiệp vụ tài chính: bảng tính tay do người thực hiện, dùng để so sánh từng ô với kết quả hệ thống.

---

## 3.5. Vấn đề thực tiễn đồ án giải quyết

Đồ án tiếp cận bài toán từ góc nhìn của **phòng gym tầm trung tại Việt Nam** — quy mô vài trăm đến khoảng một nghìn hội viên, có đội ngũ huấn luyện viên và nhân viên kinh doanh, nhưng **không có bộ phận công nghệ thông tin riêng**.

Đặc thù của mô hình này: doanh thu đến hoàn toàn từ hội viên; chi phí cố định lớn (mặt bằng, lương); biên lợi nhuận mỏng; và tỷ lệ hội viên không gia hạn cao. Vì vậy hai yếu tố quyết định sự sống còn là **kiểm soát thất thoát** và **nắm được bức tranh lãi/lỗ thật theo kỳ** — cả hai đều đang bị quản lý bằng công cụ thủ công.

### Khảo sát các giải pháp hiện tại *(cần bổ sung khảo sát thực tế — xem ⚠️12)*

| Nhóm giải pháp | Hạn chế |
|---|---|
| **Excel + Zalo + sổ giấy** | Không kiểm soát được thất thoát; tính lương thủ công dễ sai; không có báo cáo tài chính đúng nghĩa; dữ liệu phân mảnh |
| **Phần mềm quản lý phòng gym trong nước** | Mạnh về quản lý hội viên và bán gói, nhưng **ghi nhận toàn bộ tiền thu vào kỳ bán hàng** thay vì phân bổ theo kỳ phục vụ → báo cáo không phản ánh đúng lợi nhuận |
| **Phần mềm quốc tế** (Mindbody, Glofox, GymMaster…) | Chức năng đầy đủ nhưng chi phí thuê bao cao, không hỗ trợ phương thức thanh toán phổ biến tại Việt Nam (VietQR, đối soát chuyển khoản), giao diện và quy trình không sát thói quen vận hành trong nước |
| **Hệ thống kiểm soát cửa ra vào độc lập** | Chỉ ghi nhận lượt quẹt thẻ, **không liên thông với hợp đồng và tài chính** → không chặn được hội viên hết hạn, không phát hiện dùng chung thẻ |
| **Chatbot FAQ / chatbot kịch bản cố định** | Trả lời theo luồng cứng; không truy cập được dữ liệu cá nhân của hội viên; không tư vấn được theo ngân sách và thể trạng cụ thể |

### Ba nhóm vấn đề cần giải quyết

**Vấn đề 1 — Ghi nhận vận hành không đáng tin cậy và phân mảnh**

Ba sự kiện quan trọng nhất của phòng gym — *ai đã vào tập*, *buổi tập nào đã diễn ra*, *huấn luyện viên nào đã dạy buổi nào* — đang được ghi nhận rời rạc ở ba nơi khác nhau. Hệ quả: thất thoát doanh thu do dùng chung thẻ và hội viên hết hạn vẫn vào tập; sai lệch tính công dẫn tới tranh chấp lương cuối tháng; không có dữ liệu hành vi để can thiệp giữ chân hội viên.

**Vấn đề 2 — Đứt gãy chuỗi *Bán hàng → Tiền → Sổ sách*, không quản trị được lợi nhuận**

Chính sách giá, hợp đồng bán ra, dòng tiền thực thu và sổ sách kế toán nằm ở bốn nơi tách biệt. Hệ quả: nhân viên kinh doanh tự ý giảm giá mà không ai biết biên lợi nhuận thực; **không phân biệt "tiền thu được" với "doanh thu đã thực hiện"** nên chủ phòng gym thấy tháng bán gói dài thì lãi lớn, tháng sau lỗ, mà không hiểu vì sao; lương huấn luyện viên tính bằng Excel nên dễ sai và mất niềm tin.

**Vấn đề 3 — Hội viên thiếu thông tin và tư vấn để duy trì động lực**

Hội viên tương tác với phòng gym qua nhiều kênh không kết nối, không có nơi nào thấy được bức tranh tổng thể về hành trình tập luyện của mình. Khi cần tư vấn chọn gói phù hợp túi tiền, hoặc cần biết với thể trạng hiện tại thì nên tập và ăn thế nào, hội viên phải chờ gặp huấn luyện viên hoặc nhân viên tư vấn. Kết quả là hội viên không thấy tiến bộ, mất động lực và không gia hạn.

### Đóng góp chính của đồ án

| # | Đóng góp | Kết quả kỳ vọng |
|---|---|---|
| **1** | **Mô hình sổ cái tín dụng buổi tập** — áp dụng nguyên lý sổ cái chỉ ghi thêm của kế toán vào quản lý số buổi tập, kèm cơ chế xác nhận hai chiều và bộ bất biến kiểm chứng được bằng property-based testing | Sai lệch tính công về 0; mọi thay đổi đều truy vết được |
| **2** | **Kiến trúc check-in đa lớp có xác minh danh tính** — QR động HMAC-TOTP + thẻ RFID + đối chiếu ảnh hồ sơ + chống check-in trùng, tuân thủ Nghị định 13/2023/NĐ-CP | Phát hiện phần lớn lượt vào không hợp lệ; rút ngắn thời gian check-in |
| **3** | **Engine tài chính cho phòng gym** — tách bạch dòng tiền và doanh thu ghi nhận, phân bổ theo thời gian và theo lượng tiêu dùng, tự động hóa lương và hoa hồng nhiều tầng | Rút ngắn thời gian chốt sổ; bảng lương không còn sai sót; chủ phòng gym lần đầu thấy lãi/lỗ thật theo tháng |
| **4** | **Chatbot tư vấn kết hợp Tool Calling + RAG + Knowledge Graph** — phân tách rõ dữ liệu có cấu trúc (truy vấn CSDL) và tri thức văn bản (RAG), dùng đồ thị tri thức để loại trừ bài tập chống chỉ định | Câu trả lời chính xác về số liệu, an toàn về sức khỏe, cá nhân hóa theo dữ liệu thật |
| **5** | **Tầng AI có kiểm soát chi phí và an toàn** — định tuyến mô hình, prompt caching, hạn mức ngân sách, guardrails cho tư vấn sức khỏe | Chứng minh AI khả thi về kinh tế trong sản phẩm thật, kèm số liệu chi phí cụ thể |

---
---

# PHỤ LỤC — LUỒNG NGHIỆP VỤ CHI TIẾT

> Tổng hợp toàn bộ luồng nghiệp vụ chính, đối chiếu trực tiếp với `db/31_bang.md`. Mỗi luồng trình bày dưới dạng chuỗi bước/trạng thái; chỉ ghi chú khi có quy tắc rẽ nhánh không hiển nhiên.

## Cập nhật thay đổi ngày 26/8/2026:

Đối chiếu trực tiếp với code backend + web hiện tại (không suy đoán). Chuỗi trạng thái PENDING_PAYMENT → ACTIVE của A2/A3 và các luồng B/C vẫn đúng như tài liệu — không có state machine nào bị đổi. Có 3 điểm **tài liệu mô tả nhưng thực tế lệch**, phát hiện khi rà soát hôm nay, và 3 **thay đổi cơ chế thật sự** đã làm.

**1. A2 chỉ khả dụng Kênh 2, Kênh 1 có backend nhưng chưa có UI**
Backend `RegistrationService.create()` có `resolveBuyer(actorUserId, personId)` — nhân viên (RECEPTIONIST/SALE) truyền `personId` là tạo được hợp đồng hộ khách vãng lai đúng như Kênh 1 mô tả. Nhưng grep toàn bộ `web/src` thì `personId` **không xuất hiện ở đâu cả** — không có màn hình nào cho lễ tân/sale chọn khách rồi tạo hợp đồng hộ. Trên thực tế chỉ Kênh 2 (khách tự mua, tự đăng nhập) dùng được.

**2. A2 — `invoices` KHÔNG được tạo cùng lúc với `registrations`**
Tài liệu mô tả bước "CHUNG" tạo `invoices` ngay khi khách chốt mua. Thực tế `RegistrationService.create()` chỉ tạo `registrations(status=PENDING_PAYMENT)`, không đụng tới `invoices`. Hóa đơn chỉ được tạo sau, khi lễ tân chủ động bấm "Xác nhận gói tập" ở màn hình quầy (xem mục 4). Nghĩa là có một khoảng thời gian hợp đồng tồn tại ở trạng thái chờ mà **chưa hề có hóa đơn nào** — khác với tài liệu.

**3. Luồng B (Check-in) — `method` luôn bị gán cứng `QR_DYNAMIC`, không có quét QR thật**
`CheckInService.quetVao()` set `c.setMethod(CheckInMethod.QR_DYNAMIC)` không điều kiện, nhưng cơ chế thật ở "Màn hình quầy" là lễ tân gõ tên/số điện thoại tìm hội viên rồi bấm xác nhận — hoàn toàn không có bước sinh/quét mã QR. Cột `check_ins.method` vì vậy phản ánh sai 100% cách check-in thực tế đang chạy. Đây là nợ kỹ thuật cần sửa tên method (ví dụ thêm giá trị `STAFF_LOOKUP`) trước khi dùng cột này cho báo cáo/thống kê.

**4. Gộp "xuất hóa đơn" + "thu tiền" thành một thao tác "Xác nhận gói tập"**
Thêm `POST /billing/registrations/{id}/confirm` — lễ tân chọn hình thức đã nhận tiền (tiền mặt/chuyển khoản/...) rồi bấm 1 lần, backend tự chạy `xuatHoaDon()` rồi `thuTien()` đủ số tiền trong cùng một giao dịch, hợp đồng tự kích hoạt (đúng chuỗi A3). Đây là lớp tiện ích UX mới, không thay đổi state machine đã tài liệu hóa — 2 API `xuatHoaDon`/`thuTien` gốc vẫn còn, dùng cho luồng thu công nợ từng phần riêng.

**5. Bỏ nút kích hoạt thủ công bằng tay nhập ID**
Trước đây màn "Quản lý hợp đồng" có nút gọi thẳng `POST /registrations/{id}/activate` bằng ID gõ tay, bỏ qua hoàn toàn bước hóa đơn/thanh toán. Đã gỡ khỏi UI (endpoint backend vẫn còn nhưng thành dead code, đánh dấu `[CHƯA DÙNG]` trên Swagger). Từ giờ kích hoạt **chỉ** xảy ra qua đúng luồng A3 (thu đủ tiền), siết chặt hơn so với trước, không còn đường tắt.

**6. Thêm UI tìm-theo-tên, thay cho gõ tay ID, ở 3 chỗ**
Tìm hội viên bằng tên/SĐT (Luồng B, check-in) · chọn huấn luyện viên bằng tên (Luồng C1, đặt lịch) · duyệt bảo lưu từ danh sách thay vì gõ ID hợp đồng (Luồng A4). Chỉ đổi cách nhập liệu, không đổi bất kỳ quy tắc rẽ nhánh hay trạng thái nào đã mô tả ở các mục tương ứng bên dưới.

## A. Vòng đời hội viên & hợp đồng

### A1 — Đăng ký tài khoản (tự phục vụ - role member)
```
Tải app, nhập thông tin/ hoặc đăng ký tài khoản trên web → tạo persons + users (primary_role=MEMBER, status=ACTIVE)
→ CHƯA có dòng trong members (chỉ tạo khi mua gói đầu tiên)
→ Vẫn dùng được: xem bảng giá, chat với chatbot
→ Chưa dùng được: check-in, đặt lịch PT, giáo án, chỉ số cơ thể
```

### A2 — Chốt mua gói (2 kênh, chung một trình tự)
```
KÊNH 1 — Khách đến trực tiếp phòng gym
  Khách xem bảng giá + chương trình khuyến mãi đang chạy → quyết định mua
  → Lễ tân/Sale tạo hợp đồng → thanh toán NGAY tại quầy, HOẶC chuyển khoản sau

KÊNH 2 — Khách mua trên web/app
  Khách tự chọn gói + khuyến mãi → bấm mua → chuyển sang cổng thanh toán

CHUNG — khi khách chốt mua:
  → TẠO members nếu chưa có (member_code, join_date)        ← lần đầu tiên duy nhất
  → registrations(member_id, status=PENDING_PAYMENT)
     discount_amount / discount_reason = theo chương trình khuyến mãi CÔNG KHAI khách đã chọn
     (khuyến mãi là chính sách có sẵn của phòng gym — Sale không thương lượng giá riêng)
  → tạo invoices trỏ tới hợp đồng
  → Khách trả đủ tiền → xem luồng A3
  → Khách bỏ ngang không trả → job đêm chuyển status=CANCELLED sau 48h
```
> Không có bước "báo giá" riêng: giá và khuyến mãi đều công khai nên khách đã biết trước con số cuối cùng. Mỗi dòng `registrations` là **một hợp đồng đã chốt**, luôn có `member_id`.

### A3 — Kích hoạt hợp đồng
```
payments đủ SUCCEEDED cho invoice
  → registrations: status=ACTIVE, activated_at=now()
  → session_credit_ledger: GRANT +sessions_total (nếu có buổi PT)
  → revenue_schedules: khởi tạo kế hoạch ghi nhận doanh thu
  → leads (nếu khách đến từ phễu bán hàng): stage=WON
```

### A4 — Bảo lưu gói (freeze)
```
Hội viên/lễ tân gửi yêu cầu → registration_freezes(status=PENDING)
  → Đủ điều kiện tự động (gói ≥90 ngày · tổng ngày bảo lưu/năm ≤30 · số lần <2 · báo trước ≥3 ngày) → PENDING tự chuyển APPROVED
  → Không đủ điều kiện → chờ Admin duyệt thủ công → APPROVED / REJECTED
  → Tới from_date → ACTIVE: registrations.status=FROZEN, end_date += days, tạm dừng ghi nhận doanh thu, check-in bị từ chối (DENIED_FROZEN)
  → Hết to_date (hoặc kết thúc sớm qua ended_early_at) → ENDED → registrations.status=ACTIVE trở lại
```

### A5 — Hết hạn / gia hạn
```
end_date trôi qua mà còn buổi chưa dùng
  → registrations.status=COMPLETED
  → session_credit_ledger: EXPIRE −n (số buổi còn dư, không được dùng nữa)
  → members.status vẫn ACTIVE nhưng không còn hợp đồng ACTIVE nào → coi như hết gói
  → users.status KHÔNG đổi — hội viên vẫn đăng nhập được để gia hạn online (tạo registration mới, quay lại A2/A3)
```

### A6 — Hủy giữa chừng & hoàn tiền
```
Hội viên yêu cầu hủy khi registrations.status=ACTIVE
  → status=CANCELLED (close_reason ghi lý do)
  → Nếu memberships.is_refundable=TRUE → payments mới với payment_type=REFUND, amount ÂM,
     refund_of_payment_id trỏ khoản thu gốc, refund_penalty (phí hủy), approved_by
  → registrations.status=REFUNDED (trạng thái cuối)
```

---

## B. Check-in

```
Hội viên tới cổng → chọn 1 trong các cách (check_ins.method)
  QR_DYNAMIC  : quét mã đổi mỗi 30s — Hướng A (lễ tân quét máy hội viên) hoặc Hướng B (hội viên quét QR gym, WebSocket đẩy kết quả)
  (BỎ)RFID        : chạm thẻ (UID → persons.card_uid)                    [⏳ chờ quyết định giữ/bỏ]
  MANUAL      : lễ tân nhập tay — bắt buộc manual_reason
  DAY_PASS    : khách vãng lai mua vé lẻ, không cần registration ACTIVE

→ Hệ thống kiểm tra: hợp đồng ACTIVE? còn hạn? không FROZEN? không nợ tiền?
   ALLOWED / ALLOWED_OVERRIDE (lễ tân bỏ qua cảnh báo)
   DENIED_EXPIRED / DENIED_FROZEN / DENIED_UNPAID / DENIED_NOT_FOUND / DENIED_SUSPECT

→ Bất thường (kiểm tra song song):
   check-in lại trong 30 phút                → incident_type=ANTI_PASSBACK
   thẻ đang "ở trong" phòng tập lại check-in mới → SUSPECTED_SHARING
   dùng lại QR đã quét                        → REPLAY_ATTEMPT
```

---

## C. Buổi tập PT

### C1 — Đặt lịch & duyệt
```
Hội viên (hoặc PT hộ) tạo pt_sessions(status=PENDING_TRAINER, requested_by)
  → PT duyệt → status=SCHEDULED
  → PT từ chối → status=REJECTED (reject_reason)
```

### C2 — Diễn ra buổi tập & xác nhận hai chiều
```
Tới giờ → status=IN_PROGRESS → PT bấm "Kết thúc" → sinh QR xác nhận
  → trainer_confirmed_at ghi nhận
  → Hội viên quét QR xác nhận → member_confirmed_at ghi nhận
  → Cả hai có giá trị → status=COMPLETED
  → Hội viên không xác nhận trong 24h → hệ thống tự duyệt, auto_confirmed=TRUE (cờ để kiểm toán)

Khi COMPLETED:
  session_type=PAID_PT        → session_credit_ledger: CONSUME −1
  session_type=COMPLIMENTARY/TRIAL/ORIENTATION/MAKEUP/ASSESSMENT → không trừ buổi
  MỌI session_type            → payroll_items: dòng SESSION_FEE (kể cả buổi 0đ với hội viên)
  revenue_recognition_entries: ghi nhận doanh thu nếu recognition_method=PER_SESSION
```

### C3 — Hủy buổi
```
Hủy trước giờ hẹn ≥4h (booking.late_cancel_hours)
  → status=CANCELLED, is_late_cancel=FALSE
  → PAID_PT: session_credit_ledger REFUND +1 (trả lại buổi)

Hủy <4h (hoặc không đến)
  → is_late_cancel=TRUE / status=NO_SHOW_MEMBER
  → KHÔNG trả buổi, KHÔNG tính công PT
  → cancelled_by=TRAINER: PT vẫn bị PENALTY ở payroll_items nếu lỗi thuộc PT
```

---

## D. Lớp học nhóm

```
class_sessions(status=SCHEDULED) do PT dạy, capacity cố định
  Hội viên đặt chỗ → class_bookings(status=BOOKED) — BẮT BUỘC có registration hợp lệ
  Lớp đầy → status=WAITLISTED
  Buổi diễn ra → điểm danh: ATTENDED / NO_SHOW
  Hội viên hủy trước giờ → status=CANCELLED (nhường chỗ cho WAITLISTED)
```

---

## E. Thanh toán & tài chính

### E1 — Thu tiền & đối soát
```
invoices(status=UNPAID) → thu qua 1 trong 6 method (payments.method)
  CASH                    → bắt buộc gắn cash_shift_id, đối soát cuối ca
  BANK_TRANSFER/VIETQR    → webhook tự động, khớp transfer_content
  E_WALLET/GATEWAY        → IPN webhook
  CARD_POS                → nhập tay mã, đối soát sao kê cuối ngày
→ payments.status: INITIATED → PENDING → SUCCEEDED
→ SUM(paid_amount) = total_amount → invoices.status=PAID
```

### E2 — Ca làm việc lễ tân
```
Đầu ca → cash_shifts(status=OPEN, opening_balance)
Trong ca → mọi payments CASH SUCCEEDED bắt buộc gắn cash_shift_id
Cuối ca → lễ tân đếm counted_cash → hệ thống so expected_cash (đầu ca + Σ thu tiền mặt)
  Khớp   → status=CLOSED
  Lệch   → status=DISCREPANCY (difference_reason bắt buộc) → kế toán verified_by
```

### E3 — Ghi nhận doanh thu định kỳ (job đêm)
```
Với mỗi registration ACTIVE:
  STRAIGHT_LINE (gói TIME_BASED) → mỗi ngày: revenue_recognition_entries += total_amount/duration_days (bỏ qua ngày FROZEN)
  PER_SESSION (gói SESSION_BASED) → mỗi khi pt_sessions COMPLETED: += total_amount/session_count
  IMMEDIATE (DAY_PASS)            → ghi nhận toàn bộ ngay lúc bán
→ revenue_schedules.recognized_amount / deferred_amount cập nhật theo
→ Bất biến: recognized_amount + deferred_amount = total_amount (luôn đúng)
```

### E4 — Chạy lương hàng tháng
```
payroll_runs(status=DRAFT) → hệ thống tự tính từ pt_sessions + leads (hoa hồng) + KPI (feedbacks.rating)
  → sinh payroll_items (BASE_SALARY, SESSION_FEE, SALES_COMMISSION, KPI_BONUS, ALLOWANCE, DEDUCTION, PENALTY)
  → status=REVIEW: PT/Sale xem chi tiết lương của mình trong 3 ngày, phản hồi nếu sai
  → Kế toán chốt → status=APPROVED → chuyển tiền → status=PAID
```

### E5 — Ghi nhận chi phí
```
Chi phí phát sinh (thuê mặt bằng, lương, điện nước, ...)
  → expenses(status=DRAFT) → kế toán duyệt → status=APPROVED
  → allocation_method=STRAIGHT_LINE: phân bổ đều theo period_start→period_end (không dồn hết vào 1 tháng)
  → Khấu hao thiết bị: job đêm tự sinh dòng expenses từ equipment.purchase_price/useful_life_months
```

---

## F. Bán hàng & CRM

```
Tiếp nhận lead (source: FB_ADS/HOTLINE/WALK_IN/REFERRAL/GOOGLE/EVENT/APP_SELF)
  → TẠO persons ngay (lead là một con người thật) → leads.person_id NOT NULL
    persons.phone UNIQUE → người gọi hotline hôm nay, tự tải app hôm sau CHỈ CÓ 1 DÒNG
  → Ghi gói khách quan tâm: leads.interested_membership_id
  → Sale phụ trách (assigned_to) chăm sóc: last_contact_at/note, next_follow_up
  → Phễu: NEW → CONTACTED → TRIAL_BOOKED → TRIAL_DONE → WON (→ luồng A2/A3)
                                                      ↘ LOST (+ lost_reason)
→ Báo cáo cuối tháng: tỷ lệ LOST theo lost_reason → điều chỉnh giá hoặc quy trình chăm sóc
   Gói nào được quan tâm nhiều nhưng tỷ lệ chốt thấp → xem lại định giá gói đó
```

---

## G. Huấn luyện & sức khỏe

### G1 — Soạn giáo án
```
PT soạn thủ công                    → workout_plans(ai_generated=FALSE)
AI sinh nháp từ hồ sơ hội viên       → workout_plans(ai_generated=TRUE) → BẮT BUỘC PT duyệt (reviewed_by NOT NULL)
  → workout_plan_items: từng bài tập, sets/reps/rest theo ngày
→ Giao cho hội viên tập theo
```

### G2 — Hội viên tự tập / tập cùng PT
```
Theo workout_plan_items → thực hiện → workout_logs (sets_done, reps_done, weight_kg, rpe)
  pt_session_id NULL  = tự tập
  pt_session_id có giá trị = tập trong buổi PT
→ Biểu đồ tiến bộ theo tuần (VD: mức tạ bench press)
```

### G3 — Đo chỉ số cơ thể
```
Chụp phiếu InBody → LLM đọc số (OCR) → body_metrics(source=INBODY_OCR, ocr_confidence)
  → Form hiển thị sẵn, ô ocr_confidence thấp tô vàng → NGƯỜI DÙNG kiểm tra & sửa
  → confirmed_by_user=TRUE mới lưu chính thức (AI không ghi thẳng CSDL)
→ Hệ thống tự tính: BMI (ngưỡng châu Á), BMR (Mifflin-St Jeor), WHR
```

---

## H. Phản hồi & sự cố thiết bị

```
Hội viên gửi feedbacks(feedback_type, rating?)
  FACILITY → equipment.status=NEEDS_REPAIR, ẩn khỏi lịch lớp
             → xử lý xong + có repair_cost → tự sinh dòng expenses
  TRAINER  → cập nhật employees.rating_avg → ảnh hưởng KPI_BONUS; rating≤2 → task Admin trong 24h
  HYGIENE  → task quản lý ca
  SERVICE/GENERAL → hàng đợi Admin
→ status: OPEN → IN_PROGRESS → (WAITING_PARTS) → RESOLVED → CLOSED
→ Đóng xong → thông báo ngược lại cho người đã phản ánh
```

---

## I. Chatbot tư vấn AI

```
Câu hỏi hội viên vào
  → Phân loại: dữ liệu có cấu trúc (giá, số buổi còn lại, BMI)?  → Tool Calling truy vấn CSDL trực tiếp
  → Tri thức dạng văn bản (dinh dưỡng, cách tập)?                → RAG (retrieval + generation)
  → Có yếu tố an toàn (chấn thương, bệnh nền, bài tập cụ thể)?   → GraphRAG loại bài CHỐNG CHỈ ĐỊNH trước khi trả lời

→ Guardrail cứng: BMI ngoài ngưỡng an toàn / mục tiêu phi thực tế / có bệnh nền
   → chèn khuyến cáo tham vấn chuyên môn, gợi ý đặt buổi ASSESSMENT với PT (handoff)
→ Hội thoại nhiều lượt: theo dõi trạng thái tư vấn (ngân sách, mục tiêu, chấn thương, khung giờ) xuyên suốt phiên
→ Structured Output: mọi mã gói/mã bài tập LLM nhắc tới đều được xác minh tồn tại thật trong CSDL trước khi trả về
```

---

## J. Bảo mật tài khoản

```
Đăng nhập sai liên tiếp >5 lần (auth.max_failed_attempts)
  → users.auto_locked_until = now()+15 phút (auth.lockout_minutes) — KHÔNG đụng locked_until của Admin

Quên mật khẩu
  → POST /auth/forgot-password → luôn trả 200 (không tiết lộ email có tồn tại)
  → password_reset_tokens(token_hash, expires_at=+30 phút)
  → Email chứa link → đổi mật khẩu → used_at ghi nhận → thu hồi toàn bộ phiên đăng nhập cũ
  → Không có email: đến quầy, lễ tân xác minh CCCD, kích hoạt đặt lại trực tiếp

Admin khóa/mở khóa thủ công
  → status=LOCKED + locked_reason + locked_until (NULL = vô thời hạn) + locked_by
  → Job đêm tự mở khi locked_until đã qua; Admin có thể mở sớm bất cứ lúc nào
```

---

# ⚠️ ĐỀ XUẤT THÊM / KHÔNG CHẮC CHẮN, CẦN TÔI XÁC NHẬN

> Những mục dưới đây tôi **tự suy đoán hoặc đề xuất thêm**, không có căn cứ từ trao đổi trước. **Cần bạn xác nhận hoặc sửa trước khi nộp phiếu.**

## Nhóm A — Thông tin hành chính (tôi hoàn toàn không biết)

| # | Nội dung | Ghi chú |
|---|---|---|
| ⚠️1 | **Tên đề tài chính thức** | Tôi đề xuất một phương án ở mục 1. Cần bạn và giảng viên chốt. Vài phương án khác:<br>• *Xây dựng hệ thống quản lý phòng gym với chatbot tư vấn ứng dụng RAG và Knowledge Graph*<br>• *Hệ thống quản lý vận hành và tài chính phòng gym tích hợp trợ lý tư vấn AI* |
| ⚠️2 | **Lĩnh vực đề tài** | Tôi không có danh sách lĩnh vực của Trường. Phiếu mẫu chọn *"Trí tuệ nhân tạo ứng dụng"* và *"Thương mại điện tử và hậu cần"*. Bạn cần chọn từ danh sách thật |
| ⚠️3 | **Họ tên giảng viên hướng dẫn** | Trong trao đổi bạn có nhắc "cô Trinh" (góp ý về class/membership) và "cô" (gợi ý RAG/multi-tenancy/knowledge graph). Tôi không rõ ai là GVHD chính thức |
| ⚠️4 | **Thời gian làm ĐATN, mã lớp, MSSV** | Phiếu mẫu là 17 tuần. Kế hoạch tôi lập trước đây là 16 tuần — cần điều chỉnh theo lịch thật của bạn |

## Nhóm B — Quyết định kỹ thuật chưa chốt

| # | Nội dung | Tình trạng | Ảnh hưởng |
|---|---|---|---|
| ⚠️5 | **Có làm Knowledge Graph (Neo4j) không?** | Tôi **đã đưa vào** scope vì bạn nói cô gợi ý và tôi đánh giá là hợp lý. **Nhưng bạn chưa chốt.** | Nếu bỏ → mất đóng góp về chống chỉ định bài tập; tiết kiệm ~1 tuần. Phương án rẻ hơn: dùng PostgreSQL + `WITH RECURSIVE` |
| ⚠️6 | **Số bảng cuối cùng: 31 hay 33?** | Tôi **chưa nhắc tới churn** trong scope này vì bạn chưa quyết | Nếu giữ churn → thêm 2 bảng, có thêm 1 đóng góp (giữ chân hội viên có thí nghiệm nhóm đối chứng) |
| ⚠️7 | **Có làm nhận diện khuôn mặt không?** | Tôi viết ở mức *"đối chiếu ảnh hồ sơ bằng mắt lễ tân"* (bắt buộc), **không đưa nhận diện tự động vào scope** | Nếu làm → thêm ArcFace ONNX, +1–2 tuần, phải xử lý tuân thủ NĐ 13/2023 chặt chẽ |
| ⚠️8 | **Chọn mô hình LLM nào để so sánh?** | Phiếu mẫu yêu cầu **so sánh ≥ 2 mô hình**. Tôi mới ghi Claude Haiku 4.5 (chính) + Sonnet 5 | Cần chốt mô hình thứ hai để đối chiếu. Gợi ý: một mô hình local chạy được trên máy bạn (Qwen2.5 7B, Llama 3.1 8B) hoặc một API khác. **Tôi không rõ cấu hình máy của bạn** nên không dám khẳng định mô hình local nào chạy được |
| ⚠️9 | **Mô hình embedding tiếng Việt** | Tôi **cố ý không ghi tên cụ thể** vì chưa kiểm chứng. Cần thử nghiệm và so sánh trên dữ liệu thật của bạn | Ứng viên cần thử: các mô hình multilingual của `sentence-transformers`, và các mô hình embedding tiếng Việt trên HuggingFace. **Phải tự benchmark, đừng tin tên mô hình tôi hay ai đó nêu ra** |
| ⚠️10 | **Nguồn dữ liệu dinh dưỡng** | Tôi giả định dùng *"Bảng thành phần thực phẩm Việt Nam"* của Viện Dinh dưỡng. **Tôi không chắc về tình trạng bản quyền và khả năng truy cập** | Cần kiểm tra thực tế. Phương án dự phòng: dữ liệu dinh dưỡng nguồn mở quốc tế (USDA FoodData Central) + tự bổ sung món Việt |
| ⚠️11 | **Có làm reranking không?** | Tôi ghi *"tùy chọn"*. Reranking cải thiện chất lượng retrieval nhưng thêm 1 mô hình phải chạy | Nếu làm thì cần cross-encoder — tăng độ trễ và chi phí |
| ⚠️12 | **Khảo sát phần mềm đối thủ** | Phần *"Khảo sát các giải pháp hiện tại"* trong mục 3.5 tôi viết **theo nhóm giải pháp, không nêu tên phần mềm trong nước** vì tôi không chắc chắn về các sản phẩm cụ thể đang có trên thị trường Việt Nam | **Bạn cần tự khảo sát 3–5 phần mềm thật** và bổ sung tên, giá, ưu/nhược điểm cụ thể. Đây là phần hội đồng hay hỏi |
| ⚠️13 | **Cổng thanh toán nào?** | Tôi ghi chung *"cổng thanh toán sandbox"*. Chưa chốt VNPay / MoMo / ZaloPay | Nên đăng ký sandbox sớm (tuần 1–2) vì có thể mất thời gian duyệt |
| ⚠️14 | **Có làm OCR phiếu InBody không?** | Tôi **không đưa vào** phần sản phẩm kỳ vọng, chỉ nhắc `expo-camera` dùng để chụp | Nếu làm → thêm tính năng có giá trị thực tế, chi phí thấp (~3 USD/tháng), +3 ngày |

## Nhóm C — Nội dung tôi tự đề xuất thêm (không có trong trao đổi trước)

| # | Nội dung | Lý do đề xuất |
|---|---|---|
| ⚠️15 | **Mục 3.3 "Kỹ năng sinh viên phát triển được"** | Bạn không yêu cầu mục này, nhưng phiếu mẫu có. Tôi viết sẵn để bạn dùng — **cần bạn đọc lại xem có đúng với kỳ vọng không** |
| ⚠️16 | **Con số cụ thể trong mục 3.4.4 Dữ liệu** (1.000 hội viên, 1.400 hợp đồng, 62.000 check-in, 2.000 đoạn KB) | Lấy từ bộ tham số mô phỏng tôi đề xuất ở `docs/03-CO-SO-DU-LIEU.md`. **Đây là con số giả định**, chưa được xác minh bằng khảo sát thực tế |
| ⚠️17 | **Yêu cầu "100 câu hỏi benchmark" và "30–50 kịch bản hội thoại"** | Tôi lấy đúng theo phiếu mẫu của cô Giang để đảm bảo đạt chuẩn cô yêu cầu. Nếu đề tài bạn có mức khác thì điều chỉnh |
| ⚠️18 | **Đề cập Nghị định 13/2023/NĐ-CP** | Tôi đưa vào vì hệ thống có xử lý ảnh chân dung. Nếu bạn **không** làm nhận diện khuôn mặt thì mức độ liên quan giảm, nhưng vẫn nên giữ vì có lưu ảnh hồ sơ và dữ liệu sức khỏe |
| ⚠️19 | **Phần "Ranh giới an toàn cho tư vấn sức khỏe"** | Đây là nội dung tôi chủ động thêm. Chatbot tư vấn dinh dưỡng chạm vào lĩnh vực sức khỏe — cần có ranh giới rõ ràng. Tôi cho rằng đây là **điểm cộng** khi bảo vệ, nhưng bạn cần đồng ý với cách tiếp cận |
| ⚠️20 | **Multi-tenancy** | Bạn đã chốt **Hướng A (1 phòng gym)** nên tôi **không đưa multi-tenancy vào scope**. Nhưng cô đã từng gợi ý — bạn nên chủ động trao đổi lại với cô. Nếu cần, có thể bổ sung một mục *"Lộ trình mở rộng thành nền tảng SaaS"* + proof-of-concept RLS trên 3 bảng (~3 ngày) |

## Nhóm D — Những gì tôi CỐ Ý KHÔNG đưa vào

| Nội dung | Lý do |
|---|---|
| **Mục 3.2.4 "Công nghệ tích hợp kênh tương tác"** (Messenger, Telegram) | Bạn đã nói rõ đề tài không có phần này. Tôi thay bằng *"Công nghệ xây dựng ứng dụng Web và Mobile"* — thứ mà đề tài bạn **có** còn phiếu mẫu **không có** |
| Mọi nội dung liên quan tới **thương mại điện tử, tư vấn bán hàng, purchase request, so sánh giá thị trường** | Phiếu mẫu là đề tài nội thất/eCommerce. Đề tài của bạn khác hẳn về bản chất |
| **Multi-tenant, quản lý tenant, platform admin** | Bạn đã chốt Hướng A |
| **Crawl dữ liệu bằng BeautifulSoup** | Phiếu mẫu crawl website sản phẩm. Đề tài bạn dùng **bộ dữ liệu có sẵn giấy phép mở** (Free Exercise DB) — không cần crawl, và tránh được vấn đề bản quyền |
| **Fine-tune LoRA / PEFT** | Với quy mô knowledge base ~2.000 đoạn, RAG + prompt tốt cho kết quả tốt hơn fine-tune với chi phí thấp hơn nhiều |

---

## Việc cần làm tiếp

1. **Rà soát toàn bộ mục ⚠️** ở trên, đánh dấu cái nào đúng / cần sửa
2. Chốt **⚠️5 (Knowledge Graph)** và **⚠️6 (churn)** — hai quyết định ảnh hưởng lớn nhất tới khối lượng công việc
3. **Khảo sát 3–5 phần mềm quản lý phòng gym thật** để hoàn thiện mục 3.5 (⚠️12)
4. Sau khi chốt, tôi có thể viết tiếp **Mục 4 — Các nội dung sẽ thực hiện và kế hoạch triển khai** theo đúng định dạng phiếu (chia Nội dung 1→5 theo tuần, kèm kết quả giao nộp R/D/C/P)
