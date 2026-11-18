# Dayflow

Windows와 Android용 개인 생산성 앱. 독립 브랜드의 Kotlin/Compose 프로젝트입니다.

이 저장소는 개발 중입니다. TickTick Premium의 기능과 사용 흐름을 추적하지만 동등한 완성품이라고 표시하지 않습니다. AI 기능은 제외하며 기기 캘린더와 Wear OS는 필수 후반 단계입니다.

## 현재 시작점

- `core`: 두 플랫폼이 공유하는 작업·목록 모델, 검색·정렬, 저장 트랜잭션과 파일 저장소
- `composeApp`: Android Jetpack Compose / Windows Compose Desktop 앱
- `docs`: 기능 ID, 화면·상호작용 명세, 로드맵과 검증 현황
- `tools`: 개발 환경 준비, 영상 프레임 추출, 빌드·검증

Windows판은 WebView 없이 Compose Desktop/JVM과 번들 런타임으로 실행됩니다. Android판은 Jetpack Compose입니다. Windows WinUI 컨트롤을 사용하는 구현은 아닙니다.

## AI 개발 및 검증

AI는 `AGENTS.md`, `docs/PROGRESS.md`, `docs/ROADMAP.md`를 먼저 읽고 다음 기능 브랜치를 진행합니다. 사용자가 명령을 복사하거나 브랜치·PR을 직접 관리하는 것을 기본 절차로 요구하지 않습니다.

사용자 요청에 따라 개발은 백그라운드에서 수행합니다. 브라우저나 Windows 화면을 조작하지 않으며, Android 에뮬레이터도 창 없이 실행합니다. 원격 로그인 미완료는 로컬 개발의 중단 사유가 아닙니다.

개발 도구를 준비한 환경에서 `tools/verify.ps1`이 단위 테스트, UI 테스트, Android 빌드·정적 검사를 실행합니다. `-PackageWindows`는 실행 파일과 런타임을 묶습니다. 실기기·에뮬레이터 검사는 별도 결과로 남기며 빌드 성공만으로 대체하지 않습니다.

실제 결과는 [진행 기록](docs/PROGRESS.md)에 기록합니다. 민감한 참고 영상, 실제 일정, 서명키, 개발 도구와 빌드 결과는 Git에서 제외됩니다.

## 저장 방식

초기 버전은 로컬 JSON 스냅샷을 사용합니다. 파일 잠금, 임시 파일 동기화, 교체 저장, 이전 파일 `.bak` 보존을 제공합니다. 데이터 오류나 미래 버전은 초기화하지 않고 오류로 중단합니다. 대규모 검색·마이그레이션 단계에서 Android Room 및 Windows SQLite 저장소를 추가할 예정입니다. 현재 두 기기 사이 자동 동기화는 없습니다.
