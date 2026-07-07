# HaoHanMetallurgy (Hảo Hán Metallurgy)

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://papermc.io/)
[![Server Software](https://img.shields.io/badge/Server-Purpur%20%2F%20Paper-blue.svg)](https://purpurmc.org/)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)

**HaoHanMetallurgy** là một plugin Minecraft custom được thiết kế độc quyền dành riêng cho máy chủ **Hảo Hán SMP**. Plugin mang đến một hệ thống Lò Rèn Cổ Đại (Ancient Forge).

---
![alt text](image.png)

## ⚙️ Yêu Cầu Hệ Thống (Requirements)
*   **Server Software**: Paper, Purpur, hoặc Spigot (Khuyên dùng **Purpur / Paper 1.21.1**).
*   **Java Version**: Java 21 hoặc mới hơn.
*   **Tài Nguyên**: Bắt buộc cài đặt đầy đủ bộ ba: **Plugin**, **Datapack**, và **Resource Pack** đi kèm để hiển thị và hoạt động đầy đủ tính năng.

---

## 🚀 Hướng Dẫn Cài Đặt (Installation)

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

---

## 🛠 Lệnh (Commands)
*   `/metallurgy reload` (Quyền: `metallurgy.admin`): Nạp lại cấu hình `config.yml`.
*   `/metallurgy give <player> <item_id> <amount>` (Quyền: `metallurgy.admin`): Nhận vật phẩm custom từ plugin (Ví dụ: Ember Ore).

---

## 📂 Tổ Chức Dự Án Trong Organization
Bộ công cụ lò rèn **HaoHanMetallurgy** bao gồm 3 phần độc lập:
1.  **[HaoHanMetallurgy (Plugin)](https://github.com/Hao-Han-SMP/HaoHanMetallurgy)**: Phần cốt lõi xử lý logic máy, nhiệt lượng, GUI và hệ thống lưu trữ YAML.
2.  **[HaoHanMetallurgy_Resourcepack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Resourcepack)**: Bộ tài nguyên hình ảnh texture (`metallurgy.png`), các file cấu hình mô hình 3D khối đặc (`metallurgy.json`, `paper.json`).
3.  **[HaoHanMetallurgy_Datapack](https://github.com/Hao-Han-SMP/HaoHanMetallurgy_Datapack)**: Định nghĩa các công thức nung custom, loot tables của quặng, và hệ thống tiến trình thành tựu (advancements).
