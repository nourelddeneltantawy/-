package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import com.example.ui.components.TechnicianGroupChatView
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MarketplacePost
import com.example.data.model.UserProfile
import com.example.data.repository.MarketplaceRepository
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.BreadcrumbItem
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.SearchAutocompleteBox
import com.example.util.HierarchicalLocationSelector
import com.example.util.ImageHelper
import com.example.util.LocationData
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun MarketplaceScreen(
    currentUserId: String,
    currentUserEmail: String?,
    isAdmin: Boolean,
    onBackClick: () -> Unit,
    isGuest: Boolean = false,
    onAuthClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val posts by MarketplaceRepository.posts.collectAsState()
    val rawProfile by MarketplaceRepository.currentUserProfile.collectAsState()

    val myProfile = rawProfile ?: UserProfile(
        userId = currentUserId,
        email = currentUserEmail ?: "",
        displayName = currentUserEmail?.substringBefore("@") ?: "فني البراء"
    )

    // 0: Marketplace Posts (سوق ومنشورات الكروت), 1: Technicians Group Chat (جروب الصنايعية والفنيين)
    // 0: SELL, 1: BUY, 2: MY_POSTS (Facebook-style profile/posts)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGovernorateFilter by remember { mutableStateOf("الكل") }

    var showCreatePostDialog by remember { mutableStateOf(false) }
    var postToEdit by remember { mutableStateOf<MarketplacePost?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showTechniciansDialog by remember { mutableStateOf(false) }
    var postToDelete by remember { mutableStateOf<MarketplacePost?>(null) }
    var zoomedImageUrl by remember { mutableStateOf<String?>(null) }
    var profileToView by remember { mutableStateOf<UserProfile?>(null) }
    var showFilterDropdown by remember { mutableStateOf(false) }

    // Account isolation: sync active user on email changes
    LaunchedEffect(currentUserEmail) {
        MarketplaceRepository.switchUser(context, currentUserEmail, currentUserId)
    }

    // Dynamic autocomplete suggestions from current posts
    val autocompleteSuggestions = remember(posts) {
        val list = mutableListOf<String>()
        posts.forEach { p ->
            if (p.title.isNotBlank()) list.add(p.title)
            if (p.userCity.isNotBlank()) list.add(p.userCity)
            if (p.userName.isNotBlank()) list.add(p.userName)
        }
        list.addAll(listOf("إل جي", "سامسونج", "شارب", "هوفر", "زانوسي", "إنفرتر", "ماتور", "كارتة باور", "كارتة شاشة", "لوك", "طلمبة"))
        list.distinct()
    }

    // Filter posts
    val filteredPosts = remember(posts, selectedTabIndex, searchQuery, selectedGovernorateFilter, currentUserId, currentUserEmail) {
        when (selectedTabIndex) {
            0 -> posts.filter { p -> p.postType.equals("SELL", ignoreCase = true) }
            1 -> posts.filter { p -> p.postType.equals("BUY", ignoreCase = true) }
            else -> posts.filter { p ->
                p.userId == currentUserId ||
                        (currentUserEmail != null && p.userEmail.equals(currentUserEmail, ignoreCase = true))
            }
        }.filter { post ->
            val matchGov = selectedGovernorateFilter == "الكل" ||
                    post.userCity.contains(selectedGovernorateFilter, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() ||
                    post.title.contains(searchQuery, ignoreCase = true) ||
                    post.description.contains(searchQuery, ignoreCase = true) ||
                    post.userCity.contains(searchQuery, ignoreCase = true) ||
                    post.userName.contains(searchQuery, ignoreCase = true)
            matchGov && matchQuery
        }
    }

    val myPostsCount = remember(posts, currentUserId, currentUserEmail) {
        posts.count { p ->
            p.userId == currentUserId ||
                    (currentUserEmail != null && p.userEmail.equals(currentUserEmail, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("marketplace_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTabIndex != 2) {
                FloatingActionButton(
                    onClick = {
                        if (isGuest || currentUserEmail.isNullOrBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("يرجى تسجيل الدخول بحسابك أولاً لتتمكن من النشر وحفظ إعلاناتك")
                            }
                            onAuthClick()
                        } else if (myProfile.phone.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("يرجى إكمال ملفك الشخصي وإضافة رقم الهاتف أولاً للتواصل")
                            }
                            showProfileDialog = true
                        } else {
                            postToEdit = null
                            showCreatePostDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_marketplace_post_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة إعلان")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "عرض كارتة للبيع" else "طلب كارتة للشراء",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Sleek Compact Top Header Box (Requirement 4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(top = innerPadding.calculateTopPadding() + 4.dp, bottom = 8.dp, start = 14.dp, end = 14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "سوق ومنشورات الكروت 🛒",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Admin Technicians Directory Button
                            if (isAdmin) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showTechniciansDialog = true }
                                        .padding(horizontal = 7.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("الفنيون", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            // My Profile Button (compact, single line)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isGuest) onAuthClick() else showProfileDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isGuest) Icons.Default.Lock else Icons.Default.Person,
                                        contentDescription = "الملف الشخصي",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isGuest) "دخول" else "ملفي",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Predictive Search Autocomplete Box
                    SearchAutocompleteBox(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "ابحث بالاسم، الموديل، الحي، أو الفني...",
                        allSuggestions = autocompleteSuggestions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Breadcrumbs Navigation Trail
            BreadcrumbBar(
                items = listOf(
                    BreadcrumbItem(title = "الأقسام", onClick = onBackClick),
                    BreadcrumbItem(title = "سوق الكروت", onClick = { selectedTabIndex = 0 }),
                    BreadcrumbItem(
                        title = when (selectedTabIndex) {
                            0 -> "بيع كروت 🏷️"
                            1 -> "طلب شراء 🔍"
                            else -> "صفحتي وإعلاناتي 👤"
                        },
                        isCurrent = true
                    )
                ),
                onHomeClick = onBackClick
            )

            // Tabs: 1. SELL, 2. BUY, 3. MY POSTS & PROFILE
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "بيع كروت 🏷️ (${posts.count { it.postType.equals("SELL", ignoreCase = true) }})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Text(
                            text = "طلب شراء 🔍 (${posts.count { it.postType.equals("BUY", ignoreCase = true) }})",
                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            text = "إعلاناتي 👤 ($myPostsCount)",
                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            // Location Filter Bar (only for Sell/Buy tabs)
            if (selectedTabIndex != 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredPosts.size} إعلان متاح",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    )

                    // Governorate Filter Pill
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showFilterDropdown = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (selectedGovernorateFilter == "الكل") "تصفية بالمحافظة: الكل" else "المحافظة: $selectedGovernorateFilter",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showFilterDropdown,
                            onDismissRequest = { showFilterDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("جميع المحافظات") },
                                onClick = {
                                    selectedGovernorateFilter = "الكل"
                                    showFilterDropdown = false
                                }
                            )
                            val egyptGovs = LocationData.countries.first().governorates.keys.toList()
                            egyptGovs.forEach { gov ->
                                DropdownMenuItem(
                                    text = { Text(gov) },
                                    onClick = {
                                        selectedGovernorateFilter = gov
                                        showFilterDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // TAB 3: FACEBOOK-STYLE PROFILE & USER POSTS
            if (selectedTabIndex == 2) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Header Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (myProfile.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(ImageHelper.getCoilData(myProfile.avatarUrl))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = myProfile.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                                    } else {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(60.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = myProfile.displayName.ifBlank { "فني البراء" },
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (myProfile.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = "موثق معتمد",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = myProfile.profession.ifBlank { "فني صيانة غسالات" },
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        )

                                        if (myProfile.city.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = myProfile.city,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            if (isGuest) onAuthClick() else showProfileDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تعديل", fontSize = 12.sp)
                                    }
                                }

                                if (myProfile.bio.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = myProfile.bio,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(10.dp))

                                // Fast stats
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "$myPostsCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(text = "إجمالي إعلاناتي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val activeCount = posts.count { (it.userId == currentUserId || it.userEmail == currentUserEmail) && !it.isCompleted }
                                        Text(text = "$activeCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                                        Text(text = "معروضة ونشطة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val doneCount = posts.count { (it.userId == currentUserId || it.userEmail == currentUserEmail) && it.isCompleted }
                                        Text(text = "$doneCount", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF6366F1))
                                        Text(text = "تم البيع / الشراء", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Guest Login Prompt
                    if (isGuest || currentUserEmail.isNullOrBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "أنت تتصفح كزائر حالياً",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "سجل دخولك بحساب Google أو بريدك الإلكتروني ليتم ربط كافة إعلاناتك بملفك الشخصي واسترجاعها تلقائياً على أي جهاز آخر.",
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                    Button(
                                        onClick = onAuthClick,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("تسجيل الدخول الآن")
                                    }
                                }
                            }
                        }
                    }

                    // User's Own Posts Header
                    item {
                        Text(
                            text = "منشوراتي وإعلاناتي السابقة:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (filteredPosts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لم تنشر أي إعلانات بعد.\nانقر على زر (+) في تبويب البيع أو الشراء لنشر أول كارتة!",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredPosts, key = { it.id }) { post ->
                            MarketplacePostCard(
                                post = post,
                                canManage = true,
                                isAuthor = true,
                                onAuthorClick = {},
                                onWhatsAppClick = {
                                    openWhatsApp(context, post.userPhone, "السلام عليكم، بخصوص إعلاني في سوق كروت البراء: ${post.title}")
                                },
                                onCallClick = {
                                    openDialer(context, post.userPhone)
                                },
                                onToggleStatus = {
                                    val nextCompleted = !post.isCompleted
                                    MarketplaceRepository.togglePostCompleted(context, post.id, nextCompleted) { success ->
                                        if (success) {
                                            val msg = if (nextCompleted) "تم تحديد الإعلان كمنجز بنجاح!" else "تمت إعادة تنشيط الإعلان."
                                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                },
                                onEditClick = {
                                    postToEdit = post
                                    showCreatePostDialog = true
                                },
                                onImageClick = { zoomedImageUrl = it },
                                onDeleteClick = {
                                    postToDelete = post
                                }
                            )
                        }
                    }
                }
            } else {
                // TABS 0 & 1: SELL OR BUY POSTS LIST
                if (filteredPosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedGovernorateFilter != "الكل") "لا توجد نتائج مطابقة لبحثك في هذه المنطقة"
                                else if (selectedTabIndex == 0) "لا توجد كروت معروضة للبيع حالياً\nكن أول من يعرض كارتة!"
                                else "لا توجد طلبات شراء كروت حالياً\nاطلب الكارتة التي تبحث عنها!",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredPosts, key = { it.id }) { post ->
                            val isAuthor = post.userId == currentUserId ||
                                    (currentUserEmail != null && post.userEmail.equals(currentUserEmail, ignoreCase = true))
                            val canManage = isAuthor || isAdmin

                            MarketplacePostCard(
                                post = post,
                                canManage = canManage,
                                isAuthor = isAuthor,
                                onAuthorClick = {
                                    coroutineScope.launch {
                                        val profile = MarketplaceRepository.fetchAuthorProfile(context, post.userEmail.ifBlank { post.userId })
                                        profileToView = profile ?: UserProfile(
                                            userId = post.userId,
                                            email = post.userEmail,
                                            displayName = post.userName,
                                            phone = post.userPhone,
                                            city = post.userCity,
                                            profession = post.userProfession,
                                            avatarUrl = post.userAvatarUrl
                                        )
                                    }
                                },
                                onWhatsAppClick = {
                                    openWhatsApp(context, post.userPhone, "السلام عليكم، بخصوص إعلانك في سوق كروت البراء: ${post.title}")
                                },
                                onCallClick = {
                                    openDialer(context, post.userPhone)
                                },
                                onToggleStatus = {
                                    val nextCompleted = !post.isCompleted
                                    MarketplaceRepository.togglePostCompleted(context, post.id, nextCompleted) { success ->
                                        if (success) {
                                            val msg = if (nextCompleted) {
                                                "تم تحديد الإعلان كمنجز بنجاح! سيتم حذفه تلقائياً بعد 3 أيام."
                                            } else {
                                                "تم إعادة تنشيط الإعلان."
                                            }
                                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                },
                                onEditClick = if (canManage) {
                                    {
                                        postToEdit = post
                                        showCreatePostDialog = true
                                    }
                                } else null,
                                onImageClick = { zoomedImageUrl = it },
                                onDeleteClick = {
                                    postToDelete = post
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create or Edit Post
    if (showCreatePostDialog) {
        CreateOrEditPostDialog(
            initialPost = postToEdit,
            defaultPostType = if (selectedTabIndex == 0) "SELL" else "BUY",
            myProfile = myProfile,
            onDismiss = { showCreatePostDialog = false },
            onSave = { newPost ->
                MarketplaceRepository.savePost(context, newPost) { saved ->
                    if (saved) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تم نشر الإعلان في سوق الكروت وحفظه بحسابك بنجاح")
                        }
                        showCreatePostDialog = false
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("حدث خطأ أثناء حفظ الإعلان")
                        }
                    }
                }
            }
        )
    }

    // Dialog: Edit My Profile with 3-Stage Location
    if (showProfileDialog) {
        EditUserProfileDialog(
            currentProfile = myProfile,
            currentEmail = currentUserEmail,
            onDismiss = { showProfileDialog = false },
            onSave = { updated ->
                MarketplaceRepository.saveUserProfile(context, updated) { success ->
                    if (success) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تم حفظ بيانات ملفك الشخصي بنجاح")
                        }
                        showProfileDialog = false
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("تعذر حفظ الملف الشخصي")
                        }
                    }
                }
            }
        )
    }

    // Dialog: View Author Profile Details
    if (profileToView != null) {
        AuthorProfileDetailDialog(
            profile = profileToView!!,
            onDismiss = { profileToView = null },
            onWhatsAppClick = { phone ->
                openWhatsApp(context, phone, "السلام عليكم أخي الفني، تواصلت معك عبر تطبيق البراء إلكترونيك")
            },
            onCallClick = { phone ->
                openDialer(context, phone)
            }
        )
    }

    // Deletion Confirmation Dialog
    if (postToDelete != null) {
        ConfirmDeleteDialog(
            title = "حذف الإعلان من سوق الكروت",
            message = "هل أنت متأكد من رغبتك في حذف إعلان (${postToDelete!!.title}) بشكل نهائي؟",
            onConfirm = {
                val id = postToDelete!!.id
                MarketplaceRepository.deletePost(context, id) { success ->
                    if (success) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("تم حذف الإعلان بنجاح") }
                    }
                }
                postToDelete = null
            },
            onDismiss = { postToDelete = null }
        )
    }

    // Fullscreen Zoomable Image Dialog
    if (zoomedImageUrl != null) {
        FullscreenImageDialog(
            imageUrl = zoomedImageUrl!!,
            onDismiss = { zoomedImageUrl = null }
        )
    }

    // Technicians Directory Dialog for Admins
    if (showTechniciansDialog) {
        TechniciansDirectoryDialog(
            isAdmin = isAdmin,
            onDismiss = { showTechniciansDialog = false }
        )
    }
}

/**
 * Single Marketplace Post Card with robust, non-wrapping action buttons
 */
@Composable
fun MarketplacePostCard(
    post: MarketplacePost,
    canManage: Boolean,
    isAuthor: Boolean = false,
    onAuthorClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isCompleted = post.isCompleted
    val isBuyRequest = post.postType.equals("BUY", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Completed banner
            if (isCompleted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBuyRequest) "تم الشراء وتوفير الكارتة بنجاح ✓" else "تم البيع بنجاح ✓",
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Top row: Author Info + Date & Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAuthorClick() }
                        .padding(2.dp)
                ) {
                    if (post.userAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ImageHelper.getCoilData(post.userAvatarUrl))
                                .crossfade(true)
                                .build(),
                            contentDescription = post.userName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = post.userName.take(1).ifBlank { "ف" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.userName.ifBlank { "فني معتمد" },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                            if (post.isUserVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "معتمد",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (post.userProfession.isNotBlank()) {
                                Text(
                                    text = post.userProfession,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                )
                                Text(" • ", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (post.userCity.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = post.userCity,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Type Badge & Date
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBuyRequest) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)
                    ) {
                        Text(
                            text = if (isBuyRequest) "طلب شراء 🔍" else "بيع 🏷️",
                            color = if (isBuyRequest) Color(0xFFB45309) else Color(0xFF0369A1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatPostDate(post.createdTimestamp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )

            // Description
            if (post.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = post.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Image Preview (compact height, only shown if mediaUrl is non-empty)
            if (post.mediaUrl.isNotBlank() && post.mediaUrl != "null" && post.mediaUrl != "DEFAULT") {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick?.invoke(post.mediaUrl) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageHelper.getCoilData(post.mediaUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = "صورة الكارتة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "تكبير 🔍",
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Price / Buy request status
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBuyRequest) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "السعر: للتفاوض مع البائع 💬",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "السعر: ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = if (post.price.isNotBlank() && post.price != "0") "${post.price} ج.م" else "قابل للتفاوض",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                Text(
                    text = "هاتف: ${post.userPhone}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Dedicated Contact Actions (Two wide buttons - Sleek and compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WhatsApp Button
                Button(
                    onClick = onWhatsAppClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "واتساب",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // Call Button
                OutlinedButton(
                    onClick = onCallClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "اتصال",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // Owner / Admin Actions Row (Status toggle, edit & delete)
            if (canManage) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAuthor) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCompleted) Color(0xFF10B981).copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleStatus() }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = if (isCompleted) "إعادة تنشيط 🔄" else if (isBuyRequest) "تم الشراء ✓" else "تم البيع ✓",
                                color = if (isCompleted) Color(0xFF047857) else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onEditClick != null) {
                            TextButton(
                                onClick = onEditClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("تعديل ✏️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        TextButton(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("حذف", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to create or edit a post with:
 * 1. Post type toggle (Sell vs Buy)
 * 2. If Buy: NO price field!
 * 3. 3-stage location selector: الدولة -> المحافظة -> الحي/المركز
 */
@Composable
fun CreateOrEditPostDialog(
    initialPost: MarketplacePost?,
    defaultPostType: String,
    myProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (MarketplacePost) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPost?.title ?: "") }
    var description by remember { mutableStateOf(initialPost?.description ?: "") }
    var price by remember { mutableStateOf(initialPost?.price ?: "") }
    var postType by remember { mutableStateOf(initialPost?.postType ?: defaultPostType) }
    var mediaUrl by remember { mutableStateOf(initialPost?.mediaUrl ?: "") }
    var phone by remember { mutableStateOf(initialPost?.userPhone?.ifBlank { myProfile.phone } ?: myProfile.phone) }

    // 3-Stage Location
    var selectedCountry by remember { mutableStateOf("مصر") }
    var selectedGovernorate by remember { mutableStateOf(myProfile.city.ifBlank { "القاهرة" }) }
    var selectedDistrict by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val permanentUrl = ImageHelper.saveAndPreparePermanentImage(context, uri)
                    mediaUrl = permanentUrl
                } catch (e: Exception) {
                    mediaUrl = uri.toString()
                }
            }
        }
    }

    var validationError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPost == null) "نشر إعلان جديد في سوق الكروت" else "تعديل الإعلان",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Post Type Selection
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (postType == "SELL") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { postType = "SELL" }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = "🏷️ عرض كارتة للبيع",
                                textAlign = TextAlign.Center,
                                fontWeight = if (postType == "SELL") FontWeight.Bold else FontWeight.Normal,
                                color = if (postType == "SELL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (postType == "BUY") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { postType = "BUY" }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = "🔍 طلب كارتة للشراء",
                                textAlign = TextAlign.Center,
                                fontWeight = if (postType == "BUY") FontWeight.Bold else FontWeight.Normal,
                                color = if (postType == "BUY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Title
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان الإعلان (مثل: كارتة غسالة إل جي إنفرتر 8ك)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Description
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("تفاصيل الكارتة (الموديل، الفحص، العيب إن وجد)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Price (ONLY FOR SELL POSTS - HIDE COMPLETELY FOR BUY REQUESTS)
                if (postType == "SELL") {
                    item {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("السعر بالجنيه (أو اتركه فارغاً للتفاوض)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Contact Phone / WhatsApp
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف والواتساب للتواصل المباشر *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Three-Stage Location Selector
                item {
                    HierarchicalLocationSelector(
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        selectedGovernorate = selectedGovernorate,
                        onGovernorateChange = { selectedGovernorate = it },
                        selectedDistrict = selectedDistrict,
                        onDistrictChange = { selectedDistrict = it }
                    )
                }

                // Media upload
                item {
                    Column {
                        Text(
                            text = "صورة الكارتة أو الموديل (اختياري)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (mediaUrl.isBlank()) "اختيار صورة" else "تغيير الصورة")
                            }

                            if (mediaUrl.isNotBlank()) {
                                IconButton(onClick = { mediaUrl = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف الصورة", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (mediaUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ImageHelper.getCoilData(mediaUrl))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "معاينة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                if (validationError.isNotBlank()) {
                    item {
                        Text(
                            text = validationError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        validationError = "يرجى كتابة عنوان للإعلان"
                        return@Button
                    }
                    if (phone.isBlank()) {
                        validationError = "رقم الهاتف إلزامي ليتمكن الطرف الآخر من التواصل معك"
                        return@Button
                    }

                    val fullLocation = listOfNotNull(
                        selectedDistrict.takeIf { it.isNotBlank() },
                        selectedGovernorate.takeIf { it.isNotBlank() },
                        selectedCountry.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    val post = MarketplacePost(
                        id = initialPost?.id?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString(),
                        userId = myProfile.userId,
                        userEmail = myProfile.email,
                        userName = myProfile.displayName.ifBlank { "فني البراء" },
                        userPhone = phone.trim(),
                        userAvatarUrl = myProfile.avatarUrl,
                        userCity = fullLocation.ifBlank { myProfile.city },
                        userProfession = myProfile.profession,
                        isUserVerified = initialPost?.isUserVerified ?: myProfile.isVerified,
                        title = title.trim(),
                        description = description.trim(),
                        price = if (postType == "BUY") "" else price.trim(),
                        postType = postType,
                        mediaUrl = mediaUrl.trim(),
                        mediaType = if (mediaUrl.isNotBlank()) "IMAGE" else "NONE",
                        isCompleted = initialPost?.isCompleted ?: false,
                        createdTimestamp = initialPost?.createdTimestamp ?: System.currentTimeMillis()
                    )
                    onSave(post)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("نشر الإعلان الآن")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * Edit User Profile with 3-Stage Location
 */
@Composable
fun EditUserProfileDialog(
    currentProfile: UserProfile,
    currentEmail: String?,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(currentProfile.displayName) }
    var phone by remember { mutableStateOf(currentProfile.phone) }
    var profession by remember { mutableStateOf(currentProfile.profession) }
    var bio by remember { mutableStateOf(currentProfile.bio) }
    var avatarUrl by remember { mutableStateOf(currentProfile.avatarUrl) }

    var selectedCountry by remember { mutableStateOf("مصر") }
    var selectedGovernorate by remember { mutableStateOf(currentProfile.city.split("•").getOrNull(1)?.trim() ?: "القاهرة") }
    var selectedDistrict by remember { mutableStateOf(currentProfile.city.split("•").getOrNull(0)?.trim() ?: "") }

    val coroutineScope = rememberCoroutineScope()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val permanentUrl = ImageHelper.saveAndPreparePermanentImage(context, uri)
                    avatarUrl = permanentUrl
                } catch (e: Exception) {
                    avatarUrl = uri.toString()
                }
            }
        }
    }

    val professions = listOf("فني صيانة كروت", "مهندس صيانة أجهزة", "تاجر قطع غيار وكروت", "هاوي صيانة وتصليح")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ملفي الشخصي في سوق الكروت", fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Avatar row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(ImageHelper.getCoilData(avatarUrl))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "الصورة الشخصية",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تغيير الصورة الشخصية", fontSize = 12.sp)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("الاسم الكامل / اسم المركز") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف / الواتساب للتواصل *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 3-Stage Location Selector
                item {
                    HierarchicalLocationSelector(
                        selectedCountry = selectedCountry,
                        onCountryChange = { selectedCountry = it },
                        selectedGovernorate = selectedGovernorate,
                        onGovernorateChange = { selectedGovernorate = it },
                        selectedDistrict = selectedDistrict,
                        onDistrictChange = { selectedDistrict = it }
                    )
                }

                item {
                    Column {
                        Text(
                            text = "المهنة / التخصص:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            professions.take(2).forEach { prof ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (profession == prof) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { profession = prof }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = prof,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = if (profession == prof) FontWeight.Bold else FontWeight.Normal,
                                        color = if (profession == prof) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            professions.drop(2).forEach { prof ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (profession == prof) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { profession = prof }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = prof,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = if (profession == prof) FontWeight.Bold else FontWeight.Normal,
                                        color = if (profession == prof) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("نبذة مختصرة عنك أو عن المركز") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullLocation = listOfNotNull(
                        selectedDistrict.takeIf { it.isNotBlank() },
                        selectedGovernorate.takeIf { it.isNotBlank() },
                        selectedCountry.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    val updated = currentProfile.copy(
                        displayName = displayName.trim(),
                        email = currentEmail ?: currentProfile.email,
                        phone = phone.trim(),
                        city = fullLocation.ifBlank { currentProfile.city },
                        profession = profession.trim(),
                        bio = bio.trim(),
                        avatarUrl = avatarUrl.trim()
                    )
                    onSave(updated)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AuthorProfileDetailDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onCallClick: (String) -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageHelper.getCoilData(profile.avatarUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.displayName.take(1).ifBlank { "ف" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.displayName.ifBlank { "فني صيانة" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (profile.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "موثوق",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (profile.profession.isNotBlank()) {
                        Text(
                            text = profile.profession,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (profile.city.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "المدينة: ${profile.city}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (profile.phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الهاتف: ${profile.phone}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Direct Contact buttons
                if (profile.phone.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onWhatsAppClick(profile.phone) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("واتساب", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onCallClick(profile.phone) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اتصال", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

// Helpers
private fun formatPostDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale("ar"))
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

private fun openWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "")
        val formattedPhone = if (cleanPhone.startsWith("0")) "20" + cleanPhone.substring(1) else cleanPhone
        val encodedMsg = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$formattedPhone?text=$encodedMsg")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        openDialer(context, phone)
    }
}

private fun openDialer(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (e: Exception) {
        // ignore
    }
}

@Composable
fun FullscreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("معاينة صورة الكارتة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("نسبة التكبير: ${(scale * 100).toInt()}%", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF334155), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Image Container with interactive zoom, double-tap, and pan
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ImageHelper.getCoilData(imageUrl))
                            .crossfade(true)
                            .build(),
                        contentDescription = "صورة الكارتة المكبرة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Zoom Controls Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Reset Button
                        TextButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            enabled = scale > 1f || offsetX != 0f || offsetY != 0f
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = if (scale > 1f) Color(0xFF38BDF8) else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة الضبط", fontSize = 11.sp, color = if (scale > 1f) Color(0xFF38BDF8) else Color.Gray)
                        }

                        // Hint
                        Text(
                            text = "💡 اسحب بإصبعين للتكبير أو انقر نقرتين",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )

                        // Plus / Minus Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scale = (scale - 0.5f).coerceAtLeast(1f)
                                    if (scale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                },
                                enabled = scale > 1f,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (scale > 1f) Color(0xFF334155) else Color(0xFF1E293B), CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "تصغير", tint = if (scale > 1f) Color.White else Color.Gray, modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = {
                                    scale = (scale + 0.5f).coerceAtMost(6f)
                                },
                                enabled = scale < 6f,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF0284C7), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TechniciansDirectoryDialog(
    isAdmin: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var technicians by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun reload() {
        coroutineScope.launch {
            technicians = MarketplaceRepository.fetchAllProfiles(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val filtered = technicians.filter {
        searchQuery.isBlank() ||
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.city.contains(searchQuery, ignoreCase = true) ||
        it.phone.contains(searchQuery, ignoreCase = true) ||
        it.email.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Engineering, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("دليل الفنيين والمهندسين المسجلين 👥", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isAdmin) "قائمة الفنيين المسجلين (يمكنك كمسؤول توثيق أي فني أو إلغاء توثيقه):" else "قائمة حسابات الفنيين المسجلين في التطبيق (${technicians.size} فني):",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم الفني، المدينة، الهاتف...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("جاري تحميل قائمة الفنيين...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                } else if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد فنيون مسجلون مطابقون للبحث", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.userId.ifBlank { it.email } }) { tech ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (tech.isVerified) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (tech.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(ImageHelper.getCoilData(tech.avatarUrl))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = tech.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                            )
                                        } else {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(tech.displayName.take(1).ifBlank { "ف" }, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(tech.displayName.ifBlank { "فني" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (tech.isVerified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Default.Verified,
                                                        contentDescription = "موثق معتمد",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("معتمد", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (tech.city.isNotBlank()) {
                                                Text("📍 ${tech.city}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                            }
                                            if (tech.phone.isNotBlank()) {
                                                Text("📞 ${tech.phone}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            if (tech.email.isNotBlank()) {
                                                Text("✉️ ${tech.email}", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }

                                        if (tech.phone.isNotBlank()) {
                                            IconButton(
                                                onClick = { openDialer(context, tech.phone) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Call, contentDescription = "اتصال", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    // Admin Verification Toggle Section
                                    if (isAdmin) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (tech.isVerified) "حالة التوثيق: موثق ومعتمد ✓" else "حالة التوثيق: غير موثق",
                                                fontSize = 11.sp,
                                                color = if (tech.isVerified) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                                                fontWeight = FontWeight.Medium
                                            )

                                            if (tech.isVerified) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        MarketplaceRepository.setTechnicianVerified(context, tech.email, false) { success ->
                                                            if (success) {
                                                                technicians = technicians.map {
                                                                    if (it.email.equals(tech.email, ignoreCase = true)) it.copy(isVerified = false) else it
                                                                }
                                                                android.widget.Toast.makeText(context, "تم إلغاء توثيق الفني ${tech.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                                                        contentColor = Color(0xFFEF4444)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("إلغاء التوثيق", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        MarketplaceRepository.setTechnicianVerified(context, tech.email, true) { success ->
                                                            if (success) {
                                                                technicians = technicians.map {
                                                                    if (it.email.equals(tech.email, ignoreCase = true)) it.copy(isVerified = true) else it
                                                                }
                                                                android.widget.Toast.makeText(context, "تم توثيق الفني ${tech.displayName} بنجاح ✓", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF10B981),
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("توثيق الفني", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

