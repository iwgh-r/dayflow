# 구조와 결정

## 플랫폼

- Android: Kotlin + Jetpack Compose, applicationId `app.dayflow.personal`, minSdk 26.
- Windows: Compose Desktop/JVM. 런타임을 포함하는 설치형 실행 파일. WebView를 사용하지 않는다. WinUI 고유 컨트롤은 사용하지 않으며 이 차이를 숨기지 않는다.
- 공유: 순수 Kotlin/JVM 도메인(`core`), 동일 Compose UI 소스를 두 JVM 대상에 컴파일. 각 플랫폼 진입점은 분리한다.
- Wear OS: 이후 별도 `wearApp` 모듈 + Wear Compose + Data Layer. Windows와 직접 Data Layer로 통신하지 않는다.

```text
Android MainActivity ─┐
                     ├─ shared Compose UI ─ TaskRepository ─ SnapshotStore
Windows Main         ─┘                                  ├─ JsonFileStore (현재)
                                                         ├─ Room (후속 Android)
                                                         └─ SQLite (후속 Windows)
후속: reminder / calendar-provider / wearable / sync 어댑터
```

## 저장과 상태

StateFlow를 통해 저장이 성공한 상태만 UI에 공개한다. 모든 변경은 Mutex로 직렬화하고 IO 스레드에서 저장한다. 저장 실패 시 화면의 데이터 상태는 이전 값으로 유지한다. 파일 잠금으로 동일 저장소를 두 프로세스가 동시에 수정하지 못하게 한다. 잘못된 JSON, 알 수 없는 스키마, 잘못된 날짜는 원본을 지우지 않고 로드를 중단한다.

초기 JSON 저장은 임시 구현임을 명시한다. Room/SQLite 전환은 원본 보존, 레코드 수·ID·체크리스트·완료·삭제 상태 비교, 실패 시 재시도 가능성과 버전 이전 테스트를 선행한다. `fallbackToDestructiveMigration`은 사용하지 않는다. 기기 간 동기화는 구현 전이며 JSON 파일을 클라우드 폴더에 함께 열어 동기화하는 방식을 사용하지 않는다.

## 캘린더·알림·동기화의 필수 설계

- 날짜만 있는 할 일은 LocalDate, 정시 알림은 Instant + ZoneId + 원래 벽시계 시간을 분리한다.
- 반복은 규칙·시리즈·발생분·예외를 분리한다. 월말·윤년·DST·지연 완료를 반드시 검사한다.
- 알림은 작업 ID + 발생분 + 알림 ID로 멱등 예약한다. 재부팅·시간대 변경·권한 철회 시 재조정한다.
- 기기 일정은 공급자·캘린더·이벤트 ID 매핑, 동기화 토큰, 변경 출처, 충돌 정책을 별도 보관한다. 반복 인스턴스를 원본 시리즈와 혼동하지 않는다. 자동 삭제 전 dry-run 결과를 검증한다.
- 폰/워치/Windows 교환 포맷에는 UUID, 수정 버전, 삭제 tombstone, 출처 기기와 멱등 operation ID를 포함한다. 서버나 공급자를 정하기 전 자동 동기화 완료로 표시하지 않는다.

## 고정 빌드 기준

현재 초기 기준은 Kotlin 2.1.20 / Compose Multiplatform 1.8.0 / AGP 8.9.2 / Gradle 8.11.1 / JDK 17 / SDK 35이다. 최신 버전이라고 주장하지 않는다. 호환되는 기준을 먼저 빌드하고 버전 업그레이드는 별도 검증한다. 스토어 배포 시 해당 시점의 target SDK 요구사항을 다시 확인한다.

공식 근거:
- https://developer.android.com/build/releases/agp-8-9-0-release-notes
- https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html
- https://developer.android.com/reference/android/provider/CalendarContract
- https://developer.android.com/training/wearables/data/overview
