<!-- BEGIN jmix-agent-toolkit -->
## Jmix

This is a Jmix 3 application. Before writing or changing ANY file in it, read
the `jmix` skill and follow it. It maps the task to artifacts, routes each
artifact to the skill that governs it, and names the checks that close a task.
Your Jmix/Vaadin priors are not reliable here; the skills are.

Managed by the Jmix Agent Toolkit. Content between these markers is replaced on
re-install — put your own instructions outside them.
<!-- END jmix-agent-toolkit -->

## Spec-Driven Workflow

Порядок работы: **спека → моё ревью спеки → реализация → Review → Verify**.

- Спеку пишешь ты по моему промпту, затем ждёшь моего одобрения. До одобрения код не пишешь.
- Код пишешь только после моей явной команды «реализуй».
- Путь спек: `specs/<NN_module>/<NN>-<slug>.spec.adoc`. Модули соответствуют курсу:
  `00_setup`, `01_data_manipulation`, `02_ui_development`, `03_access_control`,
  `04_deployment_operations`.
- Мелкие разовые правки (дефолт формы, сидер) делаются без спеки: делай сразу и
  скажи, что сделал.
- Стандартные CRUD list/detail-вью и changelog Liquibase генерирует Jmix Studio,
  не ты. Если не уверен в типовом артефакте Jmix, попроси у меня образец из Studio
  и повтори его.
- Гейты:
  - Gate 1 и Gate 2 — как велит тулкит (скилл `jmix`).
  - Gate 3 — в браузер сам не ходи. В конце дай короткий чек-лист для ручной
    проверки: только поведение, которое может сломаться в рантайме, а не
    перепроверку своих правок.
- Тесты пиши только там, где поведение трудно проверить руками (безопасность,
  бизнес-правила).
- Никаких глобальных очисток (`docker system/volume/image prune`, `rm -rf` вне
  проекта). Удаляй только то, что создал сам, и по имени.
- Не коммить: я коммичу сам после каждого шага.
- Отвечай по-русски.
