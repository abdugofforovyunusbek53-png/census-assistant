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

## E. Android Studio o'rnatmasdan, internet orqali APK yig'ish (GitHub Actions, bepul)
1. https://github.com ga kiring (akkaunt bo'lmasa, bepul oching) -> **New repository** -> nom: `census-assistant` -> **Create**.
2. Repozitoriyada **uploading an existing file** havolasini bosing. ZIP'ni oching va
   `CensusAssistant` papkasining ICHIDAGI hamma narsani (app, gradle, .github, settings.gradle.kts va h.k.)
   sudrab tashlang -> **Commit changes**. `settings.gradle.kts` repozitoriyning eng yuqori qatorida turishi kerak.
   (`.github` papkasi ko'rinmasa: **Add file -> Create new file**, nomiga `.github/workflows/build.yml`
   deb yozing va shu ZIP'dagi `.github/workflows/build.yml` matnini joylang.)
3. **Actions** bo'limi -> chapdan **Build APK** -> **Run workflow**. 5-10 daqiqa kuting.
4. Yashil belgi chiqqach, ishga tushirilgan yozuvni oching -> pastda **Artifacts** -> `census-assistant-apk`
   ni yuklab oling (ZIP ichida `app-debug.apk`).
5. Qizil belgi chiqsa — o'sha sahifadagi xato matnini menga yuboring.

## F. AVTO rejimi (v0.3)
Yangilash: GitHub repozitoriyda `app/` papkasidagi fayllarni ZIP'dagilar bilan almashtiring (E bo'limi), Actions yangi APK yig'adi. Yangi APK'ni eskisining ustiga o'rnating; o'rnatilmasa, avval dump'larni kompyuterga ko'chirib oling.

Ishlatish:
1. Census Assistant'da Census ilovasi (uz.stat.census) tanlangan bo'lsin, **AVTO** tugmasini bosib yoqing.
2. Census ilovasida har bir sahifa (1/6 ... 6/6) ochilganda tepada panel chiqadi: nima kiritilishi va nimani o'zingiz kiritishingiz yozilgan.
3. **KIRITISH** bosilgandagina ilova variantlarni belgilaydi. Natija panelda (OK / ! belgilari) chiqadi. "!" bo'lgan joyni o'zingiz tekshiring.
4. **YOPISH** panelni yashiradi. **STOP** AVTO'ni darhol o'chiradi.

Ilova faqat shularni qiladi: ro'yxatdan tayyor variantni belgilash (RadioButton/CheckBox) va, agar sozlamada izoh yozgan bo'lsangiz, bo'sh izoh maydoniga matn yozish. Boshqa variant allaqachon tanlangan bo'lsa, o'zgartirmaydi, faqat "!" bilan xabar beradi.
Ilova HECH QACHON bosmaydi: Keyingisi, Orqaga, Tasdiqlash, Yakunlash, GPS nuqtasi, Rasmga olish. Raqamlar, telefon, F.I.Sh., STIR/JShShIR, faoliyat turi va "5.5 afzal usul" sening qo'lingda.

DIAG dump'larida endi 9 va undan uzun raqamlar (JShShIR) va GPS koordinatalari maskalanadi.

Bu versiya telefonda sinalmagan. Har bir sahifada avval KIRITISH natijasini ko'zdan kechiring.

## G. v0.4 yangiliklari
- **TASDIQLASH tugmasi** (2/6 va 3/6): "Avtomatik to'ldirildi" yozuvi bor maydonlarni avval o'zing ko'zdan kechirasan, keyin panelda bir marta TASDIQLASH bosasan; ilova shu sahifadagi "Tasdiqlash" tugmalarini bosib chiqadi. O'zi, sen bosmasdan, hech qachon tasdiqlamaydi.
- **4/6 xodimlar soni**: panelda "1 xodim, ayol 0" va "1 xodim, ayol 1" tugmalari. Respondentga mosini bos. Boshqacha bo'lsa (yollanma ishchi bor), qo'lda kirit. "Ayollar" soni "Jami"dan oshmasligi kerak.
- 5/6 xarajat va aylanma hamon qo'lda: har bir respondent uchun o'z baholaringni kiritasan.

## H. v0.5 tuzatishlar
- 1.5, 1.9, 1.4, 0.x, 2.x savollari: savol sarlavhasi ekrandan chiqib ketgan bo'lsa ham, matni noyob variant butun ekrandan qidiriladi. Pastga, topilmasa tepaga qarab ikki marta skan qilinadi.
- Tanlash/yozishdan keyin natija yangi ekran nusxasida qayta o'qib tekshiriladi; tasdiqlanmasa "OK" yozilmaydi.
- Mahalla/ko'cha tekshiruvi harf va raqamdan boshqa belgilarni (apostrof, nuqta, bo'shliq) hisobga olmaydi ("rifat", "ikarimov").
- TASDIQLASH: har savol kodi (1.1, 1.5, 2.1 ...) bir marta; bosilgach yozuv yo'qolganini tekshiradi; sabab logga yoziladi.
- 3.1: to'g'ri qiymat turgan bo'lsa, qayta yozilmaydi; sen tugma bosgan bo'lsang, boshqa qiymat almashtiriladi va yangi nusxada tekshiriladi.
- 5.5 uchun alohida tugma (6/6): respondent shunday degan bo'lsagina o'zing bosasan.
- Izoh matni sozlamada "yaxshi" bo'lib turadi (o'zgartirish mumkin).
