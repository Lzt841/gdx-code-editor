# gdx-code-editor

一个基于 [libGDX](https://libgdx.com/) Scene2D 的代码编辑器 `Widget`，面向大文本编辑、代码高亮、代码块结构分析、折叠、查找替换，以及触摸屏和鼠标双操作模式。

项目当前同时包含：

- 可直接复用的编辑器核心组件 `CodeEditor`
- 行式文档模型 `CodeDocument`
- 多种内置语法高亮器
- 可扩展的代码结构提供器
- 一个桌面调试 Demo，方便验证交互、样式和功能

## 演示截图

### Java 编辑与右键菜单

![Java editor with context menu](images/img.png)

### JSON 高亮与行号/折叠区域

![JSON highlight](images/img_1.png)

### Python 缩进结构与彩虹括号

![Python indent structure](images/img_2.png)

### Disabled 状态展示

![Disabled state](images/img_3.png)

## 主要特性

- `CodeEditor` 以 Scene2D `Widget` 形式提供，可直接放入 `Table`、`Container`、`Stack` 等布局中
- 面向大文本的按行文档模型与可见区域布局计算
- 支持自动换行和横向滚动，默认关闭自动换行
- 支持固定/非固定行号区域
- 支持代码折叠、折叠徽标、点击已折叠内容自动展开
- 支持括号匹配、彩虹括号、彩虹代码块引导线
- 支持查找高亮、当前命中单独高亮、上一个/下一个命中导航
- 支持替换当前命中、全部替换，并支持撤销/重做
- 支持连续输入、连续删除、批量替换的复合撤销/重做
- 支持鼠标与触摸两套交互模式
- 触摸模式支持惯性滚动、选择手柄、长按菜单、双指缩放
- 鼠标模式支持拖拽选择、双击选词、右键菜单、滚轮滚动、Shift+滚轮横向滚动
- 支持 `disabled` 和 `readOnly` 两种状态
- 支持自定义语法高亮器、结构分析器、交互监听器、软键盘桥接
- 编辑器的大部分布局常量已经进入 `CodeEditorStyle`，例如边距、滚动条宽度、折叠图标大小、手柄大小、引导线间距等

## 内置高亮

当前内置高亮器包括：

- Java
- Kotlin
- JavaScript / TypeScript 风格
- Python
- JSON
- XML / HTML 风格
- Plain Text

入口类：

```java
editor.setHighlighter(BuiltinCodeHighlighters.java());
editor.setHighlighter(BuiltinCodeHighlighters.kotlin());
editor.setHighlighter(BuiltinCodeHighlighters.javascript());
editor.setHighlighter(BuiltinCodeHighlighters.python());
editor.setHighlighter(BuiltinCodeHighlighters.json());
editor.setHighlighter(BuiltinCodeHighlighters.xml());
editor.setHighlighter(BuiltinCodeHighlighters.plainText());
```

## 代码结构与折叠

当前提供两种结构分析器：

- `BraceCodeStructureProvider`
  适合 Java、Kotlin、JavaScript、JSON、XML 等基于括号或标签层级的文本
- `PythonIndentCodeStructureProvider`
  适合 Python 这类基于缩进的结构分析

可用于：

- 代码块高亮
- 折叠区域生成
- 彩虹引导线层级计算

## 快速运行

运行桌面 Demo：

```bash
./gradlew lwjgl3:run
```

Windows：

```powershell
./gradlew.bat lwjgl3:run
```

编译核心模块与桌面模块：

```powershell
./gradlew.bat core:compileJava lwjgl3:compileJava
```

打包桌面 Jar：

```powershell
./gradlew.bat lwjgl3:jar
```

## 通过 JitPack 使用

这个仓库已经按“只发布 `core` 模块”的方式准备好 JitPack。

### 发布方需要做什么

1. 把仓库推到 GitHub
2. 确保仓库名就是你希望暴露的名字，例如 `gdx-code-editor`
3. 创建并推送一个 tag，例如 `v1.0.0`
4. 打开 JitPack 页面触发构建：
   `https://jitpack.io/#<GitHub用户名>/gdx-code-editor/v1.0.0`

仓库中已经包含：

- `core` 模块的 `maven-publish` 配置
- `jitpack.yml`
- JDK 17 构建配置

JitPack 会只构建并发布 `core` 模块，不会把桌面 Demo 和 Android app 当成依赖产物。
同时，`core` 发布产物里会排除 `com.lzt841.demo` 下的演示代码。

### 使用方如何接入

在项目仓库中添加 JitPack：

```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

Gradle 依赖示例：

```gradle
dependencies {
    implementation 'com.github.lzt841.gdx-code-editor:core:v1.0.0'
}
```

如果你发布的是分支快照，也可以直接依赖分支名：

```gradle
dependencies {
    implementation 'com.github.lzt841.gdx-code-editor:core:main-SNAPSHOT'
}
```

说明：

- `groupId` 形式是 `com.github.<GitHub用户名>.<仓库名>`
- `artifactId` 当前是 `core`
- `version` 使用 Git tag、commit hash 或分支快照名

## 基本接入

最小示例：

```java
BitmapFont font = new BitmapFont();

CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
style.font = font;

CodeEditor editor = new CodeEditor(style);
editor.setText("public class Demo {\n    void test() {}\n}");
editor.setWrapEnabled(false);
editor.setLineNumbersFixed(true);
editor.setHighlighter(BuiltinCodeHighlighters.java());
editor.setStructureProvider(new BraceCodeStructureProvider());
```

放入 Scene2D：

```java
Table root = new Table();
root.setFillParent(true);
root.add(editor).expand().fill();
stage.addActor(root);
```

## 查找与替换

查找：

```java
editor.setSearchText("value");
editor.setSearchCaseSensitive(false);

int matchCount = editor.getSearchMatchCount();
boolean hasCurrent = editor.hasCurrentSearchMatch();
int currentOrdinal = editor.getCurrentSearchMatchOrdinal();

editor.findNextSearchMatch();
editor.findPreviousSearchMatch();
```

替换：

```java
editor.replaceCurrentSearchMatch("result");
editor.replaceAllSearchMatches("result");
```

说明：

- 当前命中会使用单独背景高亮
- 替换当前命中和全部替换都接入了撤销/重做历史
- 全部替换会作为一次复合编辑进入撤销栈

## 交互模式

```java
editor.setInteractionMode(CodeEditorInteractionMode.AUTO);
editor.setInteractionMode(CodeEditorInteractionMode.MOUSE);
editor.setInteractionMode(CodeEditorInteractionMode.TOUCH);
```

状态切换：

```java
editor.setReadOnly(true);
editor.setDisabled(false);
```

缩放：

```java
editor.setZoomScale(1.25f);
float zoom = editor.getZoomScale();
```

触摸模式下还支持双指捏合缩放。

## 样式配置

`CodeEditorStyle` 参考 `TextFieldStyle` 的使用方式，除背景、光标、选区等 `Drawable` 之外，也支持配置一批布局参数。

常见可配置项包括：

- `background`
- `focusedBackground`
- `disabledBackground`
- `gutterBackground`
- `currentBlock`
- `currentLine`
- `cursor`
- `selection`
- `searchHighlight`
- `currentSearchHighlight`
- `selectionHandle`
- `bracketMatch`
- `guide`
- `foldExpanded`
- `foldCollapsed`
- `foldBadge`
- `scrollbarTrack`
- `scrollbarKnob`

部分尺寸类配置：

- `textLeftPadding`
- `textRightPadding`
- `rowPadding`
- `gutterMinWidth`
- `gutterFoldIndicatorGap`
- `foldIndicatorSize`
- `foldIndicatorRightPadding`
- `scrollbarWidth`
- `scrollbarHitWidth`
- `scrollbarGap`
- `guideSpacing`
- `selectionHandleRadius`

示例：

```java
CodeEditor.CodeEditorStyle style = new CodeEditor.CodeEditorStyle();
style.font = font;
style.foldExpanded = myExpandedDrawable;
style.foldCollapsed = myCollapsedDrawable;
style.foldIndicatorSize = 10f;
style.selectionHandleRadius = 12f;
style.scrollbarWidth = 8f;
style.guideSpacing = 18f;
```

## 自定义扩展

### 自定义语法高亮

实现 `CodeHighlighter`：

- 返回语法高亮 spans
- 可选返回括号忽略区间，避免彩虹括号误判字符串、注释等语言特定区域

### 自定义结构分析

实现 `CodeStructureProvider`：

- 返回缩进层级
- 返回折叠区域
- 返回代码块结构信息

### 自定义交互菜单

实现 `CodeEditorInteractionListener`：

- 长按回调
- 右键回调
- 双击回调

Demo 里已经给出了“鼠标右键 / 触摸长按弹出菜单”的用法示例。

## 项目结构

- `core`
  核心模块，包含编辑器实现和 Demo 主逻辑
- `lwjgl3`
  桌面启动入口
- `android`
  Android 工程

核心源码目录：

- `core/src/main/java/com/lzt841/editor`
  编辑器核心
- `core/src/main/java/com/lzt841/editor/highlight`
  高亮接口与内置高亮器
- `core/src/main/java/com/lzt841/editor/structure`
  结构分析与折叠相关接口和实现
- `core/src/main/java/com/lzt841/editor/input`
  鼠标/触摸交互抽象
- `core/src/main/java/com/lzt841/demo`
  桌面调试 Demo

## 当前 Demo 可调试内容

- 语言示例切换
- 交互模式切换
- 自动换行开关
- 固定行号开关
- 彩虹括号开关
- 彩虹引导线开关
- 搜索文本输入与应用
- 上一个 / 下一个命中
- 替换当前 / 全部替换
- 只读 / 禁用状态切换
- 右键菜单和长按菜单示例
- 当前状态、匹配数、缩放倍率、最近交互事件显示

## 注意事项

- `CodeEditorStyle.font` 不能为空
- 如果多个控件共享同一个 `BitmapFont`，建议统一管理样式
- 默认结构分析器是 `BraceCodeStructureProvider`
- Python 等缩进语言建议显式设置 `PythonIndentCodeStructureProvider`
- 查找目前是纯文本匹配，不区分 token 类型

## 许可

仓库当前未单独声明许可证；如果需要开源发布，建议补充 `LICENSE` 文件。
