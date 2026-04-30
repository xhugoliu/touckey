# 架构草图

## 目标

定义 Touckey 的长期软件形态：一个 Android 端可编程触屏输入表面，通过标准 Bluetooth HID 暴露为桌面端输入设备。

本文档刻意保持在高层，不进入具体实现细节。

## 系统边界

应用完全运行在 Android 端，并将自己暴露为一个标准 Bluetooth HID 外设。
桌面端只应看到标准输入设备，不应依赖额外软件。

Touckey 与 QMK/ZMK 等固件的关键差异是：输入表面不是物理矩阵，而是用户可编辑的软件布局。架构不能把“键位排布固定”作为隐含前提。

## 建议的 Report 类型

第一版建议从三类 report 开始：

- keyboard report
- relative mouse report
- consumer control report

这样既能保持较好的兼容性，也足以支撑 Quantum 行为核心和触屏布局层的早期验证。

## 建议的子系统

### 1. 应用外壳层

职责：

- 应用启动
- 权限管理
- 导航
- 前台服务生命周期接入
- 横竖屏与安全区策略

### 2. HID 服务层

职责：

- 托管 `BluetoothHidDevice` 生命周期
- 注册和注销 HID app
- 对外暴露连接状态
- 通过前台服务支撑应用在普通后台场景下继续工作
- 将已经编码的 report 发送给当前 host

### 3. 会话控制器

职责：

- 跟踪当前 host 设备
- 管理连接、断开与重连流程
- 在断连、切换 host 或取消输入时释放所有保持状态
- 暴露当前是否可以发送输入

### 4. 布局模型

职责：

- 描述页面、按键、触控板区、手势区和其他输入区域
- 描述位置、尺寸、标签、样式、触控响应范围和分组
- 支持横屏、竖屏和不同场景 profile
- 将默认布局与用户布局都表达为同一种 schema

输出：

- 可被运行时 hit-test 的 layout tree

说明：

- 当前硬编码键盘布局只应视为早期测试布局
- 长期不应把固定矩阵或固定快捷按钮写入产品模型

### 5. 输入表面运行时

职责：

- 接收 Android 触摸事件
- 对布局区域做 hit-test
- 识别按下、移动、释放、取消、多指、长按和滑动
- 将低层触摸流规范化为 key/zone/gesture 事件

输出：

- 如 `key down`、`key up`、`zone drag`、`gesture scroll` 之类的输入事件

### 6. Quantum 行为引擎

职责：

- 解释 keymap 与 layer state
- 执行 key press/release、组合键、宏、层切换、tap-hold、one-shot、combo/chord
- 将手势事件映射到通用行为，而不是硬编码快捷动作
- 保证取消、断连和 host 切换时不会残留保持状态

输出：

- 如“发送按键”“保持修饰键”“执行宏”“切换层”“发送鼠标移动/滚轮”之类的 action stream

说明：

- 早期快捷动作只能作为连通性测试或宏样例
- 长期产品能力应通过通用 behavior 表达

### 7. 动作分发器

职责：

- 接收 Quantum 行为引擎产出的 action stream
- 将 action 映射到键盘、鼠标或 consumer control report
- 统一处理顺序、延时、释放行为和发送失败反馈

### 8. 配置存储

职责：

- 持久化布局、keymap、profile 与用户偏好
- 支持未来的导入与导出
- 将默认 profile 与用户修改隔离开

### 9. 编辑器界面

职责：

- 让用户创建、移动、缩放、分组和删除触屏输入区域
- 用适合手机的方式呈现层、宏和行为绑定
- 提供吸附、最小触控尺寸、误触边距和布局校验
- 让高级功能可发现，但不变成使用门槛

## 建议的事件流

1. 用户触摸屏幕上的某个布局区域。
2. 输入表面运行时对触摸点做 hit-test，并产出规范化输入事件。
3. Quantum 行为引擎根据当前 layer、keymap 和触摸时序解释事件。
4. 动作分发器将 action stream 转换为一个或多个 HID report。
5. HID 服务将 report 发送给当前连接的 host。

## 现实约束

- Android 端的 HID 外设行为对生命周期与前台状态较为敏感
- Windows 与 macOS 对滚轮、横向滚动和部分快捷键组合的解释可能不同
- 手势能力应建立在标准 HID 行为之上，而不是建立在对原生触摸板协议的假设上
- 触屏没有物理键程和键帽边界，需要靠布局校验、视觉反馈和触觉反馈降低误触
- 自定义自由度不能绕过最小可用尺寸、边距和状态释放规则

## 参考实现

工作区中已经克隆了两个有参考价值的项目：

- `hid-barcode-scanner`
  本地：[hid-barcode-scanner](/Users/xhugoliu/Projects/hid-barcode-scanner)
  GitHub：https://github.com/Fabi019/hid-barcode-scanner
  更适合参考生命周期管理和 HID 服务结构
- `Kontroller`
  本地：[Kontroller](/Users/xhugoliu/Projects/Kontroller)
  GitHub：https://github.com/meromelo/Kontroller
  更适合参考组合 descriptor 与触控到鼠标行为的实验实现

## 技术决策原则

优先：

- 标准 HID 兼容性
- 软件定义布局
- 显式行为映射
- 输入表面运行时、Quantum 行为引擎与 HID 传输之间清晰的内部边界
- 默认 profile 与用户 profile 走同一套 schema/runtime

避免：

- 将 UI 手势直接耦合到原始蓝牙调用
- 把复杂行为塞进单一 Activity
- 把早期测试快捷按钮误当成产品核心
- 过早投入那些无法提升可编程触屏输入表面的协议实验
