<div align="center">

<img src="image.png" alt="HaoHan Metallurgy banner" width="100%">

# HaoHan Metallurgy

Plugin luyện kim tùy chỉnh cho HaoHan SMP, xây dựng quanh hệ thống Ancient Forge.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-222222?style=for-the-badge&logo=paper&logoColor=white)](https://papermc.io/)
[![Purpur](https://img.shields.io/badge/Purpur-Compatible-8A4FFF?style=for-the-badge)](https://purpurmc.org/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Gson](https://img.shields.io/badge/Gson-JSON-2E7D32?style=for-the-badge&logo=google&logoColor=white)](https://github.com/google/gson)
[![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/junit5/)

Ngôn ngữ: Tiếng Việt | [English](README.en.md)

</div>

## Tổng quan

HaoHan Metallurgy là plugin Minecraft dành cho HaoHan SMP. Plugin cung cấp hệ thống luyện kim tùy chỉnh, xử lý logic máy, công thức rèn, giao diện, vật phẩm tùy chỉnh và dữ liệu lưu trữ cho các máy luyện kim trong server.

## Công nghệ sử dụng

| Toolkit | Vai trò |
| --- | --- |
| Paper API | Nền tảng API chính để phát triển plugin server. |
| Purpur | Môi trường server khuyến nghị để triển khai. |
| Java 21 | Ngôn ngữ và runtime chính của plugin. |
| Maven | Quản lý dependency và build file `.jar`. |
| Gson | Hỗ trợ xử lý dữ liệu JSON cho recipe và cấu hình. |
| JUnit 5 | Viết và chạy unit test. |

## Thành phần dự án

| Thành phần | Mô tả |
| --- | --- |
| `HaoHanMetallurgy` | Plugin server, xử lý logic, GUI, công thức và dữ liệu máy. |
| `HaoHanMetallurgy_Datapack` | Datapack chặn một số công thức vanilla, thêm advancement, loot table và tag liên quan. |
| `HaoHanMetallurgy_Resourcepack` | Resource pack chứa texture và model cần thiết cho vật phẩm hoặc khối tùy chỉnh. |

## Yêu cầu

- Minecraft server chạy Paper hoặc Purpur.
- Java 21 trở lên.
- Maven 3.9 trở lên nếu cần build từ mã nguồn.
- Datapack và resource pack đi kèm để hệ thống hoạt động đầy đủ.

## Cài đặt

1. Build hoặc tải file `.jar` của plugin.
2. Copy file `.jar` vào thư mục `plugins/` của server.
3. Copy thư mục hoặc file `.zip` của datapack vào `world/datapacks/`.
4. Cài resource pack cho client hoặc cấu hình server để người chơi tải resource pack khi tham gia.
5. Khởi động lại server.
6. Chạy `/reload` nếu cần nạp lại datapack trong quá trình phát triển.

Sau lần chạy đầu tiên, plugin sẽ tạo file cấu hình tại `plugins/HaoHanMetallurgy/config.yml`.

## Build từ mã nguồn

Chạy lệnh sau tại thư mục gốc của dự án plugin:

```bash
mvn clean package
```

File `.jar` sau khi build nằm trong thư mục `target/`.

Nếu chỉ cần build nhanh mà không chạy test:

```bash
mvn clean package -DskipTests
```

## Script phát triển

Dự án có file `build_and_start.ps1` để hỗ trợ build và khởi động server trong môi trường phát triển cục bộ.

Trước khi dùng, kiểm tra và chỉnh lại các đường dẫn trong script cho phù hợp với máy của bạn, đặc biệt là đường dẫn dự án, thư mục `plugins/` và thư mục server.

```powershell
.\build_and_start.ps1
```

## Lệnh

Các lệnh quản trị dùng permission `haohansmp.metallurgy.admin`. Người chơi OP có permission này theo mặc định.

| Lệnh | Mô tả |
| --- | --- |
| `/metallurgy info` | Hiển thị thông tin plugin. |
| `/metallurgy reload` | Nạp lại cấu hình và công thức. |
| `/metallurgy debug` | Bật hoặc tắt chế độ debug. |
| `/metallurgy list` | Hiển thị danh sách máy luyện kim đang được quản lý. |
| `/metallurgy give <player> <item_id> [amount]` | Trao vật phẩm tùy chỉnh cho người chơi. |

Alias của lệnh chính: `/met`, `/forge`.

## Permission

| Permission | Mặc định | Mô tả |
| --- | --- | --- |
| `haohansmp.metallurgy.admin` | OP | Cho phép sử dụng các lệnh quản trị. |
| `haohansmp.metallurgy.use` | Tất cả người chơi | Cho phép tương tác với máy luyện kim. |

## Cấu trúc công thức

Các công thức luyện kim được lưu trong `src/main/resources/recipes/`. Mỗi công thức định nghĩa nguyên liệu, kết quả và các thông số xử lý của Ancient Forge.

Ví dụ tham khảo có sẵn tại:

```text
src/main/resources/recipes/example_forge.json
```

### Nhiệt độ và độ tinh khiết

Mỗi recipe có hai ngưỡng nhiệt khác nhau:

- `min_temperature`: nhiệt tối thiểu để nguyên liệu bắt đầu nóng chảy và recipe chạy.
- `purification_temperature`: nhiệt cần để loại tạp chất ổn định.
- `underheat_fail_chance`: tỷ lệ ra sỉ tại đúng ngưỡng nóng chảy.
- `fail_chance`: tỷ lệ ra sỉ cơ bản khi đạt nhiệt tinh luyện.

Trong khoảng từ `min_temperature` đến `purification_temperature`, tỷ lệ ra sỉ giảm tuyến tính theo nhiệt trung bình của toàn bộ mẻ. `max_temperature` vẫn là ngưỡng quá nhiệt làm hỏng mẻ.

Recipe đặc biệt có thể khai báo phụ gia bắt buộc:

```json
"additives": ["QUARTZ", "GLOWSTONE_DUST"],
"additive_amount": 2,
"additive_clean_output_bonus": 0.25
```

Chỉ cần một loại trong danh sách, nhưng phải đủ số lượng. `additive_clean_output_bonus: 0.25` cộng 25 điểm phần trăm vào xác suất ra quặng sạch. Recipe không khai báo bonus dùng `additives.default-clean-output-bonus` trong `config.yml`.

Điều kiện môi trường của hợp kim đặc biệt:

```json
"requires_cold_quench": true,
"requires_soul_fire": true
```

Quench lạnh chấp nhận biome lạnh hoặc Ice/Packed Ice/Blue Ice gần lò. Soul Fire chấp nhận Soul Fire, Soul Campfire hoặc Soul Lantern trong bán kính hai block quanh lò.

Lò nhận fuel vanilla cùng các nhóm bổ sung như thực vật tươi, len/bed và vật phẩm lửa Nether. `fuel-values`, `fuel-groups`, `temperature.fuel-limits` và `temperature.ignition-boosts` điều khiển thời gian cháy, trần nhiệt và xung nhiệt ban đầu.

### Tiến trình hợp kim

| Tier | Hợp kim | Nguyên liệu chính | Điều kiện |
| --- | --- | --- | --- |
| 0 | Copper | Raw Copper | 700–800°C |
| 1 | Iron | Raw Iron | 900–1000°C |
| 2 | EmberSteel | 2 Iron + Blaze Powder; Coal ở FLUX | 1200–1300°C |
| 3 | Mithril | EmberSteel + Mithril Shard | 1400°C và quench lạnh |
| 4 | SoulSteel | Mithril + Ghast Tear; Soul Sand/Soil ở FLUX | 1600°C và Soul Fire |
| 5 | Netherite | SoulSteel + Netherite Scrap; Gold ở FLUX | 2000°C |

Khi nâng từ cấu hình cũ lên `config-version: 4`, plugin tạo `config.before-v4.yml` rồi cập nhật các section cân bằng nhiệt. Recipe mặc định theo schema cũ cũng được backup thành `*.json.before-v4.bak` trước khi thay thế; recipe tùy chỉnh có tên khác không bị sửa.

## Ghi chú vận hành

- Luôn cài plugin, datapack và resource pack cùng nhau để tránh thiếu recipe, texture hoặc dữ liệu progression.
- Không nên chỉnh trực tiếp dữ liệu trong thư mục runtime của server nếu thay đổi có thể được quản lý từ mã nguồn.
- Khi cập nhật datapack hoặc recipe trong môi trường đang chạy, kiểm tra lại bằng `/reload` và `/metallurgy reload`.
