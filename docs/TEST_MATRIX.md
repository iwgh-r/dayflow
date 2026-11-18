# 검증 항목

실행 결과는 PROGRESS와 artifacts/verification 최신 보고서에서 확인한다. 이 표는 테스트 존재와 합격 조건을 구분한다.

| ID | 검사 | 증거/도구 | 현재 정의 |
|---|---|---|---|
| Q01 | 생성·수정·완료·삭제·복원 후 실제 재실행 | TaskRepositoryTest.lifecycleSurvivesRestartAndTrashRestore | 통과 |
| Q02 | 저장 실패 시 상태 롤백 | saveFailureDoesNotPublishUncommittedState | 통과 |
| Q03 | 100개 동시 추가 데이터 유실 없음 | concurrentSavesDoNotLoseTasks | 통과 |
| Q04 | 오늘·연체·다음7일 경계 | todayAndUpcomingRespectDateBoundaries | 통과 |
| Q05 | 공백·잘못된 날짜 거부 | invalidDateAndBlankTitleRejectedWithoutMutation | 통과 |
| Q06 | 체크리스트·한국어 태그·검색 | checklistAndSearchUsePersistedData | 통과 |
| Q07 | 파일 손상·동시 프로세스 잠금 | corruptFileIsPreservedAndSecondWriterRejected | 통과 |
| Q08 | 미래 스키마 거부 | futureSchemaNeverLoadsAsEmptyData | 통과 |
| Q09 | 실제 Compose UI 생성→편집→완료→휴지통→복원 | TaskUiTest.createEditCompleteTrashRestoreThroughRealControls | 통과 |
| Q10 | 실제 UI 캡처·검색 | TaskUiTest.screenCaptureAndSearchAreRealData | 통과 |
| Q11 | APK 생성·Android 정적 검사 | assembleDebug / lintDebug | 통과, 경고는 보고서 참조 |
| Q12 | Windows 런타임 포함 패키징·실제 시작 | createDistributable / 실행 | 패키징·이전 패키지 시작 확인, locale 모듈 추가 후 화면 재검사 미실행 |
| Q13 | Android 설치·재생성·한글·재시작 | AndroidTaskTest + android_smoke.py | 통과; 물리 회전·삼성 IME는 미실행 |
| Q14 | 동등 상태의 영상·화면 비교 | REFERENCE_LOG와 실제 양쪽 캡처 | 미실행 |
| Q15 | 별도 에이전트 독립 검수 | 별도 리뷰 결과 문서 | 미실행 |
| Q16 | 1만 작업·대규모 노트·스크롤·메모리 | 성능 벤치마크 | 미실행 |

후속 단계는 날짜·반복·권한·재부팅·워치 연결·동기화 충돌·백업/복원·업데이트 검사 사례를 추가해야 한다. 단위 테스트만 통과한 기능을 기기 검증 완료로 간주하지 않는다.
