# 开发者指南

## 构建

使用 Gradle 构建：

```
./gradlew build
```

编译并仅编译 Java：

```
./gradlew compileJava
```

## 运行与测试

- 在本地搭建 Paper 服务器，将生成的 jar 放入 `plugins/` 测试。
- 若需调试 web 面板，可在本地浏览器访问 `http://localhost:<web.port>/`。

## 打包建议

- 使用 `shadow` 插件生成 fat-jar（包含 org.json、sqlite-jdbc、Vault API）以便直接在服务器中部署。
- 注意不要将服务器私钥或敏感配置写入 repo。