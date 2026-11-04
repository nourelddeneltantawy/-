package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.horizontalScroll
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ContactInfoManager
import com.example.data.model.ConnectionPinItem
import com.example.data.model.FirmwareDump
import com.example.data.model.FirmwareFolder
import com.example.data.model.LicenseKeyRecord
import com.example.data.model.ProgrammerConnection
import com.example.data.model.ProgrammerSoftware
import com.example.data.repository.ProgrammingRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Professional Programming & Firmware Screen (برمجة وسوفت وير).
 * 3 Core Sub-Sections:
 * 1. برامج البرمجة وملفات التثبيت (Programmer Tools & PC Software)
 * 2. سوفتات البروسيسورات والمجلدات الشجرية (Processor Dumps Desktop Explorer)
 * 3. توصيل المبرمجات بالكروت (Programmer Pinout Diagrams)
 *
 * Integrated with the Unique Client Activation Key Engine & Admin License Manager.
 */
@Composable
fun ProgrammingScreen(
    isAdmin: Boolean,
    currentUserEmail: String? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ProgrammingRepository.init(context)
    }

    val softwares by ProgrammingRepository.softwares.collectAsState()
    val folders by ProgrammingRepository.folders.collectAsState()
    val dumps by ProgrammingRepository.dumps.collectAsState()
    val connections by ProgrammingRepository.connections.collectAsState()
    val licenseKeys by ProgrammingRepository.licenseKeys.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        "برامج البرمجة 💻",
        "سوفتات البروسيسور 📁",
        "توصيل المبرمجات 🔌"
    )

    // Current Folder Hierarchy stack inside Dumps Explorer (Endless nested folders)
    var folderStack by remember { mutableStateOf<List<FirmwareFolder>>(emptyList()) }
    val activeFolder = folderStack.lastOrNull()
    val activeFolderId = activeFolder?.id
    var defaultParentFolderIdForNew by remember { mutableStateOf("") }

    // Dialog States
    var showActivationDialog by remember { mutableStateOf(false) }
    var activationTargetScope by remember { mutableStateOf("ALL_FIRMWARE") }
    var activationTargetName by remember { mutableStateOf("جميع السوفتات والبرامج") }

    var showAdminKeysManagerDialog by remember { mutableStateOf(false) }
    var showAddSoftwareDialog by remember { mutableStateOf(false) }
    var editingSoftware by remember { mutableStateOf<ProgrammerSoftware?>(null) }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FirmwareFolder?>(null) }

    var showAddDumpDialog by remember { mutableStateOf(false) }
    var editingDump by remember { mutableStateOf<FirmwareDump?>(null) }

    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<ProgrammerConnection?>(null) }

    // Real File Picker Launcher for firmware dumps
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                var fileName = "firmware_${System.currentTimeMillis()}.bin"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                } catch (_: Throwable) {}

                val (savedPath, size) = ProgrammingRepository.saveUploadedFile(context, uri, fileName)
                if (savedPath.isNotBlank()) {
                    val ext = fileName.substringAfterLast(".", "BIN").uppercase()
                    editingDump = FirmwareDump(
                        title = fileName.substringBeforeLast("."),
                        folderId = activeFolderId ?: (folders.firstOrNull()?.id ?: ""),
                        fileType = if (ext.length in 2..5) ext else "BIN",
                        localFilePath = savedPath,
                        fileSizeBytes = size,
                        requiresLicense = true,
                        isFree = false
                    )
                    showAddDumpDialog = true
                    Toast.makeText(context, "تم حفظ الملف في النظام بنجاح ($fileName)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر حفظ الملف المختار من الذاكرة", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Search query
    var searchQuery by remember { mutableStateOf("") }

    // HIERARCHICAL BACK NAVIGATION:
    // If inside a subfolder, navigate up one folder level. When at root, return to Dashboard.
    val handleBackPress = {
        if (folderStack.isNotEmpty()) {
            folderStack = folderStack.dropLast(1)
        } else {
            onBackClick()
        }
    }

    BackHandler {
        handleBackPress()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("programming_screen"),
        topBar = {
            // Elegant Curved Top Bar matching the app's visual identity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(top = 10.dp, bottom = 14.dp, start = 16.dp, end = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // START SIDE (Right in RTL): Prominent Back Button + Screen Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { handleBackPress() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("programming_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "رجوع للخلف",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (activeFolder != null) activeFolder.name else "البرمجة والسوفت وير 💻",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isAdmin) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.25f),
                                            border = BorderStroke(1.dp, Color(0xFF10B981))
                                        ) {
                                            Text(
                                                text = "مشرف",
                                                color = Color(0xFF6EE7B7),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (activeFolder != null) "استعراض ملفات الدامب والسوفت داخل المجلد" else "برامج مبرمجات • سوفتات بروسيسور • مخططات الربط",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        // END SIDE (Left in RTL): Admin Actions (License Manager + Add)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // All controls consolidated in the bottom floating action circle
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Navigation
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    selectedTabIndex = index
                                    folderStack = emptyList()
                                },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF5EEAD4) else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isAdmin) {
                AdminSpeedDialFab(
                    isInsideFolder = activeFolderId != null,
                    activeFolderName = activeFolder?.name ?: "",
                    onAddFolder = {
                        editingFolder = null
                        defaultParentFolderIdForNew = ""
                        showAddFolderDialog = true
                    },
                    onAddSubFolder = {
                        editingFolder = null
                        defaultParentFolderIdForNew = activeFolder?.id ?: ""
                        showAddFolderDialog = true
                    },
                    onUploadDump = {
                        editingDump = null
                        filePickerLauncher.launch("*/*")
                    },
                    onAddDumpManual = {
                        editingDump = null
                        showAddDumpDialog = true
                    },
                    onAddSoftware = {
                        editingSoftware = null
                        showAddSoftwareDialog = true
                    },
                    onAddConnection = {
                        editingConnection = null
                        showAddConnectionDialog = true
                    },
                    onManageKeys = {
                        showAdminKeysManagerDialog = true
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث في البرامج والسوفتات والمخططات...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            // CONTENT BY TAB
            when (selectedTabIndex) {
                0 -> {
                    // TAB 1: Programmer Tools & PC Software
                    SoftwareListTab(
                        softwares = softwares.filter {
                            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                        },
                        isAdmin = isAdmin,
                        onEdit = { editingSoftware = it; showAddSoftwareDialog = true },
                        onDelete = { ProgrammingRepository.deleteSoftware(context, it.id) },
                        onRequestActivation = { scope, name ->
                            activationTargetScope = scope
                            activationTargetName = name
                            showActivationDialog = true
                        }
                    )
                }
                1 -> {
                    // TAB 2: Processor Dumps / Folders Explorer
                    if (activeFolderId == null) {
                        // Folders Grid (Desktop style - Root Level)
                        FoldersGridTab(
                            folders = folders.filter {
                                it.parentFolderId.isBlank() &&
                                (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.brand.contains(searchQuery, ignoreCase = true))
                            },
                            isAdmin = isAdmin,
                            onFolderClick = { folder ->
                                val isUnlocked = ProgrammingRepository.isUnlocked(folder.id, isFree = folder.isFree, isAdmin = isAdmin)
                                if (!isUnlocked && folder.isLocked && !folder.isFree) {
                                    activationTargetScope = folder.id
                                    activationTargetName = "مجلد ${folder.name}"
                                    showActivationDialog = true
                                } else {
                                    folderStack = listOf(folder)
                                }
                            },
                            onEditFolder = { editingFolder = it; defaultParentFolderIdForNew = ""; showAddFolderDialog = true },
                            onDeleteFolder = { ProgrammingRepository.deleteFolder(context, it.id) },
                            onRequestUnlockAll = {
                                activationTargetScope = "ALL_FIRMWARE"
                                activationTargetName = "جميع مجلدات وسوفتات البروسيسور"
                                showActivationDialog = true
                            }
                        )
                    } else {
                        // Inside Folder: Subfolders and Dumps (Desktop Explorer Hierarchy)
                        val currentActive = activeFolder ?: FirmwareFolder(name = "المجلد الحالي")
                        val currentSubFolders = folders.filter {
                            it.parentFolderId == activeFolderId &&
                            (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.brand.contains(searchQuery, ignoreCase = true))
                        }
                        val currentDumps = dumps.filter {
                            it.folderId == activeFolderId &&
                            (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.processorType.contains(searchQuery, ignoreCase = true))
                        }
                        DumpsListTab(
                            folder = currentActive,
                            folderStack = folderStack,
                            subFolders = currentSubFolders,
                            dumps = currentDumps,
                            isAdmin = isAdmin,
                            onBackToFolders = {
                                folderStack = folderStack.dropLast(1)
                            },
                            onNavigateToSubFolder = { sub ->
                                val isUnlocked = ProgrammingRepository.isUnlocked(sub.id, isFree = sub.isFree, isAdmin = isAdmin)
                                if (!isUnlocked && sub.isLocked && !sub.isFree) {
                                    activationTargetScope = sub.id
                                    activationTargetName = "مجلد ${sub.name}"
                                    showActivationDialog = true
                                } else {
                                    folderStack = folderStack + sub
                                }
                            },
                            onNavigateToCrumb = { idx ->
                                folderStack = if (idx < 0) emptyList() else folderStack.take(idx + 1)
                            },
                            onEditFolder = { editingFolder = it; defaultParentFolderIdForNew = it.parentFolderId; showAddFolderDialog = true },
                            onDeleteFolder = { ProgrammingRepository.deleteFolder(context, it.id) },
                            onAddSubFolder = {
                                editingFolder = null
                                defaultParentFolderIdForNew = currentActive.id
                                showAddFolderDialog = true
                            },
                            onEditDump = { editingDump = it; showAddDumpDialog = true },
                            onDeleteDump = { ProgrammingRepository.deleteDump(context, it.id) },
                            onAddDump = { editingDump = null; showAddDumpDialog = true },
                            onUploadDumpFile = { filePickerLauncher.launch("*/*") }
                        )
                    }
                }
                2 -> {
                    // TAB 3: Programmer Connection Diagrams
                    ConnectionDiagramsTab(
                        connections = connections.filter {
                            searchQuery.isBlank() || it.programmerName.contains(searchQuery, ignoreCase = true) || it.boardName.contains(searchQuery, ignoreCase = true)
                        },
                        isAdmin = isAdmin,
                        onEdit = { editingConnection = it; showAddConnectionDialog = true },
                        onDelete = { ProgrammingRepository.deleteConnection(context, it.id) }
                    )
                }
            }
        }
    }

    // =========================================================================
    // MODALS: ACTIVATION, ADMIN LICENSE KEYS, CRUD
    // =========================================================================

    // Activation Key Dialog
    if (showActivationDialog) {
        ActivationKeyDialog(
            targetScope = activationTargetScope,
            targetScopeName = activationTargetName,
            currentUserEmail = currentUserEmail,
            onDismiss = { showActivationDialog = false },
            onActivated = {
                showActivationDialog = false
                Toast.makeText(context, "تم تفعيل القسم بنجاح ومتاح الآن للاستخدام الدائم 🎉", Toast.LENGTH_LONG).show()
            }
        )
    }

    // Admin License Keys Manager Dialog
    if (showAdminKeysManagerDialog && isAdmin) {
        AdminLicenseKeysManagerDialog(
            licenseKeys = licenseKeys,
            folders = folders,
            softwares = softwares,
            onDismiss = { showAdminKeysManagerDialog = false },
            onGenerateKey = { name, phone, email, scope, scopeName, allowedFolders, allowedSoftwares, isFull, notes ->
                ProgrammingRepository.generateUniqueKey(
                    context = context,
                    clientName = name,
                    clientPhone = phone,
                    clientEmail = email,
                    targetScope = scope,
                    targetScopeName = scopeName,
                    allowedFolderIds = allowedFolders,
                    allowedSoftwareIds = allowedSoftwares,
                    isFullAccess = isFull,
                    notes = notes
                ) { newKey ->
                    if (newKey != null) {
                        Toast.makeText(context, "تم توليد وحفظ المفتاح بنجاح: ${newKey.licenseKey}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteKey = { keyId ->
                ProgrammingRepository.deleteLicenseKey(context, keyId)
            },
            onUpdateKey = { updatedKey ->
                ProgrammingRepository.updateLicenseKey(context, updatedKey) { success ->
                    if (success) {
                        Toast.makeText(context, "تم حفظ وتحديث بيانات المفتاح بنجاح ✨", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Add / Edit Software Dialog
    if (showAddSoftwareDialog && isAdmin) {
        SoftwareEditDialog(
            initialItem = editingSoftware,
            onDismiss = { showAddSoftwareDialog = false },
            onSave = { savedItem ->
                ProgrammingRepository.saveSoftware(context, savedItem)
                showAddSoftwareDialog = false
                Toast.makeText(context, "تم حفظ البرنامج بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Folder Dialog
    if (showAddFolderDialog && isAdmin) {
        FolderEditDialog(
            initialItem = editingFolder,
            defaultParentFolderId = defaultParentFolderIdForNew,
            parentFolderName = activeFolder?.name ?: "",
            onDismiss = { showAddFolderDialog = false },
            onSave = { savedItem ->
                ProgrammingRepository.saveFolder(context, savedItem)
                showAddFolderDialog = false
                Toast.makeText(context, "تم حفظ المجلد بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Dump Dialog
    if (showAddDumpDialog && isAdmin) {
        DumpEditDialog(
            initialItem = editingDump,
            currentFolderId = activeFolderId ?: "",
            onDismiss = { showAddDumpDialog = false },
            onSave = { savedItem ->
                ProgrammingRepository.saveDump(context, savedItem)
                showAddDumpDialog = false
                Toast.makeText(context, "تم حفظ ملف السوفت بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Programmer Connection Dialog
    if (showAddConnectionDialog && isAdmin) {
        ConnectionEditDialog(
            initialItem = editingConnection,
            onDismiss = { showAddConnectionDialog = false },
            onSave = { savedItem ->
                ProgrammingRepository.saveConnection(context, savedItem)
                showAddConnectionDialog = false
                Toast.makeText(context, "تم حفظ مخطط التوصيل بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// =============================================================================
// SUB-TAB 1: SOFTWARE LIST
// =============================================================================
@Composable
fun SoftwareListTab(
    softwares: List<ProgrammerSoftware>,
    isAdmin: Boolean,
    onEdit: (ProgrammerSoftware) -> Unit,
    onDelete: (ProgrammerSoftware) -> Unit,
    onRequestActivation: (scope: String, name: String) -> Unit
) {
    val context = LocalContext.current

    if (softwares.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Laptop, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("لا توجد برامج مضافة حالياً", color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(softwares, key = { it.id }) { item ->
            val isUnlocked = ProgrammingRepository.isUnlocked(item.id, isFree = item.isFree, isAdmin = isAdmin)
            val isLocked = (item.requiresLicense || item.isLocked) && !isUnlocked && !item.isFree

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0F766E).copy(alpha = 0.15f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Laptop,
                                        contentDescription = null,
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = item.version,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (item.fileSizeText.isNotBlank()) {
                                        Text(text = item.fileSizeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = item.fileType,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    // Quick Free / Lock Badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (item.isFree) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEAB308).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, if (item.isFree) Color(0xFF10B981) else Color(0xFFEAB308)),
                                        modifier = Modifier.clickable(enabled = isAdmin) {
                                            ProgrammingRepository.toggleSoftwareFree(context, item.id)
                                            Toast.makeText(context, if (!item.isFree) "أصبح البرنامج متاحاً مجاناً للجميع 🔓" else "تم قفل البرنامج ليصبح للمشتركين فقط 🔒", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (item.isFree) Icons.Default.LockOpen else Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = if (item.isFree) Color(0xFF10B981) else Color(0xFFCA8A04),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (item.isFree) "مجاني 🔓" else "مدفوع 🔒",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isFree) Color(0xFF10B981) else Color(0xFFCA8A04)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isAdmin) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onEdit(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons (Download / Share / Unlock)
                    if (isLocked) {
                        Button(
                            onClick = { onRequestActivation(item.id, item.name) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("البرنامج محمي • اضغط لإدخال مفتاح التنشيط 🔑", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Download / Install Button
                            Button(
                                onClick = {
                                    if (item.localFilePath.isNotBlank()) {
                                        shareLocalFile(context, item.localFilePath, item.name)
                                    } else if (item.fileUrl.isNotBlank()) {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.fileUrl))
                                            context.startActivity(browserIntent)
                                        } catch (_: Throwable) {
                                            Toast.makeText(context, "تعذر فتح الرابط، يرجى التأكد من صحة الرابط", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "لم يتم رفع ملف أو تحديد رابط لهذا البرنامج بعد", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.localFilePath.isNotBlank()) Icons.Default.UploadFile else Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (item.localFilePath.isNotBlank()) "تثبيت / إرسال التطبيق 📱" else "تنزيل من الرابط 🌐",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 2: FOLDERS GRID (Desktop Style)
// =============================================================================
@Composable
fun FoldersGridTab(
    folders: List<FirmwareFolder>,
    isAdmin: Boolean,
    onFolderClick: (FirmwareFolder) -> Unit,
    onEditFolder: (FirmwareFolder) -> Unit,
    onDeleteFolder: (FirmwareFolder) -> Unit,
    onRequestUnlockAll: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Banner informing about folder activation
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F766E).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "مكتبة سوفتات ومعالجات الغسالات 📁",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "مجلدات منظمة تشبه الكمبيوتر تضم ملفات فلاشات BIN/HEX لكل كارتة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (!isAdmin && !ProgrammingRepository.isUnlocked(scope = "ALL_FIRMWARE", isAdmin = isAdmin)) {
                    Button(
                        onClick = onRequestUnlockAll,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فتح الكل 🔑", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(folders, key = { it.id }) { folder ->
                val isUnlocked = ProgrammingRepository.isUnlocked(folder.id, isFree = folder.isFree, isAdmin = isAdmin)
                val isLocked = folder.isLocked && !isUnlocked && !folder.isFree

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onFolderClick(folder) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Big Desktop Folder Icon
                            Surface(
                                shape = CircleShape,
                                color = if (isLocked) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF0F766E).copy(alpha = 0.15f),
                                modifier = Modifier.size(62.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isLocked) Color(0xFFEF4444) else Color(0xFF0F766E),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (folder.brand.isNotBlank()) folder.brand else "مجلد سوفتات",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 11.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }

                        // Free / Locked Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (folder.isFree) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEAB308).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (folder.isFree) Color(0xFF10B981) else Color(0xFFEAB308)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clickable(enabled = isAdmin) {
                                    ProgrammingRepository.toggleFolderFree(context, folder.id)
                                    Toast.makeText(context, if (!folder.isFree) "أصبح المجلد متاحاً مجاناً للجميع 🔓" else "تم قفل المجلد ليصبح للمشتركين فقط 🔒", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (folder.isFree) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (folder.isFree) Color(0xFF10B981) else Color(0xFFCA8A04),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (folder.isFree) "مجاني 🔓" else "مدفوع 🔒",
                                    color = if (folder.isFree) Color(0xFF10B981) else Color(0xFFCA8A04),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isAdmin) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onEditFolder(folder) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { onDeleteFolder(folder) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 2 (INSIDE FOLDER): DUMPS LIST
// =============================================================================
@Composable
fun DumpsListTab(
    folder: FirmwareFolder,
    folderStack: List<FirmwareFolder> = emptyList(),
    subFolders: List<FirmwareFolder> = emptyList(),
    dumps: List<FirmwareDump>,
    isAdmin: Boolean,
    onBackToFolders: () -> Unit,
    onNavigateToSubFolder: (FirmwareFolder) -> Unit = {},
    onNavigateToCrumb: (Int) -> Unit = {},
    onEditFolder: (FirmwareFolder) -> Unit = {},
    onDeleteFolder: (FirmwareFolder) -> Unit = {},
    onAddSubFolder: () -> Unit = {},
    onEditDump: (FirmwareDump) -> Unit,
    onDeleteDump: (FirmwareDump) -> Unit,
    onAddDump: () -> Unit,
    onUploadDumpFile: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation & Breadcrumbs Bar (Computer File Explorer Style)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Breadcrumbs row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToFolders,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "رجوع للأعلى",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "الملفات",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onNavigateToCrumb(-1) }
                        )
                        Text(text = " > ", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                        Text(
                            text = "سوفتات البروسيسور",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { onNavigateToCrumb(-1) }
                        )
                        folderStack.forEachIndexed { index, f ->
                            Text(text = " > ", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                            val isLast = index == folderStack.lastIndex
                            Text(
                                text = f.name,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                fontSize = 12.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable { onNavigateToCrumb(index) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stats & Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${subFolders.size} مجلد فرعي • ${dumps.size} ملف سوفت • ${folder.brand.ifBlank { "عام" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Actions consolidated exclusively into the bottom speed dial FAB as requested
                }
            }
        }

        if (dumps.isEmpty() && subFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("هذا المجلد لا يحتوي على مجلدات فرعية أو ملفات سوفت حالياً", color = MaterialTheme.colorScheme.outline)
                    Text("استخدم الزر العائم (+) بالأسفل لإضافة مجلد فرعي أو رفع ملف سوفت", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "المجلدات الفرعية (${subFolders.size}) 📁",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                val chunked = subFolders.chunked(2)
                items(chunked) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { sub ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onNavigateToSubFolder(sub) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (sub.isLocked) Icons.Default.Lock else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (sub.isLocked) Color(0xFFEF4444) else Color(0xFF0F766E),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = sub.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isAdmin) {
                                        Row(horizontalArrangement = Arrangement.Center) {
                                            IconButton(onClick = { onEditFolder(sub) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                            }
                                            IconButton(onClick = { onDeleteFolder(sub) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (dumps.isNotEmpty()) {
                item {
                    Text(
                        text = "ملفات السوفت والفلاشات (${dumps.size}) 💾",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            items(dumps, key = { it.id }) { dump ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dump.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // Free / Locked Badge
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (dump.isFree) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEAB308).copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, if (dump.isFree) Color(0xFF10B981) else Color(0xFFEAB308)),
                                            modifier = Modifier.clickable(enabled = isAdmin) {
                                                ProgrammingRepository.toggleDumpFree(context, dump.id)
                                                Toast.makeText(context, if (!dump.isFree) "أصبح السوفت متاحاً مجاناً للجميع 🔓" else "تم قفل السوفت ليصبح للمشتركين فقط 🔒", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text(
                                                text = if (dump.isFree) "مجاني 🔓" else "مدفوع 🔒",
                                                color = if (dump.isFree) Color(0xFF10B981) else Color(0xFFCA8A04),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (dump.processorType.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "المعالج: ${dump.processorType}",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (dump.boardPartNumber.isNotBlank()) {
                                            Text(text = "كود: ${dump.boardPartNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        if (dump.localFilePath.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF0F766E).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "ملف محلي 💾",
                                                    color = Color(0xFF0F766E),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isAdmin) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onEditDump(dump) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { onDeleteDump(dump) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (dump.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dump.notes,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Button
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (dump.localFilePath.isNotBlank()) {
                                Button(
                                    onClick = {
                                        shareLocalFile(context, dump.localFilePath, dump.title)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تثبيت / إرسال الملف 📤", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (dump.fileUrl.isNotBlank()) {
                                            try {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dump.fileUrl))
                                                context.startActivity(browserIntent)
                                            } catch (_: Throwable) {
                                                Toast.makeText(context, "تعذر فتح رابط تحميل السوفتوير", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "لم يتم رفع ملف لهذا السوفت بعد", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تنزيل السوفتوير (${dump.fileType}) 🌐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 3: PROGRAMMER TO BOARD CONNECTION DIAGRAMS
// =============================================================================
@Composable
fun ConnectionDiagramsTab(
    connections: List<ProgrammerConnection>,
    isAdmin: Boolean,
    onEdit: (ProgrammerConnection) -> Unit,
    onDelete: (ProgrammerConnection) -> Unit
) {
    val context = LocalContext.current

    if (connections.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Cable, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("لا توجد مخططات توصيل مبرمجات مضافة حالياً", color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(connections, key = { it.id }) { conn ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header: Programmer Name & Target Board
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "المبرمجة: ${conn.programmerName}",
                                        color = Color(0xFF8B5CF6),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEAB308).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = conn.voltageNote,
                                        color = Color(0xFFCA8A04),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = conn.boardName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isAdmin) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onEdit(conn) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(conn) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Images for connection diagram (Single or Carousel)
                    val connImages = conn.getAllImages()
                    if (connImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        var currentImgIdx by remember(conn.id) { mutableIntStateOf(0) }
                        val safeImgIdx = currentImgIdx.coerceIn(0, connImages.size - 1)
                        val activeImg = connImages[safeImgIdx]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(com.example.util.ImageHelper.getCoilData(activeImg))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${conn.boardName} - صورة ${safeImgIdx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (connImages.size > 1) {
                                IconButton(
                                    onClick = { currentImgIdx = if (safeImgIdx > 0) safeImgIdx - 1 else connImages.size - 1 },
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(6.dp)
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { currentImgIdx = if (safeImgIdx < connImages.size - 1) safeImgIdx + 1 else 0 },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(6.dp)
                                        .size(32.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "${safeImgIdx + 1} / ${connImages.size}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Instructions
                    if (conn.wiringInstructions.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "ملاحظات التوصيل:\n${conn.wiringInstructions}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pins Table
                    Text(
                        text = "جدول مطابقة أطراف المبرمجة مع الكارتة (${conn.pins.size} أطراف):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    conn.pins.forEach { pin ->
                        val wireColor = try {
                            Color(android.graphics.Color.parseColor(pin.colorHex))
                        } catch (_: Throwable) {
                            Color.Gray
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(wireColor)
                                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pin.programmerPin,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⟵",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pin.boardPin,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Share button
                    OutlinedButton(
                        onClick = {
                            val pinDetails = conn.pins.joinToString("\n") { "• ${it.programmerPin} ⟵ ${it.boardPin} (${it.wireColor})" }
                            val shareText = "مخطط ربط ${conn.boardName}\nبالمبرمجة: ${conn.programmerName}\nالجهد: ${conn.voltageNote}\n\nتوصيل الأطراف:\n$pinDetails\n\n${conn.wiringInstructions}\n\nمرسل من تطبيق البراء إلكترونيك ⚡"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, conn.boardName)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة مخطط التوصيل للكمبيوتر"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة مخطط التوصيل للكمبيوتر أو الفنيين 💻", fontSize = 11.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================================
// MODAL: ACTIVATION KEY DIALOG FOR USERS
// =============================================================================
@Composable
fun ActivationKeyDialog(
    targetScope: String,
    targetScopeName: String,
    currentUserEmail: String? = null,
    onDismiss: () -> Unit,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val contactInfo by ContactInfoManager.contactInfo.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(36.dp))
        },
        title = {
            Text(
                text = "قسم مخصص للمشتركين 🔒",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "عذراً، $targetScopeName محمي ويتطلب مفتاح تنشيط فريد خاص بحسابك وجهازك.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!currentUserEmail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "الحساب الحالي: $currentUserEmail",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = {
                        keyInput = it
                        errorMessage = null
                    },
                    label = { Text("أدخل مفتاح التنشيط (مثال: ALB-SOFT-XXXX-YYYY)") },
                    placeholder = { Text("ALB-SOFT-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = errorMessage ?: "", color = Color(0xFFEF4444), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // WhatsApp Purchase button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF16A34A).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF16A34A)),
                    modifier = Modifier
                        .clickable {
                            val phone = contactInfo.whatsappNumber.ifBlank { contactInfo.phoneNumber }
                            val cleanPhone = phone.replace("+", "").replace(" ", "").trim()
                            val emailSnippet = if (!currentUserEmail.isNullOrBlank()) "\nإيميلي المسجل: $currentUserEmail" else ""
                            val msg = "السلام عليكم م. البراء، أرغب في شراء مفتاح تنشيط لـ: $targetScopeName في تطبيق البراء إلكترونيك ⚡$emailSnippet"
                            try {
                                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(msg)}"
                                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(i)
                            } catch (_: Throwable) {
                                Toast.makeText(context, "تعذر فتح تطبيق واتساب", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("💬", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "طلب وشراء المفتاح عبر الواتساب",
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyInput.isBlank()) {
                        errorMessage = "يرجى كتابة أو لصق المفتاح أولاً"
                        return@Button
                    }
                    isVerifying = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = ProgrammingRepository.activateLicenseKey(context, keyInput, currentUserEmail)
                        isVerifying = false
                        if (result.isSuccess) {
                            onActivated()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "فشل تفعيل المفتاح"
                        }
                    }
                },
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text(if (isVerifying) "جاري التحقق..." else "تفعيل الآن ✨", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// =============================================================================
// MODAL: GRANULAR SCOPE SELECTOR (اختيار المجلدات والبرامج بدقة لمفتاح العميل)
// =============================================================================
data class FlattenedFolderItem(
    val folder: FirmwareFolder,
    val level: Int,
    val hasChildren: Boolean
)

fun formatPhoneForWhatsApp(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
        digits.startsWith("0020") -> digits.removePrefix("00")
        digits.startsWith("20") -> digits
        digits.startsWith("01") && digits.length == 11 -> "20" + digits.substring(1)
        digits.startsWith("1") && digits.length == 10 -> "20$digits"
        digits.startsWith("0") && digits.length >= 10 -> "20" + digits.substring(1)
        else -> digits
    }
}

@Composable
fun GranularScopeSelectorDialog(
    folders: List<FirmwareFolder>,
    softwares: List<ProgrammerSoftware>,
    initiallySelectedFolderIds: List<String>,
    initiallySelectedSoftwareIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (selectedFolderIds: List<String>, selectedSoftwareIds: List<String>) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var filterText by remember { mutableStateOf("") }
    val tempFolderIds = remember { mutableStateListOf<String>().apply { addAll(initiallySelectedFolderIds) } }
    val tempSoftwareIds = remember { mutableStateListOf<String>().apply { addAll(initiallySelectedSoftwareIds) } }
    val expandedFolderIds = remember { mutableStateListOf<String>() }

    val childrenMap = remember(folders) { folders.groupBy { it.parentFolderId } }
    val allFolderIds = remember(folders) { folders.map { it.id }.toSet() }
    val roots = remember(folders) { folders.filter { it.parentFolderId.isBlank() || !allFolderIds.contains(it.parentFolderId) } }

    val flattenedNodes = remember(folders, expandedFolderIds.toList(), filterText) {
        if (filterText.isNotBlank()) {
            folders.filter {
                it.name.contains(filterText, ignoreCase = true) || it.brand.contains(filterText, ignoreCase = true)
            }.map { FlattenedFolderItem(it, level = 0, hasChildren = false) }
        } else {
            val result = mutableListOf<FlattenedFolderItem>()
            fun traverse(f: FirmwareFolder, level: Int) {
                val kids = childrenMap[f.id].orEmpty()
                val hasKids = kids.isNotEmpty()
                result.add(FlattenedFolderItem(f, level, hasKids))
                if (hasKids && expandedFolderIds.contains(f.id)) {
                    for (k in kids) {
                        traverse(k, level + 1)
                    }
                }
            }
            for (r in roots) {
                traverse(r, 0)
            }
            result
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تحديد صلاحيات العميل بالتفصيل 🎯", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "حدد المجلدات أو البرامج التي يحق للمستخدم فتحها بهذا المفتاح",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: Folders vs Softwares
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { activeTab = 0 }
                    ) {
                        Text(
                            text = "المجلدات (${tempFolderIds.size}/${folders.size}) 📁",
                            color = if (activeTab == 0) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { activeTab = 1 }
                    ) {
                        Text(
                            text = "البرامج (${tempSoftwareIds.size}/${softwares.size}) 💻",
                            color = if (activeTab == 1) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search & Select All row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("بحث في العناصر...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = {
                            if (activeTab == 0) {
                                val allFIds = folders.map { it.id }
                                if (tempFolderIds.containsAll(allFIds)) {
                                    tempFolderIds.clear()
                                } else {
                                    tempFolderIds.clear()
                                    tempFolderIds.addAll(allFIds)
                                }
                            } else {
                                val allSIds = softwares.map { it.id }
                                if (tempSoftwareIds.containsAll(allSIds)) {
                                    tempSoftwareIds.clear()
                                } else {
                                    tempSoftwareIds.clear()
                                    tempSoftwareIds.addAll(allSIds)
                                }
                            }
                        }
                    ) {
                        Text("الكل/إلغاء", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Items List
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    if (activeTab == 0) {
                        if (flattenedNodes.isEmpty()) {
                            Text("لا توجد مجلدات مطابقة", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(flattenedNodes, key = { it.folder.id }) { node ->
                                    val f = node.folder
                                    val isChecked = tempFolderIds.contains(f.id)
                                    val isExpanded = expandedFolderIds.contains(f.id)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                                .padding(start = (node.level * 18).dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (node.hasChildren) {
                                                IconButton(
                                                    onClick = {
                                                        if (isExpanded) expandedFolderIds.remove(f.id) else expandedFolderIds.add(f.id)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                        contentDescription = "عرض المجلدات الفرعية",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.width(28.dp))
                                            }

                                            androidx.compose.material3.Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    if (checked) tempFolderIds.add(f.id) else tempFolderIds.remove(f.id)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = if (node.hasChildren && isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isChecked) tempFolderIds.remove(f.id) else tempFolderIds.add(f.id)
                                                    }
                                            ) {
                                                Text(f.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                if (f.brand.isNotBlank()) Text(f.brand, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val filteredSoftwares = softwares.filter {
                            filterText.isBlank() || it.name.contains(filterText, ignoreCase = true) || it.description.contains(filterText, ignoreCase = true)
                        }
                        if (filteredSoftwares.isEmpty()) {
                            Text("لا توجد برامج مطابقة", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filteredSoftwares, key = { it.id }) { s ->
                                    val isChecked = tempSoftwareIds.contains(s.id)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isChecked) tempSoftwareIds.remove(s.id) else tempSoftwareIds.add(s.id)
                                            }
                                    ) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { if (it) tempSoftwareIds.add(s.id) else tempSoftwareIds.remove(s.id) }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(s.version, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
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
            Button(
                onClick = {
                    onConfirm(tempFolderIds.toList(), tempSoftwareIds.toList())
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تأكيد الصلاحيات (${tempFolderIds.size + tempSoftwareIds.size}) ✓")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// =============================================================================
// MODAL: ADMIN LICENSE KEYS MANAGER (توليد ومتابعة مفاتيح العملاء)
// =============================================================================
@Composable
fun AdminLicenseKeysManagerDialog(
    licenseKeys: List<LicenseKeyRecord>,
    folders: List<FirmwareFolder> = emptyList(),
    softwares: List<ProgrammerSoftware> = emptyList(),
    onDismiss: () -> Unit,
    onGenerateKey: (
        clientName: String,
        clientPhone: String,
        clientEmail: String,
        scope: String,
        scopeName: String,
        allowedFolders: List<String>,
        allowedSoftwares: List<String>,
        isFull: Boolean,
        notes: String
    ) -> Unit,
    onDeleteKey: (keyId: String) -> Unit,
    onUpdateKey: (updatedKey: LicenseKeyRecord) -> Unit = {}
) {
    val context = LocalContext.current
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientEmail by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var keySearchQuery by remember { mutableStateOf("") }
    var editingKeyRecord by remember { mutableStateOf<LicenseKeyRecord?>(null) }

    var isFullAccess by remember { mutableStateOf(true) }
    var showGranularScopePicker by remember { mutableStateOf(false) }
    val selectedFolderIds = remember { mutableStateListOf<String>() }
    val selectedSoftwareIds = remember { mutableStateListOf<String>() }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFEAB308))
                Spacer(modifier = Modifier.width(8.dp))
                Text("سجل التراخيص ومفاتيح العملاء 🔑", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Generate New Key Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "توليد مفتاح مخصص لعميل جديد ⚡", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = clientName,
                            onValueChange = { clientName = it },
                            label = { Text("اسم العميل / المهندس *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = clientEmail,
                            onValueChange = { clientEmail = it },
                            label = { Text("إيميل العميل (مطلوب لربط المفتاح بحسابه) *") },
                            placeholder = { Text("example@gmail.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = clientPhone,
                            onValueChange = { clientPhone = it },
                            label = { Text("رقم هاتف / واتساب العميل") },
                            placeholder = { Text("010xxxxxxxx") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات (سعر الاشتراك، كود الجهاز...)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Granular Scope Selection
                        Text("صلاحيات المفتاح:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = isFullAccess,
                                onCheckedChange = { isFullAccess = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الوصول الشامل (جميع السوفتات والبرامج) 👑", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (!isFullAccess) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showGranularScopePicker = true }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("الأقسام المسموح بها لهذا المفتاح 🎯", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = if (selectedFolderIds.isEmpty() && selectedSoftwareIds.isEmpty()) "اضغط هنا لاختيار المجلدات والبرامج" else "محدد: ${selectedFolderIds.size} مجلد • ${selectedSoftwareIds.size} برنامج مبرمجة",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "تحديد الصلاحيات ✍️",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (clientName.isBlank()) {
                                    Toast.makeText(context, "يرجى كتابة اسم العميل أولاً", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (clientEmail.isBlank() || !clientEmail.contains("@")) {
                                    Toast.makeText(context, "البريد الإلكتروني للعميل مطلوب لربط المفتاح بحسابه الشخصي في التطبيق", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val scopeName = if (isFullAccess) "الوصول الشامل لجميع السوفتات والبرامج" else "محدد (${selectedFolderIds.size} مجلد، ${selectedSoftwareIds.size} برنامج)"
                                onGenerateKey(
                                    clientName.trim(),
                                    clientPhone.trim(),
                                    clientEmail.trim(),
                                    if (isFullAccess) "ALL_FIRMWARE" else "GRANULAR",
                                    scopeName,
                                    selectedFolderIds.toList(),
                                    selectedSoftwareIds.toList(),
                                    isFullAccess,
                                    notes.trim()
                                )
                                Toast.makeText(context, "تم حفظ المفتاح وربطه بحساب العميل بنجاح 🎉", Toast.LENGTH_SHORT).show()
                                clientName = ""
                                clientPhone = ""
                                clientEmail = ""
                                notes = ""
                                selectedFolderIds.clear()
                                selectedSoftwareIds.clear()
                                isFullAccess = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("توليد مفتاح فريد وحفظه في السحاب 🔑", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: List of Generated Keys with Search
                val displayedKeys = licenseKeys.filter { key ->
                    keySearchQuery.isBlank() ||
                    key.clientPhone.contains(keySearchQuery, ignoreCase = true) ||
                    key.clientName.contains(keySearchQuery, ignoreCase = true) ||
                    key.clientEmail.contains(keySearchQuery, ignoreCase = true) ||
                    key.licenseKey.contains(keySearchQuery, ignoreCase = true)
                }

                Text(
                    text = "المفاتيح المسجلة (${displayedKeys.size} من ${licenseKeys.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = keySearchQuery,
                    onValueChange = { keySearchQuery = it },
                    placeholder = { Text("بحث في المفاتيح بالرقم، الاسم، الإيميل...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (keySearchQuery.isNotBlank()) {
                            IconButton(onClick = { keySearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (displayedKeys.isEmpty()) {
                    Text("لا توجد مفاتيح مسجلة مطابقة للبحث", fontSize = 11.sp, color = Color.Gray)
                } else {
                    displayedKeys.forEach { keyRecord ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (keyRecord.isActivated) Color(0xFF10B981) else Color(0xFFEAB308)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = keyRecord.licenseKey,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (keyRecord.isActivated) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEAB308).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (keyRecord.isActivated) "مفعل على جهاز ✓" else "متاح للاستخدام ⏳",
                                            color = if (keyRecord.isActivated) Color(0xFF10B981) else Color(0xFFCA8A04),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "العميل: ${keyRecord.clientName.ifBlank { "غير مسجل" }} ${if (keyRecord.clientPhone.isNotBlank()) "• ${keyRecord.clientPhone}" else ""}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (keyRecord.clientEmail.isNotBlank()) {
                                    Text(
                                        text = "الإيميل المقيد: ${keyRecord.clientEmail}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "القسم: ${keyRecord.targetScopeName} • تاريخ الإنشاء: ${dateFormat.format(Date(keyRecord.createdTimestamp))}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Actions: Copy / Send WhatsApp / Edit / Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // WhatsApp Send to Client with formatted Egyptian/international number
                                    Button(
                                        onClick = {
                                            val emailClause = if (keyRecord.clientEmail.isNotBlank()) "\nالحساب المرتبط: ${keyRecord.clientEmail}" else ""
                                            val clientMsg = "مرحباً يا باشمهندس ${keyRecord.clientName}،\nتم تفعيل اشتراكك في قسم ${keyRecord.targetScopeName} بتطبيق البراء إلكترونيك.$emailClause\n\nمفتاح التنشيط الخاص بك هو:\n${keyRecord.licenseKey}\n\nطريقة التفعيل: ادخل على قسم البرمجة والسوفت وير بالتطبيق، واضغط على فتح القسم، ثم الصق المفتاح واضغط تفعيل ✨\nمع تحيات م. البراء إلكترونيك ⚡"
                                            val cleanPhone = formatPhoneForWhatsApp(keyRecord.clientPhone)
                                            try {
                                                val url = if (cleanPhone.isNotBlank()) "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(clientMsg)}" else "https://api.whatsapp.com/send?text=${Uri.encode(clientMsg)}"
                                                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(i)
                                            } catch (_: Throwable) {
                                                Toast.makeText(context, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("إرسال عبر الواتساب 💬", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Copy Key
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("License Key", keyRecord.licenseKey)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ المفتاح للحافظة", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", modifier = Modifier.size(13.dp))
                                    }

                                    // Edit Key Button
                                    IconButton(
                                        onClick = { editingKeyRecord = keyRecord },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل بيانات المفتاح والصلاحيات", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }

                                    // Delete Key
                                    IconButton(
                                        onClick = { onDeleteKey(keyRecord.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )

    if (showGranularScopePicker) {
        GranularScopeSelectorDialog(
            folders = folders,
            softwares = softwares,
            initiallySelectedFolderIds = selectedFolderIds.toList(),
            initiallySelectedSoftwareIds = selectedSoftwareIds.toList(),
            onDismiss = { showGranularScopePicker = false },
            onConfirm = { fIds, sIds ->
                selectedFolderIds.clear()
                selectedFolderIds.addAll(fIds)
                selectedSoftwareIds.clear()
                selectedSoftwareIds.addAll(sIds)
                showGranularScopePicker = false
            }
        )
    }

    if (editingKeyRecord != null) {
        EditCustomerKeyDialog(
            keyRecord = editingKeyRecord!!,
            folders = folders,
            softwares = softwares,
            onDismiss = { editingKeyRecord = null },
            onSave = { updated ->
                onUpdateKey(updated)
                editingKeyRecord = null
            }
        )
    }
}

@Composable
fun EditCustomerKeyDialog(
    keyRecord: LicenseKeyRecord,
    folders: List<FirmwareFolder>,
    softwares: List<ProgrammerSoftware>,
    onDismiss: () -> Unit,
    onSave: (LicenseKeyRecord) -> Unit
) {
    var editName by remember { mutableStateOf(keyRecord.clientName) }
    var editPhone by remember { mutableStateOf(keyRecord.clientPhone) }
    var editEmail by remember { mutableStateOf(keyRecord.clientEmail) }
    var editNotes by remember { mutableStateOf(keyRecord.notes) }
    var editIsFullAccess by remember { mutableStateOf(keyRecord.isFullAccess) }
    val editSelectedFolderIds = remember { mutableStateListOf<String>().apply { addAll(keyRecord.allowedFolderIds) } }
    val editSelectedSoftwareIds = remember { mutableStateListOf<String>().apply { addAll(keyRecord.allowedSoftwareIds) } }
    var showScopePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تعديل بيانات المفتاح والصلاحيات ✏️", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "المفتاح: ${keyRecord.licenseKey}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("اسم العميل / المهندس *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editEmail,
                    onValueChange = { editEmail = it },
                    label = { Text("إيميل العميل (لربط المفتاح بحسابه) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editPhone,
                    onValueChange = { editPhone = it },
                    label = { Text("رقم هاتف / واتساب العميل") },
                    placeholder = { Text("010xxxxxxxx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("ملاحظات") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = editIsFullAccess,
                        onCheckedChange = { editIsFullAccess = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الوصول الشامل (جميع السوفتات والبرامج) 👑", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (!editIsFullAccess) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showScopePicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "محدد: ${editSelectedFolderIds.size} مجلد • ${editSelectedSoftwareIds.size} برنامج",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("تعديل الصلاحيات ✍️", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editName.isBlank()) return@Button
                    val scopeName = if (editIsFullAccess) "الوصول الشامل لجميع السوفتات والبرامج" else "محدد (${editSelectedFolderIds.size} مجلد، ${editSelectedSoftwareIds.size} برنامج)"
                    val updated = keyRecord.copy(
                        clientName = editName.trim(),
                        clientPhone = editPhone.trim(),
                        clientEmail = editEmail.trim(),
                        notes = editNotes.trim(),
                        isFullAccess = editIsFullAccess,
                        targetScope = if (editIsFullAccess) "ALL_FIRMWARE" else "GRANULAR",
                        targetScopeName = scopeName,
                        allowedFolderIds = editSelectedFolderIds.toList(),
                        allowedSoftwareIds = editSelectedSoftwareIds.toList()
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showScopePicker) {
        GranularScopeSelectorDialog(
            folders = folders,
            softwares = softwares,
            initiallySelectedFolderIds = editSelectedFolderIds.toList(),
            initiallySelectedSoftwareIds = editSelectedSoftwareIds.toList(),
            onDismiss = { showScopePicker = false },
            onConfirm = { selectedF, selectedS ->
                editSelectedFolderIds.clear()
                editSelectedFolderIds.addAll(selectedF)
                editSelectedSoftwareIds.clear()
                editSelectedSoftwareIds.addAll(selectedS)
                showScopePicker = false
            }
        )
    }
}

// =============================================================================
// MODAL: ADD / EDIT SOFTWARE
// =============================================================================
@Composable
fun SoftwareEditDialog(
    initialItem: ProgrammerSoftware?,
    onDismiss: () -> Unit,
    onSave: (ProgrammerSoftware) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var description by remember { mutableStateOf(initialItem?.description ?: "") }
    var version by remember { mutableStateOf(initialItem?.version ?: "v1.0") }
    var fileUrl by remember { mutableStateOf(initialItem?.fileUrl ?: "") }
    var localFilePath by remember { mutableStateOf(initialItem?.localFilePath ?: "") }
    var fileSizeBytes by remember { mutableStateOf(initialItem?.fileSizeBytes ?: 0L) }
    var fileSizeText by remember { mutableStateOf(initialItem?.fileSizeText ?: "") }
    var fileType by remember { mutableStateOf(initialItem?.fileType ?: "EXE") }
    var isLocked by remember { mutableStateOf(initialItem?.isLocked ?: false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                var fileName = "software_${System.currentTimeMillis()}.exe"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                } catch (_: Throwable) {}

                val (savedPath, size) = ProgrammingRepository.saveUploadedFile(context, uri, fileName)
                if (savedPath.isNotBlank()) {
                    localFilePath = savedPath
                    fileSizeBytes = size
                    val mb = size / (1024f * 1024f)
                    fileSizeText = if (mb >= 1f) String.format(Locale.US, "%.1f MB", mb) else "${size / 1024} KB"
                    val ext = fileName.substringAfterLast(".", "EXE").uppercase()
                    fileType = if (ext.length in 2..5) ext else "EXE"
                    if (name.isBlank()) {
                        name = fileName.substringBeforeLast(".")
                    }
                    Toast.makeText(context, "تم رفع وتضمين ملف البرنامج بنجاح ($fileName)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر حفظ الملف من ذاكرة الجهاز", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "إضافة برنامج برمجة جديد 💻" else "تعديل البرنامج", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم البرنامج *") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف والشرح") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("رقم الإصدار (مثل v2.0)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                // File Upload Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (localFilePath.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ملف التثبيت من ذاكرة الهاتف / الجهاز 💾", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                if (localFilePath.isNotBlank()) {
                                    Text(
                                        text = "تم التضمين: ${localFilePath.substringAfterLast("/")} ($fileSizeText)",
                                        color = Color(0xFF10B981),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("رفع ملف التثبيت المباشر (EXE / ZIP / APK...)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = if (localFilePath.isNotBlank()) Color(0xFF10B981) else Color(0xFF0F766E)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (localFilePath.isNotBlank()) "تغيير الملف" else "رفع ملف", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = fileUrl, onValueChange = { fileUrl = it }, label = { Text("أو رابط التحميل المباشر (اختياري)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = fileSizeText, onValueChange = { fileSizeText = it }, label = { Text("حجم الملف (مثل 150 MB)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = fileType, onValueChange = { fileType = it }, label = { Text("نوع الملف (EXE / ZIP / APK / RAR)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = isLocked, onCheckedChange = { isLocked = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قفل البرنامج برمز تنشيط للمشتركين فقط 🔒", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val item = (initialItem ?: ProgrammerSoftware()).copy(
                        name = name.trim(),
                        description = description.trim(),
                        version = version.trim(),
                        fileUrl = fileUrl.trim(),
                        localFilePath = localFilePath.trim(),
                        fileSizeBytes = fileSizeBytes,
                        fileSizeText = fileSizeText.trim(),
                        fileType = fileType.trim().uppercase(),
                        isLocked = isLocked,
                        requiresLicense = isLocked
                    )
                    onSave(item)
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// =============================================================================
// MODAL: ADD / EDIT FOLDER
// =============================================================================
@Composable
fun FolderEditDialog(
    initialItem: FirmwareFolder?,
    defaultParentFolderId: String = "",
    parentFolderName: String = "",
    onDismiss: () -> Unit,
    onSave: (FirmwareFolder) -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var description by remember { mutableStateOf(initialItem?.description ?: "") }
    var brand by remember { mutableStateOf(initialItem?.brand ?: "") }
    var isLocked by remember { mutableStateOf(initialItem?.isLocked ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (initialItem == null) {
                        if (parentFolderName.isNotBlank()) "إضافة مجلد فرعي جديد 📁" else "إضافة مجلد رئيسي جديد 📁"
                    } else "تعديل المجلد",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (parentFolderName.isNotBlank()) {
                    Text("داخل المجلد: $parentFolderName", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المجلد (مثل كروت رينيساس)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("الماركة أو الشركة المصنعة") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("وصف المجلد وما يحتويه") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = isLocked, onCheckedChange = { isLocked = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("قفل المجلد بمفتاح تنشيط للمشتركين 🔒", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val pId = initialItem?.parentFolderId?.ifBlank { defaultParentFolderId } ?: defaultParentFolderId
                    val item = (initialItem ?: FirmwareFolder(parentFolderId = pId)).copy(
                        name = name.trim(),
                        brand = brand.trim(),
                        description = description.trim(),
                        parentFolderId = pId,
                        isLocked = isLocked
                    )
                    onSave(item)
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// =============================================================================
// MODAL: ADD / EDIT DUMP
// =============================================================================
@Composable
fun DumpEditDialog(
    initialItem: FirmwareDump?,
    currentFolderId: String,
    onDismiss: () -> Unit,
    onSave: (FirmwareDump) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var processorType by remember { mutableStateOf(initialItem?.processorType ?: "") }
    var boardPartNumber by remember { mutableStateOf(initialItem?.boardPartNumber ?: "") }
    var fileUrl by remember { mutableStateOf(initialItem?.fileUrl ?: "") }
    var localFilePath by remember { mutableStateOf(initialItem?.localFilePath ?: "") }
    var fileSizeBytes by remember { mutableStateOf(initialItem?.fileSizeBytes ?: 0L) }
    var fileType by remember { mutableStateOf(initialItem?.fileType ?: "BIN") }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                var fileName = "dump_${System.currentTimeMillis()}.bin"
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: fileName
                        }
                    }
                } catch (_: Throwable) {}

                val (savedPath, size) = ProgrammingRepository.saveUploadedFile(context, uri, fileName)
                if (savedPath.isNotBlank()) {
                    localFilePath = savedPath
                    fileSizeBytes = size
                    val ext = fileName.substringAfterLast(".", "BIN").uppercase()
                    fileType = if (ext.length in 2..5) ext else "BIN"
                    if (title.isBlank()) {
                        title = fileName.substringBeforeLast(".")
                    }
                    Toast.makeText(context, "تم حفظ الملف بنجاح ($fileName)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "إضافة ملف سوفتوير جديد 💾" else "تعديل ملف السوفت", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان السوفت وموديل الغسالة") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = processorType, onValueChange = { processorType = it }, label = { Text("نوع المعالج (مثل R5F21334 أو STM32)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = boardPartNumber, onValueChange = { boardPartNumber = it }, label = { Text("رقم أو كود البوردة") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                // File Attachment Status / Picker
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (localFilePath.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ملف الفلاشة من الجهاز 💾", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            if (localFilePath.isNotBlank()) {
                                Text(
                                    text = "تم التضمين: ${localFilePath.substringAfterLast("/")}",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text("لا يوجد ملف محلي مرفق", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = if (localFilePath.isNotBlank()) Color(0xFF10B981) else Color(0xFF0F766E)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (localFilePath.isNotBlank()) "تغيير" else "اختيار", fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = fileUrl, onValueChange = { fileUrl = it }, label = { Text("أو رابط تحميل ملف السوفت (اختياري)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = fileType, onValueChange = { fileType = it }, label = { Text("نوع الملف (BIN / HEX / EEP / ZIP)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات وشروط الحرق والقراءة") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val item = (initialItem ?: FirmwareDump(folderId = currentFolderId)).copy(
                        title = title.trim(),
                        folderId = currentFolderId,
                        processorType = processorType.trim(),
                        boardPartNumber = boardPartNumber.trim(),
                        fileUrl = fileUrl.trim(),
                        localFilePath = localFilePath.trim(),
                        fileSizeBytes = fileSizeBytes,
                        fileType = fileType.trim().uppercase(),
                        notes = notes.trim()
                    )
                    onSave(item)
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// =============================================================================
// MODAL: ADD / EDIT CONNECTION DIAGRAM
// =============================================================================
@Composable
fun ConnectionEditDialog(
    initialItem: ProgrammerConnection?,
    onDismiss: () -> Unit,
    onSave: (ProgrammerConnection) -> Unit
) {
    val context = LocalContext.current
    var programmerName by remember { mutableStateOf(initialItem?.programmerName ?: "RT809H") }
    var boardName by remember { mutableStateOf(initialItem?.boardName ?: "") }
    var voltageNote by remember { mutableStateOf(initialItem?.voltageNote ?: "3.3V DC") }
    var wiringInstructions by remember { mutableStateOf(initialItem?.wiringInstructions ?: "") }

    val imageUrlsList = remember {
        mutableStateListOf<String>().apply {
            addAll(initialItem?.getAllImages() ?: emptyList())
        }
    }
    var directImgUrl by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val localPath = com.example.util.ImageHelper.copyUriToLocalStorage(context, uri)
                imageUrlsList.add(localPath)
            } catch (_: Exception) {
                imageUrlsList.add(uri.toString())
            }
        }
    }

    val pinsList = remember {
        mutableStateListOf<ConnectionPinItem>().apply {
            if (initialItem != null && initialItem.pins.isNotEmpty()) {
                addAll(initialItem.pins)
            } else {
                addAll(
                    listOf(
                        ConnectionPinItem("Pin 1: VCC 3.3V", "TP1", "أحمر", "#EF4444"),
                        ConnectionPinItem("Pin 2: GND", "TP2", "أسود", "#1E293B"),
                        ConnectionPinItem("Pin 3: TX", "TP3", "أزرق", "#2563EB"),
                        ConnectionPinItem("Pin 4: RX", "TP4", "أخضر", "#16A34A")
                    )
                )
            }
        }
    }

    var newProgPin by remember { mutableStateOf("") }
    var newBoardPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "إضافة مخطط ربط مبرمجة 🔌" else "تعديل مخطط الربط", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = programmerName, onValueChange = { programmerName = it }, label = { Text("اسم المبرمجة (مثل RT809H أو CH341A)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = boardName, onValueChange = { boardName = it }, label = { Text("اسم كارتة الغسالة أو المعالج") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = voltageNote, onValueChange = { voltageNote = it }, label = { Text("ملاحظة الجهد (مثل 3.3V DC)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = wiringInstructions, onValueChange = { wiringInstructions = it }, label = { Text("تعليمات وخطوات التوصيل") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))

                // Images Section (Multiple Photos Support)
                Text("صور ومخططات الربط (${imageUrlsList.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                if (imageUrlsList.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp)
                    ) {
                        imageUrlsList.forEachIndexed { index, imgItem ->
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(com.example.util.ImageHelper.getCoilData(imgItem))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "صورة $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .align(Alignment.BottomEnd)
                                ) {
                                    IconButton(
                                        onClick = { imageUrlsList.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "حذف الصورة",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة من المعرض", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = directImgUrl,
                        onValueChange = { directImgUrl = it },
                        placeholder = { Text("أو مسار / رابط صورة...", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (directImgUrl.isNotBlank()) {
                                imageUrlsList.add(directImgUrl.trim())
                                directImgUrl = ""
                            }
                        },
                        enabled = directImgUrl.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("+")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("جدول الأطراف (${pinsList.size} طرف):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                pinsList.forEachIndexed { idx, p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${p.programmerPin} ⟵ ${p.boardPin}", fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { pinsList.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = newProgPin, onValueChange = { newProgPin = it }, placeholder = { Text("طرف المبرمجة", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = newBoardPin, onValueChange = { newBoardPin = it }, placeholder = { Text("نقطة الكارتة", fontSize = 10.sp) }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(
                        onClick = {
                            if (newProgPin.isNotBlank() && newBoardPin.isNotBlank()) {
                                pinsList.add(ConnectionPinItem(newProgPin.trim(), newBoardPin.trim()))
                                newProgPin = ""
                                newBoardPin = ""
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (boardName.isBlank()) return@Button
                    val allImgs = imageUrlsList.toList()
                    val item = (initialItem ?: ProgrammerConnection()).copy(
                        programmerName = programmerName.trim(),
                        boardName = boardName.trim(),
                        voltageNote = voltageNote.trim(),
                        wiringInstructions = wiringInstructions.trim(),
                        boardImageUrl = allImgs.firstOrNull() ?: "",
                        imageUrls = allImgs,
                        pins = pinsList.toList()
                    )
                    onSave(item)
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

// =============================================================================
// COMPONENT: ADMIN EXPANDABLE SPEED DIAL FAB (مطابق للصورة)
// =============================================================================
@Composable
fun AdminSpeedDialFab(
    isInsideFolder: Boolean = false,
    activeFolderName: String = "",
    onAddFolder: () -> Unit,
    onAddSubFolder: () -> Unit,
    onUploadDump: () -> Unit,
    onAddDumpManual: () -> Unit,
    onAddSoftware: () -> Unit,
    onAddConnection: () -> Unit,
    onManageKeys: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (expanded) {
            // Option 1: Manage Keys (Always available)
            SpeedDialItem(
                label = "مفاتيح وتراخيص العملاء 🔑",
                icon = Icons.Default.Key,
                backgroundColor = Color(0xFFEAB308),
                onClick = {
                    expanded = false
                    onManageKeys()
                }
            )

            if (isInsideFolder) {
                // Inside Folder Actions
                SpeedDialItem(
                    label = "إضافة مجلد فرعي داخل ($activeFolderName) 📁",
                    icon = Icons.Default.CreateNewFolder,
                    backgroundColor = Color(0xFF10B981),
                    onClick = {
                        expanded = false
                        onAddSubFolder()
                    }
                )

                SpeedDialItem(
                    label = "رفع ملف سوفت من الهاتف 💾",
                    icon = Icons.Default.UploadFile,
                    backgroundColor = Color(0xFF0284C7),
                    onClick = {
                        expanded = false
                        onUploadDump()
                    }
                )

                SpeedDialItem(
                    label = "إضافة سوفتوير يدوياً 📄",
                    icon = Icons.Default.Add,
                    backgroundColor = Color(0xFF0F766E),
                    onClick = {
                        expanded = false
                        onAddDumpManual()
                    }
                )
            } else {
                // Root Actions
                SpeedDialItem(
                    label = "إضافة برنامج برمجة 💻",
                    icon = Icons.Default.Computer,
                    backgroundColor = Color(0xFF0F766E),
                    onClick = {
                        expanded = false
                        onAddSoftware()
                    }
                )

                SpeedDialItem(
                    label = "رفع فلاشة / سوفت 💾",
                    icon = Icons.Default.UploadFile,
                    backgroundColor = Color(0xFF0284C7),
                    onClick = {
                        expanded = false
                        onUploadDump()
                    }
                )

                SpeedDialItem(
                    label = "إضافة مجلد رئيسي جديد 📁",
                    icon = Icons.Default.CreateNewFolder,
                    backgroundColor = Color(0xFF10B981),
                    onClick = {
                        expanded = false
                        onAddFolder()
                    }
                )

                SpeedDialItem(
                    label = "إضافة مخطط توصيل مبرمجة 🔌",
                    icon = Icons.Default.Cable,
                    backgroundColor = Color(0xFF8B5CF6),
                    onClick = {
                        expanded = false
                        onAddConnection()
                    }
                )
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = if (expanded) Color(0xFF0F766E) else Color(0xFF0D9488),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(58.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "قائمة الإجراءات السريعة",
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        FloatingActionButton(
            onClick = onClick,
            containerColor = backgroundColor,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =============================================================================
// HELPER: SHARE LOCAL FILE VIA FILEPROVIDER (للكمبيوتر والواتساب وتطبيقات المشاركة)
// =============================================================================
fun shareLocalFile(context: Context, localPath: String, title: String) {
    try {
        val file = File(localPath)
        if (!file.exists()) {
            Toast.makeText(context, "الملف غير موجود على الجهاز", Toast.LENGTH_SHORT).show()
            return
        }
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "ملف سوفت وير / برمجة من تطبيق البراء إلكترونيك ⚡\n$title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "إرسال الملف للكمبيوتر أو مشاركته 💻"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

