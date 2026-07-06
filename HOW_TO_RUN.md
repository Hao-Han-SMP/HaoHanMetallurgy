# HaoHan Metallurgy — Hướng dẫn Build & Chạy thử

## Yêu cầu

| Công cụ | Version | Kiểm tra |
|---------|---------|----------|
| Java JDK | **21+** | `java -version` |
| Maven | **3.8+** | `mvn -version` |

> Nếu chưa có Maven: tải tại https://maven.apache.org/download.cgi
> hoặc dùng IDE (IntelliJ IDEA tích hợp Maven sẵn).

---

## Bước 1: Build Plugin

```bash
# Mở terminal tại thư mục HaoHanMetallurgy/
mvn clean package -DskipTests
```

**Output:** `target/HaoHanMetallurgy-1.0-SNAPSHOT.jar`

---

## Bước 2: Cài Purpur Server Test

### 2a. Download Purpur
Tải Purpur 1.21.x tại: https://purpurmc.org/downloads

### 2b. Tạo thư mục server
```
C:\MC_TestServer\
├── purpur-1.21.x-xxx.jar
├── start.bat          ← tạo file này (xem nội dung bên dưới)
└── plugins\
```

### 2c. Tạo file start.bat
```bat
@echo off
java -Xms512M -Xmx2G -jar purpur-1.21.x-xxx.jar nogui
pause
```
*(Đổi tên JAR cho đúng)*

### 2d. Chạy lần đầu (accept EULA)
```
double-click start.bat
```
Server sẽ tự dừng và tạo `eula.txt`. Mở file đó, đổi:
```
eula=false  →  eula=true
```

---

## Bước 3: Deploy Plugin

```
copy target\HaoHanMetallurgy-1.0-SNAPSHOT.jar  C:\MC_TestServer\plugins\
```

Chạy `start.bat` lại. Server log sẽ hiện:

```
[HaoHanMetallurgy] === HaoHan Metallurgy ===
[HaoHanMetallurgy] Loaded 1 recipe(s) from 1 file(s).
[HaoHanMetallurgy] TickEngine started (rate=20 ticks).
[HaoHanMetallurgy] Core Engine enabled. Recipes: 1 | Machines: 0
```

---

## Bước 4: Test trong game

Vào game (offline mode OK với `server.properties` → `online-mode=false`):

```
/metallurgy info
/metallurgy debug
/metallurgy list
/metallurgy reload
```

---

## Bước 5: Thêm recipe tùy chỉnh

Tạo file JSON trong `plugins/HaoHanMetallurgy/recipes/my_recipe.json`:

```json
{
  "id": "ancient_forge/my_recipe",
  "machine_type": "ANCIENT_FORGE",
  "inputs": [
    { "material": "GOLD_INGOT", "amount": 3 }
  ],
  "output": {
    "material": "GOLD_INGOT",
    "amount": 1,
    "display_name": "&eGolden Ember",
    "lore": ["&7My custom recipe"],
    "custom_model_data": 0
  },
  "fuel_cost": 200,
  "time_seconds": 15,
  "min_temperature": 500
}
```

Rồi dùng `/metallurgy reload` — không cần restart server!

---

## Troubleshooting

| Lỗi | Nguyên nhân | Giải pháp |
|-----|------------|-----------|
| `BUILD FAILURE` khi `mvn package` | Thiếu Java 21 hoặc Maven | Kiểm tra `java -version` và `mvn -version` |
| Plugin không load | JAR sai thư mục | Phải để trong `plugins/` |
| `Unknown material` trong console | Tên material sai trong JSON | Dùng tên chính xác từ [Material enum](https://jd.papermc.io/paper/1.21/) |
| Command không có | `plugin.yml` lỗi | Kiểm tra log server khi start |
