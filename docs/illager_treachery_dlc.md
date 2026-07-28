# 灾厄诡计 DLC 接入

## 强制前置

DLC 的 `META-INF/mods.toml` 至少包含：

```toml
[[dependencies.goetyarkham_carcosa]]
modId="goetyarkham"
mandatory=true
versionRange="[0.1.0,)"
ordering="AFTER"
side="BOTH"
```

`goetyarkham_carcosa` 应替换为 DLC 自己的 mod id；遭遇资源也必须使用
DLC 自己的命名空间。

## 只复用本体通用类型

文件：
`data/goetyarkham_carcosa/goetyarkham/illager_treachery/encounters/byakhee_hunt.json`

```json
{
  "schema_version": 1,
  "type": "goetyarkham:message",
  "default_enabled": true,
  "default_weight": 2,
  "data": {
    "translation_key": "message.goetyarkham_carcosa.illager_treachery.byakhee_hunt"
  }
}
```

这个定义的遭遇 ID 是 `goetyarkham_carcosa:byakhee_hunt`，不需要
注册 Java 执行器。

## 自定义复杂类型

在 DLC 的正常模组构造阶段注册类型，必须早于第一次服务端数据包加载：

```java
public final class CarcosaMod {
    public CarcosaMod() {
        IllagerTreacheryApi.registerEncounterType(
                new ResourceLocation("goetyarkham_carcosa", "byakhee_hunt"),
                new ByakheeHuntEncounterType()
        );
    }
}

public final class ByakheeHuntEncounterType
        implements EncounterType<ByakheeHuntEncounterType.Data> {
    @Override
    public Data parse(
            ResourceLocation encounterId,
            JsonObject json,
            EncounterParseContext context)
            throws EncounterDefinitionException {
        // 严格验证 JSON 参数，返回不可变数据；禁止反射类名或任意命令。
        return new Data(/* validated values */);
    }

    @Override
    public void execute(Data data, EncounterExecutionContext context) {
        ServerPlayer player = context.player();
        // 只在逻辑服务器执行 DLC 自己的复杂逻辑。
    }

    public record Data(/* immutable fields */) {
    }
}
```

对应 JSON 的 `type` 使用
`goetyarkham_carcosa:byakhee_hunt`。

## 加载流程

1. Forge 按强制前置关系构造 Goety: Arkham 与 DLC。
2. 本体和 DLC 在模组构造阶段注册遭遇类型或兼容的纯 Java 遭遇。
3. `AddReloadListenerEvent` 扫描所有命名空间的
   `goetyarkham/illager_treachery/encounters`。
4. Minecraft 资源优先级先确定同一路径的最高优先级 JSON。
5. 每份 JSON 通过已注册类型解析并验证，随后整批替换当前数据定义。
6. 当前世界的集中 TOML 自动执行 `sync`：新 ID 使用 JSON 默认值，
   已有 ID 和已卸载 DLC 的旧条目都原样保留。
7. 下一轮灾厄诡计创建不可变快照时，新 DLC 遭遇即可参与抽取。

旧纯 Java 遭遇与数据包遭遇使用同一快照。二者 ID 冲突时，数据包定义
会被明确拒绝并记录日志；不会按加载先后静默覆盖。
