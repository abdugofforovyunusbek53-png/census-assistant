# Census Assistant — 1-bosqich (diagnostika), v0.1

Bu bosqichda ilova **hech narsani bosmaydi va kiritmaydi**. U faqat Census ilovasi
ekranidagi elementlar (matn, id, tugma turi)ni o'qib, telefonda faylga yozadi.
Shu ma'lumot asosida 2-bosqich (avtomatik javob kiritish) aniq yoziladi.

Ilovada INTERNET ruxsati yo'q — tarmoqqa chiqa olmaydi. Hamma narsa telefonda qoladi.
Kiritish maydonlari (JSHSHIR, STIR va h.k.) matni dump'da maskalanadi (faqat uzunligi yoziladi).

## A. APK yig'ish (kompyuterda, bir marta)
1. Android Studio (Koala yoki yangiroq) o'rnating: https://developer.android.com/studio
2. ZIP'ni oching. Android Studio -> **Open** -> `CensusAssistant` papkasini tanlang.
3. Gradle sinxronizatsiyasi tugaguncha kuting (birinchi marta internet kerak, 5-15 daqiqa).
4. Menyu: **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
5. Tugagach "locate" bosing yoki `app/build/outputs/apk/debug/app-debug.apk` ni oching.
6. Xato chiqsa — Build oynasidagi matnni menga yuboring, tuzataman.

## B. Telefonga o'rnatish (Realme Note 50, Android 13)
1. `app-debug.apk` ni telefonga yuboring (kabel, Telegram, Drive).
2. Faylni oching -> "Noma'lum manbalardan o'rnatish"ga ruxsat bering -> O'rnatish.

## C. Maxsus imkoniyatlar (Accessibility) xizmatini yoqish
Android 13'da APK'dan o'rnatilgan ilova uchun bu tugma avval bloklangan bo'ladi:
1. Sozlamalar -> Ilovalar -> Census Assistant (yoki ilovadagi "Cheklangan sozlama" tugmasi).
2. O'ng yuqoridagi **uch nuqta** -> **Cheklangan sozlamalarga ruxsat berish** -> tasdiqlang.
3. Sozlamalar -> Maxsus imkoniyatlar -> O'rnatilgan xizmatlar -> **Census Assistant** -> Yoqish -> Ruxsat berish.

## D. Diagnostika
1. Census Assistant ilovasini oching — yuqorida "Xizmat: YOQILGAN" turishi kerak.
2. Census ilovasini bir marta oching, so'ng Census Assistant'ga qayting.
3. Ro'yxatdan Census ilovasining paketini bosing (tanlangan ilova yangilanadi).
4. Census ilovasiga o'ting. Ekran chetida ko'k **DIAG** tugmasi chiqadi.
5. So'rovnomaning **har bir savol ekranida** DIAG'ni bosing. "Saqlandi" xabari chiqadi.
   Hech narsani yubormang, faqat ekranlar bo'ylab yuring.
6. Census Assistant'ga qayting -> "Oxirgi dump'ni ko'rsatish" -> "Nusxalash" ->
   matnni menga yuboring. Har bir ekran uchun alohida dump yuboring va qaysi savol
   ekanini yozib qo'ying.

Agar DIAG tugmasi chiqmasa yoki dump bo'sh bo'lsa — "Xatolik logini ko'rsatish"ni yuboring.


## E. GitHub Actions orqali APK yig‘ish (Android Studio kerak emas)

Bu ZIP ichida GitHub Actions workflow mavjud: `.github/workflows/build-apk.yml`.

1. GitHub'da yangi repository yarating (masalan, `census-assistant`).
2. ZIP'ni oching va ichidagi `CensusAssistant` papkasining ichidagi fayl hamda papkalarni repository ildiziga yuklang. Muhim: `.github` yashirin papkasini ham yuklang.
3. Repository'da **Actions** bo‘limini oching.
4. Chap tomondan **Build APK** workflow'ini tanlang va **Run workflow** ni bosing.
5. Jarayon tugagach, bajarilgan run sahifasining pastidagi **Artifacts** bo‘limidan `census-assistant-apk` ni yuklab oling.
6. ZIP ichidagi `app-debug.apk` faylini telefonga o‘rnating.

Birinchi build internet orqali Gradle va Android vositalarini yuklaydi. Build muvaffaqiyatsiz bo‘lsa, Actions sahifasidagi qizil xato yozuvini yoki logning oxirgi qismini yuboring.
