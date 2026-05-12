# AlgoProfiler - Hệ thống phân tích hồ sơ thuật toán Codeforces

## I. Công nghệ sử dụng
- **Ngôn ngữ:** Java 17.
- **Giao diện:** Java Swing.
- **Cơ sở dữ liệu:** H2 Database (Embedded).
- **Thu thập dữ liệu:** Selenium Automation & Codeforces API.
- **Trí tuệ nhân tạo:** Google Gemini 2.5 Flash API.
- **Quản lý dự án:** Maven.

## II. Hướng dẫn chạy chương trình

### Cách 1: Sử dụng Antigravity (VS Code)
1. Mở thư mục dự án bằng VS Code.
2. Chờ Maven tải xong thư viện.
3. Mở file `src/main/java/com/project/ui/MainFrame.java` và nhấn **Run**.

### Cách 2: Sử dụng Eclipse
1. Chọn **File > Import > Existing Maven Projects**.
2. Trỏ đường dẫn đến thư mục dự án.
3. Chuột phải vào dự án chọn **Run As > Java Application** (chọn class `MainFrame`).

## III. Cách sử dụng chương trình

*Lưu ý: Dữ liệu mẫu đã được tích hợp sẵn trong thư mục `database/`. Bạn có thể xem ngay các tab mà không cần crawl data lại.*

**Bước 1: Cài đặt hệ thống (Bắt buộc nếu muốn crawl mới)**
   - Nhấn **Mở Chrome xác minh ngay** để vượt Cloudflare và đăng nhập tài khoản Codeforces bất kỳ (sử dụng tài khoản đã có submit một vài bài tập). 
   *Lưu ý: Giữ cửa sổ Chrome này mở suốt quá trình crawl.*
   - Nhập **Gemini API Key** và nhấn **Kiểm tra & Xác nhận API**.

**Bước 2: Quản lý Handle**
   - Vào tab **Quản lý Handle** để xem danh sách nick đã thêm hoặc thêm mới.

**Bước 3: Thu thập Source Code (Crawl)**
   - Chuyển sang tab **Source Code**.
   - Chọn một handle trong danh sách (ví dụ: `tourist`) và nhấn **Lọc**. Danh sách các bài nộp đã lưu trong máy sẽ hiện ra.
   - Nhấn **Crawl thêm 5 source**: Chương trình sẽ tự động điều khiển Chrome để lấy mã nguồn mới nhất của các bài đã `Accepted`. Kết quả sẽ được lưu trực tiếp vào cơ sở dữ liệu.
   *Lưu ý: Trong quá trình Crawl có thể Codeforces sẽ hiện các thông báo lỗi, nhưng vẫn để giữ nguyên máy để code thực hiện cho đến khi hoàn thành xong.*

**Bước 4: Phân tích AI**
   - Chuyển sang tab **Phân tích Source**.
   - Chọn handle và nhấn **Lọc** để thấy danh sách mã nguồn đã crawl.
   - Chọn một dòng bài tập cụ thể và nhấn **Phân tích code đã chọn**.
   - **Thanh tiến trình** sẽ chạy và hiển thị phần trăm. Lúc này, AI đang đọc hiểu mã nguồn để bóc tách: *Cấu trúc dữ liệu, Thuật toán và đánh giá xem code này có giống do AI viết hay không.*

**Bước 5: Tổng quan đánh giá**
   - Chuyển sang tab **Đánh giá**.
   - Chọn handle và nhấn **Lọc**. Chương trình sẽ tổng hợp toàn bộ kết quả phân tích AI để:
     - Hiển thị **Biểu đồ tròn** về tỷ lệ các Cấu trúc dữ liệu/Thuật toán handle đó hay dùng.
     - Tính toán **Điểm số AI (AI Usage Score)**: Càng cao nghĩa là code càng có nhiều dấu hiệu của AI sinh ra.

**Bước 6: Thiết lập Crawl định kỳ (Nếu cần)**
   - Tại tab **Cài đặt hệ thống**, bạn có thể bật **Lịch crawl định kỳ**.
   - Thiết lập thời gian (ví dụ: `02:00`) và số lượng bài mỗi handle.
   - Chương trình sẽ tự động chạy ngầm và thu thập dữ liệu mới hàng ngày cho toàn bộ handle có trong hệ thống mà không cần thao tác thủ công.
   - Bạn có thể nhấn **Chạy crawl định kỳ ngay** để kiểm tra tính năng này lập tức.

## IV. Kiểm tra Cơ sở dữ liệu
Khi chương trình đang chạy, thầy có thể kiểm tra dữ liệu trực tiếp qua trình duyệt:
1. Truy cập địa chỉ: **[http://localhost:8082](http://localhost:8082)**
2. Nhập thông tin kết nối:
   - **JDBC URL:** `jdbc:h2:./database/algo_profiler`
   - **User Name:** `sa`
   - **Password:** (để trống)
3. Nhấn **Connect** để xem và truy vấn các bảng: `USERS`, `SUBMISSIONS`, `ANALYSIS`.

---
Thành viên nhóm (1 thành viên):
- Châu Thanh Nam Trung - MSSV: 102240117 - STT: 26 - Lớp: 24T_DT2.
