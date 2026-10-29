package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Harness
import com.example.data.model.HarnessPin
import com.example.data.model.MotorPinPresets
import com.example.ui.components.APPLIANCE_COMPONENTS
import com.example.ui.components.STANDARD_WIRE_COLORS
import com.example.ui.components.VisualBoardPinMarker
import com.example.ui.components.WireColorBadge
import com.example.util.ImageHelper
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHarnessScreen(
    initialHarness: Harness?,
    onSaveSuccess: (Harness) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(initialHarness?.name ?: "") }
    var brand by remember { mutableStateOf(initialHarness?.brand ?: "") }
    var model by remember { mutableStateOf(initialHarness?.model ?: "") }
    var notes by remember { mutableStateOf(initialHarness?.notes ?: "") }
    var boardImageUrl by remember { mutableStateOf(initialHarness?.boardImageUrl ?: "") }

    // Board Pins state
    val boardPins = remember {
        mutableStateListOf<HarnessPin>().apply {
            val loaded = initialHarness?.boardPins ?: emptyList()
            val distinctSocketNames = loaded.map { it.socketName.trim() }.filter { it.isNotBlank() }.distinct()
            if (loaded.size > 7 && distinctSocketNames.size <= 1) {
                val compGroups = loaded.groupBy { it.connectedComponent.ifBlank { "ماتور" } }
                var sIdx = 1
                compGroups.forEach { (comp, groupPins) ->
                    val sName = "سوكيت $sIdx ($comp)"
                    sIdx++
                    groupPins.forEach { p ->
                        add(p.copy(socketName = sName))
                    }
                }
            } else {
                loaded.forEach { p ->
                    add(if (p.socketName.isBlank()) p.copy(socketName = "سوكيت 1") else p)
                }
            }
        }
    }

    // Bottom Pins state
    val bottomPins = remember {
        mutableStateListOf<HarnessPin>().apply {
            val loaded = initialHarness?.bottomPins ?: emptyList()
            loaded.forEach { p ->
                val comp = p.connectedComponent.ifBlank { "ماتور" }
                val sName = p.socketName.ifBlank { "سوكيت $comp" }
                add(p.copy(connectedComponent = comp, socketName = sName))
            }
        }
    }

    // Track active selection & expanded state for Sockets
    var selectedSocketName by remember { mutableStateOf<String?>(null) }
    val expandedBoardSockets = remember { mutableStateMapOf<String, Boolean>() }
    val expandedBottomSockets = remember { mutableStateMapOf<String, Boolean>() }

    // Dialog state for adding new sockets
    var showAddBoardSocketDialog by remember { mutableStateOf(false) }
    var showAddBottomSocketDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val localPath = ImageHelper.copyUriToLocalStorage(context, uri)
                    boardImageUrl = localPath
                    snackbarHostState.showSnackbar("تم حفظ صورة الكارتة بنجاح")
                } catch (e: Exception) {
                    boardImageUrl = uri.toString()
                    snackbarHostState.showSnackbar("تم تحديد الصورة")
                }
            }
        }
    }

    // Group Board Pins into distinct Sockets
    val boardSocketNames = boardPins.map { it.socketName.ifBlank { "سوكيت 1" }.trim() }.distinct()
    // Group Bottom Pins into distinct Sockets
    val bottomSocketNames = bottomPins.map { it.socketName.ifBlank { it.connectedComponent.ifBlank { "سوكيت سفلي 1" } }.trim() }.distinct()

    Scaffold(
        modifier = modifier.testTag("admin_harness_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (initialHarness == null) "إضافة خريطة ضفيرة جديدة" else "تعديل خريطة الضفيرة", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: General Details Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("بيانات الضفيرة الأساسية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("اسم الضفيرة (مثال: ضفيرة غسالة إل جي انفرتر)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("الماركة") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("الموديل") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات فنية / طريقة الفحص") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Section 2: Visual Board Pin Marker Canvas
            item {
                VisualBoardPinMarker(
                    boardImageUrl = boardImageUrl,
                    pins = boardPins,
                    bottomPins = bottomPins,
                    onUpdateBoardImageUrl = { boardImageUrl = it },
                    onPickImageClick = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAddSocket = { socketName, pinCount ->
                        val existingMaxPin = boardPins.maxOfOrNull { it.pinNumber } ?: 0
                        val sName = socketName.trim().ifBlank { "سوكيت ${boardSocketNames.size + 1}" }
                        val distinctCount = boardSocketNames.size
                        val startY = (20f + (distinctCount * 22f)).coerceIn(15f, 75f)
                        val startX = 20f
                        val stepX = (50f / pinCount.coerceAtLeast(1)).coerceIn(4f, 8f)
                        for (i in 1..pinCount) {
                            val nextNum = existingMaxPin + i
                            boardPins.add(
                                HarnessPin(
                                    pinNumber = nextNum,
                                    label = "طرف $i",
                                    wireColor = "أزرق",
                                    colorHex = "#2563EB",
                                    secondaryColorHex = null,
                                    secondaryColorName = null,
                                    connectedComponent = "",
                                    targetPinNumber = null,
                                    socketName = sName,
                                    pinX = (startX + (i - 1) * stepX).coerceIn(4f, 96f),
                                    pinY = startY
                                )
                            )
                        }
                        selectedSocketName = sName
                        expandedBoardSockets[sName] = true
                    },
                    onMoveSocket = { socketName, deltaX, deltaY ->
                        val target = socketName.trim()
                        boardPins.indices.forEach { i ->
                            val p = boardPins[i]
                            if (p.socketName.ifBlank { "سوكيت 1" }.trim() == target) {
                                boardPins[i] = p.copy(
                                    socketName = socketName,
                                    pinX = (p.pinX + deltaX).coerceIn(2f, 98f),
                                    pinY = (p.pinY + deltaY).coerceIn(2f, 98f)
                                )
                            }
                        }
                    },
                    onResizeSocket = { socketName, scaleFactor ->
                        val target = socketName.trim()
                        val targetPins = boardPins.filter { it.socketName.ifBlank { "سوكيت 1" }.trim() == target }
                        if (targetPins.isNotEmpty()) {
                            val centerX = targetPins.map { it.pinX }.average().toFloat()
                            val centerY = targetPins.map { it.pinY }.average().toFloat()
                            boardPins.indices.forEach { i ->
                                val p = boardPins[i]
                                if (p.socketName.ifBlank { "سوكيت 1" }.trim() == target) {
                                    val newX = (centerX + (p.pinX - centerX) * scaleFactor).coerceIn(2f, 98f)
                                    val newY = (centerY + (p.pinY - centerY) * scaleFactor).coerceIn(2f, 98f)
                                    boardPins[i] = p.copy(pinX = newX, pinY = newY)
                                }
                            }
                        }
                    },
                    onDeleteSocket = { socketName ->
                        val target = socketName.trim()
                        boardPins.removeAll { it.socketName.ifBlank { "سوكيت 1" }.trim() == target }
                        if (selectedSocketName == socketName) {
                            selectedSocketName = null
                        }
                    },
                    selectedSocketName = selectedSocketName,
                    onSelectSocket = { selectedSocketName = it }
                )
            }

            // =========================================================================
            // SECTION 3: BOARD SOCKETS SLICES (شرائح سوكيتات الكارتة العلوية)
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "1. سوكيتات الكارتة العلوية (${boardSocketNames.size} سوكيت)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "تحت كل سوكيت أطرافه المستقلة، يمكنك طي أو إظهار أطراف أي سوكيت",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }

                    Button(
                        onClick = { showAddBoardSocketDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة سوكيت للكارتة +", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Render Each Board Socket as an Expandable Slice / Card
            itemsIndexed(boardSocketNames) { _, socketName ->
                val socketPins = boardPins.filter { it.socketName.ifBlank { "سوكيت 1" }.trim() == socketName }
                val isExpanded = expandedBoardSockets[socketName] ?: true

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Socket Slice Header (Always Visible)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedBoardSockets[socketName] = !isExpanded
                                        selectedSocketName = socketName
                                    }
                            ) {
                                Surface(shape = CircleShape, color = Color(0xFF0284C7), modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Power, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(socketName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF0284C7).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${socketPins.size} أطراف",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF0284C7), fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isExpanded) "انقر للطي ▲" else "انقر لإظهار الأطراف ▼",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Expand / Collapse Toggle Button
                                IconButton(onClick = { expandedBoardSockets[socketName] = !isExpanded }) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "إظهار/طي الأطراف"
                                    )
                                }

                                // Delete Whole Socket Button
                                IconButton(
                                    onClick = {
                                        boardPins.removeAll { it.socketName.ifBlank { "سوكيت 1" }.trim() == socketName }
                                        if (selectedSocketName == socketName) selectedSocketName = null
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف السوكيت", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Editable Socket Name
                        OutlinedTextField(
                            value = socketName,
                            onValueChange = { newName ->
                                val trimmed = newName.trim()
                                if (trimmed.isNotBlank()) {
                                    boardPins.indices.forEach { i ->
                                        if (boardPins[i].socketName.ifBlank { "سوكيت 1" }.trim() == socketName) {
                                            boardPins[i] = boardPins[i].copy(socketName = newName)
                                        }
                                    }
                                    expandedBoardSockets[newName] = isExpanded
                                    expandedBoardSockets.remove(socketName)
                                    if (selectedSocketName == socketName) selectedSocketName = newName
                                }
                            },
                            label = { Text("اسم شريحة السوكيت (قابل للتعديل)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Expandable Body: Pins inside this Socket
                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                socketPins.forEach { pin ->
                                    val realIndex = boardPins.indexOf(pin)
                                    if (realIndex >= 0) {
                                        BoardPinItemCard(
                                            pin = pin,
                                            bottomPins = bottomPins,
                                            onUpdate = { updated -> boardPins[realIndex] = updated },
                                            onDelete = { boardPins.removeAt(realIndex) }
                                        )
                                    }
                                }

                                // Add Pin to this specific socket
                                Button(
                                    onClick = {
                                        val nextNum = (boardPins.maxOfOrNull { it.pinNumber } ?: 0) + 1
                                        val avgY = if (socketPins.isNotEmpty()) socketPins.map { it.pinY }.average().toFloat() else 50f
                                        val maxX = if (socketPins.isNotEmpty()) socketPins.maxOf { it.pinX } + 6f else 50f
                                        boardPins.add(
                                            HarnessPin(
                                                pinNumber = nextNum,
                                                label = "طرف ${socketPins.size + 1}",
                                                wireColor = "أزرق",
                                                colorHex = "#2563EB",
                                                connectedComponent = "",
                                                targetPinNumber = null,
                                                socketName = socketName,
                                                pinX = maxX.coerceIn(4f, 96f),
                                                pinY = avgY
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ إضافة طرف جديد لهذا السوكيت ($socketName)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // SECTION 4: BOTTOM APPLIANCE SOCKETS SLICES (شرائح المكونات السفلية المقابلة)
            // =========================================================================
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "2. سوكيتات وأجزاء المكونات السفلية (${bottomSocketNames.size} أجزاء)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "الماتور، اللوك، الليفل، الطلمبة، السخان، الشداد - ربط كل طرف بما يقابله بالكارتة",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                        )
                    }

                    Button(
                        onClick = { showAddBottomSocketDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة قطعة سفلية +", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Render Each Bottom Socket as an Expandable Slice / Card
            itemsIndexed(bottomSocketNames) { _, bottomSockName ->
                val socketPins = bottomPins.filter {
                    it.socketName.ifBlank { it.connectedComponent.ifBlank { "سوكيت سفلي 1" } }.trim() == bottomSockName
                }
                val isExpanded = expandedBottomSockets[bottomSockName] ?: true

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { expandedBottomSockets[bottomSockName] = !isExpanded }
                            ) {
                                Surface(shape = CircleShape, color = Color(0xFF059669), modifier = Modifier.size(32.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(bottomSockName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF059669).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "${socketPins.size} أطراف",
                                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF059669), fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isExpanded) "انقر للطي ▲" else "انقر لإظهار الأطراف ▼",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { expandedBottomSockets[bottomSockName] = !isExpanded }) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "إظهار/طي"
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        bottomPins.removeAll {
                                            it.socketName.ifBlank { it.connectedComponent.ifBlank { "سوكيت سفلي 1" } }.trim() == bottomSockName
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Editable Name
                        OutlinedTextField(
                            value = bottomSockName,
                            onValueChange = { newName ->
                                val trimmed = newName.trim()
                                if (trimmed.isNotBlank()) {
                                    bottomPins.indices.forEach { i ->
                                        val cur = bottomPins[i].socketName.ifBlank { bottomPins[i].connectedComponent.ifBlank { "سوكيت سفلي 1" } }.trim()
                                        if (cur == bottomSockName) {
                                            bottomPins[i] = bottomPins[i].copy(socketName = newName, connectedComponent = newName)
                                        }
                                    }
                                    expandedBottomSockets[newName] = isExpanded
                                    expandedBottomSockets.remove(bottomSockName)
                                }
                            },
                            label = { Text("اسم الجزء / السوكيت السفلي (قابل للتعديل)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        // Expandable Body: Pins inside this Bottom Socket
                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                socketPins.forEach { pin ->
                                    val realIndex = bottomPins.indexOf(pin)
                                    if (realIndex >= 0) {
                                        BottomPinItemCard(
                                            pin = pin,
                                            boardPins = boardPins,
                                            onUpdate = { updated -> bottomPins[realIndex] = updated },
                                            onDelete = { bottomPins.removeAt(realIndex) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val nextNum = (bottomPins.maxOfOrNull { it.pinNumber } ?: 0) + 1
                                        bottomPins.add(
                                            HarnessPin(
                                                pinNumber = nextNum,
                                                label = "طرف ${socketPins.size + 1}",
                                                wireColor = "أزرق",
                                                colorHex = "#2563EB",
                                                connectedComponent = bottomSockName,
                                                targetPinNumber = null,
                                                socketName = bottomSockName
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ إضافة طرف جديد لهذا الجزء ($bottomSockName)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("برجاء كتابة اسم الضفيرة") }
                            return@Button
                        }
                        val finalHarness = Harness(
                            id = initialHarness?.id?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            notes = notes.trim(),
                            boardImageUrl = boardImageUrl.trim(),
                            bottomImageUrl = initialHarness?.bottomImageUrl ?: "",
                            boardPins = boardPins.toList(),
                            bottomPins = bottomPins.toList(),
                            orderIndex = initialHarness?.orderIndex ?: 0
                        )
                        onSaveSuccess(finalHarness)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ خريطة الضفيرة والسوكيتات بالكامل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Add Board Socket Dialog
    if (showAddBoardSocketDialog) {
        var sName by remember { mutableStateOf("سوكيت ${boardSocketNames.size + 1}") }
        var pinCount by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showAddBoardSocketDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Power, null, tint = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة سوكيت جديد للكارتة", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("حدد اسم السوكيت وعدد أطرافه. سينضاف السوكيت كشريحة مستقلة قابلة للطي والتعديل:")
                    OutlinedTextField(
                        value = sName,
                        onValueChange = { sName = it },
                        label = { Text("اسم السوكيت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinCount,
                        onValueChange = { pinCount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("عدد أطراف السوكيت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = pinCount.toIntOrNull()?.coerceIn(1, 24) ?: 5
                        val finalName = sName.trim().ifBlank { "سوكيت ${boardSocketNames.size + 1}" }
                        val existingMaxPin = boardPins.maxOfOrNull { it.pinNumber } ?: 0
                        val distinctCount = boardSocketNames.size
                        val startY = (20f + (distinctCount * 22f)).coerceIn(15f, 75f)
                        val startX = 20f
                        val stepX = (50f / count.coerceAtLeast(1)).coerceIn(4f, 8f)
                        for (i in 1..count) {
                            val nextNum = existingMaxPin + i
                            boardPins.add(
                                HarnessPin(
                                    pinNumber = nextNum,
                                    label = "طرف $i",
                                    wireColor = "أزرق",
                                    colorHex = "#2563EB",
                                    connectedComponent = "",
                                    targetPinNumber = null,
                                    socketName = finalName,
                                    pinX = (startX + (i - 1) * stepX).coerceIn(4f, 96f),
                                    pinY = startY
                                )
                            )
                        }
                        selectedSocketName = finalName
                        expandedBoardSockets[finalName] = true
                        showAddBoardSocketDialog = false
                    }
                ) {
                    Text("تأكيد الإضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBoardSocketDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // Add Bottom Socket Dialog
    if (showAddBottomSocketDialog) {
        var sName by remember { mutableStateOf("ماتور") }
        var pinCount by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = { showAddBottomSocketDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Color(0xFF059669))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة سوكيت / جزء سفلي جديد", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اختر أو اكتب اسم القطعة السفلية (ماتور، لوك، طلمبة...) وعدد أطرافها:")
                    OutlinedTextField(
                        value = sName,
                        onValueChange = { sName = it },
                        label = { Text("اسم الجزء السفلي (مثال: ماتور، لوك الباب، طلمبة)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinCount,
                        onValueChange = { pinCount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("عدد أطراف هذه القطعة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = pinCount.toIntOrNull()?.coerceIn(1, 40) ?: 3
                        val finalName = sName.trim().ifBlank { "سوكيت سفلي ${bottomSocketNames.size + 1}" }
                        val existingMaxPin = bottomPins.maxOfOrNull { it.pinNumber } ?: 0
                        for (i in 1..count) {
                            val nextNum = existingMaxPin + i
                            bottomPins.add(
                                HarnessPin(
                                    pinNumber = nextNum,
                                    label = "طرف $i",
                                    wireColor = "أزرق",
                                    colorHex = "#2563EB",
                                    connectedComponent = finalName,
                                    targetPinNumber = null,
                                    socketName = finalName
                                )
                            )
                        }
                        expandedBottomSockets[finalName] = true
                        showAddBottomSocketDialog = false
                    }
                ) {
                    Text("تأكيد الإضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBottomSocketDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

/**
 * Individual Pin Item Card inside a Board Socket Slice.
 */
@Composable
fun BoardPinItemCard(
    pin: HarnessPin,
    bottomPins: List<HarnessPin>,
    onUpdate: (HarnessPin) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 16.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طرف #${pin.pinNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(22.dp).clickable {
                            if (pin.pinNumber > 1) onUpdate(pin.copy(pinNumber = pin.pinNumber - 1))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(22.dp).clickable {
                            onUpdate(pin.copy(pinNumber = pin.pinNumber + 1))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    if (pin.isBlankPin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF334155)) {
                            Text("طرف فارغ (NC)", color = Color(0xFF94A3B8), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = pin.isBlankPin,
                        onCheckedChange = { isBlank ->
                            onUpdate(
                                pin.copy(
                                    isBlank = isBlank,
                                    wireColor = if (isBlank) "فارغ (بدون سلك)" else "أزرق",
                                    colorHex = if (isBlank) "#94A3B8" else "#2563EB",
                                    label = if (isBlank) "مكان فاضي بدون سلك (NC)" else pin.label
                                )
                            )
                        }
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف الطرف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Function / Label
            OutlinedTextField(
                value = pin.label,
                onValueChange = { onUpdate(pin.copy(label = it)) },
                label = { Text("وظيفة الطرف في الكارتة (مثال: تاكو / فازة U / تغذية)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Wire Color Dropdown
            var showColorDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showColorDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 14.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("لون السلك: ${pin.wireColor}")
                        }
                        Text("تغيير ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                DropdownMenu(expanded = showColorDropdown, onDismissRequest = { showColorDropdown = false }) {
                    STANDARD_WIRE_COLORS.forEach { (cName, hex) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WireColorBadge(colorHex = hex, size = 16.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cName)
                                }
                            },
                            onClick = {
                                onUpdate(
                                    pin.copy(
                                        wireColor = cName,
                                        colorHex = hex,
                                        secondaryColorHex = if (cName.contains("/")) "#16A34A" else null,
                                        secondaryColorName = if (cName.contains("/")) "أخضر" else null
                                    )
                                )
                                showColorDropdown = false
                            }
                        )
                    }
                }
            }

            // Opposite Connection Dropdown (متصل بالطرف السفلي)
            var showOppositeDropdown by remember { mutableStateOf(false) }
            val matchedBottomPin = bottomPins.find { it.pinNumber == pin.targetPinNumber }

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { showOppositeDropdown = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("متصل بالطرف المقابل في الجزء السفلي:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (matchedBottomPin != null) "${matchedBottomPin.socketName.ifBlank { matchedBottomPin.connectedComponent }} - طرف #${matchedBottomPin.pinNumber} (${matchedBottomPin.label})"
                                else "غير متصل (اضغط للاختيار)",
                                fontWeight = FontWeight.Bold,
                                color = if (matchedBottomPin != null) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                        Text("تعديل ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                DropdownMenu(expanded = showOppositeDropdown, onDismissRequest = { showOppositeDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("غير متصل (فارغ)") },
                        onClick = {
                            onUpdate(pin.copy(connectedComponent = "", targetPinNumber = null))
                            showOppositeDropdown = false
                        }
                    )
                    bottomPins.forEach { bPin ->
                        val isCurSelected = pin.targetPinNumber == bPin.pinNumber
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WireColorBadge(colorHex = bPin.colorHex, size = 12.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${bPin.socketName.ifBlank { bPin.connectedComponent }} - طرف #${bPin.pinNumber} (${bPin.label})",
                                        fontWeight = if (isCurSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurSelected) Color(0xFF0284C7) else Color.Unspecified
                                    )
                                }
                            },
                            onClick = {
                                onUpdate(pin.copy(
                                    connectedComponent = bPin.socketName.ifBlank { bPin.connectedComponent },
                                    targetPinNumber = bPin.pinNumber
                                ))
                                showOppositeDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Pin Item Card inside a Bottom Appliance Socket Slice.
 */
@Composable
fun BottomPinItemCard(
    pin: HarnessPin,
    boardPins: List<HarnessPin>,
    onUpdate: (HarnessPin) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 16.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طرف #${pin.pinNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(22.dp).clickable {
                            if (pin.pinNumber > 1) onUpdate(pin.copy(pinNumber = pin.pinNumber - 1))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(22.dp).clickable {
                            onUpdate(pin.copy(pinNumber = pin.pinNumber + 1))
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف الطرف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }

            OutlinedTextField(
                value = pin.label,
                onValueChange = { onUpdate(pin.copy(label = it)) },
                label = { Text("وظيفة الطرف بالقطعة (مثال: تاكو / شربون / L / N)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Wire color
            var showColorDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showColorDropdown = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WireColorBadge(colorHex = pin.colorHex, secondaryColorHex = pin.secondaryColorHex, size = 14.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("لون السلك: ${pin.wireColor}")
                        }
                        Text("تغيير ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                DropdownMenu(expanded = showColorDropdown, onDismissRequest = { showColorDropdown = false }) {
                    STANDARD_WIRE_COLORS.forEach { (cName, hex) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WireColorBadge(colorHex = hex, size = 16.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cName)
                                }
                            },
                            onClick = {
                                onUpdate(pin.copy(wireColor = cName, colorHex = hex))
                                showColorDropdown = false
                            }
                        )
                    }
                }
            }

            // Connection to Board Pin Dropdown
            var showBoardPinDropdown by remember { mutableStateOf(false) }
            val matchedBoardPin = boardPins.find { it.pinNumber == pin.targetPinNumber }

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable { showBoardPinDropdown = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("متصل بأي طرف علوي من الكارتة:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (matchedBoardPin != null) "${matchedBoardPin.socketName} - طرف #${matchedBoardPin.pinNumber} (${matchedBoardPin.label})"
                                else "غير متصل (اضغط للاختيار)",
                                fontWeight = FontWeight.Bold,
                                color = if (matchedBoardPin != null) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                        Text("تعديل ▾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                DropdownMenu(expanded = showBoardPinDropdown, onDismissRequest = { showBoardPinDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("غير متصل (فارغ)") },
                        onClick = {
                            onUpdate(pin.copy(targetPinNumber = null))
                            showBoardPinDropdown = false
                        }
                    )
                    boardPins.forEach { bPin ->
                        val isCurSelected = pin.targetPinNumber == bPin.pinNumber
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    WireColorBadge(colorHex = bPin.colorHex, size = 12.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${bPin.socketName} - طرف #${bPin.pinNumber} (${bPin.label})",
                                        fontWeight = if (isCurSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurSelected) Color(0xFF059669) else Color.Unspecified
                                    )
                                }
                            },
                            onClick = {
                                onUpdate(pin.copy(targetPinNumber = bPin.pinNumber))
                                showBoardPinDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}
