# 데이터 모델 v1

현재 직렬화 모델의 진실은 core/Model.kt이다.

- Snapshot: schemaVersion=1, lists, tasks.
- TaskList: UUID, name. 고정 ID `inbox`를 항상 유지한다.
- TaskItem: UUID, title, notes, listId, ISO dueDate?, priority(0~3), tags[], checklist[], completedAt?, deletedAt?, createdAt, updatedAt.
- CheckItem: UUID, title, done.

timestamp는 epoch milliseconds, 날짜만 있는 마감일은 시간대 변환하지 않는 ISO LocalDate이다. 새 편집 저장 시 기존 createdAt·completedAt·deletedAt를 보존한다. 완료·삭제 조작은 별도 함수로 수행한다.

검증: 지원 스키마, ID 중복, 소속 목록 존재, 제목 비공백/1000자 이하, 설명10만자 이하, 우선순위 범위, 실제 달력 날짜, 체크 항목 ID/제목.

후속 테이블: parent/section/order, note/attachment/template, recurrence/occurrence/exception, reminders, habit/checkins, focus sessions, user settings, provider links, sync operations/tombstones. 새 필드는 원본 백업과 스키마 이전 테스트를 동반한다.

초기 Windows 기본 경로는 LocalAppData/Dayflow/dayflow.json, Android는 앱 내부 filesDir/dayflow.json. 검사용 데이터는 evidence_private 등 별도 임시 폴더로 격리한다. 두 경로는 동기화되지 않는다.
