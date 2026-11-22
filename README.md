# BE

*Formerly Istiqamah (استقامة, steadfastness).* One place to see how your days are going: faith, body, and soon career and wealth. It pulls in data from the apps you already use.

## What it shows

- **Today:** your day as one timeline, ordered around today's prayer times, with Fitness-style rings for each area and a daily score.
- **Deen:** the five prayers (on time, jama'ah, late, missed), Monday and Thursday fasts, morning and evening adhkar, Quran memorisation and revision, and a **summary from the Qiyam alarm app** (alarm, tonight's last third, streak).
- **Body:** steps, sleep and resting heart rate from your **Fitbit** (through Health Connect), workouts with sets, reps and weights from **Lyfta**, weekly lifting volume, gym days and bodyweight.
- **Career** and **Wealth:** coming next.

It follows your phone's light or dark mode, with an Apple-style look and the Inter typeface.

## Connected apps

| App | How | What |
|---|---|---|
| Qiyam | Reads Qiyam's read-only summary on the phone (both apps share a signing key) | Alarm status, tonight's times, streak, nights woken |
| Fitbit Air | Health Connect (turn on sharing in the Google Health app) | Steps, sleep, resting heart rate, workouts, weight |
| Lyfta | Lyfta API with your personal key from my.lyfta.app/community/api | Workouts with exercises, sets, reps, weights, records |

Everything is read-only and stays on your phone.

## Install

Every push to `main` is tested and built by GitHub Actions. The APK and screenshots of every screen are published under **Releases**. Open the latest release on your phone and tap `BE.apk`.

The alarm itself lives in the separate [Qiyam](https://github.com/basemelshafei/qiyam-alarm) app.

The Inter typeface is used under the SIL Open Font License.
