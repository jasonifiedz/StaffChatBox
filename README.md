# Staff Chat Box

A **client-side** Fabric mod for Minecraft **1.21.11** that pulls
[StaffChat](../plugins/StaffChat/) messages out of the main chat and shows them in a
separate chat box in the top-left of the screen, with a quick-reply screen.

## How it detects staff chat

The server's StaffChat header is a gradient `Staff` built from `&#RRGGBB` codes:

```
&#22C2D8&lS&#4DD1CCt&#78E1C1a&#A2F0B5f&#CDFFA9f
```

Bukkit's `ChatColor.translateAlternateColorCodes('&', …)` only converts the single-letter
codes (`&l` → bold) and **leaves `&#hex` as literal text**. So every staff message arrives
on the client with `&#22C2D8…` literally in its text — a stable fingerprint.

`StaffChatDetector` matches on that literal signature (see
`StaffChatConfig.TEXT_SIGNATURES`), with a fallback that matches the gradient RGB values as
real styled colours in case a server-side hex translator converted them.

## What it does

- **Intercepts** staff messages via `ClientReceiveMessageEvents.ALLOW_GAME` and removes them
  from the vanilla chat (toggle "Keep in chat" to keep them in both).
- **Passive box** during gameplay (newest at the bottom): shows recent staff messages and
  fades them after the configured time.
- **Two boxes when you press T.** Opening the normal chat (T) gives you the usual chat input
  *and* a separate staff panel right there — its own **search** box, a **scrollable** log
  (mouse wheel + scrollbar), and its **own input box** that sends to staff chat via `/sc`.
  Both boxes are independent; click whichever input you want and type. No staff-chat keybind.
- **Configurable** via a live, draggable/resizable preview (drag to move, drag the corner to
  resize) plus sliders/toggles for scale, opacity, line count, fade time, keep-in-chat,
  timestamps, the `/sc` command, and an **editable timestamp format** (`java.time` pattern
  like `HH:mm:ss` or `hh:mm a`) with a live valid/invalid indicator.

Implementation note: the staff panel is added to the vanilla `ChatScreen` with
`fabric-screen-api-v1` (no mixins) — its input box is a real widget registered in the screen's
children, so focus and typing work; Enter on the staff input is intercepted and routed to `/sc`.

## Opening the settings

Configure it the normal way: **Mods menu → Staff Chat Box → Configure** (the gear button).
This requires [ModMenu](https://modrinth.com/mod/modmenu) (you almost certainly already have
it). The settings screen has the live, draggable/resizable preview and every option.

There is **no keybind** — staff chat lives inside the normal chat (press **T**), and settings
live under Configure.

## Settings

Everything is saved to `config/staffchatbox.json` and editable in-game via the Configure
screen: enabled, position, width, max lines, scale, background opacity, fade time, keep-in-
main-chat, timestamps on/off, timestamp format, and timestamp colour. The detection
signatures also live in that JSON if the server ever changes its staff header.

## Build

Requires JDK 21 (already on this machine).

```sh
cd StaffChatMod
./gradlew build
```

Output jar: `build/libs/staffchatbox-1.0.0.jar`.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.11.
2. Drop **[Fabric API](https://modrinth.com/mod/fabric-api)** (1.21.11) into `.minecraft/mods/`.
3. Drop **[ModMenu](https://modrinth.com/mod/modmenu)** into `.minecraft/mods/` (for the
   Configure button — optional but recommended).
4. Drop `staffchatbox-1.0.0.jar` into `.minecraft/mods/`.
5. Launch, join your server. Configure via Mods → Staff Chat Box → Configure; press **O** to
   open the staff chat window.

## Tuning

Most tuning happens in-game via the settings screen and persists to
`config/staffchatbox.json`. Defaults and detection signatures live in `StaffChatConfig.java`.

> If Gradle can't resolve dependencies, bump `loader_version` / `fabric_version` in
> `gradle.properties` to the current 1.21.11 builds listed at
> <https://fabricmc.net/develop/>.
