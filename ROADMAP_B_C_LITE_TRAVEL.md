# Lộ trình triển khai B và C — LiteTravel

> Tài liệu này cụ thể hóa phần việc của B và C sau khi nhóm chốt phạm vi app du lịch gồm **5 module tổng cộng**: base app `app` và bốn dynamic feature `gallery`, `audio_guide`, `itinerary`, `food`. Đây là lộ trình triển khai; chưa phải báo cáo kết quả đo.

## 1. Mục tiêu sản phẩm

LiteTravel là app cẩm nang du lịch địa phương. Sau khi cài base app, người dùng xem được danh sách địa điểm và thông tin văn bản cơ bản. Bốn tính năng có nội dung lớn hơn được tải khi người dùng chọn:

| Module | Nội dung | Hành động trong app |
|---|---|---|
| `app` | Home, danh sách địa điểm, mô tả cơ bản, điều hướng và trạng thái cài feature | Dùng được ngay sau khi cài |
| `gallery` | Bộ ảnh địa điểm | Tải/mở thư viện ảnh |
| `audio_guide` | Các đoạn thuyết minh địa điểm | Tải/mở trình nghe audio |
| `itinerary` | Lịch trình tham khảo theo chủ đề | Tải/mở danh sách lịch trình |
| `food` | Món ăn và đặc sản địa phương | Tải/mở danh sách đặc sản |

Bốn feature không được đặt nhầm tài nguyên nội dung vào `app`; base app vẫn phải có nội dung hữu ích khi chưa cài feature nào. Chọn một địa phương và bộ dữ liệu thống nhất để tránh mỗi màn hình dùng nội dung khác nhau.

## 2. Phân công và ranh giới file

### B — Base app, giao diện và tối ưu

- Tạo skeleton Android ban đầu, khóa phiên bản công cụ theo repo và cấu hình `app`.
- Quản lý `app/build.gradle.kts`, `app/proguard-rules.pro`, manifest và mã/UI thuộc base app.
- Làm Home bằng Android Views/XML, hiển thị nội dung cơ bản và trạng thái của bốn feature.
- Cấu hình công tắc build release cho R8 và resource shrinking theo phiên bản AGP đã khóa.
- Nối UI với API C bàn giao; không import Activity hoặc implementation class từ feature.
- Tích hợp khai báo `dynamicFeatures` và dependency Play Feature Delivery trong `app/build.gradle.kts` dựa trên thay đổi C đề xuất. B là người duy nhất chỉnh file Gradle của app.

### C — Contract, cài đặt động và nội dung feature

- Quản lý `core/contracts/*`, `app/delivery/*`, `app/navigation/*` và `feature/*`.
- Hiện thực trạng thái cài đặt, yêu cầu cài, cập nhật tiến trình, xử lý xác nhận người dùng, lỗi/hủy và mở feature đã cài.
- Tạo bốn dynamic feature cùng manifest, Activity và tài nguyên riêng.
- Bàn giao cho B một API ổn định để UI yêu cầu cài/mở feature mà không phụ thuộc chi tiết Play Core.
- Không tự sửa `app/build.gradle.kts`, `settings.gradle.kts` hoặc version catalog. Gửi phần cấu hình cần thiết cho B tích hợp và cùng rà soát khi merge.

### Quy tắc file dùng chung

- Một người sở hữu mỗi file cốt lõi; không để B và C sửa cùng lúc `settings.gradle.kts`, `libs.versions.toml` hoặc `app/build.gradle.kts`.
- Tên module, package, ID ứng dụng, tên Activity và trạng thái UI phải được thống nhất trước khi tạo mã.
- Nếu contract cần đổi, C đề xuất thay đổi và B đồng ý trước khi cả hai cập nhật.

## 3. Contract cần khóa trước khi triển khai song song

Giữ các giá trị của shared core và mở rộng nhất quán cho đủ feature:

| Thuộc tính | Giá trị |
|---|---|
| Application ID | `vn.demo.liteload` theo shared core hiện tại; đổi chỉ khi cập nhật đồng bộ contract trước khi code |
| Module Gradle | `:app`, `:core:contracts`, `:feature:gallery`, `:feature:audio_guide`, `:feature:itinerary`, `:feature:food` |
| Split/module name | `gallery`, `audio_guide`, `itinerary`, `food` |
| Delivery khi demo E2 | Bốn feature đều `on-demand` |
| Base UI | Android Views + XML; không dùng Compose trong MVP |
| Feature entry point | Một Activity nội bộ trong mỗi feature; base mở bằng tên lớp đầy đủ sau khi xác nhận đã cài |

Shared core hiện mô tả một Gallery. Trước khi code feature, cập nhật hợp đồng chung để có định danh cho cả bốn feature và trạng thái đủ dùng cho từng feature, gồm tối thiểu: `NotInstalled`, `Pending`, `Downloading`, `Installing`, `Installed`, `RequiresConfirmation`, `Failed`, `Canceled`. Trạng thái tải phải gắn được với module tương ứng để Home không nhầm tiến trình giữa các feature.

API C bàn giao phải cho phép B:

1. Hỏi một feature đã được cài chưa.
2. Bắt đầu cài feature cụ thể và nhận cập nhật trạng thái.
3. Chuyển yêu cầu xác nhận do Play yêu cầu tới Activity một cách hợp lệ.
4. Mở feature cụ thể chỉ sau khi đã cài; nhận kết quả thất bại thay vì crash.
5. Hủy đăng ký listener khi không còn cần để tránh rò rỉ Activity.

C có thể chọn chữ ký API phù hợp, nhưng phải ghi rõ cách xử lý lifecycle và confirmation trong PR trước khi B nối UI. Không coi callback listener là tự xử lý được bước xác nhận.

## 4. Lộ trình theo giai đoạn

### Giai đoạn 0 — Chốt nền tảng và tạo skeleton (B dẫn, C phối hợp)

**B thực hiện**

- Tạo Android Studio project, Gradle Wrapper, root Gradle, version catalog và cấu hình app ban đầu.
- Khóa AGP, Gradle, Kotlin, JDK, `compileSdk`, `targetSdk`, `minSdk` tương thích; không tự nâng version giữa chừng.
- Tạo base app launch được với Home placeholder và application ID đã chốt.
- Ghi môi trường build vào `experiments/environment.md` khi repo có thư mục experiments.

**C thực hiện cùng B**

- Rà soát khả năng dùng dynamic feature với bộ version đã khóa.
- Chốt package, split name, feature Activity, API cài đặt và cách mở màn hình xác nhận.
- Cập nhật shared core thành contract cho bốn feature.

**Điều kiện qua giai đoạn**

- Skeleton sync/build được bằng Gradle Wrapper.
- Danh sách module và contract được ghi rõ; không còn tên Gallery đơn lẻ ở nơi được kỳ vọng hỗ trợ bốn feature.
- B/C thống nhất người sửa từng file Gradle cốt lõi.

### Giai đoạn 1 — Khung dynamic feature và API (C dẫn)

- Thêm bốn module feature vào cấu trúc Gradle theo cách B tích hợp vào các file Gradle do B sở hữu.
- Mỗi feature có manifest khai báo on-demand, Activity nội bộ và tiêu đề/tài nguyên cần thiết.
- Cài đặt lớp delivery dùng Play Feature Delivery: lấy installed modules, gửi yêu cầu cài, theo dõi state, xử lý lỗi/hủy/confirmation và tháo listener.
- Cài đặt navigator dùng contract; chỉ mở Activity sau khi feature được xác nhận là đã cài.
- Thêm SplitCompat cho base và Activity feature theo cấu hình tương thích đã thống nhất.
- Bàn giao hướng dẫn gọi API, ví dụ trạng thái cho từng module và cách đóng/unregister listener.

**Điều kiện qua giai đoạn**

- Module names/split names khớp chính xác: `gallery`, `audio_guide`, `itinerary`, `food`.
- Base không có dependency implementation trực tiếp tới các feature.
- B có thể gọi API chung mà không tham chiếu class feature.

### Giai đoạn 2 — Home base app (B dẫn, C hỗ trợ contract)

- Làm Home LiteTravel bằng XML, có tiêu đề và danh sách địa điểm mẫu thống nhất.
- Tạo bốn khu vực tính năng với mô tả, trạng thái riêng và nút phù hợp: `Tải` khi chưa cài, trạng thái tiến trình khi đang cài, `Mở` khi đã cài, `Thử lại`/thông báo khi lỗi.
- Sau khi app mở lại, đọc trạng thái module từ hệ thống và cập nhật UI; không chỉ dựa vào state trong bộ nhớ.
- Hiển thị cấu hình R8/shrinking và version theo dữ liệu build thực có thể lấy được; không ghi nhãn ON cố định cho mọi artifact.
- Tích hợp confirmation flow do C bàn giao và giữ UI phản hồi được trong lúc cài.
- Không đặt ảnh/audio/lịch trình/đặc sản nặng trong base chỉ để làm màn hình trông đầy đủ.

**Điều kiện qua giai đoạn**

- Home chạy khi cả bốn feature chưa cài.
- Mỗi nút gọi đúng module; tiến trình/lỗi của module này không làm đổi trạng thái module khác.
- UI không thể mở feature khi state chưa phải `Installed`.

### Giai đoạn 3 — Nội dung bốn feature (C dẫn)

Mỗi feature cần một màn hình tối thiểu nhưng có nội dung thực:

- `gallery`: lưới ảnh địa điểm, tên ảnh và thao tác quay lại; ảnh nằm trong tài nguyên feature.
- `audio_guide`: danh sách đoạn thuyết minh, điều khiển phát/dừng cơ bản; audio nằm trong feature.
- `itinerary`: một số lịch trình mẫu có tiêu đề, thời lượng hoặc các điểm dừng; dữ liệu và hình minh họa riêng nằm trong feature.
- `food`: danh sách món ăn/đặc sản, mô tả và ảnh minh họa; dữ liệu và ảnh riêng nằm trong feature.

Nội dung demo phải hợp lệ và thống nhất với địa phương đã chọn. Chọn số lượng/kích thước tài nguyên đủ để có thể đo được, nhưng không dùng tài nguyên giả, lặp hoặc vô nghĩa chỉ để phóng to module. Ghi lại nguồn/tự tạo nội dung khi cần cho báo cáo.

**Điều kiện qua giai đoạn**

- Mỗi feature có thể mở sau cài đặt, có nút quay lại và không yêu cầu base chứa tài nguyên của feature.
- Có ít nhất một nội dung nhìn/nghe được thật trong từng feature.
- Mở lại feature đã cài sau khi đóng/mở app vẫn hoạt động.

### Giai đoạn 4 — R8, resource shrinking và release (B dẫn, C xử lý tương thích)

- B cấu hình một thuộc tính Gradle duy nhất, ví dụ `sizeOptimization`, điều khiển R8 và resource shrinking trên release theo DSL đúng với AGP đã khóa.
- Build baseline với tối ưu tắt và build optimized với tối ưu bật; cùng source, tài nguyên, signing và các cấu hình khác.
- B chỉ thêm keep rule hẹp khi release thực sự cần; C cung cấp tên entry point/phần reflection cần giữ và cùng kiểm tra feature launch.
- C rà soát manifest/activity và luồng mở bằng class name trên bản release đã tối ưu.
- Không dùng debug APK để kết luận về on-demand delivery hoặc số liệu AAB release.

**Điều kiện qua giai đoạn**

- Có thể build release với tối ưu ON/OFF từ cùng commit.
- R8 không làm mất entry point hay khiến feature crash.
- Build ON/OFF được lưu thành artifact riêng, không ghi đè nhau.

### Giai đoạn 5 — Tích hợp demo và bàn giao cho D

- Tạo AAB release có bốn feature on-demand.
- Dùng bundletool local-testing để cài bộ APK từ AAB; xác nhận Home chạy với các feature chưa cài.
- Tải và mở từng feature; kiểm tra trạng thái tiến trình, installed, lỗi/retry và confirmation nếu môi trường yêu cầu.
- Khởi động lại app, xác nhận feature đã cài vẫn mở được.
- Bàn giao cho D: commit SHA, artifact, hướng dẫn build/cài, API/contract cuối, danh sách nội dung/tài nguyên và các vấn đề còn tồn tại.

Local testing chứng minh được luồng cài module từ bộ APK cục bộ; không được mô tả như phép đo tốc độ tải thật từ Google Play.

## 5. Ma trận build cần B/C hỗ trợ

| Experiment | R8 + resource shrinking | Delivery của bốn feature | Mục đích |
|---|---|---|---|
| E0 | OFF | Install-time | Baseline kích thước không tối ưu |
| E1 | ON | Install-time | Đo riêng tác động tối ưu so với E0 |
| E2 | ON | On-demand | Đo riêng tác động delivery so với E1 |
| E3 tùy chọn | ON | On-demand + tài nguyên ảnh đã tối ưu | Đo thêm tác động tối ưu ảnh |

Giữ nội dung, mã, thiết bị, signing và cấu hình khác giống nhau khi so sánh. Thay đổi install-time/on-demand chỉ được áp dụng có chủ đích giữa snapshot E1 và E2. Không gộp kết luận E0→E2 thành tác động riêng của R8.

Do E1 cần feature install-time trong khi E2 cần on-demand, phải quản lý delivery mode như cấu hình build/snapshot có thể tái lập; không sửa tay thiếu ghi nhận giữa các lần build. Chốt cách tạo hai snapshot với D trước khi đo.

## 6. Definition of Done cho B và C

- [ ] Có skeleton và phiên bản toolchain được khóa, ghi lại.
- [ ] Có đúng năm module tổng cộng: `app`, `gallery`, `audio_guide`, `itinerary`, `food`.
- [ ] Bốn feature được khai báo on-demand trong artifact demo E2.
- [ ] Base Home dùng được ngay khi chưa cài feature nào.
- [ ] UI hiển thị trạng thái riêng cho mỗi feature và có tải/mở/retry phù hợp.
- [ ] Feature tải được, cài được, mở đúng màn hình và mở lại được sau khi app khởi động lại.
- [ ] Có xử lý pending, downloading, installing, confirmation, failed và canceled.
- [ ] Tài nguyên/nội dung chính của feature không nằm trong base app.
- [ ] Release R8 ON và OFF build được; R8 ON không làm hỏng luồng mở feature.
- [ ] Có AAB release dùng được với bundletool local-testing.
- [ ] Bàn giao cho D source SHA, lệnh build, artifact và quy trình demo.
- [ ] Không ghi số đo chưa thực hiện thành kết quả; không commit secret/keystore.

## 7. Danh sách bàn giao

**B bàn giao:** source Home/XML, cấu hình release R8/shrinking ON/OFF, danh sách thay đổi Gradle thuộc app, lệnh build Windows/Linux, ghi nhận build đã thực hiện và artifact AAB nếu có.

**C bàn giao:** contract bốn feature, implementation installer/navigator, source bốn dynamic feature, mô tả confirmation/error/lifecycle, danh sách tài nguyên và hướng dẫn demo từng luồng.

**Cả hai cùng bàn giao:** commit SHA tích hợp, danh sách thay đổi, vấn đề đã biết, hướng dẫn cài local-testing và xác nhận các mục Done tương ứng. Dùng những artifact và source cùng một SHA cho phép đo/báo cáo.

## 8. Lệnh build tham khảo trên Windows

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:bundleRelease -PsizeOptimization=false
.\gradlew.bat :app:bundleRelease -PsizeOptimization=true
```

Sao lưu/đổi tên AAB sau mỗi lần build vì Gradle có thể ghi đè output. Các lệnh chỉ là khung tham khảo; dùng task và thuộc tính đúng với cấu hình repo cuối cùng.
