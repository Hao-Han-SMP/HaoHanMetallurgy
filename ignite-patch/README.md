# HaoHan Metallurgy Ignite Patch

Optional Ignite/Mixin patch layer for HaoHan Metallurgy.

The Purpur plugin remains the main runtime surface. This module is only for
server internals that Bukkit/Paper/Purpur API cannot expose cleanly.

## Build

```powershell
cd ignite-patch
mvn clean package
```

Output:

```text
ignite-patch/target/HaoHanMetallurgy-IgnitePatch-1.0-SNAPSHOT.jar
```

## Test Server Layout

```text
TestServer/
  ignite.jar
  purpur-1.21.11-2568.jar
  plugins/
    HaoHanMetallurgy-1.0-SNAPSHOT.jar
  mods/
    HaoHanMetallurgy-IgnitePatch-1.0-SNAPSHOT.jar
```

Start with:

```powershell
java -Xms512M -Xmx2G `
  -Dignite.locator=paper `
  -Dignite.paper.jar=./purpur-1.21.11-2568.jar `
  -Dignite.paper.version=1.21.11 `
  -jar ignite.jar nogui
```

Successful injection currently logs:

```text
[HaoHanMetallurgy/IgnitePatch] CraftServer constructor injected successfully.
```
