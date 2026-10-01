<!-- elms: kind=1 taskName="Add Channel management (CRUD, list, auto-complete)" -->
# Tasks — add-channel-crud

Thứ tự test: Controller chưa tồn tại nên theo `backend-principles.md` Principle I — implement (Core) trước, test sau. Đây là ngoại lệ có chủ đích của "test trước implement" vì chưa có gì để test.

## Core

- T001 [skill:itz-spring-implement] [pattern] FR-001, FR-006, FR-008 — Entity `Channel` (`db_channel`, `name`), `ChannelRepository` (`existsByName`, `existsByNameAndIdNot`), `ChannelCriteria` (`id`, `status`, `name`).
- T002 [skill:itz-spring-implement] [pattern] FR-002, FR-003, FR-005, FR-007 — `CreateChannelForm`, `UpdateChannelForm`, `ChannelDto`, `ChannelMapper` (kể cả id-only và auto-complete `id`/`name`).
- T003 [skill:itz-spring-implement] [contract] FR-002..FR-008 — `ChannelController` `/v1/channel` (create/update/delete/get/list/auto-complete, prefix `CHN`) + `ErrorCode` `CHANNEL_ERROR_NOT_FOUND`, `CHANNEL_ERROR_NAME_EXISTED` (đăng ký cùng chỗ throw).

## Tests

- T004 [skill:itz-spring-test] [pattern] FR-002, FR-003, FR-004, FR-008 — `ChannelControllerTest`: create ok/trùng name, update ok/name đổi trùng/name giữ nguyên, delete/get not-found, auto-complete mặc định status active. Lệnh: `mvn -q -f source/appmgr-api/pom.xml -Dtest=ChannelControllerTest test`.

## Integration

- T005 [skill:itz-liquibase-diff] [pattern] FR-001 — Sinh changelog cho bảng `db_channel` (dedicated task, Liquibase runtime-active).

## Test decisions per FR

| FR | Test? | Lý do |
|---|---|---|
| FR-001 | no | khai báo Entity; sai sẽ vỡ build / liquibase diff (T005) thấy ngay |
| FR-002 | yes (T004) | nhánh trùng name |
| FR-003 | yes (T004) | nhánh chỉ check unique khi name đổi |
| FR-004 | yes (T004) | not-found |
| FR-005 | no | map đơn giản; compiler bắt sai shape, get-not-found nằm chung T004 |
| FR-006 | no | Criteria khai báo; sai field vỡ build |
| FR-007 | yes (T004) | default filter active là nhánh dễ sai im lặng |
| FR-008 | yes (T004) | nghiệp vụ chính |

Không có FR ship trước đó bị ảnh hưởng (resource mới) → không cần test hồi quy.

## Batches

| batch | tasks | class | why grouped |
|---|---|---|---|
| B1 | T001, T002 | pattern | cùng gói model/form/dto, liên tục, không `[P]` |
| B2 | T003 | contract | định nghĩa endpoint + error code, tách riêng |
| B3 | T004 | pattern | test, sau Core |
| B4 | T005 | pattern | liquibase diff, cần Entity xong |

## Skills

| task | skill | why |
|---|---|---|
| T001–T003 | itz-spring-implement | full CRUD resource mới |
| T004 | itz-spring-test | Controller unit test; verification skill cho test này |
| T005 | itz-liquibase-diff | thay đổi schema, Liquibase active |
