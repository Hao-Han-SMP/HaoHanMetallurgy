# HaoHanMetallurgy (Hảo Hán Metallurgy)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://papermc.io/)
[![Server Software](https://img.shields.io/badge/Server-Purpur%20%2F%20Paper-blue.svg)](https://purpurmc.org/)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)

**HaoHanMetallurgy** là một plugin Minecraft custom được thiết kế độc quyền dành riêng cho máy chủ **Hảo Hán SMP**. Plugin mang đến một hệ thống Lò Rèn Cổ Đại (Ancient Forge).

---
![alt text](image.png)

## Yêu Cầu Hệ Thống (Requirements)
*   **Server Software**: Paper, Purpur, hoặc Spigot (Khuyên dùng **Purpur / Paper 1.21.1**).
*   **Java Version**: Java 21 hoặc mới hơn.
*   **Tài Nguyên**: Bắt buộc cài đặt đầy đủ bộ ba: **Plugin**, **Datapack**, và **Resource Pack** đi kèm để hiển thị và hoạt động đầy đủ tính năng.

---

## Hướng Dẫn Cài Đặt và Biên Dịch (Installation & Build)

### 1. Dành cho Quản trị viên (Chạy bằng file JAR có sẵn)
1.  **Cài đặt Plugin**:
    *   Tải file `.jar` biên dịch từ dự án này.
    *   Copy file `.jar` vào thư mục `/plugins/` của máy chủ.
    *   Khởi động lại server để sinh thư mục cấu hình `/plugins/HaoHanMetallurgy/config.yml`.
2.  **Cài đặt Datapack**:
    *   Tải folder hoặc file `.zip` của [HaoHanMetallurgy_Datapack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Datapack).
    *   Thả vào thư mục `/world/datapacks/` của máy chủ.
    *   Chạy lệnh `/reload` để tải các công thức rèn custom và tiến trình thành tựu.
3.  **Cài đặt Resource Pack**:
    *   Tải gói [HaoHanMetallurgy_Resourcepack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Resourcepack).
    *   Nén thành file `.zip` (hoặc để nguyên folder) bỏ vào thư mục `resourcepacks/` trên máy khách (Client) Minecraft của người chơi và kích hoạt nó.

### 2. Dành cho Lập trình viên (Tự biên dịch từ mã nguồn)
1.  **Yêu cầu chuẩn bị**:
    *   Cài đặt **Java Development Kit (JDK) 21**.
    *   Cài đặt **Apache Maven (3.9+)**.
2.  **Các bước biên dịch từ A -> Z**:
    *   Mở terminal/command prompt tại thư mục gốc của dự án plugin.
    *   Chạy lệnh Maven để dọn dẹp và đóng gói:
        ```bash
        mvn clean package -DskipTests
        ```
    *   Sau khi build thành công (`BUILD SUCCESS`), file plugin đầu ra dạng `.jar` sẽ xuất hiện tại thư mục:
        `target/HaoHanMetallurgy-1.0-SNAPSHOT.jar`
3.  **Tự động hóa phát triển nhanh (PowerShell Script)**:
    *   Dự án có sẵn một file script hỗ trợ tự động hóa phát triển: **`build_and_start.ps1`**.
    *   *Lưu ý: Script này chứa các đường dẫn cố định được cấu hình sẵn theo máy cục bộ của tác giả. Để sử dụng trên thiết bị khác, bạn cần mở file script này lên và chỉnh sửa lại các biến `$projectDir`, `$serverPluginsDir`, `$serverDir` cho khớp với đường dẫn thư mục dự án và server của bạn.*
    *   Chạy script bằng lệnh:
        ```powershell
        .\build_and_start.ps1
        ```

---

## Lệnh (Commands) và Quyền Hạn
Tất cả các lệnh dưới đây yêu cầu quyền hạn quản trị viên: `haohansmp.metallurgy.admin` (mặc định OP sẽ có quyền này).

*   `/metallurgy info` — Hiển thị thông tin phiên bản plugin.
*   `/metallurgy reload` — Tải lại toàn bộ cấu hình `config.yml` và các công thức rèn (recipes).
*   `/metallurgy debug` — Bật/tắt chế độ debug (hiển thị logs chi tiết về nhiệt lượng/hoạt động).
*   `/metallurgy list` — Hiển thị danh sách tất cả các lò rèn đang hoạt động trong thế giới.
*   `/metallurgy give <player> <item_id> <amount>` — Nhận vật phẩm tùy chỉnh từ plugin (Ví dụ: Ember Ore).

---

## Requirement

Bộ công cụ lò rèn **HaoHanMetallurgy** bao gồm 3 phần độc lập bắt buộc cài đặt cùng nhau:
1.  **[HaoHanMetallurgy (Plugin)](https://github.com/Hao-Han-SMP/HaoHanMetallurgy)**: Phần cốt lõi xử lý logic máy, nhiệt lượng, GUI và hệ thống lưu trữ YAML.
2.  **[HaoHanMetallurgy_Resourcepack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Resourcepack)**: Bộ tài nguyên hình ảnh texture (`metallurgy.png`), các file cấu hình mô hình 3D khối đặc (`metallurgy.json`, `paper.json`).
3.  **[HaoHanMetallurgy_Datapack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Datapack)**: Định nghĩa các công thức nung custom, loot tables của quặng, và hệ thống tiến trình thành tựu (advancements).
