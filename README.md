# Virus VPN

A fresh, original Android VPN app (package `com.virusvpn.app`), built from scratch
with a Bornali-style simple home screen: Splash, LOCATION / NETWORK cards,
username and password fields, a big START button, a server picker, Custom Tweaks,
UPDATES, TWEAKS and EXIT.

Safety notes, kept deliberately:
- No server, panel, username, password or subscription link is bundled in the app.
- Servers are only added by the user, by pasting a VLESS link in the app.
- The username/password and NETWORK / Tweaks fields are stored locally only
  and do not change the connection until a server panel exists (VPS later).
- The VPN engine is only the open-source libv2ray core library, downloaded
  at build time. No other VPN app's code or UI is included.

Build: push to `master` triggers GitHub Actions, which builds a debug APK
(`gradle assembleDebug`) and uploads it as `universal-apk`. No keystore is used.
