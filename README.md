# EdenHonor

Плагин для Purpur / Paper 1.21.10+, который реализует систему честности / PK-меток:

- **зелёный** — не наносил PvP-урон за окно
- **оранжевый** — нанёс `300+` урона игрокам за окно
- **красный** — убил хотя бы одного игрока за окно
- если убить **красного** игрока, убийца тоже станет **красным**

Окно по умолчанию: **96 часов**.

## Зависимости

- Java 21+
- Purpur / Paper 1.21.10+
- PlaceholderAPI (для TAB-интеграции)
- TAB (необязательно, но именно через него выводится значок в табе / над головой)

## Что умеет

- считает PvP-урон по `EntityDamageByEntityEvent`
- считает убийства по `PlayerDeathEvent`
- учитывает `last aggressor`, если игрок умер не от прямого удара, а вскоре после PvP
- хранит историю в `plugins/EdenHonor/data.yml`
- регистрирует PlaceholderAPI-плейсхолдеры для TAB
- умеет добавлять значок в обычный Paper-чат

## Плейсхолдеры

- `%edenhonor_indicator%` -> `&a●` / `&6●` / `&c●`
- `%edenhonor_status%` -> `GREEN` / `ORANGE` / `RED`
- `%edenhonor_status_text%` -> цветной текст статуса
- `%edenhonor_damage%` -> урон за окно
- `%edenhonor_kills%` -> кол-во активных убийств за окно
- `%edenhonor_timeleft%` -> сколько осталось до сброса красного
- `%edenhonor_reason%` -> последняя причина красного
- `%edenhonor_is_red%`
- `%edenhonor_is_orange%`
- `%edenhonor_is_green%`

## Команды

- `/honor` — свой статус
- `/honor status <player>` — статус игрока
- `/honor pardon <player>` — очистить статус игрока
- `/honor wipe <player|all>` — стереть историю
- `/honor reload` — перезагрузить конфиг и данные

## Пример интеграции с TAB

### groups.yml

```yml
_DEFAULT_:
  tabsuffix: ' %edenhonor_indicator%'
  tagsuffix: ' %edenhonor_indicator%'
```

### config.yml TAB

```yml
placeholder-refresh-intervals:
  default-refresh-interval: 500
  "%edenhonor_indicator%": 1000
  "%edenhonor_status%": 1000
  "%edenhonor_status_text%": 1000
  "%edenhonor_damage%": 1000
```

## Сборка

```bash
mvn clean package
```

Готовый jar появится в `target/edenhonor-1.0.0.jar`.

## Важно

Если у тебя уже стоит отдельный чат-плагин, встроенный Paper-чат форматтер EdenHonor лучше отключить:

```yml
chat:
  enabled: false
```

Тогда значок в чате можно выводить уже через сам чат-плагин, используя `%edenhonor_indicator%`.
