# 贡献指南

## 本地验证

要求 Java 21。无需预装 Maven：

```powershell
.\mvnw.cmd clean verify
java -jar target\workspace-agent-0.2.0.jar --version
```

Linux/macOS 使用 `./mvnw clean verify`。

## 变更要求

- 新规则必须提供可核验证据、行动建议和严重度。
- 涉及文件访问的功能必须经过 `WorkspaceGuard` 或等价真实路径校验。
- 不得在测试、示例或文档中提交真实密钥。
- 行为变更需要对应 JUnit 测试和 README 更新。
- 健康分变化必须说明原因，不能只为提高项目自评分而放宽规则。

## 提交前检查

1. 运行 `clean verify`。
2. 运行 `--check-json .` 并确认输出可被 JSON 解析器读取。
3. 运行 `--daily . agent-state 90` 验证无人值守路径。
4. 更新 `CHANGELOG.md` 中的未发布部分或目标版本。
