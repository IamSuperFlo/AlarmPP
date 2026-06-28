# Photo Alarm 📸⏰

O aplicație de alarmă pentru Android care **nu se oprește până nu faci o poză la un obiect cerut**
(ex. „fă poză la o cană"). Recunoașterea obiectului se face cu un model AI care rulează
**direct pe telefon, offline** — gratuit, fără cont, fără API key, fără limite.

## Cum funcționează

1. Setezi ora alarmei în aplicație.
2. Când sună, ecranul se aprinde (chiar și blocat), pornește sunetul tare + vibrația.
3. Aplicația îți cere un obiect ales aleatoriu (cană, banană, sticlă, telefon, telecomandă,
   pantof, rucsac, ochelari, pernă, tastatură, ceas, laptop).
4. Faci poză la acel obiect. Modelul TFLite (TensorFlow Lite / MobileNet) verifică pe loc.
5. Doar dacă obiectul e corect, alarma se oprește. Butonul „înapoi" e blocat.

---

## Cum obții APK-ul (fără să instalezi nimic pe PC) — GitHub Actions

Sandbox-ul în care a fost creat proiectul nu poate compila APK-uri, dar GitHub o face gratuit.

1. Fă-ți cont gratuit pe https://github.com (dacă nu ai).
2. Apasă **New repository** → dă-i un nume (ex. `photo-alarm`) → **Create repository**.
3. Pe pagina repo-ului gol, apasă **uploading an existing file** și
   **trage tot conținutul folderului `PhotoAlarm`** (inclusiv folderele `app` și `.github`).
   - Important: încarcă *conținutul* folderului PhotoAlarm, nu folderul în sine, astfel încât
     `app/` și `.github/` să fie în rădăcina repo-ului.
4. Apasă **Commit changes**. Build-ul pornește automat.
5. Mergi la tab-ul **Actions** → deschide rularea cea mai recentă „Build APK" → așteaptă bila verde
   (~3-5 min).
6. În josul paginii rulării, la secțiunea **Artifacts**, descarcă **photo-alarm-apk**.
   Dezarhivează → vei avea `app-debug.apk`.

### Instalează pe telefon
1. Trimite-ți `app-debug.apk` pe telefon (mail, Drive, cablu).
2. Deschide-l. Android va cere să permiți „instalarea din surse necunoscute" → permite.
3. Instalează și deschide aplicația.

---

## Alternativă: Android Studio (dacă preferi pe PC)

1. Instalează **Android Studio** (gratuit, are nevoie de internet).
2. **Open** → alege folderul `PhotoAlarm`. Lasă-l să sincronizeze Gradle (descarcă singur tot).
3. Conectează telefonul (cu „depanare USB" activată) sau pornește un emulator.
4. Apasă **Run ▶**. Aplicația se instalează direct.
   - APK-ul rezultat e în `app/build/outputs/apk/debug/app-debug.apk`.

---

## Permisiuni de activat după instalare (important!)

Ca alarma să funcționeze sigur, activează în setările aplicației:
- **Cameră** — cerută automat la prima pornire.
- **Notificări** — cerută automat (Android 13+).
- **Alarme și mementouri** — aplicația te trimite la setare când apeși „Setează alarma".
- **Notificări pe tot ecranul** (Android 14+) — ca alarma să apară peste ecranul blocat.
- **Dezactivează optimizarea bateriei** pentru aplicație (Setări → Baterie → Nerestricționat),
  altfel sistemul ar putea întârzia alarma.

Folosește butonul **„Testează acum"** ca să verifici tot fluxul fără să aștepți ora setată.

---

## Despre partea de AI (răspuns la întrebarea „se poate fără limite?")

Da. Folosim **TensorFlow Lite** cu modelul **MobileNet v1**, împachetat în aplicație.
- Rulează **local, pe telefon**, fără internet.
- **Gratuit, nelimitat**, fără cont și fără cheie API.
- Recunoaște ~1000 de obiecte uzuale; folosim un subset bine recunoscut (vezi `ObjectClassifier.kt`).

Modelul (`model.tflite`) se descarcă automat la build prin `app/download_model.gradle`.

### Vrei să fie mai greu de păcălit?
Poți crește pragul de încredere în `ObjectClassifier.kt` (`setScoreThreshold`) sau reduce lista
de obiecte la cele din camera ta.

---

## Limitări oneste
- Butonul **Home** nu poate fi blocat complet de o aplicație obișnuită (restricție Android).
  Alarma rămâne însă pornită în fundal și revine.
- Pe unele telefoane (Xiaomi, Huawei, Samsung agresiv cu bateria) trebuie să permiți manual
  „autostart"/„rulare în fundal" pentru fiabilitate maximă.
- Modelul recunoaște obiecte generice; poate confunda obiecte similare. Lumină bună = recunoaștere
  mai bună.

## Structură proiect
- `app/src/main/java/com/example/photoalarm/`
  - `MainActivity.kt` — setarea alarmei + permisiuni
  - `AlarmScheduler.kt` — programarea exactă (AlarmManager)
  - `AlarmReceiver.kt` — pornește alarma la ora setată
  - `AlarmService.kt` — sunet + vibrație + notificare pe tot ecranul
  - `AlarmActivity.kt` — ecranul alarmei + camera + blocare „înapoi"
  - `ObjectClassifier.kt` — modelul AI (TFLite) + lista de obiecte
  - `BootReceiver.kt` — re-programare după repornirea telefonului
