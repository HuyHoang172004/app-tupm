# SHARED CORE SPEC — Android App Size Reduction & Dynamic Delivery

> **Phiên bản:** v1.0 · **Ngày:** 19/11/2026  
> **Mục đích:** Hợp đồng kỹ thuật chung để A, *B, C, D cùng dùng AI triển khai song song, có thể ghép thành **1 ứng dụng Android demo + 1 báo cáo Word** mà không lệch kiến trúc, số liệu hoặc giao diện.

---

## 0. Quyết định phải thống nhất trước khi code

| Hạng mục | Quy ước cố định cho MVP |
|---|---|
| Tên dự án | **LiteLoad Demo** |
| Mã nguồn | 1 repository Git duy nhất: `liteload-demo` |
| Ngôn ngữ | Kotlin |
| Giao diện | Android Views + XML, không dùng Compose trong MVP (giữ dependency đơn giản để dễ đo) |
| Build | Gradle Kotlin DSL (`*.gradle.kts`), Android App Bundle (`.aab`) làm artifact chính |
| Android | `minSdk = 23`; `compileSdk`/`targetSdk`, Android Gradle Plugin (AGP), Gradle, Kotlin và JDK **khóa phiên bản tương thích trong repo** trước khi các agent làm việc. Không để từng người tự nâng cấp. |
| Application ID | `vn.demo.liteload` |
| 1 tính năng tách động | **Gallery** (thư viện ảnh demo); tên dynamic module: `gallery` |
| Mô hình phát hành | Base App có Home + màn hình trạng thái; Gallery là module **On-demand** |
| Thư viện chính | Android SDK, AndroidX tối thiểu; Play Feature Delivery API; không tự thêm thư viện UI/DI/network nặng |
| Đo dung lượng | Cùng device spec, cùng kiểu ký, cùng tài nguyên, cùng release build; đo APK phân phối ban đầu, kích thước feature và AAB riêng |
| Đơn vị đo | Byte là số liệu gốc; MiB = byte / 1,048,576; phần trăm giảm = (trước − sau) / trước × 100 |

**Quy tắc quan trọng:** *B và C cần thống nhất, tạo, build thành công bộ khung tối thiểu rồi commit vào `main` **trước khi** các nhánh tính năng bắt đầu chạy độc lập. Sau đó cả 4 làm song song trên cùng contract này.

**Không được nhầm lẫn:** `.aab` là artifact phát hành, không phải kích thước mà từng điện thoại chắc chắn phải tải. APK universal, APK đã tối ưu cho thiết bị, kích thước tải ban đầu và kích thước lưu trữ sau cài đặt là **các số đo khác nhau**.

---

## 1. Kiến trúc core chung

```text
LiteLoad Demo
├── app (base, install-time)
│   ├── MainActivity / Home UI
│   ├── AppSizeApplication (SplitCompat)
│   ├── delivery/PlayFeatureInstaller
│   └── navigation/GalleryNavigator
│
├── core:contracts (thư viện luôn có trong base)
│   ├── FeatureContract
│   ├── InstallUiState
│   └── MetricLabels / ExperimentId
│
├── feature:gallery (dynamic-feature, on-demand)
│   ├── GalleryActivity
│   ├── res/drawable/ (ảnh demo được dùng thật)
│   └── AndroidManifest.xml (dist:on-demand)
│
└── docs + experiments (bằng chứng và tài liệu)
```

**Phụ thuộc bắt buộc**

```text
:app              ──implementation──> :core:contracts
:feature:gallery  ──implementation──> :app
:app              ──dynamicFeatures─> :feature:gallery
```

- Module động có thể sử dụng API từ base. **Base không được thêm `implementation(project(":feature:gallery"))`** vì sẽ phá mô hình phân phối động.
- `:core:contracts` giữ những tên module, trạng thái và giao diện đã thống nhất; chỉ bổ sung/đổi bằng PR được tất cả đồng ý.
- Base không gọi trực tiếp class Gallery qua `GalleryActivity::class.java`: dùng tên class đầy đủ sau khi xác nhận module đã được cài.

---

## 2. Cấu trúc repository chuẩn

```text
liteload-demo/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradlew / gradlew.bat
├── .gitignore
├── README.md
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/vn/demo/liteload/
│       │   ├── AppSizeApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── delivery/PlayFeatureInstaller.kt
│       │   └── navigation/GalleryNavigator.kt
│       └── res/values/strings.xml
├── core/
│   └── contracts/
│       ├── build.gradle.kts
│       └── src/main/java/vn/demo/liteload/core/
│           ├── FeatureContract.kt
│           └── InstallUiState.kt
├── feature/
│   └── gallery/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/vn/demo/liteload/feature/gallery/GalleryActivity.kt
│           └── res/drawable/
├── experiments/
│   ├── device-spec.json              # Sinh từ bundletool, không tự bịa thông số
│   ├── results.csv                    # Số đo thô, không tự điền số giả
│   ├── environment.md                 # SDK, AGP, thiết bị, build, git SHA
│   └── outputs/                      # Có thể bỏ khỏi Git nếu file lớn
├── docs/
│   ├── report/                        # Nội dung, hình, bảng cho Word
│   ├── evidence/                      # Ảnh chụp APK Analyzer / screen demo
│   └── demo-script.md
└── scripts/
    └── README.md                      # Cách build/đo reproducible
```

**File ownership:** Chỉ một người chịu trách nhiệm merge/chỉnh từng file cốt lõi; các thành viên khác gửi PR hoặc đề xuất thay đổi, không để 2 AI cùng sửa `settings.gradle.kts`, `libs.versions.toml`, `app/build.gradle.kts`.

---

## 3. CORE A — Build & dependency contract

### 3.1 Các module Gradle

```kotlin
// settings.gradle.kts (đoạn include)
include(":app", ":core:contracts", ":feature:gallery")
```

```kotlin
// app/build.gradle.kts (trong android { ... })
dynamicFeatures += setOf(":feature:gallery")
```

```kotlin
// feature/gallery/build.gradle.kts (ý chính)
plugins {
    alias(libs.plugins.android.dynamic.feature)
    alias(libs.plugins.kotlin.android)
}

dependencies {
    implementation(project(":app"))
}
```

```kotlin
// app/build.gradle.kts (ý chính)
dependencies {
    implementation(project(":core:contracts"))
    implementation("com.google.android.play:feature-delivery-ktx:2.1.0")
}
```

> **Lưu ý:** Các snippet là hợp đồng cấu trúc, không phải file Gradle độc lập có thể paste và chạy ngay. *B phải tạo project bằng Android Studio, khóa plugin version thực sự tương thích trong `libs.versions.toml`. Với AGP **8.x/9.0–9.2**, dùng DSL `isMinifyEnabled`/`isShrinkResources` tương ứng phiên bản; với **AGP 9.3+**, tài liệu Android hướng dẫn DSL `optimization { enable = true }`. Không trộn DSL của các phiên bản.

### 3.2 Chế độ build so sánh R8 (một công tắc thống nhất)

Trên AGP hỗ trợ DSL `isMinifyEnabled`/`isShrinkResources`:

```kotlin
// Trong app/build.gradle.kts, dùng cho AGP 8.x hoặc 9.0–9.2 phù hợp.
val sizeOptimization = providers.gradleProperty("sizeOptimization")
    .map(String::toBoolean)
    .orElse(true)

android {
    buildTypes {
        getByName("release") {
            isMinifyEnabled = sizeOptimization.get()
            isShrinkResources = sizeOptimization.get()
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

- **Baseline R8 OFF:** `./gradlew :app:bundleRelease -PsizeOptimization=false`
- **Optimized R8 ON:** `./gradlew :app:bundleRelease -PsizeOptimization=true`
- Mỗi lần build **copy đổi tên** artifact ngay (`baseline.aab`, `optimized.aab`) vì build sau sẽ ghi đè.
- Hai build phải dùng cùng source commit và cùng cấu hình khác ngoại trừ công tắc được thử nghiệm; ghi `git rev-parse HEAD` vào bảng kết quả.
- Chỉ thêm quy tắc `-keep` thật sự cần thiết sau khi kiểm thử; không thêm `-keep class ** { *; }` vì làm mất ý nghĩa R8.
- `proguard-rules.pro` do **B** quản lý; nguồn API reflection/feature activity có thể cần quy tắc keep phù hợp và phải được kiểm thử với release.

### 3.3 Manifest của module động

```xml
<!-- feature/gallery/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:dist="http://schemas.android.com/apk/distribution">
    <dist:module dist:instant="false" dist:title="@string/gallery_title">
        <dist:delivery>
            <dist:on-demand />
        </dist:delivery>
        <dist:fusing dist:include="true" />
    </dist:module>
    <application>
        <activity
            android:name=".GalleryActivity"
            android:exported="false" />
    </application>
</manifest>
```

- `@string/gallery_title` phải có trong **base app** (`app/src/main/res/values/strings.xml`).
- `android:exported="false"` cho activity động, chỉ mở nội bộ sau khi cài xong.
- `dist:on-demand` có ý nghĩa cho build AAB/Play Feature Delivery. Không được lấy một APK build trực tiếp bằng `assembleDebug` để khẳng định đã demo được cơ chế tải on-demand thực tế.

---

## 4. CORE B — Shared API / contract giữa *B và C

### 4.1 Tên và định danh không được tự đổi

```kotlin
// core/contracts/.../FeatureContract.kt
package vn.demo.liteload.core

object FeatureContract {
    const val GALLERY_MODULE = "gallery"
    const val GALLERY_ACTIVITY =
        "vn.demo.liteload.feature.gallery.GalleryActivity"
}
```

**Bất biến:** Module Gradle `:feature:gallery` có split name là `gallery`; `GalleryActivity` nằm đúng package và tên trên; application ID là `vn.demo.liteload`.

### 4.2 UI state dùng chung

```kotlin
// core/contracts/.../InstallUiState.kt
package vn.demo.liteload.core

sealed interface InstallUiState {
    data object NotInstalled : InstallUiState
    data object Pending : InstallUiState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : InstallUiState
    data object Installing : InstallUiState
    data object Installed : InstallUiState
    data object RequiresConfirmation : InstallUiState
    data class Failed(val code: Int, val message: String) : InstallUiState
    data object Canceled : InstallUiState
}
```

### 4.3 Hợp đồng triển khai

- `PlayFeatureInstaller` (C) chịu trách nhiệm: `SplitInstallManager`, `startInstall`, listener theo dõi trạng thái, `installedModules`, yêu cầu xác nhận nếu Play yêu cầu, xử lý lỗi và unregister listener để tránh leak.
- `GalleryNavigator` (C) chịu trách nhiệm: chỉ chạy `Intent.setClassName(packageName, FeatureContract.GALLERY_ACTIVITY)` khi module ở trạng thái `Installed`; lỗi không crash.
- `MainActivity` (*B) chịu trách nhiệm: màn hình Home và nút **"Tải Gallery"** / **"Mở Gallery"**, text trạng thái/tiến trình, chỗ hiển thị dung lượng minh họa. Chỉ gọi API public của `PlayFeatureInstaller`/`GalleryNavigator`.
- Lệnh khởi chạy feature không được chạy khi trạng thái là `Pending`, `Downloading`, `Installing`, `Failed` hoặc `Canceled`.
- Với activity trong feature: bật `SplitCompat.installActivity(this)` trong `attachBaseContext`; base `Application` dùng `SplitCompatApplication` hoặc `SplitCompat.install` theo hướng dẫn Android.
- Nếu R8 loại bỏ activity chỉ được gọi bằng tên (string/reflection), bổ sung keep rule hẹp và test release, không tắt R8 toàn cục.

**Đầu ra API để *B gọi (mô tả chữ ký; C hiện thực):**

```kotlin
class PlayFeatureInstaller(context: android.content.Context) {
    fun isGalleryInstalled(): Boolean
    fun installGallery(onUpdate: (InstallUiState) -> Unit)
    fun close()
}

object GalleryNavigator {
    fun open(activity: android.app.Activity): Boolean
}
```

`installGallery()` phải có phương án xử lý `REQUIRES_USER_CONFIRMATION` ở Activity (ví dụ activity-result contract) và bàn giao callback/hook rõ ràng trong PR; chữ ký trên là API tối thiểu, **không hàm ý rằng một listener tự lo được bước xác nhận**.

---

## 5. CORE C — Product flow / demo UI

**Screen 1: Home (base app, hoạt động ngay sau lần cài đầu tiên)**

```text
LiteLoad Demo
----------------------------------
App Size Reduction + Dynamic Delivery

[ Xem cấu hình tối ưu ]
  - R8: ON/OFF theo build đang chạy
  - Resource shrinking: ON/OFF
  - Version: ...

Feature: Gallery
Trạng thái: Chưa cài / Đang tải ... / Đã cài / Thất bại
[ Tải Gallery ]          (nếu chưa cài)
[ Mở Gallery ]          (nếu đã cài)

Vùng thông báo lỗi + tiến trình + retry
```

**Screen 2: Gallery (chỉ nằm trong `:feature:gallery`)**

- Danh sách/lưới 12–20 ảnh demo hợp lệ, dung lượng không quá lớn gây khó test; ít nhất một ảnh hiển thị thật.
- Có tên ảnh, nút Quay lại; có thể mở một ảnh chi tiết.
- Tất cả ảnh feature đặt trong `feature/gallery/src/main/res/drawable*` hoặc asset của feature, **không copy về `app/`**.
- Nếu muốn minh họa việc resource shrinker loại bỏ ảnh không dùng, đặt các ảnh dư trong module đang thử nghiệm, không dùng chúng như dữ liệu Gallery bắt buộc.

**Luồng demo bắt buộc:** Cài base từ AAB qua bundletool local-testing → mở Home khi Gallery chưa có → nhấn tải → thấy trạng thái tải/cài → `Installed` → mở Gallery thật → tắt/mở lại app và mở Gallery lần nữa.

**Hạn chế demo offline:** Local testing mô phỏng cài đặt module từ các split APK đã copy vào máy, **không chứng minh tốc độ tải từ Google Play hay băng thông mạng thực**. Muốn xác nhận hành vi phân phối thật cần Play Console internal testing/internal app sharing.

---

## 6. CORE D — Thực nghiệm và quy tắc đo lường

### 6.1 Tách ảnh hưởng từng kỹ thuật

| ID | R8 + Shrink | Gallery delivery | Mục đích |
|---|---|---|---|
| **E0** | OFF | Install-time | Mốc gốc: không R8, tính năng cài cùng base |
| **E1** | ON | Install-time | Đo riêng ảnh hưởng R8/Resource Shrinking so với E0 |
| **E2** | ON | On-demand | Đo riêng ảnh hưởng chuyển sang phân phối động so với E1 |
| **E3** (tùy chọn) | ON | On-demand + ảnh nén | Đo thêm ảnh hưởng nén tài nguyên so với E2 |

**Cần lưu 3 snapshot (Git tag/commit), không tự động lấy số từ E0 so với E2 rồi tuyên bố hoàn toàn do R8.** Khi chuyển Gallery từ install-time sang on-demand, thay đổi manifest delivery có chủ đích, giữ nguyên các chức năng và dữ liệu khác.

### 6.2 Các số liệu cần thu

| Chỉ tiêu | Ý nghĩa / cách ghi |
|---|---|
| `aab_bytes` | Kích thước file `.aab` trên ổ đĩa (tham khảo, không phải download size) |
| `initial_download_bytes` | Ước lượng APK download khi cài lần đầu cho `device-spec.json` bằng bundletool |
| `with_gallery_download_bytes` | Ước lượng khi có cả base và gallery, với cùng device spec |
| `estimated_gallery_extra_bytes` | `with_gallery_download_bytes - initial_download_bytes` (ước lượng phần tăng thêm) |
| `installed_size_bytes` | Dung lượng lưu trữ app trên máy (nếu đo được bằng cùng một phương pháp) |
| `dex_bytes`, `resources_bytes` | Phân tích cấu phần APK bằng Android Studio APK Analyzer |
| `feature_install_success` | Có/Không; kèm log trạng thái và screenshot |
| `device`, `sdk`, `abi`, `density`, `agp`, `gradle`, `git_sha` | Metadata bắt buộc cho tái lập |

### 6.3 Lệnh chuẩn (Windows dùng `gradlew.bat`, Linux/macOS dùng `./gradlew`)

```bash
# 1) Build AAB release, phải sao lưu riêng từng điều kiện
./gradlew :app:bundleRelease -PsizeOptimization=false
# Copy app/build/outputs/bundle/release/app-release.aab -> experiments/outputs/E0.aab

./gradlew :app:bundleRelease -PsizeOptimization=true
# Copy app/build/outputs/bundle/release/app-release.aab -> experiments/outputs/E1.aab hoặc E2.aab

# 2) Lấy cấu hình thiết bị TEST THẬT/EMULATOR đang kết nối
java -jar bundletool.jar get-device-spec \
  --output=experiments/device-spec.json

# 3) Tạo APK set để kiểm tra kích thước tải
java -jar bundletool.jar build-apks \
  --bundle=experiments/outputs/E2.aab \
  --output=experiments/outputs/E2.apks \
  --local-testing

# 4) Kích thước tải ban đầu (theo device spec)
java -jar bundletool.jar get-size total \
  --apks=experiments/outputs/E2.apks \
  --device-spec=experiments/device-spec.json

# 5) Kích thước khi yêu cầu cả base và gallery
java -jar bundletool.jar get-size total \
  --apks=experiments/outputs/E2.apks \
  --device-spec=experiments/device-spec.json \
  --modules=base,gallery

# 6) Cài local-test để demo tải On-demand
java -jar bundletool.jar install-apks \
  --apks=experiments/outputs/E2.apks
```

- Câu lệnh trên minh họa cú pháp shell Bash; với PowerShell, bỏ `\` xuống dòng hoặc viết một dòng lệnh.
- `get-size total` mặc định đo **các module nằm trong lần cài đầu**; với E2 On-demand, mặc định không bao gồm Gallery. `--modules=base,gallery` đo trường hợp yêu cầu thêm Gallery.
- Nếu build không có chữ ký release riêng, bundletool có thể dùng debug signing để local testing; dùng **cùng một cấu hình ký** giữa các điều kiện.
- Không dùng APK universal để báo cáo kết quả **initial download**; nó có thể chứa tài nguyên/configuration không cần thiết trên máy được đo.
- Local testing phải cài bằng `bundletool install-apks` sau `--local-testing`; chỉ `adb install app-debug.apk` không đại diện cho quá trình phân phối động.
- Khi test lại trạng thái "chưa cài", gỡ app/dữ liệu trên máy rồi cài lại từ đúng bộ APK set; luôn giữ cùng thiết bị và phương pháp.

### 6.4 Header file số liệu chuẩn `experiments/results.csv`

```csv
experiment_id,git_sha,agp_version,device_name,android_sdk,abi,shrink_enabled,delivery_mode,aab_bytes,initial_download_bytes,with_gallery_download_bytes,estimated_gallery_extra_bytes,installed_size_bytes,dex_bytes,resources_bytes,feature_install_success,notes
```

Không điền giá trị giả chỉ để có biểu đồ. Ô chưa đo để trống, báo cáo ghi rõ "chưa có kết quả".

---

## 7. CORE E — Giao diện tài liệu và minh chứng

**Báo cáo Word** do A dựng với các mục và nơi nhận đầu vào cố định:

1. Giới thiệu vấn đề: App Size Reduction & Dynamic Delivery.
2. Các yếu tố ảnh hưởng dung lượng APK/AAB (DEX, resources, native libs, ABI/configuration).
3. R8 / ProGuard rules và cơ chế code shrinking, optimization, obfuscation.
4. Resource Shrinking, tối ưu ảnh, loại bỏ dependency/tài nguyên dư.
5. Android App Bundle và Play Feature Delivery / On-demand.
6. Kiến trúc LiteLoad Demo (dựa trên **mục 1–5** tài liệu này).
7. Các bước triển khai thực tế (nhận từ *B + C).
8. Thiết kế thực nghiệm E0, E1, E2, E3; thiết bị và công cụ đo (nhận từ D).
9. Bảng số liệu, biểu đồ, phân tích giới hạn (D cung cấp số đo thật).
10. Kết luận, khả năng phát triển và tài liệu tham khảo.

**Quy cách minh chứng gửi về `docs/evidence/`:**

```text
E0_home.png
E0_apk_analyzer.png
E1_apk_analyzer.png
E2_home_not_installed.png
E2_gallery_downloading.png
E2_gallery_installed.png
E2_gallery_open.png
E2_bundletool_size.txt
E2_device_spec.json
```

Đặt chú thích ảnh + `experiment_id` khi sử dụng trong Word; không dùng screenshot và số liệu từ các build khác nhau mà không chú thích.

---

## 8. Phân công file ownership / hợp đồng bàn giao

| Người | Phạm vi phụ trách | File / output bàn giao | Không tự chỉnh |
|---|---|---|---|
| **A** | Báo cáo lý thuyết, sơ đồ, bố cục Word | `docs/report/*`, báo cáo `.docx` | Gradle, implementation app/feature |
| **B** | Base app + tối ưu R8/resource | `app/build.gradle.kts`, `app/proguard-rules.pro`, `MainActivity.kt`, XML UI, ảnh baseline | Activity + manifest Gallery |
| **C** | Contract động + Play Feature Delivery | `core/contracts/*`, `app/delivery/*`, `app/navigation/*`, `feature/gallery/*` | ProGuard/build settings release (đề xuất PR nếu cần) |
| **D** | Kiểm thử, đo, bảng biểu, demo | `experiments/*`, `docs/evidence/*`, `docs/demo-script.md` | Logic app, contract, dynamic feature |

**Khóa cấu hình chung ban đầu:** *B tạo Android Studio project, Gradle wrapper, root Gradle, version catalog, `app` trống và `main` build được; C bổ sung khai báo `:core:contracts`, `:feature:gallery`, Play Feature Delivery vào chính bộ khung đó qua **một PR tích hợp**; sau khi merge, không tùy tiện đổi package/module/AGP.

**Branch naming:**

```text
main
feat/hoan-base-r8
feat/chien-dynamic-feature
docs/hoang-report
test/tu-benchmark
```

**PR phải nêu:** phạm vi file, cách chạy, đã test gì, artifact/screenshot, có thay contract không, cần người nào review.

---

## 9. Mốc tích hợp và Definition of Done

### Milestone M0 — Skeleton (bắt buộc trước chạy song song)

- [ ] Sync Gradle, `./gradlew :app:assembleDebug` thành công.
- [ ] Cấu trúc module và package đúng sơ đồ.
- [ ] Có Home rỗng + nút Gallery placeholder.
- [ ] `FeatureContract` compile và được base app sử dụng.
- [ ] AGP/Gradle/Kotlin/JDK/SDK đã khóa phiên bản, ghi `experiments/environment.md`.

### Milestone M1 — Các nhánh làm song song

- [ ] A: Word có mục 1–6 và 10, các phần kết quả để placeholder.
- [ ] *B: Home chạy, R8 OFF/ON build được, tối ưu được kiểm thử và có AAB riêng.
- [ ] C: Gallery on-demand chạy qua SplitInstallManager, hiển thị state, retry và mở activity.
- [ ] D: Có thiết bị `device-spec.json`, `results.csv`, quy trình bundletool và bộ ảnh minh chứng đầu tiên.

### Milestone M2 — Tích hợp và nghiệm thu cuối ngày

- [ ] Merge các nhánh lên `main` không conflict.
- [ ] Build thành công AAB release với R8 ON.
- [ ] Cài bằng bundletool local-testing, Gallery không có từ đầu và tải được theo yêu cầu.
- [ ] Có đủ ít nhất E0, E1, E2 được đo theo cùng điều kiện; nếu thiếu số, nêu giới hạn rõ ràng.
- [ ] Báo cáo Word phản ánh đúng source và kết quả đo, không trộn số mẫu/số thật.
- [ ] Có kịch bản demo 3–5 phút và người trình diễn chạy thử thành công.

### Kiểm tra rủi ro trước khi nộp

- [ ] R8 không làm crash Gallery khi mở bằng class name.
- [ ] `REQUIRES_USER_CONFIRMATION` và `FAILED` có thông báo/hành vi hợp lệ.
- [ ] Không đặt tài nguyên feature trong base app khiến AAB initial download vẫn lớn.
- [ ] Phân biệt download size, APK/AAB file size, installed size và local-testing.
- [ ] Chỉ khai báo kết quả phần trăm giảm khi so sánh đúng cặp E0→E1 hoặc E1→E2.
- [ ] Không commit keystore, mật khẩu, token hoặc secret vào Git.

---

## 10. Prompt giao cho AI của từng thành viên

> **Prompt khởi tạo dùng CHUNG:** "Hãy đọc toàn bộ `ANDROID_APP_SIZE_SHARED_CORE.md`. Tuân thủ tuyệt đối package, module, contracts, file ownership, build matrix E0/E1/E2 và các điều kiện nghiệm thu. Không tự ý đổi cấu trúc hoặc phiên bản Gradle. Chỉ sửa các file thuộc phạm vi tôi được giao; nếu cần thay contract, mô tả đề xuất thay vì tự sửa. Cuối cùng xuất danh sách file đã thay đổi, cách chạy và bằng chứng kiểm thử."

**A:** "Tạo báo cáo Word về App Size Reduction và Dynamic Delivery theo mục 7 của shared core. Chỉ dùng số đo từ experiments/results.csv, mọi số chưa có để placeholder. Có sơ đồ kiến trúc và phần phân tích riêng cho R8, Resource Shrinking và Play Feature Delivery."

**B:** "Tạo màn hình base app XML/Kotlin, bảo đảm app launch được, tích hợp FeatureContract và UI status; cấu hình R8/Resource Shrinking theo contract, có bản build OFF/ON. Không trực tiếp import GalleryActivity."

**C:** "Triển khai dynamic-feature `gallery` đúng contract: SplitInstallManager + state listener + SplitCompat + xử lý user confirmation/lỗi + mở Activity sau cài, kiểm thử bằng bundletool local-testing. Không sửa MainActivity khi không cần; giao API cho *B gọi."

**D:** "Tạo quy trình đo E0/E1/E2 lặp lại được theo device spec, ghi số thô và git SHA vào CSV, đo bằng bundletool/APK Analyzer, lưu screenshot thật; viết demo script và gửi bảng, biểu đồ cho A. Không được bịa số đo."

---

## 11. Tài liệu Android chính thức để kiểm chứng

- R8 optimization (chú ý khác biệt DSL theo AGP): https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Resource keep/shrinking: https://developer.android.com/topic/performance/app-optimization/customize-which-resources-to-keep
- Play Feature Delivery: https://developer.android.com/guide/playcore/feature-delivery
- On-demand + SplitInstallManager + SplitCompat + local testing: https://developer.android.com/guide/playcore/feature-delivery/on-demand
- Google Play Core libraries (Feature Delivery): https://developer.android.com/guide/playcore
- bundletool, get-size total, device spec: https://developer.android.com/tools/bundletool

**Phạm vi tài liệu:** Đây là **spec/contract để triển khai**, không phải một Android project đã có mã chạy hoàn chỉnh. Kết quả build và dung lượng thực tế cần được các thành viên đo trên thiết bị theo quy trình trên.
