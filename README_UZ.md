# Anketa Yordamchi 1.1.0

> 1.1.0 da nima o'zgargani: `CHANGES_UZ.md` (mahalla "fat" qidiruvi, uy raqami hisoblagichi, 1.9, avto-tasdiq, kichik panel).

Census (uz.stat.census) ilovasida variantlarni tanlaydi, matn kiritadi va tekshiradi.
Eski "Census Assistant" (uz.censusassistant) dan **butunlay alohida ilova**: boshqa nom, boshqa paket (`uz.anketayordamchi.app`), boshqa sozlamalar. Ikkalasi yonma-yon turadi, hech biri ikkinchisini yangilamaydi.

## Xavfsizlik
- INTERNET ruxsati yo'q: ilova tarmoqqa chiqa olmaydi. Hamma narsa telefonda (`Android/data/uz.anketayordamchi.app/files`).
- GPS va KAMERA ruxsati yo'q, ilova ularga tegmaydi. GPS nuqtasi, rasm, JShShIR/STIR, telefon, faoliyat turi/tavsifi, 2.3c, summalar: faqat o'zingiz.
- "Keyingisi", "Orqaga", "Yakunlash", "Rasmga olish", "GPS nuqtasi" tugmalariga hech qachon tegilmaydi (kodda ikki qavat to'siq). Yakuniy topshirish faqat sizniki.
- Faqat siz panelda tugma bosganingizda ishlaydi. **STOP** istalgan paytda to'xtatadi.
- JShShIR, telefon, manzil kodda yo'q. Logda va dump'larda 9+ raqamlar va koordinatalar maskalanadi.

## 1. APK yig'ish (kompyuterga hech narsa o'rnatmasdan)
1. github.com da bepul akkaunt, yangi **private** repository (masalan `anketa-yordamchi`).
2. ZIP'ni oching. `AnketaYordamchi` papkasi ICHIDAGI hamma narsani (yashirin `.github` papkasi bilan) repository'ga yuklang. `settings.gradle.kts` repository'ning eng yuqori qatorida turishi kerak.
   `.github` ko'rinmasa: **Add file -> Create new file**, nomiga `.github/workflows/build.yml` yozib, shu ZIP'dagi fayl matnini joylang.
3. **Actions -> Build APK -> Run workflow**. 5-10 daqiqa kuting.
4. Yashil belgidan keyin yozuvni oching -> pastda **Artifacts** -> `anketa-yordamchi-apk` -> yuklab oling (ichida `app-debug.apk`).
5. Qizil belgi chiqsa: o'sha sahifadagi xato matnini yuboring.

Android Studio bor bo'lsa: papkani oching -> Build -> Build APK(s).

## 2. O'rnatish (Realme Note 50, Android 13)
1. `app-debug.apk` ni telefonga yuboring va oching.
2. "Noma'lum manbalardan o'rnatish"ga ruxsat bering -> **O'rnatish**.

## 3. Accessibility xizmatini yoqish (Android 13)
APK'dan o'rnatilgan ilova uchun tugma avval bloklangan bo'ladi:
1. **Sozlamalar -> Ilovalar -> Anketa Yordamchi** (yoki ilovadagi "Cheklangan sozlama" tugmasi).
2. O'ng yuqoridagi **uch nuqta -> Cheklangan sozlamalarga ruxsat berish** -> tasdiqlang.
3. **Sozlamalar -> Maxsus imkoniyatlar (Qo'shimcha sozlamalar) -> O'rnatilgan xizmatlar -> Anketa Yordamchi -> Yoqish -> Ruxsat berish**.
4. Ilovani oching: yuqorida "Xizmat: YOQILGAN" turishi kerak.

## 4. Birinchi sozlash
1. Census'ni bir marta oching, so'ng Anketa Yordamchi'ga qayting.
2. Ro'yxatdan **uz.stat.census** ni bosing (tanlangan ilova yangilanadi).
3. Sozlamalar: izoh (`yaxshi`), mahalla (`Ma'rifat`), mahalla qidiruv so'zi (`fat`), ko'cha (`I. Karimov`), keyingi uy raqami (masalan `1`), Avto-tasdiq tugmasi. O'zgartirsangiz **Sozlamalarni saqlash**. Bo'sh qoldirilgan maydon uchun tegilmaydi.
4. **AVTO** tugmasini bosib yoqing.

## 5. Ishlatish
Census'da har bir sahifa (1/6 ... 6/6) ochilganda ekran chetida kichik panel (sahifa belgisi, ▶, ■) turadi; belgini bossangiz to'liq panel ochiladi. Klaviatura ochilganda panel o'zi yashiriladi.
- **KIRITISH**: shu sahifadagi javoblarni belgilaydi, har birini ekranni qayta o'qib tekshiradi. Natija: `OK` yoki `!` (sababi bilan).
- **TASDIQLASH** (2/6, 3/6, 6/6): "Avtomatik to'ldirildi" yozuvi va "Tasdiqlash" tugmasi bor maydonlarni siz ko'zdan kechirgach, bir martadan tasdiqlaydi. Tasdiqlangan maydon qayta bosilmaydi.
- **1 xodim, ayol 0 / 1** (4/6): Jami=1 va Ayollar qiymatini yozadi va tekshiradi. Qayta bossangiz, to'g'ri qiymatga tegmaydi.
- **TASHXIS**: hech narsa o'zgartirmaydi. Ekrandagi savollar, variantlar va ularning holatini `dump_trace_...` faylga yozadi.
- **DIAG** (ko'k tugma): xom dump.
- **STOP**: darhol to'xtatadi va AVTO'ni o'chiradi.

Sahifa bo'yicha nima kiritiladi:
| Sahifa | Avtomatik | Qo'lda (siz) |
|---|---|---|
| 1/6 | 0.0 Boshqa joy, 0.1 Bino, 0.2 Umumiy kirish, 0.3 Mahalla va Ko'cha (qidirib, to'g'riligini tekshirib), 0.4 Ha, ochiq | GPS, rasm, 0.3 uy raqami |
| 2/6 | 1.4, 1.5 (tekshiriladi), 1.5d Naqd, 1.9 Yo'q (veb-sayt yo'q) | JShShIR, 1.10 telefon, TASDIQLASH |
| 3/6 | 2.1 holati (faqat ko'rsatiladi), 2.4, 2.6 | 2.3a, 2.3d, 2.3c, TASDIQLASH |
| 4/6 | tugma bosilganda Jami=1, Ayollar=0/1 | |
| 5/6 | | 4.1, 4.2 summalar |
| 6/6 | 5.4c Ha, 5.5 Yuzma-yuz, 5.5b (tekshiriladi), 5.5d Izoh | 5.5a, 5.5c, Yakunlash |

## 6. Xato bo'lsa
Ilovada **Xatolik/AVTO logini ko'rsatish** -> nusxalab yuboring. Har bir qatorda sahifa, savol, holat va sabab bor. "Variant topilmadi" yoki "holat ko'rinmadi" chiqsa: o'sha sahifada **TASHXIS** bosing, so'ng ilovada "Oxirgi dump'ni ko'rsatish" -> nusxalab yuboring.

## 7. Qo'shimcha eslatma
Zaxira usul (matn kiritish ishlamasa) vaqtincha telefon buferiga yozadi (faqat izoh/son kabi shaxsiy bo'lmagan matn). Shundan keyin bufer tarkibi shu matn bo'ladi.
