package com.example.pillrecognitionapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================
// 하단 탭
// =============================================================
enum class HomeTab {
    HOME,
    SEARCH,
    PILL_INFO,
    PROFILE
}

// =============================================================
// 홈 탭 내부 라우트
// =============================================================
sealed class HomeRoute {
    object Home : HomeRoute()
    data class CategoryDetail(val categoryName: String) : HomeRoute()
    data class RecommendedDetail(val pillName: String) : HomeRoute()
    data class RecentSearchDetail(val keyword: String) : HomeRoute()
    data class FavoriteDetail(val pillName: String) : HomeRoute()
}

// =============================================================
// 내정보 탭 내부 라우트
// =============================================================
sealed class MyInfoRoute {
    object MyInfoHome : MyInfoRoute()

    // 내 약 목록
    object MyMeds : MyInfoRoute()
    object SavedMeds : MyInfoRoute()
    object CurrentMeds : MyInfoRoute()

    // ✅ 추가된 화면
    object MedDetailInfo : MyInfoRoute()
    object MedHistory : MyInfoRoute()

    // 건강 정보
    object HealthInfo : MyInfoRoute()

    // 기록
    object History : MyInfoRoute()

    // 계정 정보
    object Account : MyInfoRoute()
}

// =============================================================
// 상위 HomeScreen
// =============================================================
@Composable
fun HomeScreen(
    onClickSearchCamera: () -> Unit
) {
    var currentTab by remember { mutableStateOf(HomeTab.HOME) }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()

        when (currentTab) {
            HomeTab.HOME -> HomeTabContent(modifier = contentModifier)
            HomeTab.SEARCH -> SearchTab(
                modifier = contentModifier,
                onClickSearchCamera = onClickSearchCamera
            )
            HomeTab.PILL_INFO -> PillInfoTab(modifier = contentModifier)
            HomeTab.PROFILE -> ProfileTab(modifier = contentModifier)
        }
    }
}

// =============================================================
// 하단 네비게이션 바
// =============================================================
@Composable
private fun BottomNavBar(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentTab == HomeTab.HOME,
            onClick = { onTabSelected(HomeTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "홈") },
            label = { Text("홈") }
        )
        NavigationBarItem(
            selected = currentTab == HomeTab.SEARCH,
            onClick = { onTabSelected(HomeTab.SEARCH) },
            icon = { Icon(Icons.Filled.Search, contentDescription = "검색") },
            label = { Text("검색") }
        )
        NavigationBarItem(
            selected = currentTab == HomeTab.PILL_INFO,
            onClick = { onTabSelected(HomeTab.PILL_INFO) },
            icon = { Icon(Icons.Filled.Medication, contentDescription = "약정보") },
            label = { Text("약정보") }
        )
        NavigationBarItem(
            selected = currentTab == HomeTab.PROFILE,
            onClick = { onTabSelected(HomeTab.PROFILE) },
            icon = { Icon(Icons.Filled.Person, contentDescription = "내정보") },
            label = { Text("내정보") }
        )
    }
}

// =============================================================
// 1) 홈 탭
// =============================================================
@Composable
private fun HomeTabContent(
    modifier: Modifier = Modifier
) {
    var homeRoute by remember { mutableStateOf<HomeRoute>(HomeRoute.Home) }

    when (val route = homeRoute) {
        is HomeRoute.Home -> {
            HomeTabHomeScreen(
                modifier = modifier,
                onCategoryClick = { name ->
                    homeRoute = HomeRoute.CategoryDetail(name)
                },
                onRecommendedClick = { pill ->
                    homeRoute = HomeRoute.RecommendedDetail(pill)
                },
                onRecentSearchClick = { keyword ->
                    homeRoute = HomeRoute.RecentSearchDetail(keyword)
                },
                onFavoriteClick = { pill ->
                    homeRoute = HomeRoute.FavoriteDetail(pill)
                }
            )
        }

        is HomeRoute.CategoryDetail -> {
            SimpleDetailScreen(
                title = "카테고리: ${route.categoryName}",
                body = "\"${route.categoryName}\" 카테고리에 대한 상세 화면입니다.",
                onBack = { homeRoute = HomeRoute.Home },
                modifier = modifier
            )
        }

        is HomeRoute.RecommendedDetail -> {
            SimpleDetailScreen(
                title = route.pillName,
                body = "추천 약 \"${route.pillName}\"에 대한 상세 화면입니다.",
                onBack = { homeRoute = HomeRoute.Home },
                modifier = modifier
            )
        }

        is HomeRoute.RecentSearchDetail -> {
            SimpleDetailScreen(
                title = route.keyword,
                body = "최근 검색어 \"${route.keyword}\"에 대한 결과 화면입니다.",
                onBack = { homeRoute = HomeRoute.Home },
                modifier = modifier
            )
        }

        is HomeRoute.FavoriteDetail -> {
            SimpleDetailScreen(
                title = "즐겨찾기: ${route.pillName}",
                body = "\"${route.pillName}\" 즐겨찾기 약의 상세 화면입니다.",
                onBack = { homeRoute = HomeRoute.Home },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun HomeTabHomeScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit,
    onRecommendedClick: (String) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onFavoriteClick: (String) -> Unit
) {
    val categories = listOf("정제", "시럽/액상", "기타")
    val recentSearches = listOf("아세트아미노펜", "아스피린", "타이레놀")
    val favorites = listOf("이부프로펜", "멜록시캄")

    Column(
        modifier = modifier
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 카테고리
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "카테고리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                categories.forEach { name ->
                    CategoryChip(
                        label = name,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCategoryClick(name) }
                    )
                }
            }
        }

        // 추천 약
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "추천 약",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val recommendedPill = "아세트아미노펜"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRecommendedClick(recommendedPill) },
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = recommendedPill,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("진통제", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("1일 3회 식후", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 최근 검색
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "최근 검색",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            recentSearches.forEach { keyword ->
                Text(
                    text = keyword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentSearchClick(keyword) }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // ✅ 즐겨 찾기
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "즐겨 찾기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            favorites.forEach { pill ->
                Text(
                    text = pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFavoriteClick(pill) }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// 공통 상세 화면
@Composable
private fun SimpleDetailScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("뒤로")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(text = body, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
    }
}

// 카테고리 칩
@Composable
private fun CategoryChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFE0E5F8)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontWeight = FontWeight.Medium)
        }
    }
}

// =============================================================
// 2) 검색 탭
// =============================================================
@Composable
private fun SearchTab(
    modifier: Modifier = Modifier,
    onClickSearchCamera: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }

    if (showResult) {
        SearchResultScreen(
            keyword = query,
            onBack = { showResult = false }
        )
    } else {
        Column(
            modifier = modifier
                .background(Color(0xFFF6F6FB))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "약 검색",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("약 이름 또는 증상을 입력하세요") },
                singleLine = true
            )

            // 검색 / 카메라 검색 버튼 (같은 크기)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showResult = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3F4E8C),
                        contentColor = Color.White
                    )
                ) {
                    Text("검색")
                }

                Button(
                    onClick = onClickSearchCamera,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3F4E8C),
                        contentColor = Color.White
                    )
                ) {
                    Text("카메라로 검색")
                }
            }
        }
    }
}

// 검색 결과 전체 화면
@Composable
private fun SearchResultScreen(
    keyword: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("뒤로")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = keyword.ifBlank { "검색 결과" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        // 예시 더미 데이터
        Text(
            text = """
                효능/효과
                - 두통, 치통 등 경증 통증 완화에 사용됩니다. (예시 데이터)

                용법/용량
                - 성인: 1회 1정, 1일 3회 식후 복용. (예시 데이터)

                성분
                - 아세트아미노펜 160mg (예시 데이터)

                부작용
                - 메스꺼움, 속쓰림 등이 나타날 수 있습니다. (예시 데이터)

                금기
                - 심한 간 질환 환자는 사용하지 않습니다. (예시 데이터)

                상호작용
                - 다른 진통제와 병용 시 주의가 필요합니다. (예시 데이터)
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )
    }
}

// =============================================================
// 3) 약정보 탭 (상비약 / 처방약 탭 구조만 간단 구현)
// =============================================================
@Composable
private fun PillInfoTab(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("상비약", "처방약")

    Column(
        modifier = modifier
            .background(Color(0xFFF6F6FB))
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> PillInfoContentScreen(title = "상비약 정보")
            1 -> PillInfoContentScreen(title = "처방약 정보")
        }
    }
}

@Composable
private fun PillInfoContentScreen(
    title: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "가정에서常備하는 약들에 대한 정보를 보여주는 화면입니다. (예시)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// =============================================================
// 4) 내정보 탭
// =============================================================
@Composable
fun ProfileTab(
    modifier: Modifier = Modifier
) {
    var currentMyInfoRoute by remember { mutableStateOf<MyInfoRoute>(MyInfoRoute.MyInfoHome) }

    when (currentMyInfoRoute) {
        is MyInfoRoute.MyInfoHome -> {
            MyInfoHomeScreen(
                modifier = modifier,
                onClickMyMeds = { currentMyInfoRoute = MyInfoRoute.MyMeds },
                onClickHealthInfo = { currentMyInfoRoute = MyInfoRoute.HealthInfo },
                onClickHistory = { currentMyInfoRoute = MyInfoRoute.History },
                onClickAccount = { currentMyInfoRoute = MyInfoRoute.Account }
            )
        }

        is MyInfoRoute.MyMeds -> {
            MyMedsScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyInfoHome },
                onOpenSavedMeds = { currentMyInfoRoute = MyInfoRoute.SavedMeds },
                onOpenCurrentMeds = { currentMyInfoRoute = MyInfoRoute.CurrentMeds }
            )
        }

        is MyInfoRoute.SavedMeds -> {
            SavedMedsScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyMeds },
                onOpenMedDetailInfo = { currentMyInfoRoute = MyInfoRoute.MedDetailInfo },
                onOpenMedHistory = { currentMyInfoRoute = MyInfoRoute.MedHistory }
            )
        }

        is MyInfoRoute.CurrentMeds -> {
            CurrentMedsScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyMeds }
            )
        }

        is MyInfoRoute.MedDetailInfo -> {
            MedDetailInfoScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.SavedMeds }
            )
        }

        is MyInfoRoute.MedHistory -> {
            MedHistoryScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.SavedMeds }
            )
        }

        is MyInfoRoute.HealthInfo -> {
            HealthInfoScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyInfoHome }
            )
        }

        is MyInfoRoute.History -> {
            HistoryScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyInfoHome }
            )
        }

        is MyInfoRoute.Account -> {
            AccountScreen(
                onBack = { currentMyInfoRoute = MyInfoRoute.MyInfoHome }
            )
        }
    }
}

// ---------- 내정보 홈 ----------
@Composable
fun MyInfoHomeScreen(
    modifier: Modifier = Modifier,
    onClickMyMeds: () -> Unit,
    onClickHealthInfo: () -> Unit,
    onClickHistory: () -> Unit,
    onClickAccount: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "내 정보",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 내 약 목록
        InfoSectionCard(
            title = "내 약 목록 (MY MEDS)",
            items = listOf("저장한 약 보기", "복용 중인 약 관리"),
            onItemClick = { index ->
                when (index) {
                    0 -> onClickMyMeds()
                    1 -> onClickMyMeds()
                }
            }
        )

        // 건강 정보
        InfoSectionCard(
            title = "건강 정보 (HEALTH INFO)",
            items = listOf("복용 중인 약품", "알레르기 정보"),
            onItemClick = { _ -> onClickHealthInfo() }
        )

        // 기록
        InfoSectionCard(
            title = "기록 (HISTORY)",
            items = listOf("최근 검색 기록", "기록 전체 관리"),
            onItemClick = { _ -> onClickHistory() }
        )

        // 계정 정보
        InfoSectionCard(
            title = "계정 정보 (ACCOUNT)",
            items = listOf("프로필 정보", "이메일 계정"),
            onItemClick = { _ -> onClickAccount() }
        )
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    items: List<String>,
    onItemClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Divider(color = Color(0xFFE0E0E0))

            items.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(index) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// ---------- 내 약 목록 ----------
@Composable
fun MyMedsScreen(
    onBack: () -> Unit,
    onOpenSavedMeds: () -> Unit,
    onOpenCurrentMeds: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "내 약 목록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        // ✅ 카드 안에서는 제목 없이 세부 항목만 표시
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSavedMeds() }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "저장한 약 보기",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Divider(color = Color(0xFFE0E0E0))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCurrentMeds() }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "복용 중인 약 관리",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// ---------- 저장한 약 보기 ----------
@Composable
fun SavedMedsScreen(
    onBack: () -> Unit,
    onOpenMedDetailInfo: () -> Unit,
    onOpenMedHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("뒤로")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "저장한 약 보기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMedDetailInfo() }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "약별 상세 정보",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Divider(color = Color(0xFFE0E0E0))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMedHistory() }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "복용 히스토리",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---------- 복용 중인 약 ----------
@Composable
fun CurrentMedsScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "복용 중인 약 관리",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "현재 복용 중인 약을 관리하는 화면입니다. (예시)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------- 약별 상세 정보 ----------
@Composable
fun MedDetailInfoScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "약별 상세 정보",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "저장해 둔 각 약의 상세 정보를 보여주는 화면입니다.\n" +
                    "향후 효능, 용법/용량, 성분, 부작용 등을 실제 데이터와 연동할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------- 복용 히스토리 ----------
@Composable
fun MedHistoryScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "복용 히스토리",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "사용자의 약 복용 이력을 시간순으로 보여주는 화면입니다.\n" +
                    "향후 캘린더 뷰나 타임라인 형식으로 확장할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------- 건강 정보 ----------
@Composable
fun HealthInfoScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "건강 정보",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "복용 중인 약품, 알레르기 정보 등을 관리하는 화면입니다. (예시)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------- 기록 ----------
@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "기록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "검색 기록과 분석 기록 등을 관리하는 화면입니다. (예시)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---------- 계정 정보 ----------
@Composable
fun AccountScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6FB))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "계정 정보",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "프로필, 이메일 계정 등 로그인 정보를 확인/수정하는 화면입니다. (예시)",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}