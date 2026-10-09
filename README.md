## এই ধাপে যা আছে (ধাপ ০ থেকে ৭ পর্যন্ত — সম্পূর্ণ)

**ধাপ ০ + ১ — স্কেলটন ও হোম ড্যাশবোর্ড:**
- সম্পূর্ণ Gradle প্রজেক্ট স্ট্রাকচার (Kotlin DSL)
- Compose থিম — ওয়েব CSS থেকে হুবহু তোলা রং (`ui/theme/Color.kt`)
- হোম ড্যাশবোর্ড স্ক্রিন: টপবার, KPI স্ট্রিপ, ৬টা হোম কার্ড গ্রিড

**ধাপ ২ — Full-screen List View + Detail View + Firestore CRUD:**
- `Entry` মডেল ওয়েব অ্যাপের আসল ফর্ম ফিল্ড অনুযায়ী পূর্ণাঙ্গ করা হয়েছে
- **FvListScreen** — সার্চ, লিস্ট, লং-প্রেস এডিট/ডিলিট, এন্ট্রি ফর্ম
- **DetailScreen** — Excel-স্টাইল লেনদেন টেবিল, নতুন বকেয়া/জমা ইনলাইন ফর্ম

**ধাপ ৩ — Debtors List (Party Ledger):**
- **DebtorsListScreen** — ক্যাটাগরি ফিল্টার ট্যাব, সার্চ, Excel টেবিল, কল/জমা বাটন
- ফুটারে "মোট পাওনা"/"মোট বকেয়া" আলাদা যোগফল

**ধাপ ৪ — Aggregate Report / Net Profit Breakdown / P&L Trends / Fund Flow:**
- `WeeklyReport` মডেল, imran_store/backup ডকুমেন্টের chart_data ফিল্ডে সংরক্ষিত
- **AggregateReportOverlay**, **NetProfitBreakdownOverlay**, **PLTrendsOverlay**, **FundFlowCard**

**ধাপ ৫ — Side Menu, নোটিফিকেশন প্যানেল, DB Connect Modal:**
- `Notification` মডেল — NotificationCenter (ইন-মেমরি, প্রসেস-স্কোপড, Firestore-এ sync হয় না, ওয়েব অ্যাপের localStorage
  `app_notifications`/`notifPush()` এর বহু-ডিভাইস sync সংস্করণ)
- **NotificationPanel** — success/error/info আইকন, "কত সময় আগে" টেক্সট, "সব মুছো" বাটন,
  টপবারে unread badge
- **SideMenu** — মূল ওয়েব অ্যাপে side-menu-body খালি ছিল (ভবিষ্যতের জন্য রাখা); এখানে কাঠামো
  অভিন্ন রেখে ব্যবহারিক শর্টকাট (হোম, Party Ledger, ডাটাবেইজ সংযোগ) যোগ করা হয়েছে
- **DbConnectModal** — সংযোগ স্ট্যাটাস বার (connected/connecting/error ডট), "সংযোগ পুনরায় যাচাই",
  এবং ঝুঁকিপূর্ণ "ক্লাউড ডেটা মুছে ফেলো" অ্যাকশন (কনফার্মেশন ডায়ালগসহ)
  - **গুরুত্বপূর্ণ পার্থক্য:** ওয়েব অ্যাপ localStorage ↔ Firestore push/pull সিঙ্ক করত (দুটো আলাদা
    ডেটা স্টোর), কিন্তু এই Android অ্যাপ শুরু থেকেই সরাসরি Firestore-নেটিভ (Firestore-ই একমাত্র
    সোর্স অব ট্রুথ, কোনো লোকাল কপি নেই) — তাই dbPush()/dbPull() বাটনের বদলে সংযোগ-যাচাই ও
    wipe অ্যাকশন রাখা হয়েছে, যা এই আর্কিটেকচারে অর্থবহ।

**ধাপ ৬ — পাইকারি ক্যালকুলেটর:**
- `PaikkariCalculator.kt` — ওয়েব অ্যাপের `calculate()`/`weeklyFixed()` ফাংশনের হুবহু Kotlin
  পুনর্গঠন: COGS/Gross (গজ×লাভ পদ্ধতি), Opex, স্থায়ী খরচ (বাৎসরিক/মাসিক→সাপ্তাহিক রূপান্তর),
  যাকাত (২.৫%/৫২ সপ্তাহ), নিট মুনাফা, Cash Profit/Loss, ক্যাশফ্লো, মার্জিন শতাংশ — সব একই সূত্রে
- `WeekLabel.kt` — `getWeekLabel()`/`getWeekNumber()` এর সমতুল্য (বাংলা মাস নাম + ISO সপ্তাহ সংখ্যা)
- ৩-ধাপের ইনপুট ফর্ম (**PkStep1Fixed**, **PkStep2Stock**, **PkStep3Sale**) — মোবাইলে একটানা
  স্ক্রলযোগ্য ফর্মে (ওয়েব অ্যাপের ট্যাব-নেভিগেশনের বদলে, মোবাইল UX-এর জন্য বেশি স্বাভাবিক),
  প্রতিটা ধাপে ইনলাইন স্বয়ংক্রিয় হিসাব (সাপ্তাহিক স্থায়ী খরচ, আনুমানিক লাভ, মোট বিক্রি)
- ইনপুট ভ্যালিডেশন (`validatePaikkariInput`) — বিক্রি/স্টক শূন্য থাকলে বা অসঙ্গতি থাকলে
  নির্দিষ্ট ধাপে নির্দেশনাসহ এরর বার্তা
- **PaikkariResultPage** — নিট প্রফিট/লোকসান ব্যানার, তিনটা মার্জিন স্ট্যাট (গ্রস/নিট/নগদ),
  সম্পূর্ণ লাইন-বাই-লাইন ব্রেকডাউন (P&L + ক্যাশফ্লো + বিক্রয় বিশ্লেষণ)
- হিসাব সম্পন্ন হওয়ার সাথে সাথে imran_store/backup এর chart_data ফিল্ডে স্বয়ংক্রিয়ভাবে সেভ হয় (`saveToChart()` এর
  সমতুল্য) — একই বছর+সপ্তাহে থাকলে ওভাররাইট করে, নাহলে নতুন এন্ট্রি তৈরি করে; সফল/ব্যর্থ উভয়
  ক্ষেত্রে নোটিফিকেশন পুশ হয়
- হোম ড্যাশবোর্ডে "📊 সাপ্তাহিক হিসাব আপডেট করুন" বাটন (ওয়েব অ্যাপের `weeklyUpdateBtn`) থেকে
  এই ক্যালকুলেটরে প্রবেশ করা যায়

**ধাপ ৭ — Fabric (কাপড় ক্রয়) মডিউল:**
- `FabricGroup`/`FabricPurchase` মডেল, imran_store/backup ডকুমেন্টের fabricPurchaseData_v2 ফিল্ডে সংরক্ষিত (ওয়েব অ্যাপের
  `fabricPurchaseData_v2` এর সমতুল্য — একটা কাপড়ের নাম/গ্রুপ, ভেতরে একাধিক purchase)
- **FabricScreen** — হেডার (হোম কার্ডে "X টি | ৳Y" সামারি), নতুন এন্ট্রি টগল ফর্ম, সার্চ, গ্রুপ কার্ড লিস্ট
- **FabricNewPurchaseForm** — নাম+পরিমাণ+একক+দর, লাইভ "মোট: ৳X" প্রিভিউ (`fabricCalcPreview()`
  এর সমতুল্য); একই নামের কাপড় থাকলে স্বয়ংক্রিয়ভাবে বিদ্যমান গ্রুপে যোগ হয়, নাহলে নতুন গ্রুপ তৈরি হয়
- **FabricGroupCard** — প্রতিটা purchase রো (তারিখ, পরিমাণ×দর, মোট, ডিলিট বাটন), একাধিক
  purchase থাকলে "গড় দর" ব্যাজ (DCA/Dollar-Cost-Averaging পদ্ধতি: মোট টাকা÷মোট পরিমাণ),
  এবং "+ আরো কিনেছি" ইনলাইন ফর্ম যা বিদ্যমান গ্রুপে নতুন purchase যোগ করে
  (`fabricDcaSave()` এর সমতুল্য)
- একটা purchase মুছে ফেললে, গ্রুপে আর কিছু না থাকলে পুরো গ্রুপও মুছে যায়
  (`fabricDeletePurchase()` এর সমতুল্য)
- হোম ড্যাশবোর্ডে "কাপড় ক্রয়" কার্ড (ওয়েব অ্যাপের Card 7 / `fabricOpen()`) থেকে প্রবেশ করা যায়

**সব ৭টা ধাপ শেষ — সম্পূর্ণ Android অ্যাপ:**
হোম ড্যাশবোর্ড → এন্ট্রি লিস্ট/ডিটেইল → Party Ledger → রিপোর্ট ওভারলে → সাইড মেনু/নোটিফিকেশন/
DB Connect → পাইকারি ক্যালকুলেটর → Fabric — সব মিলিয়ে মূল ওয়েব অ্যাপের প্রতিটা স্ক্রিন ও
ব্যবসায়িক যুক্তি Kotlin + Jetpack Compose + Firestore-এ পুনর্গঠিত হয়েছে।

## Android Studio-তে চালানোর আগে যা করতে হবে

1. **Firebase সংযোগ**: Firebase Console থেকে আপনার প্রজেক্টের
   `google-services.json` ফাইল ডাউনলোড করে `app/` ফোল্ডারে রাখুন
   (এই ফাইলটা এখানে নেই — এতে সিক্রেট কী থাকে, তাই আমি বানাইনি)।
   প্যাকেজ নাম মিলাতে হবে: `com.imran.clothstore`
   (অথবা Firebase Console-এ গিয়ে এই নামে একটা নতুন Android অ্যাপ যোগ করুন)।

2. **Firestore ডেটা স্ট্রাকচার**: রিপোজিটরি ওয়েব অ্যাপের সাথে হুবহু মিলিয়ে **একটামাত্র
   ডকুমেন্ট** ব্যবহার করে —
   ```
   imran_store/backup
   ```
   এই একটা ডকুমেন্টেই সব ডেটা থাকে: `is_c1_list`..`is_c4_list` (৪টা এন্ট্রি ক্যাটাগরি),
   `fabricPurchaseData_v2`, `chart_data`, `profitStripData_v1`, `is_deleted_ids`।
   বিস্তারিত `MIGRATION_NOTES.md`-তে ও `data/backup/BackupPayload.kt`-এর কমেন্টে আছে।
   আপনার Firebase প্রজেক্টে ওয়েব অ্যাপ থেকে ইতিমধ্যে ডেটা থাকলে, এই Android অ্যাপ সেই
   একই ডেটা সরাসরি পড়তে/লিখতে পারবে — কোনো আলাদা migration লাগবে না।

3. **Gradle Sync**: Android Studio-তে প্রজেক্ট খুলে "Sync Now" করুন।

4. **ফন্ট (ঐচ্ছিক)**: ওয়েব অ্যাপ Noto Sans Bengali ব্যবহার করেছে।
   হুবহু মিলাতে চাইলে `.ttf` ফাইল `app/src/main/res/font/` এ রেখে
   `ui/theme/Type.kt`-তে `FontFamily.Default` এর জায়গায় বসান।

## সম্পন্ন — এরপর কী

মূল ওয়েব অ্যাপের সব স্ক্রিন ও ব্যবসায়িক যুক্তি এখন Kotlin প্রজেক্টে আছে। এখান থেকে বাস্তবসম্মত পরের পদক্ষেপ:

1. Android Studio-তে খুলে Gradle Sync করে **প্রথম compile error গুলো ঠিক করা** — এই এনভায়রনমেন্টে
   Android SDK না থাকায় আমি কম্পাইল করে দেখতে পারিনি, তাই ছোটখাটো টাইপ/ইম্পোর্ট মিসম্যাচ থাকতে পারে।
2. আপনার Firestore প্রজেক্টে `google-services.json` যোগ করে প্রকৃত ডেটা দিয়ে পরীক্ষা করা।
3. UI পালিশ — ছবি আপলোড (Entry/Fabric ফর্মে `photoUrl` ফিল্ড আছে কিন্তু আপলোড UI নেই),
   ড্র্যাগ-রিঅর্ডার, এবং কিছু মাইক্রো-অ্যানিমেশন যেগুলো এই ধাপে ইচ্ছাকৃতভাবে বাদ দেওয়া হয়েছে
   সময়-সাশ্রয়ী রাখতে।

কোনো নির্দিষ্ট স্ক্রিনে বাগ পেলে বা কোনো অংশ ওয়েব অ্যাপের সাথে না মিললে জানাবেন — ঠিক করে দেব।

## মেমো ছবি (Google Drive + Firestore metadata, ফোনে লগইন ছাড়া)

পার্টি ডিটেইল পেইজের হেডারের ডান পাশে "মেমো" বাটন → মেমোর ছবির গ্যালারি (গ্যালারি/ক্যামেরা থেকে যোগ, পিঞ্চ-জুম, ডিলিট সিক্রেট কী সহ)।

- **ছবি**: `tareqrayhan0@gmail.com`-এর Google Drive-এ, `ImranClothStore-Memos` ফোল্ডারে। অ্যাপ সরাসরি Drive-এ যায় না; একটা Google Apps Script web app (`apps-script/Code.gs`) মাঝখানে থাকে, তাই কোনো ফোনে Google লগইন লাগে না। আপলোডের আগে ছবি ছোট করা হয় (বড় পাশ ১৬০০px, JPEG ৮০)।
- **Firestore (শুধু metadata)**: `party_memos/{ক্যাটাগরি}_{entryId}/files/{driveFileId}` → `driveFileId, fileName, mimeType, sizeBytes, width, height, createdAt`।
- কোড: `data/memo/` (MemoConfig, DriveProxy, MemoRepository), `ui/screens/memo/`, `util/MemoImage.kt`।

### একবারের সেটআপ
1. `tareqrayhan0@gmail.com` দিয়ে <https://script.google.com> → New project। `apps-script/Code.gs`-এর পুরো কোড বসান।
2. কোডের `SECRET`-এ একটা লম্বা গোপন কী দিন (যেমন ৩০+ অক্ষরের এলোমেলো লেখা)।
3. Deploy → New deployment → Type: **Web app** → Execute as: **Me** → Who has access: **Anyone** → Deploy। প্রথমবার Drive-এর অনুমতি চাইবে — Allow করুন ("unverified app" দেখালে Advanced → Go to ... চাপুন)।
4. যে `.../exec` URL পাবেন সেটা আর ওই গোপন কী `data/memo/MemoConfig.kt`-এর `SCRIPT_URL` ও `SCRIPT_KEY`-তে বসান, তারপর অ্যাপ বিল্ড করুন।
5. স্ক্রিপ্ট বদলালে Deploy → Manage deployments → Edit → New version দিতে হবে (URL একই থাকে)।

নোট: কী অ্যাপের ভেতরে থাকে, তাই এটা ছোট ব্যক্তিগত ব্যবহারের জন্য যথেষ্ট; স্ক্রিপ্ট শুধু মেমোর ফোল্ডারের ফাইলেই হাত দেয়, Drive-এর বাকি কিছুতে নয়।
