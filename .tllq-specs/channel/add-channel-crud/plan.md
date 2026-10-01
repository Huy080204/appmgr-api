# Plan — add-channel-crud

Tier M. Module `source/appmgr-api`, base package `com.appmgr.api`. Graph: `Channel` không tồn tại trên `dev` (0 kết quả), vault không có spec → resource mới, đã chạy bộ câu hỏi new-feature (đủ đáp án).

## Approach

Theo `rules/itz/convention/*` và skill `itz-spring-implement`; chi tiết đặt tên/base class lấy từ skill + reference, không đọc sibling.

- **Entity** `Channel extends Auditable<String>`, `@Table(name = <prefix>+"channel")` → `db_channel`; field `name`.
- **Repository** `ChannelRepository` + `ChannelCriteria` (filter `id`, `status`, `name`); finder `existsByName`, `existsByNameAndIdNot`.
- **Form** `CreateChannelForm` (`name` `@NotBlank`), `UpdateChannelForm` (`id` `@NotNull`, `name` `@NotBlank`).
- **DTO** `ChannelDto extends ABasicAdminDto` + `name`. **Mapper** `ChannelMapper` (form→entity, entity→dto, id-only dto cho create, auto-complete dto `id`/`name`).
- **Controller** `ChannelController` `/v1/channel`: create/update/delete/get/list + `/auto-complete` (không `@PreAuthorize`, mặc định `status = active`), `{PREFIX}` = `CHN`.
- **Unique name**: create → `existsByName`; update → chỉ check khi name đổi (`existsByNameAndIdNot`).
- **ErrorCode** `CHANNEL_ERROR_NOT_FOUND`, `CHANNEL_ERROR_NAME_EXISTED` — số/độ rộng lấy từ grep `dto/ErrorCode.java` lúc implement (độ rộng 3 vs 4 chữ số lẫn lộn trong file); đăng ký cùng diff với chỗ throw.
- **Liquibase** đang runtime-active (`spring.liquibase.enabled=true`, `ddl-auto=none`) → task changelog riêng qua `itz-liquibase-diff`.
- **Test**: JUnit5+Mockito+AssertJ có sẵn qua `spring-boot-starter-test`. Controller chưa tồn tại → implement trước, test sau (backend-principles Principle I).

## Constitution Check

1. One executor — pass (carriers only, không `/implement`).
2. Test decision explicit — pass (xem tasks.md).
3. Graph-first — pass; graph = `dev`, Channel vắng mặt; blind spot không ảnh hưởng (resource mới).
4. Vault read-only — pass (chỉ ghi `.tllq-specs/`, không `put_spec`).
5. Review gates — pass (analyze + review ở build).
Project rules: constructor injection không dùng, `@Autowired` field; không `@SpringBootTest` — pass.
