# O'zgarishlar (Anketa Yordamchi 1.0.0 -> 1.1.0)

`applicationId` o'zgarmadi (`uz.anketayordamchi.app`): 1.0.0 ustiga yangilanadi, sozlamalar saqlanadi. `versionCode` 2.

| # | So'rov | Nima qilindi |
|---|---|---|
| 1 | Mahalla ro'yxatdan topilmadi, "fat" deb qidirsa chiqadi | Yangi sozlama **Mahalla QIDIRUV so'zi** (standart: `fat`). Ro'yxat qidiruviga birinchi shu yoziladi, so'ng natijalar ichidan mahalla nomi (`Ma'rifat`) aynan mos kelganini tanlaydi. Nom bo'sh bo'lsa va natija bitta bo'lsa, o'shani tanlaydi. Ro'yxatni ochish uchun 4 xil usul ketma-ket sinaladi (bosiladigan element -> matn maydoni -> maydon qutisining YUQORI qismiga bosish -> boshqa). Yozish `set` -> `fokus+set` -> `paste` tartibida, maydonda so'z ko'ringunicha. Topilmasa, xabarda ro'yxatda ko'ringan nomlar chiqadi. Ko'cha uchun ham ixtiyoriy qidiruv so'zi bor. |
| 2 | Uy raqami tartib bilan o'sib borsin | Yangi sozlama **Keyingi uy raqami**. 1/6 sahifada bo'sh `0.3 Uy raqami` maydoniga shu raqam yoziladi va hisoblagich +1 bo'ladi. To'lgan maydonga tegilmaydi (qayta KIRITISH bosilsa, ikki marta oshmaydi). To'liq panelda `Uy raqami -1 / +1` tugmalari bor. Sozlama bo'sh bo'lsa, uy raqamiga tegilmaydi. |
| 3 | 1.9 da "Yo'q, foydalanmadi" belgilansin | Variantlar ketma-ket sinaladi: `yo'q, foydalanmadi`, `yo'q, foydalanmaydi`, `foydalanmadi`, `foydalanmaydi`, eski `yo'q, veb-sayt...`, oxirida `yo'q`. Mos kelmasa, ko'ringan variantlar xatoda chiqadi. |
| 4 | JShShIR qidirgandan keyingi 4 ta oyna so'ramasdan tasdiqlansin | **Avto-tasdiq** (sozlamada yoqish/o'chirish tugmasi, standart YOQILGAN). (a) 2/6, 3/6, 6/6 da "Avtomatik to'ldirildi" + "Tasdiqlash" paydo bo'lishi bilan o'zi bosadi (ekranni aylantirmasdan, har birini bir martadan). (b) 2/6 va 3/6 dan keyin chiqqan alohida oyna/dialogda "Tasdiqlash / Ha / OK" tugmasini bosadi. Oynada yakunlash/chiqish/o'chirish/bekor/saqlash so'zlari bo'lsa tegmaydi. "Keyingisi", "Orqaga", "Yakunlash" tugmalariga hamon hech qachon tegilmaydi. Oyna tugmalari topilmasa, log'ga tugmalar ro'yxati yoziladi. |
| 5 | Panel ekranni yopib qo'yadi, matn kiritish qiyin | Panel endi odatda **kichik (mini)**: 3 ta tugma (sahifa belgisi, ▶ KIRITISH, ■ STOP), surib yuriladi (belgi tugmasini ushlab sudrang), joyi eslab qolinadi. Belgini bosish - to'liq panelni ochadi (natijalar), "KICHRAYTIRISH" - yopadi. Natija chiqqanda to'liq panel o'zi ochiladi. **Klaviatura ochilganda hamma panel va DIAG tugmasi yashiriladi**, yopilganda qaytadi. To'liq panel matni 28% balandlikda skroll qilinadi. |

## Boshqa
- 2/6, 3/6, 6/6 sahifalarda yangi sahifaga o'tganda "bosilgan Tasdiqlash" xotirasi tozalanadi (eski versiyada keyingi anketada ham qayta bosilmay qolishi mumkin edi).
- `accessibility_service_config.xml`: `typeWindowsChanged` qo'shildi (klaviatura ochilishini bilish uchun).

## Tekshiruv holati (muhim)
Bu sandbox'da Kotlin kompilyatori va Android SDK yo'q edi: kod **kompilyatsiya qilinmagan** va `verify/` sinovlari qayta **ishga tushirilmagan** (eski `NATIJA.txt` o'chirildi). Qavslar muvozanati va mantiq qo'lda tekshirilgan. GitHub Actions build qizil chiqsa, xato matnini yuboring.
Qurilmada sinash kerak: mahalla ro'yxati ochilishi va "fat" yozilgani, alohida tasdiq oynalarining haqiqiy ko'rinishi (menda dump yo'q), uy raqami maydoniga yozish.

---

# Oldingi o'zgarishlar (CensusAssistant v0.4 -> Anketa Yordamchi 1.0.0)

Yangi nom va yangi `applicationId` (`uz.anketayordamchi.app`): eskisining ustiga yozilmaydi.
Barcha DIAG imkoniyatlari saqlandi (DIAG tugmasi, dump'lar, nusxalash, log) va kengaytirildi.

## Fayllar

| Fayl | Nima o'zgardi |
|---|---|
| `Text.kt` (yangi) | `fz()` yumshoq kalit: apostrof (barcha turlari), tutuq belgisi, nuqta, tire (U+2011 ham), bo'shliq, harf registri e'tiborga olinmaydi. `nameMatch()` mahalla/ko'cha nomini so'zlar bo'yicha solishtiradi ("m.f.y. Ma'rifat", "I.Karimov", "Karimov I." aniq mos; "Ma'rifat-2" faqat o'xshash). `searchQueries()` qidiruv variantlari: yozilgani, `’`/`ʻ`/apostrofsiz, `rifat`, `Karimov`. |
| `Ui.kt` (yangi) | Android'dan mustaqil ekran daraxti va `UiDriver` interfeysi. Variant matni: o'zining text/desc'i, bo'lmasa ota-element ichidagi matn, bo'lmasa yonidagi matn. Variant holati: `checked`, `stateDescription`, ota `checked`, `selected`. Blok navigatsiya tugmasida (Keyingisi/Orqaga/Yakunlash) tugaydi. |
| `Rules.kt` | Yangi qoidalar: `Select` (mahalla/ko'cha), `SubField` (3.1), `Status` (2.1). 5.5 qoidasi qo'shildi. Izoh standarti `yaxshi`. Sozlamalar kodga qattiq yozilmagan. |
| `Engine.kt` (yangi) | Barcha amallar: har bir amaldan keyin ekran qayta o'qiladi, holat tekshiriladi, bitta tugma bir ishga tushirishda bir marta bosiladi. Barcha bosishlar `press()` orqali (taqiqlangan tugmalar ro'yxati). |
| `AccDriver.kt` (yangi) | Haqiqiy ekran drayveri: Census'ning barcha oynalari (ochiq ro'yxat/dialog ham), ACTION_CLICK, zaxira koordinata (o'z panelimiz ustiga tushmaydi), SET_TEXT, paste zaxirasi, SHOW_ON_SCREEN, skroll. |
| `DiagService.kt` | Panel yangi dvigatelga ulandi; TASHXIS tugmasi; dump'ga `state`, `selected`, `visible`, `scrollable` va barcha oynalar qo'shildi; natijalar logga sahifa/savol/sabab bilan yoziladi. |
| `MainActivity.kt` | Izoh, mahalla, ko'cha sozlamalari; log/dump tozalash. |
| `AppLog.kt` | Hajm chegarasi (400 KB, aylanma), maskalash, o'qish/tozalash. |
| `accessibility_service_config.xml` | `flagRetrieveInteractiveWindows` (ochiq ro'yxat oynasini ko'rish), `canPerformGestures` (zaxira bosish). |
| `build.gradle.kts`, `settings.gradle.kts`, `AndroidManifest.xml`, `strings.xml`, `build.yml` | Yangi nom/ID; INTERNET ruxsati hamon yo'q. |
| `verify/` (yangi) | Soxta ekranlarda 123 ta avtomatik tekshiruv (`NATIJA.txt`). |

## Xatolar va tuzatishlar

| # | Xato (eski kodda sabab) | Tuzatish |
|---|---|---|
| 1 | Mahalla faqat "ko'rinyaptimi" deb tekshirilgan, tanlanmagan | Ro'yxatni ochadi, qidiradi (variantlar bilan), aniq mos natijani tanlaydi, maydonda ko'rinishini tekshiradi |
| 2 | Ko'cha xuddi shunday | Xuddi shunday; faqat aniq mos nom tanlanadi; "Islom Karimov", "A.Karimov", "I.Karimov-2" tanlanmaydi |
| 3 | 1.5: holat faqat `isChecked`dan o'qilgan; yarim ko'ringan blokda "tanlanmagan" deb erta xulosa | Ko'p belgili holat aniqlash; blok to'liq ko'rinmaguncha kutadi; ishonchsiz bo'lsa "OK" demaydi |
| 4 | 1.9: matn faqat o'z `desc`idan; U+2011 chiziqcha; ekrandan pastda | Yumshoq moslash; matn ota/yon elementdan; skroll; bosgandan keyin qayta o'qib tasdiqlash |
| 5 | Tasdiqlash: faqat `Button` sinfi; takror bosish (koordinata kalit, skrolldan keyin o'zgaradi); "topilmadi" xato sifatida | Savol kodi bo'yicha bir marta; natija tekshiriladi; bosilgan qayta bosilmaydi; kerak bo'lmagan sahifada xato emas |
| 6 | 2.1: tasdiq holati hisobga olinmagan | `Status`: bo'sh / to'ldirilgan-tasdiqlanmagan / tasdiqlangan; to'xtatmaydi |
| 7 | 3.1: to'g'ri qiymat ham "bo'sh emas - tegilmadi"; qayta yozish; yorliq aniq tengligi | To'g'ri qiymatga tegmaydi (OK); bo'sh bo'lsa bir marta yozadi; noto'g'ri bo'lsa faqat foydalanuvchi tugmasi bilan; maydon geometriya bo'yicha; yozuvdan keyin ekran qayta o'qiladi |
| 8 | 5.5 uchun qoida umuman yo'q edi | Qo'shildi (`Yuzma-yuz intervyu`), tekshiruv bilan |
| 9 | Izoh standarti bo'sh, shuning uchun yozilmagan | Standart `yaxshi`; maydon geometriya/sarlavha bo'yicha; set -> fokus+set -> paste; qayta o'qib tekshiradi |
| 10 | Ishlayotganlar (0.0, 0.1, 0.2, 0.4, 1.4, 1.5d, 2.4, 2.6, 5.4c, 5.5b) | Mantiq saqlandi (avvalgi moslash birinchi, yangi zaxiralar keyin); sinovlarda tekshirilgan |
| 11 | Oxirgi ko'rinayotgan savol bloki daraxt oxirigacha cho'zilib, "Keyingisi" bosilishi mumkin edi (yangi kodda topilib, tuzatildi) | Blok navigatsiya tugmasida tugaydi; `press()` taqiqlangan tugmalarni rad etadi; sinov bor |

## Qurilmada sinash KERAK bo'lganlar (men ko'ra olmaganlarim)
Quyidagilar soxta ekranda ishlaydi, lekin Census'ning haqiqiy daraxti menga noma'lum:
1. **Mahalla/ko'cha ro'yxati**: qidiruv maydoni va natija elementlari qanday ko'rinishi. Eng xavfli joy. Ishlamasa, log'da ro'yxatdagi matnlar chiqadi.
2. **1.5 va 1.9** holat belgilari: ishlamasa `!` va "TASHXIS bosing" chiqadi, "OK" chiqmaydi.
3. **"Tasdiqlash"** tugmasining joylashuvi va bosgandan keyin ilova holati.
4. Census'ning ichki holati SET_TEXT bilan kiritilgan qiymatni qabul qilishi (3.1, izoh). Ekranda ko'rinishi tekshiriladi, ilova ichki holatini men bilmayman. "Keyingisi" xato bersa, bu shu bo'lishi mumkin.
5. Koordinata bilan zaxira bosish (gesture), Android 13 cheklangan sozlamalar oqimi, GitHub Actions build.
