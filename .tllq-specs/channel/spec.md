# Channel — spec

Surface: `channel`. Quản lý Channel (entity `Channel`, table `db_channel`), endpoint `/v1/channel`.

## Functional requirements

- **FR-001** Entity `Channel` (table `db_channel`) có field `name: String`; `id`/`status`/audit kế thừa từ `Auditable<String>`.
- **FR-002** Create: `POST /v1/channel/create`, form `name` bắt buộc (`@NotBlank`). Trả `ApiMessageDto<ChannelDto>` chỉ chứa `id`. Quyền `CHN_C`.
- **FR-003** Update: `PUT /v1/channel/update`, form `id` (`@NotNull`) + `name` (`@NotBlank`). Không tìm thấy id → lỗi not-found của Channel. Quyền `CHN_U`.
- **FR-004** Delete: `DELETE /v1/channel/delete/{id}`. Không tìm thấy → lỗi not-found. Quyền `CHN_D`.
- **FR-005** Get: `GET /v1/channel/get/{id}` trả `ChannelDto` (`id`, `status`, `createdDate`, `modifiedDate`, `name`). Quyền `CHN_V`.
- **FR-006** List: `GET /v1/channel/list`, phân trang, lọc theo `id`, `status`, `name`. Quyền `CHN_L`.
- **FR-007** Auto-complete: `GET /v1/channel/auto-complete`, không `@PreAuthorize`, trả `id`, `name`; filter mặc định `status = active`.
- **FR-008** Ràng buộc nghiệp vụ: `name` phải unique — create trùng name bị từ chối; update chỉ kiểm tra khi name đổi (loại trừ chính id đó).

## Acceptance

- Given name chưa tồn tại, When create với name hợp lệ, Then trả thành công kèm id.
- Given name đã tồn tại, When create/update sang name đó, Then trả lỗi `CHANNEL_ERROR_NAME_EXISTED`.
- Given update giữ nguyên name của chính nó, Then không lỗi unique.
- Given Channel inactive, When gọi auto-complete không truyền status, Then Channel đó không có trong kết quả.
- Given id không tồn tại, When get/update/delete, Then trả `CHANNEL_ERROR_NOT_FOUND`.

## Changelog

2026-10-01 · add-channel-crud · spec mới cho Channel CRUD + List + Auto-complete
