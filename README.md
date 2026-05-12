# AlgoProfiler

## 1. Giới thiệu dự án
AlgoProfiler là ứng dụng Java Swing dùng để:

- Quản lý danh sách handle Codeforces.
- Crawl danh sách submission Accepted và lưu source code vào H2 Database.
- Gửi source code tới mô hình AI để phân tích CTDL, thuật toán và mức độ nghi ngờ do AI sinh ra.
- Chấm điểm tổng cho từng nick và hiển thị dashboard đánh giá.

Ứng dụng được tổ chức theo chuẩn Maven để có thể mở trực tiếp bằng VS Code hoặc import vào Eclipse.

## 2. Tính năng chính

- Thêm handle Codeforces vào hệ thống qua giao diện Swing.
- Lưu dữ liệu bằng H2 Database với file đặt ngay trong thư mục project.
- Crawl source code bằng Selenium + WebDriverManager, không yêu cầu cài ChromeDriver thủ công.
- Cấu hình AI API Key qua giao diện, không cần sửa code.
- Phân tích source code bằng AI API và lưu kết quả xuống bảng `ANALYSIS`.
- Tính điểm tổng dựa trên thuật toán/CTDL và cảnh báo nguy cơ lạm dụng AI.
- Double-click trên dashboard để xem chi tiết từng submission đã bị phân tích.

## 3. Cấu trúc dự án

- `src/main/java/com/project/config`: cấu hình database và config file.
- `src/main/java/com/project/model`: model dữ liệu.
- `src/main/java/com/project/dao`: DAO thao tác với H2.
- `src/main/java/com/project/ui`: giao diện Swing.
- `src/main/java/com/project/crawler`: module crawl Codeforces.
- `src/main/java/com/project/ai`: module gọi AI API.
- `src/main/java/com/project/service`: dịch vụ chấm điểm tổng hợp.
- `database/`: nơi lưu file H2 sau khi chạy chương trình.
- `config.properties`: file lưu API key và AI API URL.

## 4. Cấu trúc H2 Database

### Bảng `CF_USER`

- `ID`: khóa chính.
- `HANDLE`: nick Codeforces.
- `DISPLAY_NAME`: tên hiển thị.
- `RATING`, `MAX_RATING`, `RANK_TITLE`: thông tin mở rộng.
- `TOTAL_SCORE`: điểm tổng sau đánh giá.
- `LAST_CRAWLED_AT`: thời điểm crawl gần nhất.
- `CREATED_AT`: thời điểm tạo.

### Bảng `SUBMISSION`

- `ID`: khóa chính nội bộ.
- `USER_ID`: khóa ngoại tới `CF_USER`.
- `CONTEST_ID`: mã contest.
- `SUBMISSION_ID`: mã submission trên Codeforces.
- `PROBLEM_INDEX`, `PROBLEM_NAME`: thông tin bài.
- `PROGRAMMING_LANGUAGE`, `VERDICT`: thông tin ngôn ngữ và kết quả.
- `SUBMITTED_AT`: thời gian nộp.
- `SOURCE_CODE`: mã nguồn crawl được.
- `CODE_HASH`: hash nội dung code.
- `IS_ANALYZED`: đã phân tích AI hay chưa.
- `CREATED_AT`: thời điểm tạo.

### Bảng `ANALYSIS`

- `ID`: khóa chính.
- `SUBMISSION_ID`: khóa ngoại tới `SUBMISSION.ID`.
- `DATA_STRUCTURES`: danh sách CTDL phát hiện.
- `ALGORITHMS`: danh sách thuật toán phát hiện.
- `AI_USAGE_SCORE`: xác suất nghi ngờ AI.
- `AI_USAGE_LABEL`: nhãn đánh giá AI.
- `CONFIDENCE_SCORE`: độ tin cậy nội bộ.
- `SUMMARY`: nhận xét tổng hợp.
- `ANALYZED_AT`: thời điểm phân tích.

## 5. Hướng dẫn import dự án vào Eclipse

1. Mở Eclipse.
2. Chọn `File -> Import`.
3. Chọn `Maven -> Existing Maven Projects`.
4. Chọn thư mục gốc `algoprofiler`.
5. Nhấn `Finish`.
6. Chờ Eclipse tự tải dependencies Maven.
7. Nếu Eclipse hỏi chọn JDK, hãy đặt về Java 17.

## 6. Yêu cầu môi trường

- Java 17.
- Google Chrome đã cài trên máy Windows.
- Kết nối Internet để tải Maven dependencies và gọi AI API.

Không cần cài ChromeDriver thủ công vì dự án đã dùng `WebDriverManager`.

## 7. Hướng dẫn cấu hình AI API Key

Ứng dụng hỗ trợ lưu API key qua giao diện:

1. Chạy chương trình.
2. Vào màn hình `Cài đặt hệ thống`.
3. Nhập `AI API Key`.
4. Nếu cần, kiểm tra `AI API URL`.
5. Nhấn `Lưu cấu hình`.

Sau bước này, ứng dụng sẽ ghi dữ liệu vào file `config.properties` ở thư mục gốc project. Người dùng không cần sửa mã nguồn.

## 8. Hướng dẫn chạy chương trình

### Chạy bằng Eclipse

1. Mở class `com.project.ui.MainFrame`.
2. Nhấn `Run As -> Java Application`.

### Chạy bằng terminal

Nếu máy có Maven:

```bash
mvn compile
```

Sau đó chạy class `com.project.ui.MainFrame`.

## 9. Quy trình sử dụng đề xuất

1. Mở ứng dụng.
2. Vào `Cài đặt hệ thống`, nhập AI API Key và lưu.
3. Vào `Quản lý Nick`, thêm các handle Codeforces.
4. Vào `Dashboard Đánh Giá`, nhấn `Bắt đầu Crawl & Phân tích`.
5. Chờ progress bar chạy xong.
6. Double-click vào một nick để xem chi tiết submission và nhận xét AI.

## 10. Lưu ý khi chấm bài

- Codeforces có thể giới hạn hoặc chặn tạm thời việc crawl nếu truy cập quá nhanh.
- Dự án đã thêm `WebDriverWait` và delay ngắn để giảm nguy cơ bị block.
- Nếu AI API key trống hoặc sai, chương trình sẽ báo lỗi rõ ràng thay vì crash toàn bộ ứng dụng.
- Nếu một bài phân tích AI lỗi, hệ thống sẽ đánh dấu `Lỗi phân tích` cho bài đó và tiếp tục xử lý các bài còn lại.
