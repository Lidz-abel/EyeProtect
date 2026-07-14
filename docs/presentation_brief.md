# EyeProtect 汇报辅助文档

## 1. 实现过程：视频帧的流向

本项目在 Android 平板端运行，核心链路是：

```text
前置摄像头
→ CameraX Preview
→ CameraX ImageAnalysis
→ ML Kit Face Detection
→ 人脸与左右眼关键点
→ 红色眼部点绘制
→ 距离估计与性能指标输出
```

具体过程如下：

1. 用户点击 `Start Front Camera` 后，App 申请摄像头权限。
2. 权限通过后，`CameraX` 选择前置摄像头：

```kotlin
CameraSelector.DEFAULT_FRONT_CAMERA
```

3. CameraX 同时绑定两个通道：

```text
Preview：负责显示实时摄像头画面
ImageAnalysis：负责把视频帧送给算法处理
```

4. `ImageAnalysis` 每收到一帧，会进入 `EyePositionAnalyzer.analyze(...)`。
5. 为了降低 CPU，占用现在设置为每 4 帧才检测一次：

```text
第 1、2、3 帧：直接丢弃
第 4 帧：送入 ML Kit 检测
```

6. 需要检测的帧会被转换为 ML Kit 输入：

```kotlin
InputImage.fromMediaImage(mediaImage, rotationDegrees)
```

7. ML Kit 执行人脸检测：

```kotlin
detector.process(inputImage)
```

8. 检测成功后，App 从 ML Kit 结果中读取：

```text
FaceContour.LEFT_EYE
FaceContour.RIGHT_EYE
FaceLandmark.LEFT_EYE
FaceLandmark.RIGHT_EYE
```

9. App 把左右眼关键点映射到预览画面上，用红色点显示。
10. App 使用左右眼中心点之间的像素距离，估计眼睛到屏幕的距离。

当前版本为了降低 CPU，已经删除：

```text
左右眼 ROI 裁剪
左右眼小窗显示
眼部边界框计算
眼部矩形框绘制
每帧 ML Kit 检测
```

因此现在是轻量化版本：

```text
只保留眼部红点 + 眼部中心点 + 距离估计 + 性能指标
```

## 2. 最终呈现内容与数值意义

界面和 Logcat 中主要显示以下内容。

### fps

```text
fps = 当前检测结果更新频率
```

它不是摄像头原始帧率，而是算法检测结果的更新频率。因为现在每 4 帧检测一次，所以 fps 会低于摄像头真实帧率。

意义：

```text
fps 越高，检测反馈越流畅
fps 越低，说明检测频率或设备性能较低
```

### latency_ms

```text
latency_ms = 单次 ML Kit 检测耗时
```

它表示一帧图像从送入 ML Kit 到返回检测结果所花的时间。

意义：

```text
数值越小，检测越快
数值越大，算法处理越重
```

### source_width / source_height

```text
source_width / source_height = 实际送入分析器的视频帧尺寸
```

虽然 App 提供了 `Low`、`Balanced`、`HD` 三挡选择，但 CameraX 会根据设备能力选择最接近的实际尺寸。因此以界面或日志中的 `source` 为准。

示例：

```text
source=320x240
source=720x720
```

意义：

```text
分辨率越高，点位可能更细，但 CPU 可能更高
分辨率越低，CPU 更低，但精度可能下降
```

### face_count

```text
face_count = 当前画面中检测到的人脸数量
```

当前项目主要按第一张脸进行展示和距离估计。

意义：

```text
face_count = 0：未检测到人脸
face_count = 1：正常单人检测
face_count > 1：多人场景，结果可能不适合做个人护眼判断
```

### left_eye_center / right_eye_center

```text
left_eye_center  = 左眼关键点中心
right_eye_center = 右眼关键点中心
```

它们是根据左右眼关键点平均计算出的中心坐标。

注意：

```text
这里的中心点是眼部区域中心，不是瞳孔中心，也不是虹膜中心。
```

意义：

```text
用于显示眼部位置
用于计算双眼像素距离
用于估计眼睛到屏幕的距离
```

### eye_distance_px

```text
eye_distance_px = 左右眼中心点之间的像素距离
```

人离屏幕越近，画面中的双眼距离通常越大；人离屏幕越远，双眼距离通常越小。

意义：

```text
这是距离估计的核心输入
```

### estimated_distance_cm

```text
estimated_distance_cm = 估计的眼睛到屏幕距离
```

估计方法是一次校准法：

```text
用户在约 40cm 处点击 Calibrate 40cm
记录此时 eye_distance_px
之后根据当前 eye_distance_px 的变化估计距离
```

计算思想：

```text
当前距离 ≈ 40cm × 校准时双眼像素距离 / 当前双眼像素距离
```

注意：

```text
这是估计值，不是物理传感器直接测得的真实距离。
```

### distance_state

```text
distance_state = 距离状态
```

当前规则：

```text
TOO_CLOSE：距离小于 30cm
NORMAL：距离约 30cm 到 70cm
FAR：距离大于 70cm
NEEDS_CALIBRATION：还没有完成 40cm 校准
UNKNOWN：数据不足或置信度较低
```

意义：

```text
用于护眼提醒，例如距离过近时提示用户远离屏幕。
```

### distance_confidence

```text
distance_confidence = 距离估计置信度
```

它根据眼距比例、人脸数量等信息给出一个粗略可信度。

意义：

```text
置信度越高，距离状态越可信
置信度较低时，不应触发强提醒
```

## 3. 汇报时可以强调的结论

可以这样总结：

```text
本项目通过 CameraX 获取前置摄像头视频帧，
使用 ML Kit Face Detection 提取人脸和左右眼关键点，
再通过自定义规则计算左右眼中心点和双眼像素距离，
最终估计眼睛到屏幕的距离，并用红色点实时标出眼部位置。
```

轻量化优化点：

```text
每 4 帧检测一次
删除 ROI 裁剪
删除眼部边界框
只绘制红色关键点
后台和锁屏时释放摄像头
```

当前实现边界：

```text
没有自训练模型
没有虹膜检测
没有视线落点预测
距离是基于 40cm 校准后的视觉估计
```
