# LiteTravel — Quy trình và API thống nhất giữa B/C

> Tài liệu bàn giao cho B và C. Dùng cùng với [ROADMAP_B_C_LITE_TRAVEL.md](ROADMAP_B_C_LITE_TRAVEL.md) để triển khai, và [ANDROID_APP_SIZE_SHARED_CORE.md](ANDROID_APP_SIZE_SHARED_CORE.md) làm tham chiếu ràng buộc kỹ thuật. Phạm vi sản phẩm là 5 module tổng cộng: base `app` và bốn dynamic feature.

## 1. Mục tiêu phối hợp

- **B** sở hữu base app: Home/XML, trạng thái hiển thị, cấu hình release R8/resource shrinking, manifest base và các file Gradle cấp project/app.
- **C** sở hữu API phần feature, installer/navigator và bốn dynamic feature `gallery`, `audio_guide`, `itinerary`, `food`.
- B và C cùng khóa contract trước khi làm UI và implementation độc lập.
- C không sửa trực tiếp file base do B sở hữu. C gửi thay đổi Gradle/manifest cần thiết để B tích hợp vào file đó.
- Dùng đúng tên module, package, API và trạng thái bên dưới; mọi thay đổi contract cần được B/C đồng ý và cập nhật trong PR trước khi implementation phụ thuộc vào nó.

## 2. Cấu trúc đích và định danh cố định

```text
:app
:core:contracts
:feature:gallery
:feature:audio_guide
:feature:itinerary
:feature:food
```

| Feature ID | Gradle module | Split/module name | Activity class đầy đủ |
|---|---|---|---|
| `GALLERY` | `:feature:gallery` | `gallery` | `vn.demo.liteload.feature.gallery.GalleryActivity` |
| `AUDIO_GUIDE` | `:feature:audio_guide` | `audio_guide` | `vn.demo.liteload.feature.audioguide.AudioGuideActivity` |
| `ITINERARY` | `:feature:itinerary` | `itinerary` | `vn.demo.liteload.feature.itinerary.ItineraryActivity` |
| `FOOD` | `:feature:food` | `food` | `vn.demo.liteload.feature.food.FoodActivity` |

Các giá trị trên bổ sung cho contract đang có trong `core/contracts`. Application ID vẫn là `vn.demo.liteload`; tên hiển thị sản phẩm là LiteTravel. Base không được thêm `implementation(project(":feature:..."))`; dynamic feature phụ thuộc vào base theo cấu hình Android Gradle Plugin.

## 3. Shared contract trong `:core:contracts`

### 3.1 Định danh feature

C bổ sung một enum để B truyền feature an toàn, không tự gõ tên module hoặc tên Activity ở nhiều nơi:

```kotlin
enum class FeatureId(
    val moduleName: String,
    val activityClassName: String
) {
    GALLERY(FeatureContract.GALLERY_MODULE, FeatureContract.GALLERY_ACTIVITY),
    AUDIO_GUIDE(FeatureContract.AUDIO_GUIDE_MODULE, FeatureContract.AUDIO_GUIDE_ACTIVITY),
    ITINERARY(FeatureContract.ITINERARY_MODULE, FeatureContract.ITINERARY_ACTIVITY),
    FOOD(FeatureContract.FOOD_MODULE, FeatureContract.FOOD_ACTIVITY)
}
```

Enum lấy các giá trị từ `FeatureContract` hiện có. C chỉ cần mở rộng object đó cho bốn feature, không khai báo lại string module/activity trong chỗ khác.

### 3.2 Trạng thái cài đặt và event callback

Giữ các trạng thái hiện có trong `InstallUiState.kt` và thêm feature ID vào mọi cập nhật:

```kotlin
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

data class FeatureInstallUpdate(
    val featureId: FeatureId,
    val state: InstallUiState
)
```

`FeatureInstallUpdate` là event từ C sang B. Callback được gọi trên main thread. Bytes tải về là số byte; UI có thể hiển thị progress nếu `totalBytes > 0`, nếu không thì dùng progress không xác định. Không giả định Play luôn cung cấp tổng dung lượng.

## 4. API phần C bàn giao cho B

### 4.1 Cài đặt và truy vấn trạng thái

API công khai nằm trong `app/delivery/PlayFeatureInstaller.kt`; B chỉ gọi API này, không gọi `SplitInstallManager` trực tiếp.

```kotlin
class PlayFeatureInstaller(context: Context) : AutoCloseable {
    fun isInstalled(featureId: FeatureId): Boolean

    fun refresh(
        featureId: FeatureId,
        onUpdate: (FeatureInstallUpdate) -> Unit
    )

    fun install(
        featureId: FeatureId,
        confirmationLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onUpdate: (FeatureInstallUpdate) -> Unit
    )

    override fun close()
}
```

Ngữ nghĩa bắt buộc:

- `isInstalled` đọc trạng thái hiện tại từ danh sách module đã cài, không dựa vào state chỉ lưu trong Activity.
- `refresh` phát trạng thái hiện tại cho đúng feature. Gọi khi Home được tạo/được resume để đồng bộ sau khi Activity hoặc process được tạo lại.
- `install` yêu cầu module theo `featureId`; phát trạng thái ngay cả khi yêu cầu thất bại đồng bộ.
- `confirmationLauncher` được B đăng ký trên `MainActivity` bằng Activity Result API và truyền vào lúc bắt đầu cài. C chỉ dùng launcher khi phiên chuyển sang `REQUIRES_USER_CONFIRMATION`, không giữ launcher sau phiên.
- MVP chỉ có **một phiên cài feature do UI khởi tạo tại một thời điểm**. B khóa các nút tải khác khi một phiên đang chạy; C từ chối yêu cầu trùng/song song bằng `Failed` có thông điệp rõ, không để callback bị gán nhầm feature.
- C lọc state listener theo `sessionId` của yêu cầu, ánh xạ session đúng `FeatureId` và gỡ listener/callback khi terminal state hoặc `close()`.
- Callback không được giữ Activity sau `close()`. B gọi `close()` trong `MainActivity.onDestroy()`; sau khi quay lại Home, B gọi `refresh()`.
- `MainActivity` dùng `ComponentActivity` để đăng ký Activity Result API. B bổ sung dependency AndroidX Activity vào version catalog/app theo phiên bản đã khóa; C gửi đề xuất dependency cùng PR contract, B tích hợp vào file Gradle do B sở hữu.

### 4.2 Xác nhận của Play

- Khi nhận `REQUIRES_USER_CONFIRMATION`, C phát `RequiresConfirmation` và gọi overload `SplitInstallManager.startConfirmationDialogForResult(state, confirmationLauncher)`.
- B không tự tạo dialog giả và không coi việc gọi listener là đã xác nhận.
- B đăng ký launcher bằng `ActivityResultContracts.StartIntentSenderForResult` trước khi Activity vào trạng thái STARTED. Callback result có thể kích hoạt refresh UI; state listener của Play vẫn là nguồn trạng thái cài đặt chính.
- Đồng ý làm session chuyển tiếp; từ chối làm session chuyển `CANCELED`; nếu người dùng chưa chọn và dialog bị đóng, trạng thái có thể vẫn là `REQUIRES_USER_CONFIRMATION`, khi đó B/C cho phép yêu cầu xác nhận lại. Nếu không khởi chạy được dialog, C phát `Failed`.

Đây là đường xác nhận duy nhất của dự án. Play cung cấp overload `ActivityResultLauncher<IntentSenderRequest>`; từ chối chuyển session thành `CANCELED`, còn đóng dialog mà chưa chọn có thể giữ `REQUIRES_USER_CONFIRMATION`. Tham khảo [SplitInstallManager API](https://developer.android.com/reference/com/google/android/play/core/splitinstall/SplitInstallManager) và [hướng dẫn xác nhận on-demand](https://developer.android.com/guide/playcore/feature-delivery/on-demand).

### 4.3 Mở feature

Trong `app/navigation/FeatureNavigator.kt`:

```kotlin
enum class OpenFeatureResult {
    Opened,
    NotInstalled,
    Failed
}

object FeatureNavigator {
    fun open(activity: Activity, featureId: FeatureId): OpenFeatureResult
}
```

- Navigator dùng `featureId.activityClassName` và `Intent.setClassName`; base không tham chiếu `GalleryActivity::class.java` hay class của feature khác.
- Chỉ mở nếu module đã cài. Nếu chưa cài, trả `NotInstalled`.
- Bắt lỗi khởi chạy phù hợp và trả `Failed`; không để lỗi activity name/module làm crash app.
- B nhận kết quả để cập nhật UI hoặc báo lỗi; navigator không tự hiển thị Toast/Dialog.

## 5. Trách nhiệm theo file

| Chủ sở hữu | Sửa trực tiếp | Phối hợp/đề xuất thay đổi |
|---|---|---|
| **B** | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/vn/demo/liteload/MainActivity.kt`, `app/src/main/res/**`, `app/proguard-rules.pro`, root `settings.gradle.kts`, version catalog | Tích hợp Play dependency, `dynamicFeatures`, include module và SplitCompat mà C đề xuất |
| **C** | `core/contracts/**`, `app/delivery/**`, `app/navigation/**`, `feature/gallery/**`, `feature/audio_guide/**`, `feature/itinerary/**`, `feature/food/**` | Gửi chính xác các dòng Gradle/manifest cần B thêm; không tự sửa file B đang sở hữu |

Nếu cần sửa `FeatureContract`/`InstallUiState`, C mở PR contract riêng hoặc commit phần contract trước trong PR implementation. B review tên và chữ ký API trước khi code Home phụ thuộc vào chúng.

## 6. Quy trình làm việc theo thứ tự

### Bước 1 — B đưa skeleton lên `main`

B đồng bộ các file base hiện có, xác nhận repository sync được trong Android Studio, rồi push/merge skeleton vào `main`. Không bắt đầu feature trên một bản Gradle khác hoặc tự đổi application ID/toolchain.

### Bước 2 — C mở PR contract trước

C tạo branch `feat/c-feature-contract`, bổ sung `FeatureId`, `FeatureInstallUpdate`, chữ ký installer/navigator và quyết định cụ thể về confirmation. PR chỉ tập trung contract và tài liệu cần thiết.

B review các điểm: module/activity name, cách callback mang feature ID, xử lý lifecycle/listener, giới hạn một phiên tải và API confirmation. Chỉ sau khi PR contract được merge mới nối Home với API đó.

### Bước 3 — B và C triển khai song song

- B làm UI Home, mapping trạng thái thành nhãn/nút/progress/retry, và cấu hình R8/resource shrinking. B dùng contract đã merge; không tự gọi API Play.
- C làm `PlayFeatureInstaller`, `FeatureNavigator`, SplitCompat và bốn dynamic feature. Mỗi feature có Activity nội bộ và tài nguyên riêng.
- C gửi một danh sách patch Gradle/manifest cho B: `include(...)`, `dynamicFeatures`, dependency Play Feature Delivery, application SplitCompat và manifest feature. B tích hợp vào các file do B sở hữu.
- Mỗi PR ghi file sở hữu, contract có đổi không, cách build và phần nào đã kiểm thử.

### Bước 4 — Tích hợp trên một commit chung

1. B merge cấu hình module/Play dependency được hai bên rà soát.
2. C rebase/merge `main`, xử lý conflict trong file thuộc C; không tự giải quyết conflict bằng cách ghi đè file B.
3. B nối nút Home với installer/navigator và xử lý mọi trạng thái.
4. B/C cùng build debug để bắt lỗi cấu trúc/module trước khi làm release.
5. B/C build release R8 bật; C kiểm tra Activity mở bằng class name sau shrink. Chỉ thêm keep rule hẹp nếu kiểm chứng được cần thiết.
6. Push commit tích hợp để D dùng đúng `git_sha` khi tạo AAB/số đo.

## 7. Bản đồ trạng thái Home

| State từ C | Hành vi UI của B |
|---|---|
| `NotInstalled` | Hiện nút **Tải** |
| `Pending` | Hiện **Đang chờ**; khóa nút tải khác |
| `Downloading` | Hiện **Đang tải** và progress; nếu thiếu total thì progress không xác định |
| `Installing` | Hiện **Đang cài** |
| `RequiresConfirmation` | Hiện thông báo xác nhận trong Play; C gọi luồng confirmation thật |
| `Installed` | Hiện nút **Mở** |
| `Failed(code, message)` | Hiện lỗi dễ hiểu và nút **Thử lại**; giữ code/message cho log |
| `Canceled` | Hiện **Đã hủy** và cho phép tải lại |

Trạng thái luôn gắn với `FeatureId`; không dùng một biến state chung khiến tải Gallery hiển thị nhầm trên Audio/Ẩm thực/Lịch trình.

## 8. Definition of Done cho B/C

- [ ] Contract bốn feature được merge trước khi B nối UI.
- [ ] Có đúng module `app`, `core:contracts`, `gallery`, `audio_guide`, `itinerary`, `food`.
- [ ] Base Home chạy khi chưa cài feature; thông tin cơ bản không phụ thuộc dynamic module.
- [ ] Tải từng feature phát đúng trạng thái, feature ID và tiến trình; xử lý lỗi, hủy, confirmation.
- [ ] Cài xong mới mở Activity; mở lại app vẫn nhận biết feature đã cài.
- [ ] Bốn feature có nội dung thật và tài nguyên nằm trong module tương ứng.
- [ ] SplitCompat bật cho base và từng feature Activity theo hướng dẫn Android.
- [ ] R8 OFF/ON release build dùng chung source, tài nguyên, signing và cấu hình còn lại.
- [ ] Bản R8 ON mở được cả bốn Activity; không dùng keep-all.
- [ ] Cài thử từ AAB bằng bundletool local-testing; không dùng `assembleDebug` để tuyên bố đã chứng minh on-demand.
- [ ] D nhận commit SHA, AAB, thiết bị/device spec và log/screenshot thật.

Play yêu cầu bật SplitCompat cho base và từng Activity trong on-demand module; local testing dùng bundletool để kiểm tra luồng cài module cục bộ, không chứng minh tải từ Play thật. Xem [hướng dẫn on-demand và SplitCompat](https://developer.android.com/guide/playcore/feature-delivery/on-demand).

## 9. Bàn giao cho người C khi pull repo

C bắt đầu bằng các bước:

1. Pull branch `main` mới nhất sau khi B đã push skeleton.
2. Đọc file này, `ROADMAP_B_C_LITE_TRAVEL.md` và phần module/API trong `ANDROID_APP_SIZE_SHARED_CORE.md`.
3. Tạo branch `feat/c-feature-contract`; mở PR contract trước, chưa viết logic Home.
4. Sau khi contract được merge, tạo/tiếp tục branch implementation cho installer và bốn feature.
5. Gửi B danh sách chỉnh Gradle/manifest cần tích hợp trước khi đưa PR vào `main`.

Nếu cần đổi tên package, module, application ID hoặc chữ ký API, dừng phần phụ thuộc vào giá trị đó và yêu cầu B duyệt PR contract; không tự đổi trong một feature riêng.
