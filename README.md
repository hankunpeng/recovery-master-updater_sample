# 背景信息

本项目是 [updater_sample](https://android.googlesource.com/platform/bootable/recovery/+/master/updater_sample/) 的 AndroidStudio Project 版本，用来调研 FOTA 升级。

分割线包裹的内容来源于 https://android.googlesource.com/platform/bootable/recovery/+/master/updater_sample/README.md 。


-------------------------------- 分割线 - 开始 --------------------------------


# SystemUpdaterSample

This app demonstrates how to use Android system updates APIs to install
[OTA updates](https://source.android.com/devices/tech/ota/). It contains a
sample client for `update_engine` to install A/B (seamless) updates.

A/B (seamless) update is available since Android Nougat (API 24), but this sample
targets the latest android.


## Workflow

SystemUpdaterSample app shows list of available updates on the UI. User is allowed
to select an update and apply it to the device. App shows installation progress,
logs can be found in `adb logcat`. User can stop or reset an update. Resetting
the update requests update engine to cancel any ongoing update, and revert
if the update has been applied. Stopping does not revert the applied update.


## Update Config file

In this sample updates are defined in JSON update config files.
The structure of a config file is defined in
`com.example.android.systemupdatersample.UpdateConfig`, example file is located
at `res/raw/sample.json`.

In real-life update system the config files expected to be served from a server
to the app, but in this sample, the config files are stored on the device.
The directory can be found in logs or on the UI. In most cases it should be located at
`/data/user/0/com.example.android.systemupdatersample/files/configs/`.

// TODO - 1 - No such file or directory, why?
```bash
$ adb shell ls -l /data/user/0/com.example.android.systemupdatersample/files/configs/
ls: /data/user/0/com.example.android.systemupdatersample/files/configs/: No such file or directory
```

SystemUpdaterSample app downloads OTA package from `url`. In this sample app
`url` is expected to point to file system, e.g. `file:///data/my-sample-ota-builds-dir/ota-002.zip`.

// TODO - 2 - Where is the ota zip?

If `ab_install_type` is `NON_STREAMING` then app checks if `url` starts
with `file://` and passes `url` to the `update_engine`.

If `ab_install_type` is `STREAMING`, app downloads only the entries in need, as
opposed to the entire package, to initiate a streaming update. The `payload.bin`
entry, which takes up the majority of the space in an OTA package, will be
streamed by `update_engine` directly. The ZIP entries in such a package need to be
saved uncompressed (`ZIP_STORED`), so that their data can be downloaded directly
with the offset and length. As `payload.bin` itself is already in compressed
format, the size penalty is marginal.

if `ab_config.force_switch_slot` set true device will boot to the
updated partition on next reboot; otherwise button "Switch Slot" will
become active, and user can manually set updated partition as the active slot.

Config files can be generated using `tools/gen_update_config.py`.
Running `./tools/gen_update_config.py --help` shows usage of the script.


## Sample App State vs UpdateEngine Status

UpdateEngine provides status for different stages of update application
process. But it lacks of proper status codes when update fails.

This creates two problems:

1. If sample app is unbound from update_engine (MainActivity is paused, destroyed),
   app doesn't receive onStatusUpdate and onPayloadApplicationCompleted notifications.
   If app binds to update_engine after update is completed,
   only onStatusUpdate is called, but status becomes IDLE in most cases.
   And there is no way to know if update was successful or not.

2. This sample app demostrates suspend/resume using update_engins's
   `cancel` and `applyPayload` (which picks up from where it left).
   When `cancel` is called, status is set to `IDLE`, which doesn't allow
   tracking suspended state properly.

To solve these problems sample app implements its own separate update
state - `UpdaterState`. To solve the first problem, sample app persists
`UpdaterState` on a device. When app is resumed, it checks if `UpdaterState`
matches the update_engine's status (as onStatusUpdate is guaranteed to be called).
If they doesn't match, sample app calls `applyPayload` again with the same
parameters, and handles update completion properly using `onPayloadApplicationCompleted`
callback. The second problem is solved by adding `PAUSED` updater state.


## Sample App UI

### Text fields

- `Current Build:` - shows current active build.
- `Updater state:` - SystemUpdaterSample app state.
- `Engine status:` - last reported update_engine status.
- `Engine error:` - last reported payload application error.

### Buttons

- `Reload` - reloads update configs from device storage.
- `View config` - shows selected update config.
- `Apply` - applies selected update config.
- `Stop` - cancel running update, calls `UpdateEngine#cancel`.
- `Reset` - reset update, calls `UpdateEngine#resetStatus`, can be called
            only when update is not running.
- `Suspend` - suspend running update, uses `UpdateEngine#cancel`.
- `Resume` - resumes suspended update, uses `UpdateEngine#applyPayload`.
- `Switch Slot` - if `ab_config.force_switch_slot` config set true,
            this button will be enabled after payload is applied,
            to switch A/B slot on next reboot.


## Sending HTTP headers from UpdateEngine

Sometimes OTA package server might require some HTTP headers to be present,
e.g. `Authorization` header to contain valid auth token. While performing
streaming update, `UpdateEngine` allows passing on certain HTTP headers;
as of writing this sample app, these headers are `Authorization` and `User-Agent`.

`android.os.UpdateEngine#applyPayload` contains information on
which HTTP headers are supported.


## Used update_engine APIs

### UpdateEngine#bind

Binds given callbacks to update_engine. When update_engine successfully
initialized, it's guaranteed to invoke callback onStatusUpdate.

### UpdateEngine#applyPayload

Start an update attempt to download an apply the provided `payload_url` if
no other update is running. The extra `key_value_pair_headers` will be
included when fetching the payload.

`key_value_pair_headers` argument also accepts properties other than HTTP Headers.
List of allowed properties can be found in `system/update_engine/common/constants.cc`.

### UpdateEngine#cancel

Cancel the ongoing update. The update could be running or suspended, but it
can't be canceled after it was done.

### UpdateEngine#resetStatus

Reset the already applied update back to an idle state. This method can
only be called when no update attempt is going on, and it will reset the
status back to idle, deleting the currently applied update if any.

### Callback: onStatusUpdate

Called whenever the value of `status` or `progress` changes. For
`progress` values changes, this method will be called only if it changes significantly.
At this time of writing this doc, delta for `progress` is `0.005`.

`onStatusUpdate` is always called when app binds to update_engine,
except when update_engine fails to initialize.

### Callback: onPayloadApplicationComplete

Called whenever an update attempt is completed or failed.


## Running on a device

The commands are expected to be run from `$ANDROID_BUILD_TOP` and for demo
purpose only.

### Without the privileged system permissions

1. Compile the app `mmma -j bootable/recovery/updater_sample`.
2. Install the app to the device using `adb install <APK_PATH>`.
3. Change permissions on `/data/ota_package/` to `0777` on the device.
4. Set SELinux mode to permissive. See instructions below.
5. Add update config files; look above at [Update Config file](#Update-Config-file).
6. Push OTA packages to the device.
7. Run the sample app.

### With the privileged system permissions

To run sample app as a privileged system app, it needs to be installed in `/system/priv-app/`.
This directory is expected to be read-only, unless explicitly remounted.

The recommended way to run the app is to build and install it as a
privileged system app, so it's granted the required permissions to access
`update_engine` service as well as OTA package files. Detailed steps are as follows:

1. [Prepare to build](https://source.android.com/setup/build/building)
2. Add the module (SystemUpdaterSample) to the `PRODUCT_PACKAGES` list for the
   lunch target.
   e.g. add a line containing `PRODUCT_PACKAGES += SystemUpdaterSample`
   to `device/google/marlin/device-common.mk`.
3. [Whitelist the sample app](https://source.android.com/devices/tech/config/perms-whitelist)
   * Add
   ```
    <privapp-permissions package="com.example.android.systemupdatersample">
        <permission name="android.permission.ACCESS_CACHE_FILESYSTEM"/>
    </privapp-permissions>
   ```
   to `frameworks/base/data/etc/privapp-permissions-platform.xml`
5. Build sample app `make -j SystemUpdaterSample`.
6. Build Android `make -j`
7. [Flash the device](https://source.android.com/setup/build/running)
8. Add update config files; look above at `## Update Config file`;
   `adb root` might be required.
9. Push OTA packages to the device if there is no server to stream packages from;
   changing of SELinux labels of OTA packages directory might be required
   `chcon -R u:object_r:ota_package_file:s0 /data/my-sample-ota-builds-dir`
10. Run the sample app.


## Development

- [x] Create a UI with list of configs, current version,
      control buttons, progress bar and log viewer
- [x] Add `PayloadSpec` and `PayloadSpecs` for working with
      update zip file
- [x] Add `UpdateConfig` for working with json config files
- [x] Add applying non-streaming update
- [x] Prepare streaming update (partially downloading package)
- [x] Add applying streaming update
- [x] Add stop/reset the update
- [x] Add demo for passing HTTP headers to `UpdateEngine#applyPayload`
- [x] [Package compatibility check](https://source.android.com/devices/architecture/vintf/match-rules)
- [x] Deferred switch slot demo
- [x] Add UpdateManager; extract update logic from MainActivity
- [x] Add Sample app update state (separate from update_engine status)
- [x] Add smart update completion detection using onStatusUpdate
- [x] Add pause/resume demo
- [x] Verify system partition checksum for package


## Running tests

The commands are expected to be run from `$ANDROID_BUILD_TOP`.

1. Build `make -j SystemUpdaterSample` and `make -j SystemUpdaterSampleTests`.
2. Install app
   `adb install $OUT/system/priv-app/SystemUpdaterSample/SystemUpdaterSample.apk`
3. Install tests
   `adb install $OUT/testcases/SystemUpdaterSampleTests/SystemUpdaterSampleTests.apk`
4. Run tests
   `adb shell am instrument -w com.example.android.systemupdatersample.tests/android.support.test.runner.AndroidJUnitRunner`
5. Run a test file
   ```
   adb shell am instrument \
     -w -e class com.example.android.systemupdatersample.UpdateManagerTest#applyUpdate_appliesPayloadToUpdateEngine \
     com.example.android.systemupdatersample.tests/android.support.test.runner.AndroidJUnitRunner
   ```


## Accessing `android.os.UpdateEngine` API

`android.os.UpdateEngine` APIs are marked as `@SystemApi`, meaning only system
apps can access them.


## Getting read/write access to `/data/ota_package/`

Access to cache filesystem is granted only to system apps.


## Setting SELinux mode to permissive (0)

```txt
local$ adb root
local$ adb shell
android# setenforce 0
android# getenforce
```


## License

SystemUpdaterSample app is released under
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).


-------------------------------- 分割线 - 结束 --------------------------------


## 补充信息

### FOTA 涉及 [实现 A/B 更新](https://source.android.google.cn/devices/tech/ota/ab/ab_implement)，生成 OTA 更新包要注意匹配各自手头的硬件（A 样件，或 B 样件）。

### FOTA 涉及 [通过 HTTPS 和 SSL 确保安全](https://developer.android.google.cn/training/articles/security-ssl)，比如 HU 独立升级的场景。

### A 样件上通过命令行验证本地升级

1 代码全编译

```bash
cd ~/fx/pangu_master/
source env_setup.sh dianka ic421_A-userdebug
make all | tee ~/Documents/make.log
```

2 刷自己编译出来的系统

```bash
cd ~/fx/pangu_master/LINUX/android/out/target/product/ic421_A/
adb reboot bootloader
fastboot flash boot boot.img
fastboot flash vendor vendor.img
fastboot flash system system.img
fastboot reboot
```

3 准备升级包

```bash
$ adb root
$ adb disable-verity
$ adb reboot

$ adb root
$ adb remount

$ cp ～/fx/pangu_master/LINUX/android/out/target/product/ic421_A/ic421_A-ota-eng.hankunpeng.zip ～/fx/pangu_master/scripts/
$ cd ～/fx/pangu_master/scripts/
$ python ota_update.py ic421_A-ota-eng.hankunpeng.zip
update_engine_client --update --follow --payload=file:///data/ota_package/g6p_update.zip --offset=7961 --size=1294713948 --headers="FILE_HASH=/D1pEWqr2At2ZkTSxYl5PsppC9Q+kDNwmiazHaNyYnk=
FILE_SIZE=1294713948
METADATA_HASH=RsYX8f1E/BfTLuYA0YPjNXZ6quPdp3zzuJVylcg1vtA=
METADATA_SIZE=119474
"
$ adb push ic421_A-ota-eng.hankunpeng.zip /data/ota_package/g6p_update.zip
```

4 验证升级

```bash
$ adb shell
ic421_A:/ # update_engine_client --update --follow --payload=file:///data/ota_package/g6p_update.zip --offset=7961 --size=1294713948 --headers="FILE_HASH=/D1pEWqr2At2ZkTSxYl5PsppC9Q+kDNwmiazHaNyYnk=
> FILE_SIZE=1294713948
> METADATA_HASH=RsYX8f1E/BfTLuYA0YPjNXZ6quPdp3zzuJVylcg1vtA=
> METADATA_SIZE=119474
> "
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_IDLE (0), 0)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_UPDATE_AVAILABLE (2), 0)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 1.88034e-05)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0100285)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0200383)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.030048)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0400577)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0500675)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0600772)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0700869)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0800967)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.0901064)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.100116)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.110126)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.120136)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.130145)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.140155)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.150165)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.160175)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.170184)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.180194)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.190204)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.200214)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.210223)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.220233)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.230243)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.240252)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.250262)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.260272)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.270282)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.280291)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.290301)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.300311)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.310321)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.32033)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.33034)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.34035)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.35036)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.360369)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.370379)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.380389)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.390398)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.400408)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.410418)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.420428)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.430437)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.440447)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.450457)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.460467)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.470476)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.480486)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.490496)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.500506)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.510515)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.520525)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.530535)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.540545)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.550554)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.560564)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.570574)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.580583)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.590593)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.600603)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.610613)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.620622)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.630632)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.640642)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.650652)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.660661)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.670671)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.680681)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.690691)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.7007)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.71071)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.72072)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.730729)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.740739)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.750749)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.760759)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.770768)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.780778)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.790788)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.800798)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.810807)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.820817)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.830827)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.840837)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.850846)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.860856)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.870866)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.880876)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.890885)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.900895)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.910905)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.920914)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.930924)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.940934)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.950944)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.960953)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.970963)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.980973)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_DOWNLOADING (3), 0.990983)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_FINALIZING (5), 0)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_FINALIZING (5), nan)
[INFO:update_engine_client_android.cc(90)] onStatusUpdate(UPDATE_STATUS_UPDATED_NEED_REBOOT (6), 0)
[INFO:update_engine_client_android.cc(98)] onPayloadApplicationComplete(ErrorCode::kSuccess (0))
```

由 `ErrorCode::kSuccess (0)` 可知升级成功了。
