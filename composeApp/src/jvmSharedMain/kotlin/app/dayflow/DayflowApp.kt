@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.dayflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dayflow.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF4C6FE8)
private val PriorityColors = listOf(Color(0xFF8792A5), Color(0xFF4C84D9), Color(0xFFD49B2B), Color(0xFFD95555))
private val viewNames = mapOf(View.ALL to "모든 할 일", View.INBOX to "수집함", View.TODAY to "오늘", View.UPCOMING to "다음 7일", View.COMPLETED to "완료", View.TRASH to "휴지통")

@Composable
fun LoadDayflow(store: SnapshotStore) {
    var repository by remember(store) { mutableStateOf<TaskRepository?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try { repository = TaskRepository.open(store) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "데이터를 읽을 수 없습니다." }
    }
    var dark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val isDark = dark ?: isSystemInDarkTheme()
    DayflowTheme(isDark) {
        Surface(Modifier.fillMaxSize()) {
            val repo = repository
            if (repo != null) DayflowApp(repo, isDark) { dark = !isDark }
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator()
                else Column(Modifier.padding(32.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("데이터를 열 수 없습니다", style = MaterialTheme.typography.headlineSmall)
                    Text(error!!)
                    Text("원본 파일은 보존되어 있습니다. 백업을 확인한 뒤 다시 실행해 주세요.")
                }
            }
        }
    }
}

@Composable
fun DayflowTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9DAFFF)) else lightColorScheme(primary = Blue, primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Color(0xFF233663), surface = Color.White, surfaceContainerLow = Color(0xFFF5F7FB), background = Color(0xFFF7F8FA)), content = content)
}

@Composable
fun DayflowApp(repository: TaskRepository, dark: Boolean, toggleTheme: () -> Unit) {
    val snapshot by repository.state.collectAsState()
    var view by rememberSaveable { mutableStateOf(View.TODAY) }
    var selectedList by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.DUE) }
    var editor by remember { mutableStateOf<TaskItem?>(null) }
    var showListDialog by remember { mutableStateOf(false) }
    var showNav by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var quickTitle by rememberSaveable { mutableStateOf("") }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); today = LocalDate.now() } }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    fun mutate(success: String? = null, action: suspend () -> Unit) {
        if (pending) return
        pending = true
        operationError = null
        scope.launch {
            var notice = success
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = e.message ?: "저장하지 못했습니다."; operationError = notice }
            finally { pending = false }
            notice?.let { snackbar.showSnackbar(it) }
        }
    }
    val tasks = snapshot.query(TaskQuery(view, selectedList, search, sort), today)
    val title = if (view == View.LIST) snapshot.lists.find { it.id == selectedList }?.name ?: "목록" else viewNames[view]!!
    fun newTask() = TaskItem(title = "", listId = if (view == View.LIST) selectedList ?: INBOX else INBOX, dueDate = if (view == View.TODAY) today.toString() else null)

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 850.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationPanel(snapshot, view, selectedList, today, dark, { v, id -> view = v; selectedList = id }, { showListDialog = true }, toggleTheme, Modifier.width(248.dp).fillMaxHeight())
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!wide) IconButton(onClick = { showNav = true }) { Icon(Icons.Outlined.Menu, "목록 열기") }
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                                if (view == View.TODAY) Text(today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text("${tasks.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "정렬") }
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    listOf(Sort.DUE to "날짜순", Sort.PRIORITY to "우선순위순", Sort.TITLE to "제목순", Sort.CREATED to "최근 생성순").forEach { (mode, label) ->
                                        DropdownMenuItem(text = { Text((if (sort == mode) "✓ " else "") + label) }, onClick = { sort = mode; sortMenu = false })
                                    }
                                }
                            }
                        }
                        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), placeholder = { Text("제목, 설명, 태그 검색") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "검색 지우기") } }, singleLine = true, shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    }
                },
                bottomBar = {
                    if (view != View.TRASH && view != View.COMPLETED) Row(Modifier.fillMaxWidth().imePadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(quickTitle, { quickTitle = it }, Modifier.weight(1f), placeholder = { Text("할 일 추가") }, enabled = !pending, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = {
                            if (quickTitle.isNotBlank() && !pending) mutate { repository.saveTask(newTask().copy(title = quickTitle)); quickTitle = "" }
                        }), shape = RoundedCornerShape(12.dp))
                        IconButton(onClick = { if (quickTitle.isBlank()) editor = newTask() else mutate { repository.saveTask(newTask().copy(title = quickTitle)); quickTitle = "" } }, enabled = !pending) { Icon(Icons.Outlined.Add, "할 일 추가", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            ) { padding ->
                if (tasks.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (search.isBlank()) Icons.Outlined.CheckCircle else Icons.Outlined.Search, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Text(if (search.isNotBlank()) "검색 결과가 없습니다" else if (view == View.TRASH) "휴지통이 비어 있습니다" else if (view == View.COMPLETED) "완료한 할 일이 없습니다" else "할 일이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(task, snapshot.lists.find { it.id == task.listId }?.name ?: "수집함", today, pending, { editor = task }, { mutate { repository.toggleComplete(task.id) } }, { mutate("휴지통으로 이동했습니다") { repository.trash(task.id) } }, { mutate { repository.restore(task.id) } })
                    }
                }
            }
        }
        if (showNav && !wide) ModalBottomSheet(onDismissRequest = { showNav = false }) {
            NavigationPanel(snapshot, view, selectedList, today, dark, { v, id -> view = v; selectedList = id; showNav = false }, { showListDialog = true; showNav = false }, toggleTheme, Modifier.fillMaxWidth().heightIn(max = 600.dp))
        }
    }
    editor?.let { task ->
        TaskEditor(task, snapshot.lists, pending, operationError, { editor = null; operationError = null }) { edited ->
            mutate { repository.saveTask(edited); editor = null }
        }
    }
    if (showListDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!pending) showListDialog = false }, title = { Text("새 목록") }, text = { OutlinedTextField(name, { name = it }, label = { Text("목록 이름") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank() && !pending, onClick = { mutate { repository.addList(name); showListDialog = false } }) { Text("만들기") } }, dismissButton = { TextButton(onClick = { showListDialog = false }) { Text("취소") } })
    }
}

@Composable
private fun NavigationPanel(snapshot: Snapshot, view: View, listId: String?, today: LocalDate, dark: Boolean, select: (View, String?) -> Unit, addList: () -> Unit, toggleTheme: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Outlined.DoneAll, null, Modifier.padding(8.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Text("Dayflow", Modifier.padding(start = 12.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        val icons = mapOf(View.ALL to Icons.Outlined.Checklist, View.INBOX to Icons.Outlined.Inbox, View.TODAY to Icons.Outlined.Today, View.UPCOMING to Icons.Outlined.DateRange, View.COMPLETED to Icons.Outlined.CheckCircle, View.TRASH to Icons.Outlined.DeleteOutline)
        listOf(View.ALL, View.INBOX, View.TODAY, View.UPCOMING).forEach { item -> NavRow(viewNames[item]!!, icons[item]!!, view == item, snapshot.query(TaskQuery(item), today).size) { select(item, null) } }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 목록", Modifier.weight(1f).padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            IconButton(onClick = addList) { Icon(Icons.Outlined.Add, "목록 만들기", Modifier.size(20.dp)) }
        }
        snapshot.lists.filter { it.id != INBOX }.forEach { list -> NavRow(list.name, Icons.Outlined.Tag, view == View.LIST && listId == list.id, snapshot.query(TaskQuery(View.LIST, list.id), today).size) { select(View.LIST, list.id) } }
        listOf(View.COMPLETED, View.TRASH).forEach { item -> NavRow(viewNames[item]!!, icons[item]!!, view == item, snapshot.query(TaskQuery(item), today).size) { select(item, null) } }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = toggleTheme) { Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(if (dark) "밝은 화면" else "어두운 화면") }
    }
}

@Composable
private fun NavRow(title: String, icon: ImageVector, selected: Boolean, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            if (count > 0) Text(count.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, listName: String, today: LocalDate, pending: Boolean, edit: () -> Unit, toggle: () -> Unit, trash: () -> Unit, restore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = task.deletedAt == null, onClick = edit).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(task.completedAt != null, { toggle() }, enabled = !pending && task.deletedAt == null, colors = CheckboxDefaults.colors(uncheckedColor = PriorityColors[task.priority]))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (task.completedAt != null) TextDecoration.LineThrough else null, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(listName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                task.dueDate?.let { date -> Text(date, fontSize = 11.sp, color = if (LocalDate.parse(date) < today && task.completedAt == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                if (task.checklist.isNotEmpty()) Text("${task.checklist.count { it.done }}/${task.checklist.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.tags.isNotEmpty()) Text(task.tags.joinToString("  ") { "#$it" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        if (task.deletedAt != null) IconButton(onClick = restore, enabled = !pending) { Icon(Icons.Outlined.Restore, "복원", Modifier.size(20.dp)) }
        else IconButton(onClick = trash, enabled = !pending) { Icon(Icons.Outlined.DeleteOutline, "휴지통으로 이동", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
}

@Composable
private fun TaskEditor(task: TaskItem, lists: List<TaskList>, pending: Boolean, error: String?, dismiss: () -> Unit, save: (TaskItem) -> Unit) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    var date by remember(task.id) { mutableStateOf(task.dueDate) }
    var priority by remember(task.id) { mutableIntStateOf(task.priority) }
    var listId by remember(task.id) { mutableStateOf(task.listId) }
    var tags by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var checks by remember(task.id) { mutableStateOf(task.checklist) }
    var checkTitle by remember { mutableStateOf("") }
    var datePicker by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!pending) dismiss() },
        title = { Text(if (task.title.isBlank()) "새 할 일" else "할 일 수정") },
        text = {
            Column(Modifier.widthIn(min = 260.dp, max = 520.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("제목") }, isError = title.length > 1000, maxLines = 3)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("설명") }, minLines = 3, maxLines = 8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { datePicker = true }) { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(date ?: "날짜 설정") }
                    if (date != null) IconButton(onClick = { date = null }) { Icon(Icons.Outlined.Close, "날짜 해제", Modifier.size(18.dp)) }
                }
                Text("우선순위", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("없음", "낮음", "중간", "높음").forEachIndexed { i, label -> FilterChip(priority == i, { priority = i }, label = { Text(label, fontSize = 12.sp) }) }
                }
                Box {
                    OutlinedButton(onClick = { listMenu = true }) { Icon(Icons.Outlined.Inbox, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(lists.find { it.id == listId }?.name ?: "수집함") }
                    DropdownMenu(listMenu, { listMenu = false }) { lists.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = { listId = list.id; listMenu = false }) } }
                }
                OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("태그 · 쉼표로 구분") }, singleLine = true)
                Text("체크리스트", style = MaterialTheme.typography.labelMedium)
                checks.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.done, { checks = checks.map { if (it.id == item.id) it.copy(done = !it.done) else it } })
                        Text(item.title, Modifier.weight(1f), textDecoration = if (item.done) TextDecoration.LineThrough else null)
                        IconButton(onClick = { checks = checks.filterNot { it.id == item.id } }) { Icon(Icons.Outlined.Close, "항목 제거", Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(checkTitle, { checkTitle = it }, Modifier.weight(1f), placeholder = { Text("체크 항목 추가") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (checkTitle.isNotBlank()) { checks = checks + CheckItem(title = checkTitle.trim()); checkTitle = "" } }))
                    IconButton(onClick = { if (checkTitle.isNotBlank()) { checks = checks + CheckItem(title = checkTitle.trim()); checkTitle = "" } }, enabled = checkTitle.isNotBlank()) { Icon(Icons.Outlined.Add, "체크 항목 추가") }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && title.length <= 1000 && notes.length <= 100_000 && !pending, onClick = { save(task.copy(title = title, notes = notes, dueDate = date, priority = priority, listId = listId, tags = tags.split(","), checklist = checks)) }) { Text(if (pending) "저장 중…" else "저장") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !pending) { Text("취소") } },
    )
    if (datePicker) CalendarDialog(date, { datePicker = false }) { date = it; datePicker = false }
}

@Composable
private fun CalendarDialog(selected: String?, dismiss: () -> Unit, choose: (String) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected?.let(LocalDate::parse) ?: LocalDate.now())) }
    val offset = month.atDay(1).dayOfWeek.value % 7
    AlertDialog(onDismissRequest = dismiss, title = { Text("날짜") }, text = {
        Column(Modifier.widthIn(max = 360.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { choose(LocalDate.now().toString()) }) { Text("오늘") }
                TextButton(onClick = { choose(LocalDate.now().plusDays(1).toString()) }) { Text("내일") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "이전 달") }
                Text("${month.year}년 ${month.monthValue}월", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "다음 달") }
            }
            Row { listOf("일", "월", "화", "수", "목", "금", "토").forEach { Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row {
                    repeat(7) { col ->
                        val day = week * 7 + col - offset + 1
                        Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                            if (day in 1..month.lengthOfMonth()) {
                                val value = month.atDay(day).toString()
                                Surface(onClick = { choose(value) }, shape = RoundedCornerShape(20.dp), color = if (selected == value) MaterialTheme.colorScheme.primary else Color.Transparent) {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(day.toString(), fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("닫기") } })
}
