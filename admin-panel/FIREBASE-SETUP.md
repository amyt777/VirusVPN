# Virus VPN — Firebase Setup (Bengali)

Firebase = free database + login for the admin panel. The VPN traffic server is separate — Firebase does NOT run the VPN server.

## 1. Create the Firebase project (you do this, in your Google account)
1. Go to Firebase Console: https://console.firebase.google.com
2. Add project → name: **Virus VPN** → continue (Google Analytics off is fine).
3. Build → **Firestore Database** → Create database → Start in **production mode** → location: pick the nearest (e.g. asia-south1) → Enable.
4. Build → **Authentication** → Get started → Sign-in method → enable **Email/Password** → Save.
5. Authentication → Users → **Add user** → your admin email + a strong password. Only this account can add/edit/delete in the panel.

## 2. Web app config (for this panel)
1. Project settings (gear icon) → Your apps → **Web app** (`</>`) → nickname: Virus VPN Panel → Register app.
2. Copy the `firebaseConfig` object shown there.
3. Open `firebase-panel.html` and paste it into `FIREBASE_CONFIG` (replace the PASTE_YOUR_* values).
4. Open `firebase-panel.html` in a browser, login with the admin email/password from step 1.5, then add servers, networks and users.

## 3. Android app config (for the app — later step)
1. Project settings → Your apps → **Android app** → package name: `com.virusvpn.app` → Register.
2. Download `google-services.json` and send it to Muse. That file is what lets the app talk to this same Firebase project. Do NOT paste its contents into the panel — the panel uses the web config, the app uses google-services.json.

## 4. Firestore rules
Publish `firestore.rules` (in this folder) in Firebase Console → Firestore Database → Rules → Publish. Meaning:
- Anyone can **read** servers, networks and app settings (the app needs this to show them).
- Only a logged-in admin can **write** servers, networks, settings and users.
- User records can only be written by the admin, never by the app.

## 5. Firestore collections (created automatically when you add the first item)
- `servers`: name, country, countryName, tunnelType, connectFrom, customPayload, link, free, protocol
- `networks`: name, subtitle, mode, icon, bugHost, frontQuery, backQuery, payload
- `users`: username, expiry, premium, active, gbUsed
- `settings/app`: configVersion, announcement, telegramUrl

## 6. Free tier — honest note
Firebase Spark (free) plan covers a small app. If users/data grow past the free quota, Firebase starts charging by use. The VPN traffic server itself is never on Firebase.
