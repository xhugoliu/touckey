# 产品方向

## 一句话概述

Touckey 是运行在 Android 触屏上的可塑形可编程输入表面，通过标准 Bluetooth HID 向 Windows 和 macOS 输出键盘、鼠标与 consumer control report，桌面端无需安装任何伴生软件。

它可以借鉴 QMK/ZMK 的行为模型，但不能简单复制固件产品的假设：Touckey 的“键盘”不是焊死的物理矩阵，而是用户可以完全重排、缩放、分区和切换的软件界面。

## 产品定位

Touckey 不应被定位为远程鼠标应用，也不应被定位为一组固定快捷按钮。

它的核心是两层能力：

- 触屏布局层：让用户定义按键、手势区域、触控板区域、页面和视觉反馈
- Quantum 行为层：把触摸事件解释为按键、层切换、宏、tap-hold、one-shot、combo/chord 等行为

Bluetooth HID 是输出边界，负责让桌面端看到标准输入设备；快捷动作或媒体按钮在早期只用于验证连通性，不应成为长期产品能力的主要表达方式。

## 核心产品支柱

- 软件定义输入表面，而不是固定硬件键位
- 基于标准 Bluetooth HID，实现桌面端零配置
- 将布局、手势和行为模型解耦，让同一套 Quantum 能力服务默认布局和用户自定义布局
- 默认布局应是内置 keymap/profile，而不是硬编码快捷功能
- 触屏编辑、触觉反馈、可视状态和误触控制是产品体验的一部分
- 高级能力应通过宏、层、tap-hold、one-shot、combo/chord 等通用机制表达

## 产品阶段

### 阶段 A：HID 连通性验证

目标是证明 Android 端可以稳定作为 Bluetooth HID 组合设备工作：

- keyboard report
- relative mouse report
- consumer control report
- 基础点击、拖拽、滚动和横向滚动
- 一个固定测试界面，用来触发上述 report

这一阶段允许存在少量媒体键或系统快捷按钮，但它们只是 smoke test，不代表长期产品形态。

### 阶段 B：Quantum 行为核心

在 HID 传输可信后，产品重心转向输入行为模型：

- layout/keymap schema
- key press/release 与组合键
- macro
- layer
- tap-hold
- one-shot modifier
- combo/chord
- gesture-to-behavior mapping

这一阶段的重点是纯逻辑可测试、状态可解释、行为可组合，而不是先做完整编辑器。

### 阶段 C：可编辑触屏表面

当 Quantum 核心稳定后，再让用户塑形输入表面：

- 创建、移动、缩放和删除按键/区域
- 组合键区、手势区和触控板区
- 横竖屏不同布局
- Windows/macOS 或不同场景 profile
- 本地持久化、导入和导出

这一阶段让 Touckey 从“可运行的输入引擎”变成“用户可塑形的输入控制台”。

### 阶段 D：产品化与生态

在基础行为和编辑体验成熟后，再考虑：

- 更强的布局模板
- profile 分享
- 按应用切换布局
- 更完整的调试与回放工具
- 更成熟的视觉设计

## 目标用户

- 键盘、固件和自动化爱好者
- 想把手机变成自定义输入面的桌面用户
- 需要沙发场景、便携场景或临时控制面的用户
- 希望用触屏自由度突破物理键盘矩阵限制的人

## 应该重点打磨的体验

- 用户能快速理解“布局”和“行为”是两件事
- 触摸区域有清晰的按下、保持、层状态和宏运行反馈
- 自定义布局时有吸附、最小触控尺寸、误触边距和可访问的默认模板
- Quantum 行为在断连、切换 host、取消触摸、后台切换时不会残留按键状态
- 同一套默认布局和用户自定义布局都通过统一 schema/runtime 执行

## 产品定义

Touckey 对外应始终表现为一个标准 Bluetooth HID 组合设备：

- 键盘输入
- 相对鼠标移动
- 滚轮与横向滚动
- 音量、媒体等 consumer control 按键

Touckey 对内应围绕可编程输入模型组织：

- layout：按键、区域、页面、尺寸、位置、样式和触控响应范围
- behavior：key、macro、layer、tap-hold、one-shot、combo/chord、gesture mapping
- runtime：解释触摸事件、维护层状态、执行行为并产出 HID action stream
- backend：将 action stream 编码并发送为标准 HID report

## 早期版本的非目标

- 固定快捷遥控器作为长期产品形态
- 桌面端伴生软件
- 自定义桌面驱动
- 完整远程桌面或屏幕串流
- 伪装成原生 MacBook 触摸板或 Windows Precision Touchpad
- 在经典 HID 组合输入尚未稳定前，过早投入 BLE 专用协议工作
- 云同步、插件系统或 profile 市场
- 游戏手柄支持

## 为什么不优先追求原生触摸板协议

第一阶段最重要的是可靠地产出标准 HID 行为，而不是协议“纯正”。
Touckey 的差异化来自可塑形触屏表面与 Quantum 行为层，不来自模拟某个平台的私有触摸板体验。

## 工作原则

先证明 Bluetooth HID transport 可靠，再构建可测试的 Quantum 行为核心；随后再把触屏编辑器和用户自定义能力铺开。
