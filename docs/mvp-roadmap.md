# MVP 路线图

## 当前阶段

当前目标是把项目口径从“手机控制面板/快捷遥控器”调转为“可编程触屏输入表面”，并让工程路线围绕 HID transport、Quantum 行为核心和可编辑布局层展开。

## 产品阶段视角

为了避免把测试入口误当成长期产品能力，产品阶段单独定义如下。

### P0：HID 可行性验证版

- 证明 Android 端能够作为 Bluetooth HID 组合设备稳定工作
- 证明 keyboard、relative mouse、consumer control report 都能被桌面端正确接收
- 证明手机触屏可以作为基础输入事件来源
- 允许保留少量快捷/媒体测试按钮，但它们只服务连通性验证

### P1：Quantum 行为核心版

- 建立 layout/keymap/behavior 的基础 schema
- 将当前固定 UI 输入事件改为进入统一行为 runtime
- 支持普通按键、修饰键、组合键、基础宏和层切换
- 默认布局也通过同一套 schema 表达

### P2：触屏布局可编辑版

- 允许用户调整按键和区域的位置、尺寸、标签和分组
- 支持触控板区、手势区、键区混合排布
- 支持横竖屏或不同场景 profile
- 本地持久化配置，并为后续导入导出留边界

### P3：进阶可编程版

- tap-hold
- one-shot modifier
- combo/chord
- 更复杂的 gesture-to-behavior mapping
- 宏编辑与调试反馈

### P4：产品化版

- 更好的设备配对体验
- 更成熟的布局编辑器
- profile 模板
- 按应用或场景切换布局
- 布局导入与导出
- 更成熟的视觉设计

## 工程阶段

## 阶段 0

项目定义与范围确认：

- 明确 Touckey 是可编程触屏输入表面，而不是固定快捷遥控器
- 定义 HID transport 与 Quantum runtime 的边界
- 定义 layout/keymap/behavior schema 的最小形态
- 找到最小但可信的演示版本

## 阶段 1

Bluetooth HID 核心验证：

- Android 应用能够注册为 Bluetooth HID 组合设备
- 桌面端无需额外软件即可配对
- 键盘 report 可发送普通按键与修饰键组合
- 触控区域可驱动相对指针移动
- 基础单击、右击、拖拽、纵向滚动和横向滚动可用
- consumer control report 可验证媒体键等基础用途

成功标准：

- 至少在一台现代 Windows 设备和一台现代 Mac 上可用
- 重连流程清晰可理解
- 断连、切换 host、取消触摸时不会残留按键或鼠标按下状态
- 延迟足以支撑触屏输入和行为测试

## 阶段 2

Quantum 行为核心：

- 定义 `Layout`、`Zone`、`Keymap`、`Behavior`、`LayerState` 等核心模型
- 将固定按键/触控事件转换为 layout hit-test 结果
- 行为 runtime 产出按键、鼠标、滚轮和 consumer control 的 action stream
- 支持默认 keymap，并让默认布局走同一套 runtime
- 为 macro、layer、tap-hold 等行为建立纯 Kotlin 单元测试

成功标准：

- UI 不再直接表达长期产品语义
- 默认布局不是硬编码快捷功能，而是可替换的内置 profile
- 行为核心可以在不启动 Android UI 的情况下测试

## 阶段 3

触屏布局可编辑层：

- 用户可以移动、缩放、增删按键和区域
- 支持按键区、手势区、触控板区并存
- 支持横屏/竖屏不同布局
- 支持最小触控尺寸、吸附、误触边距和布局校验
- 本地保存用户 profile

成功标准：

- 用户无需改代码即可塑造一个可用输入表面
- 编辑器产出的配置可直接被 Quantum runtime 执行

## 阶段 4

进阶行为层：

- tap-hold 行为
- one-shot modifier
- combo 或 chord 动作
- 宏动作与宏状态反馈
- 更复杂的手势到行为映射

成功标准：

- 可编程能力已经服务真实工作流，而不只是概念展示
- 高级行为不会破坏基础输入稳定性

## 阶段 5

打磨与产品化：

- 更好的设备配对体验
- profile 模板
- 按应用或场景定制布局
- 布局导入与导出
- 更成熟的视觉设计

## 在证明必要之前暂缓的事项

- 固定快捷遥控器路线
- 原生精密触摸板仿真
- 以 BLE HOGP 为中心的架构
- 多设备同步
- 云端备份
- 插件系统

## 第一个值得骄傲的 Demo

一个运行在手机上的可编程输入表面：

- 一个由 schema 驱动的默认布局
- 一个支持点击、滚动和拖拽的触控区
- 一个能通过 Quantum runtime 触发键盘、鼠标和 consumer control report 的行为层

这个 Demo 可以包含应用切换、媒体控制、窗口管理或浏览器导航等测试动作，但它们应作为宏/行为样例存在，而不是硬编码产品功能。
