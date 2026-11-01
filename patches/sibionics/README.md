# 硅基动感（SiBionics）支持补丁

本目录包含让 xDrip+ 接收**硅基动感（SiBionics）官方 App** 血糖广播的完整补丁方案。

## 组成

| 文件 | 用途 |
|------|------|
| `Aaa.smali` / `Aab.smali` | 打在硅基官方 App（`com.sisensing.sisensingcgm`）上的 smali 补丁，使官方 App 每收到一个血糖值就广播一条 Intent（来源：diyaps/cgmpatches） |
| `../..`（仓库内）`SibionicsAppReceiver.java` | xDrip+ 端的广播接收器，本仓库已内置并注册，无需额外操作 |

## 广播协议

- Action：`com.eveningoutpost.dexdrip.SI_APP`
- Extra：`sgv`（血糖值 mg/dl）、`direction`（趋势）、`date`（时间戳）、`manongCode`（校验码 `magic372manongxyes`）等

## 使用方法

### 1. 硅基官方 App 打广播补丁

1. 下载硅基动感官方 APK
2. 反编译（apktool），将 `Aab.smali` 放入 `smali/com/sisensing/sisensingcgm/` 目录
3. 在官方 App 收到血糖数据的入口调用 `Aab.g(context).s(bloodGlucoseEntity)`（等价于注入一行钩子代码）
4. 回编译并签名安装

### 2. 安装本仓库编译的 xDrip+ APK

1. 安装编译出的 xDrip+ APK（已内置 `SibionicsAppReceiver`）
2. 打开 xDrip+ → 菜单 → **Start Sensor**（新建传感器记录）
3. 保持硅基补丁版官方 App 在前台/后台运行
4. xDrip+ 会自动接收广播并写入血糖、趋势、报警、Nightscout 上传等均正常生效

## 说明

- 广播数据来自硅基官方 App 内部已校准的血糖值，xDrip+ 直接作为计算值入库
- 如需 AAPS 闭环，保持 xDrip+ 的 Inter-app 广播开启即可
