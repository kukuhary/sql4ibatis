# Changelog - sql4ibatis Eclipse Plugin

이 파일은 `sql4ibatis` 이클립스 플러그인의 모든 기능 개발, 버그 수정 및 UI 최적화 변경 이력을 기록하는 영구 히스토리 문서입니다. 변경 사항이 발생할 때마다 최신 날짜순으로 내용이 기록됩니다.

---

## [v1.1.1] - 2026-07-07
### 버그 수정 및 개선 기능
1. **MyBatis 동적 SQL 조건식 평가 시 따옴표 처리 및 공백 제어 버그 수정**:
   - 다이얼로그 입력값이 공백일 때 `Quote` 설정에 의해 `''` (따옴표로 감싸진 공백) 형태로 리턴되어 `<if test="...">` 등의 조건식(예: `!= null`, `!=""`)에서 비어있지 않은 값으로 오인되어 쿼리가 실행되던 현상을 원천 차단했습니다. 입력값이 공백이면 따옴표 옵션과 상관없이 빈 문자열(`""`)로 전달하도록 okPressed 로직을 패치했습니다.
   - 조건식 내부에서 따옴표가 결합된 실제 매핑 값(예: `"'Y'"`)과 비교문 대상 값(예: `"Y"`)의 불일치로 인해 참이어야 하는 조건절이 거짓으로 오판되던 파싱 엔진 버그를 수정했습니다. 조건 비교 전에 값의 바깥쪽 싱글 따옴표를 전처리 제거하는 `getCleanValue` 헬퍼 메서드를 연동했습니다.

## [v1.1.0] - 2026-06-29
### 추가 및 변경 기능
1. **MyBatis 동적 SQL 파서 탑재**:
   - `<if test="...">` 조건절을 파싱하고, 다이얼로그 입력 값에 기초하여 조건 표현식(예: `!= null`, `!var.isEmpty()`, `var == "Y"` 등)을 계산하여 참인 쿼리문만 빌드하는 동적 SQL 컴파일 기능을 적용했습니다.
   - 홑따옴표와 쌍따옴표가 혼용된 복잡한 속성 구조(`test='useYn == "Y"'`)를 정상 인식하도록 정규식을 고도화했습니다.
2. **입력 변수 자동 동기화**:
   - 다이얼로그에서 기저 이름이 같은 변수(예: `(if) sbzMstNm` 과 `sbzMstNm`) 중 한 곳에 값을 기입하면 다른 쪽에도 실시간으로 값이 복사되어 채워지도록 양방향 리스너를 결합했습니다.
3. **쿼리 작성 순서 정렬**:
   - 변수 입력창에 노출되는 파라미터 리스트가 쿼리문에 출현하는 실제 물리적 순서(Index Offset)와 100% 일치하도록 정렬 방식을 최적화했습니다.
4. **시각적 강조 및 UI 편의성 보강**:
   - IF 조건에 연관된 변수들을 일반 변수와 쉽게 구분하기 위해 라벨 전경색을 파란색(`SWT.COLOR_BLUE`)으로 강조 표시했습니다. (OS 테마 간섭을 우회하기 위해 `CLabel` 위젯 사용)
   - 각 변수 입력란 우측 끝에 개별 입력 내용을 즉시 지울 수 있는 **`[X]`** 단추를 부착했습니다.
   - 다이얼로그 하단 영역에 모든 입력 필드를 일괄 공백 처리하는 **`[전체 초기화]`** 단추를 탑재했습니다.
5. **실행 속도 극대화 (정적 캐싱)**:
   - 단축키(`Alt+Q`) 실행 시 매번 하드디스크에서 JDBC Driver Jar 파일을 읽고 드라이버 클래스로딩 및 인스턴스를 동적 생성하던 병목 현상을 해소했습니다.
   - 메모리 영역(`static`)에 `ClassLoader`와 `Driver` 인스턴스를 캐싱하여 두 번째 쿼리 실행부터 드라이버 로드 딜레이를 `0ms`로 최적화했습니다.
6. **대용량 조회 방지 (OOM 예방)**:
   - 쿼리 실행 중 OOM 및 이클립스 UI 락(Freeze) 현상을 막기 위해 `Statement.setMaxRows(maxRows)` 제어를 이식했습니다.
   - **Window > Preferences > iBATIS SQL Executor** 설정 페이지에 `Max Rows Limit` (기본값 1000) 옵션을 신설하여 사용자가 최대 행을 제한할 수 있게 조치했습니다.
7. **실행 쿼리 미리보기 영역 통합**:
   - Query Result 뷰를 상/하로 분할(`SashForm`)하여 상단은 결과 테이블, 하단은 실제 파라미터가 바인딩되어 데이터베이스에 날아간 최종 완성형 SQL 문자열을 보여주도록 기능을 개선했습니다.
8. **단일 셀(Cell) 단위 Ctrl+C 복사**:
   - 표 전체 행 복사가 아닌, 사용자가 마우스로 콕 집어 선택한 단 하나의 특정 칸(Cell) 값만 클립보드로 복사(`Ctrl+C`)할 수 있는 마우스 좌표 역추적 리스너를 이식했습니다.
9. **NULL 데이터 강조 표시 및 공백 처리**:
   - 데이터베이스 조회 결과 중 `null` 데이터나 `"NULL"` 텍스트 문자열이 들어있는 셀은 텍스트를 공백(`""`)으로 표시하여 비워두고, 눈이 편안한 부드러운 시스템 연노랑색(`SWT.COLOR_INFO_BACKGROUND`) 배경색을 입혀 한눈에 강조 구분되도록 조치했습니다.
10. **에러 발생 시 실행 쿼리 창 노출 및 에러 정보 렌더링**:
   - 데이터베이스 조회 도중 DB 예외(예: ORA-00984) 등이 발생할 때, 에러 팝업창을 띄우기 전 결과 뷰를 활성화해 그리드 테이블 영역에는 에러 원인을 출력하고 하단 실행 쿼리 영역에는 사용자가 실행을 시도했던 완성형 SQL 문자열(`sql`)을 강제 노출하도록 핸들러 예외 흐름 제어를 대폭 강화했습니다.
11. **구조적 리팩토링 (역할 분리 및 단일 책임 원칙 준수)**:
   - 하나의 파일에 비대하게 집중되어 있던 쿼리 파싱 및 데이터베이스 수행 책임을 각각 전담하는 독립 유틸리티 클래스(`SqlParser.java`, `DbExecutor.java`)로 분할하여 모듈화를 극대화했습니다. `RunQueryHandler.java`는 흐름(Flow)만 제어하도록 구조를 대폭 슬림화하고 코드 가독성과 유지보수성을 극대화했습니다.

### 수정된 파일 및 클래스/메서드 상세
* **[PreferenceConstants.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/preferences/PreferenceConstants.java)**:
  - `DB_MAX_ROWS` 상수 필드 신설.
* **[Activator.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/Activator.java)**:
  - `initializeDefaultPreferences` 메서드 추가 및 기본 최대 조회 한도 1000건 적용.
* **[DatabasePreferencePage.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/preferences/DatabasePreferencePage.java)**:
  - `createFieldEditors` 메서드 내 `IntegerFieldEditor` 필드 추가.
* **[ParameterInputDialog.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/dialogs/ParameterInputDialog.java)**:
  - `CLabel` 변경 및 `SWT.COLOR_BLUE` 부여, `X` 버튼 부착을 위한 3열 그리드 레이아웃 전면 수정.
  - `createButtonsForButtonBar` 오버라이딩을 통해 전체 초기화 단추 기능 이식.
  - `addModifyListener` 동기화 리스너 바인딩.
* **[QueryResultView.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/views/QueryResultView.java)**:
  - `SashForm` 구현을 통한 결과창 레이아웃 수정 및 완성형 SQL 출력 텍스트(`sqlText`) 생성.
  - 완성형 SQL을 보여주는 하단 텍스트 창의 비율을 결과 테이블과 동일하게 **반반(50%:50%) 분할 레이아웃**(`new int[] { 50, 50 }`)으로 설정하여 화면 균형성 확보.
  - 상단 쿼리 요약 표시줄의 조회 건수 한글 단위인 `" 건"`을 영문 **`" row(s)"`**로 교환하여 타겟 이클립스 배포 환경에서의 인코딩 깨짐을 최종 방지.
  - `addMouseListener` 좌표 계산 인덱스 캡처 및 `KeyAdapter`Ctrl+C 연동을 통한 단일 셀 복사 기능 이식.
  - `updateData` 메서드 내 NULL 셀 공백 렌더링 및 배경색(`SWT.COLOR_INFO_BACKGROUND`) 설정 로직 적용.
* **[SqlParser.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/utils/SqlParser.java)** (NEW):
  - iBATIS/MyBatis XML 구문 파싱, 물리적 변수 정렬 추출 및 MyBatis 동적 SQL `<if>` 표현식 계산/치환을 전담하는 파서 유틸리티 구현.
* **[DbExecutor.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/utils/DbExecutor.java)** (NEW):
  - 동적 JDBC Driver ClassLoader 캐싱 관리, 커넥션 수립, 쿼리문 수행 및 자원 GC 클리닝 전담 기능 구현.
  - 리팩토링 후 미사용 클래스로 남은 `VarOccurrence` 정적 이너 클래스 및 미사용 `IProgressMonitor` 임포트 제거하여 코드 최종 정돈 완료.
  - 다이얼로그 경고/안내 한글 텍스트 리터럴을 영문(English)으로 전면 전환하여 인코딩 깨짐 현상 예방 및 글로벌 표준화 적용.
  - 이클립스 기본 XML Editor 등 탭이 나뉜 `MultiPageEditor` 환경에서도 리플렉션과 어댑터를 활용해 기저의 실제 `ITextEditor`를 탐색하는 `getTextEditor` 헬퍼 메서드를 이식하여 에디터 타입 불일치 경고를 방지.
12. **전체 소스코드 파일 한글 주석 및 Javadoc 전면 영문화**:
   - 컴파일러 및 다른 PC 이클립스 리소스 뷰어에서의 문자열 깨짐을 원천적으로 막기 위해, 프로젝트 내의 모든 자바 소스코드 파일(`Activator.java`, `SqlParser.java`, `DbExecutor.java`, `QueryResultView.java` 등)에 하드코딩되어 있던 한글 Javadoc 설명문 및 인라인 주석들을 영문(English)으로 100% 개정 조치했습니다.
13. **국소적 CDATA 영역 파싱 버그 해결 (ORA-00900 방지)**:
   - MyBatis 쿼리 본문 내에 부등호 등을 표현하기 위해 `<![CDATA[...]]>` 구문이 포함되어 있을 때, 쿼리 전체가 그 CDATA 내용으로 오버라이팅되어 오발췌되던 파싱 엔진 버그를 전격 수정했습니다. 쿼리 껍데기 태그만 분리한 후 쿼리 내부의 모든 CDATA 태그를 언랩(Unwrap)하도록 패치하여 원본 SQL의 형상을 유지했습니다.
14. **MyBatis <include> 태그 동적 해석 및 인라인 치환 (워크스페이스 교차 프로젝트 지원)**:
   - 쿼리 본문 내에 `<include refid="참조ID" />` 태그가 존재할 때, 현재 단일 프로젝트뿐만 아니라 **이클립스 워크스페이스에 열려있는 다른 모든 의존 프로젝트(Multi-project / Library 프로젝트) 내부의 XML 및 SQLX(`.sqlx`) 파일들까지 실시간 순회 스캔**하여, 일치하는 네임스페이스 및 SQL 구문 조각을 추적하고 본문에 인라인 대입 처리하는 고도화된 include 리졸버를 구축했습니다. 네임스페이스 경로 매칭을 자동 지원하며 중첩된 include(Nested include) 또한 최대 5단계까지 재귀 병합을 보장합니다.
   - **(식별 마커 보강)** 하단 결과 뷰(`Executed SQL String`)에서 병합된 include의 출처를 쉽게 파악할 수 있도록 치환이 발생한 구문 영역 전후에 SQL 표준 주석(`/* -- INCLUDE START: 참조ID -- */`, `/* -- INCLUDE END: 참조ID -- */`)을 자동으로 각인하도록 설계했습니다.
15. **MyBatis <choose>/<when>/<otherwise> 및 <foreach> 조건절 동적 평가 지원**:
   - MyBatis의 다중 조건 태그인 `<choose>`, `<when test="...">`, `<otherwise>` 구문을 논리 평가하여, 참인 조건의 분기문만 남겨두고 나머지는 자동 탈락시키는 동적 분기 해석기를 탑재했습니다. 또한 배열이나 컬렉션을 순회하는 `<foreach>` 태그의 껍데기를 날리고 내부 변수를 기저 수집 변수명으로 자동 환원해 주는 언랩(Unwrap) 변환 프로세스를 이식하여 오라클 SQL 동작성을 극대화했습니다.
16. **프로젝트 내 XML/SQLX 탐색 시 ProgressMonitorDialog 프로그레시브 팝업 연동 및 최적화**:
   - 대규모 프로젝트에서 동적 쿼리 스캔 시 이클립스 UI가 정지(Freezing)되는 피드백을 수용하여, XML/SQLX 수집 연산을 백그라운드 스레드로 분리했습니다. `ProgressMonitorDialog` 팝업을 연동하여 현재 작업 상태(Analyzing file: ...)를 실시간으로 출력하며 사용자가 도중에 `Cancel`을 눌러 취소할 수 있도록 UI를 개선했습니다.
   - **(성능 최적화)** 실행하려는 쿼리 내부에 `<include` 키워드가 존재하지 않는 일반적인 쿼리의 경우, 불필요한 전체 리소스 순회 탐색을 **100% 스킵(Bypass)하여 0ms만에 즉각 변수 입력 다이얼로그를 팝업**하도록 고도화 적용했습니다.
17. **MyBatis <sql> 공통 쿼리 조각 태그 내 단독 실행 지원**:
   - 기존 select, insert, update, delete 태그 영역으로만 국한되어 있던 쿼리 캡처 스캐너 범위를 `<sql id="...">` 태그 영역까지 확장했습니다. 이제 공통 쿼리 조각 내부에 커서가 위치할 때도 단축키 `Alt+Q`를 통해 정상적으로 쿼리를 독립 테스트할 수 있습니다.

### 수정된 파일 및 클래스/메서드 상세
* **[Activator.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/Activator.java)**:
  - `initializeDefaultPreferences` 메서드 추가 및 기본 최대 조회 한도 1000건 적용.
  - 클래스 Javadoc 및 내부 인라인 주석 영문화 적용.
* **[PreferenceConstants.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/preferences/PreferenceConstants.java)**:
  - `DB_MAX_ROWS` 상수 필드 신설 및 클래스 설명 주석 영문화 적용.
* **[ParameterInputDialog.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/dialogs/ParameterInputDialog.java)**:
  - 다이얼로그 내 설명 라벨, 툴팁, 전체 초기화 버튼 등의 모든 한글 리터럴을 영문(English)으로 전면 치환.
* **[DatabasePreferencePage.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/preferences/DatabasePreferencePage.java)**:
  - 설정 페이지 설명 및 DB 커넥션 테스트 성공/실패 팝업 한글 메시지를 영문(English)으로 전면 치환하여 인코딩 호환 오류 원천 차단.
* **[SqlParser.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/utils/SqlParser.java)** (NEW):
  - iBATIS/MyBatis XML 구문 파싱, 물리적 변수 정렬 추출 및 MyBatis 동적 SQL `<if>` 표현식 계산/치환을 전담하는 파서 유틸리티 구현 및 Javadoc 영문화.
  - `extractQueryAtCursor` 및 `extractQueryId` 메서드 내에서 쿼리 검출 대상 식별자에 `<sql` 태그를 추가하여 공통 SQL 조각을 단독 수행 가능하도록 보강.
  - **(중복 태그 충돌 버그 수정)** iBATIS의 최상위 태그인 `<sqlMap>`과 `<sql>`의 접두어 매칭 충돌로 인해 오작동(Alt+Q 단독 실행 오류)이 일어나던 현상을 해결하기 위해, 태그 뒤에 공백이나 `>`가 위치하는지 검증해주는 `isTargetTag` 단어 경계 헬퍼를 도입했습니다. 추가로, 복사/붙여넣기 된 코드에 섞여있는 유니코드 특수 공백(NBSP, 전각 공백 등) 또한 완벽히 공백으로 포괄할 수 있도록 `Character.isWhitespace()` 검증을 연동하여 태그 파싱의 안정성을 최종 확보했습니다.
  - `extractQueryAtCursor` 메서드 내에서 `<![CDATA[` 가 존재할 때 오발췌되던 버그를 껍데기 태그 선도려내기 후 내부 CDATA 태그 전체를 `replace` 언랩(Unwrap)하는 방식으로 대폭 수정 보완.
  - `<include refid="xxx">` 태그를 파싱하고 XML 문서 내의 `<sql id="xxx">` 내용으로 재귀적으로 치환하는 `resolveIncludes`, `findSqlFragment`, `extractSqlTagContent` 헬퍼 메서드 추가 구현.
  - `<choose>/<when>/<otherwise>` 다중 분기를 계산하여 참 조건 영역만 남기고 조율하는 `parseChooseSql` 및 `evaluateChooseBody` 메서드 신설.
  - `<foreach>` 루프 태그를 언래핑하여 내부 item 변수를 collection 변수명으로 자동 환원해 주는 `unwrapForeach` 메서드 추가.
  - `extractParameters` 에 `<when test="...">` 내 조건 변수들을 수집하는 정규식 스캐너 추가.
* **[DbExecutor.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/utils/DbExecutor.java)** (NEW):
  - 동적 JDBC Driver ClassLoader 캐싱 관리, 커넥션 수립, 쿼리문 수행 및 자원 GC 클리닝 전담 기능 구현 및 Javadoc 영문화.
  - **(SELECT 판별 버그 패치)** 쿼리문 처음에 블록 주석(`/* ... */`)이나 라인 주석(`-- ...`)이 위치할 때, 이를 SELECT 쿼리가 아닌 DML(UPDATE/INSERT/DELETE 등)로 잘못 인식하여 결과 그리드를 출력하지 못하던 버그를 해결하기 위해, 정규식으로 주석들을 모두 소거한 상태의 알맹이 키워드를 기준으로 SELECT/WITH 쿼리 여부를 오차 없이 식별하도록 고도화.
* **[QueryResultView.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/views/QueryResultView.java)**:
  - `SashForm` 구현을 통한 결과창 레이아웃 수정 및 완성형 SQL 출력 텍스트(`sqlText`) 생성.
  - 완성형 SQL을 보여주는 하단 텍스트 창의 비율을 결과 테이블과 동일하게 **반반(50%:50%) 분할 레이아웃**(`new int[] { 50, 50 }`)으로 설정하여 화면 균형성 확보.
  - 상단 쿼리 요약 표시줄의 조회 건수 한글 단위인 `" 건"`을 영문 **`" row(s)"`**로 교환하여 타겟 이클립스 배포 환경에서의 인코딩 깨짐을 최종 방지.
  - `addMouseListener` 좌표 계산 인덱스 캡처 및 `KeyAdapter`Ctrl+C 연동을 통한 단일 셀 복사 기능 이식.
  - `updateData` 메서드 내 NULL 셀 공백 렌더링 및 배경색(`SWT.COLOR_INFO_BACKGROUND`) 설정 로직 적용.
  - `updateData` 내에서 Executed SQL String을 화면에 표시하기 전, Windows OS의 SWT Text 컨트롤에서 줄바꿈이 정상 처리되도록 모든 개행 문자를 **`\r\n` (CRLF)** 로 일괄 정규화(Normalize) 처리하여 깨진 상자 기호가 출력되는 버그를 전격 패치.
  - Javadoc 및 인라인 주석 영문화 적용.
* **[RunQueryHandler.java](file:///c:/mySts_work/sql4ibatis/src/sql4ibatis/handlers/RunQueryHandler.java)**:
  - 쿼리 파싱 및 DB 연동 코드를 `SqlParser`와 `DbExecutor`로 이관하여 기존 500라인 이상에서 200라인 이하로 코드 다이어트 적용.
  - `executeDatabaseQuery` 메서드 내 예외 처리 흐름을 수정하여 쿼리 실행 실패(DB 에러) 시에도 QueryResultView를 선제 활성화해 에러 원인 표출 및 전송 시도 쿼리(sql)를 하단에 출력하도록 보완.
  - 리팩토링 후 미사용 클래스로 남은 `VarOccurrence` 정적 이너 클래스 및 미사용 `IProgressMonitor` 임포트 제거하여 코드 최종 정돈 완료.
  - 다이얼로그 경고/안내 한글 텍스트 리터럴을 영문(English)으로 전면 전환하여 인코딩 깨짐 현상 예방 및 글로벌 표준화 적용.
  - 이클립스 기본 XML Editor 등 탭이 나뉜 `MultiPageEditor` 환경에서도 리플렉션과 어댑터를 활용해 기저의 실제 `ITextEditor`를 탐색하는 `getTextEditor` 헬퍼 메서드를 이식하여 에디터 타입 불일치 경고를 방지.
  - 쿼리 파라미터 스캔 및 컴파일을 타기 전 에디터 도큐먼트 전체(`xmlText`)를 로드하여 `SqlParser.resolveIncludes` 를 호출해 공통 SQL 조각을 인라인 병합한 뒤 쿼리를 실행하도록 실행 파이프라인 개정.
  - 쿼리 가공 흐름 중간에 `SqlParser.unwrapForeach` 와 `SqlParser.parseChooseSql` 프로세스를 추가 주입하여 다중 조건 선택 및 루프 쿼리 완성 성능 확보.
  - 워크스페이스 전역 범위의 교차 include 파일들을 수집하는 연산을 `ProgressMonitorDialog` 기반의 비동기 흐름으로 개편하여 UI 프리징 현상을 완벽히 차단하고, 타 라이브러리/공통 프로젝트에 포함된 include 파일도 정상적으로 분석하도록 보완.
  - **(이클립스 Resource Out of Sync 버그 해결)** 이클립스가 디스크 상의 변경점을 즉각 캐시하지 못하여 `file.getContents()` 호출 시 `CoreException` (Resource is out of sync) 에러를 뿜으며 무음 스킵되던 버그를 완벽히 해결하기 위해, 1차 수집 실패 시 `file.getLocation().toFile()` 경로의 물리 디바이스 파일을 직접 읽어들이는 강력한 2차 로드 Fallback 프로세스를 연동 구축.
* **[DEPLOY.md](file:///c:/mySts_work/sql4ibatis/DEPLOY.md)** (NEW):
  - 타 PC 및 실 서버 환경에 플러그인 이식을 지원하기 위한 빌드(Export) 및 설치(dropins 활용) 가이드 문서 신규 제작.
* **[MANIFEST.MF](file:///c:/mySts_work/sql4ibatis/META-INF/MANIFEST.MF)**:
  - 워크스페이스 내 프로젝트 리소스 및 파일 순회 탐색(IFile, IProject) 기능을 위한 **`org.eclipse.core.resources`** 플러그인 의존성을 `Require-Bundle`에 보강 정의.

---

## [v1.0.0] - 2026-06-29
### 추가 및 변경 기능
* **초기 프로토타입 골격 구축**:
  - `plugin.xml` 및 `MANIFEST.MF` 기본 의존성 및 확장점 연동.
  - `Alt+Q` 단축키 연동 커맨드 핸들러 기본형 작성.
  - Window > Preferences 설정 창 및 데이터베이스 커넥션 검증(Test Connection) 기본 폼 완성.
  - `ProgressMonitorDialog` 연동을 통한 백그라운드 DB 쿼리 실행 골격 구현.
  - Query Result View 기본형 테이블 바인딩 완료.
