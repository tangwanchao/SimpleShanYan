[参考文档](http://shanyan.253.com/document/details?lid=519&cid=93&pc=28&pn=%25E9%2597%25AA%25E9%25AA%258CSDK#Q33d1)


## 基础配置
1. 导入项目 shanyan 模块下 aar
2. 配置压缩
   在 raw 下新建 keep.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="
    @anim/umcsdk*,
    @drawable/umcsdk*,
    @layout/layout_shanyan*,
    @id/shanyan_view*" />
```
3. 允许明文请求
```xml
<application
    android:name=".view.MyApplication"
    android:usesCleartextTraffic="true"/>
```