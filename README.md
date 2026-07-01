# sql4ibatis - Eclipse/STS MyBatis & iBATIS SQL Executor Plugin

`sql4ibatis`는 Eclipse 및 Spring Tool Suite(STS) 개발 환경에서 MyBatis 및 iBATIS XML 매퍼 파일의 SQL 쿼리를 데이터베이스를 대상으로 직접 실행하고 결과를 확인할 수 있는 개발자 편의용 이클립스 플러그인입니다.

에디터에서 복잡한 XML 쿼리를 복사하여 토드(Toad)나 디비버(DBeaver) 등의 외부 DB 툴에서 변수를 수동으로 치환하는 번거로움 없이, IDE 내부에서 단축키 하나로 즉시 파라미터를 입력받아 실행 결과를 조회할 수 있습니다.

---

## 🚀 주요 기능 (Key Features)

1. **에디터 단축키 실행 (`Alt + Q`)**
   - XML 파일 내 마우스 커서가 위치한 쿼리 블록(`<select>`, `<insert>`, `<update>`, `<delete>`) 또는 마우스로 블록 지정한 SQL을 즉시 파싱하여 실행합니다.
   
2. **MyBatis 동적 SQL 파싱 및 조건문 계산**
   - `<if test="...">` 조건절을 분석하여 사용자가 입력한 변수 값을 기반으로 조건식(예: `!= null`, `!var.isEmpty()`, `var == "Y"` 등)의 참/거짓을 평가한 뒤, 활성화되는 동적 SQL만 컴파일하여 실행합니다.

3. **지능형 파라미터 입력 다이얼로그**
   - 쿼리 내 물리적 순서대로 변수 입력 필드가 나열됩니다.
   - 이름이 같은 동일 변수는 값을 하나만 입력해도 다른 필드에 실시간으로 값이 복사(양방향 동기화)됩니다.
   - 각 입력 필드 우측에 개별 지우기 `[X]` 버튼 및 하단에 `[전체 초기화]` 버튼을 제공합니다.

4. **강력한 결과 조회 뷰 (QueryResultView)**
   - **반반 분할 레이아웃 (50:50)**: 상단에는 쿼리 결과 테이블 그리드, 하단에는 실제 파라미터가 바인딩되어 실행된 **완성형 SQL 문장**이 표시됩니다.
   - **단일 셀 Ctrl+C 복사**: 표의 특정 칸(Cell) 하나만 선택하여 간편하게 클립보드에 복사할 수 있습니다.
   - **NULL 값 시각화**: 데이터베이스 결과 중 `NULL` 값은 빈 문자열로 표시하며, 부드러운 노란색 배경색을 입혀 한눈에 구분되도록 강조합니다.
   - **예외 자동 추적**: DB 실행 오류 발생 시, 상단 그리드에 에러 원인을 표시하고 하단에 실행을 시도했던 완성형 SQL을 즉시 렌더링합니다.

5. **최적화된 성능 및 안정성**
   - JDBC Driver Jar 로딩 및 인스턴스 생성을 메모리(`static`)에 캐싱하여 두 번째 쿼리 실행부터 드라이버 로드 딜레이가 `0ms`로 작동합니다.
   - 대용량 데이터 조회로 인한 이클립스 OOM(Out Of Memory) 및 화면 멈춤(Freeze)을 예방하기 위해 설정된 `Max Rows Limit` 내에서 조회를 강제 제한합니다.

---

## 🛠️ 개발 및 실행 환경 (Requirements)

- **JDK Version**: Java 17 이상 권장
- **IDE**: Eclipse 4.22 이상 또는 STS 4.x 이상 (Plug-in Development Environment(PDE) 환경 필요)

---

## ⚙️ 설정 방법 (Database Configuration)

플러그인을 실행하기 전에 먼저 대상 데이터베이스의 접속 정보를 설정해야 합니다.

1. 이클립스/STS 상단 메뉴에서 **Window > Preferences**로 이동합니다.
2. 설정 창 좌측에서 **iBATIS SQL Executor** (또는 등록된 설정 페이지 명칭)을 선택합니다.
3. 다음 항목들을 입력합니다:
   - **JDBC Driver Jar**: 데이터베이스 드라이버 파일 경로 지정 (예: `ojdbc8.jar`, `mysql-connector-j-x.x.x.jar` 등)
   - **JDBC Driver Class**: 연결할 드라이버의 클래스 명 (예: `oracle.jdbc.OracleDriver`, `com.mysql.cj.jdbc.Driver`)
   - **JDBC Connection URL**: 데이터베이스 연결 주소 (예: `jdbc:oracle:thin:@localhost:1521:xe`)
   - **Database User**: 사용자 계정명
   - **Database Password**: 패스워드
   - **Max Rows Limit**: 최대 조회 행 수 제한 (기본값: 1000)
4. **[Test Connection]** 버튼을 클릭하여 데이터베이스와 정상적으로 연결되는지 확인 후 `Apply and Close`를 눌러 저장합니다.

---

## 📖 사용 방법 (How to Use)

1. **SQL XML 매퍼 파일 열기**: 이클립스 XML 에디터에서 실행하고자 하는 MyBatis/iBATIS XML 파일을 엽니다.
2. **단축키 실행**:
   - 실행하려는 `<select>` 등의 태그 내부에 마우스 커서를 위치시키거나, 실행할 SQL 영역을 마우스로 드래그합니다.
   - 단축키 **`Alt + Q`**를 입력합니다.
3. **파라미터 입력**:
   - 화면에 파라미터 입력창이 나타나면 쿼리에 들어갈 파라미터 값을 입력하고 `OK`를 누릅니다.
   - `<if>` 조건식에 포함된 변수는 파란색으로 표시되어 쉽게 구분할 수 있습니다.
4. **결과 확인**:
   - 하단 `Query Result` 뷰에 조회 건수, 결과 데이터 목록 및 실제 데이터베이스로 전송된 완성형 SQL이 표시됩니다.

---

## 📦 배포 및 설치 (Deploy & Install)

본인의 다른 개발 컴퓨터나 팀원에게 플러그인을 공유하여 설치하는 방법입니다.

### 1단계: 플러그인 내보내기 (Export)
1. STS/이클립스 **Package Explorer**에서 `sql4ibatis` 프로젝트를 우클릭한 후 **Export...**를 선택합니다.
2. **Plug-in Development > Deployable plug-ins and fragments**를 선택하고 **Next**를 누릅니다.
3. 대상 프로젝트(`sql4ibatis`)를 체크하고 **Destination** 탭에서 내보낼 로컬 디렉토리(예: `C:\temp`)를 선택한 후 **Finish**를 누릅니다.
4. 지정한 경로의 `plugins/` 폴더 하위에 **`sql4ibatis_1.1.0.<빌드일자>.jar`** 배포용 파일이 생성됩니다.

### 2단계: 수동 설치 및 재시작 (Install)
1. 설치를 진행할 타겟 이클립스/STS의 홈 디렉토리 내의 **`dropins`** 폴더를 찾습니다.
   - 예: `C:\STS-4.22.0.RELEASE\dropins\`
2. 생성된 배포용 `.jar` 파일을 이 `dropins` 폴더 안에 복사해 넣습니다.
3. 이클립스/STS를 재시작합니다.
   - 만약 플러그인이 정상 작동하지 않거나 캐시로 인해 등록이 지연된다면, 이클립스 실행 시 `-clean` 옵션을 부여하여 캐시를 초기화해 줍니다.
     ```cmd
     SpringToolSuite4.exe -clean
     ```
