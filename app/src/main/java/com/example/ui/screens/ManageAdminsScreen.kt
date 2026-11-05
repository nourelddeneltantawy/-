package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminRecord
import com.example.data.repository.AdminRepository
import com.example.ui.components.ConfirmDeleteDialog
import kotlinx.coroutines.launch

data class AdminPermissionOption(
    val key: String,
    val title: String,
    val description: String
)

val AVAILABLE_ADMIN_PERMISSIONS = listOf(
    AdminPermissionOption("all", "كامل الصلاحيات (وصول شامل) 👑", "التحكم في كافة أقسام التطبيق بدون قيود"),
    AdminPermissionOption("motors", "مواتير الغسالات ومخططات الأطراف ⚡", "إضافة وتعديل وحذف المواتير ورسم الأطراف"),
    AdminPermissionOption("harnesses", "الضفائر وتوصيلات الكروت 🔌", "تعديل وحفظ ضفائر الغسالات"),
    AdminPermissionOption("parts", "لوكات الباب والليفلات والأجزاء 🔒", "إدارة أجزاء الغسالة والفحوصات"),
    AdminPermissionOption("programming", "البرمجة والسوفت وتراخيص العملاء 💻", "إضافة السوفتات وتوليد مفاتيح التفعيل"),
    AdminPermissionOption("settings", "بيانات المركز وإعدادات التواصل ⚙️", "تحديث أرقام الهاتف والفيسبوك والموقع")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageAdminsScreen(
    admins: List<AdminRecord>,
    onAddAdmin: (String, List<String>, (Boolean, String) -> Unit) -> Unit,
    onRemoveAdmin: (String, (Boolean, String) -> Unit) -> Unit,
    onUpdatePermissions: (String, List<String>, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var newEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isAddFormOpen by remember { mutableStateOf(false) }

    val newPermissions = remember { mutableStateListOf("all") }
    var editingAdmin by remember { mutableStateOf<AdminRecord?>(null) }
    var deletingAdmin by remember { mutableStateOf<AdminRecord?>(null) }

    Scaffold(
        modifier = modifier.testTag("manage_admins_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "إدارة المشرفين والصلاحيات 🛡️",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "إضافة وتعديل وحذف المشرفين المعتمدين بالبريد الإلكتروني",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Header for Registered Admins (PROMINENT AT TOP)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "المشرفون المعتمدون (${admins.size}) 🛡️",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            )
                            Text(
                                text = "المشرفون المصرح لهم بالتحكم والتعديل في التطبيق",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Button(
                        onClick = { isAddFormOpen = !isAddFormOpen },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isAddFormOpen) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isAddFormOpen) "إغلاق النموذج" else "إضافة مشرف +", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Section 2: Admins List
            if (admins.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("جاري مزامنة قائمة المشرفين من السحاب...", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                items(admins, key = { it.email }) { admin ->
                    val isProtected = AdminRepository.isSuperAdminEmail(admin.email)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isProtected) Color(0xFFEAB308).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isProtected) Color(0xFFEAB308).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isProtected) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isProtected) Color(0xFFCA8A04) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = admin.email,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    )
                                    Text(
                                        text = if (isProtected) "👑 مشرف رئيسي (Super Admin)" else "🛡️ مشرف معتمد (Admin)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isProtected) Color(0xFFCA8A04) else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }

                            // Permissions Badges
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val currentPerms = if (isProtected || admin.permissions.contains("all") || admin.permissions.isEmpty()) {
                                    listOf("all")
                                } else {
                                    admin.permissions
                                }

                                currentPerms.forEach { pKey ->
                                    val opt = AVAILABLE_ADMIN_PERMISSIONS.firstOrNull { it.key == pKey }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (pKey == "all") Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (pKey == "all") Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = opt?.title?.substringBefore("(") ?: pKey,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pKey == "all") Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Action Buttons (Only for non-super admins)
                            if (!isProtected) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingAdmin = admin },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تعديل الصلاحيات ✏️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { deletingAdmin = admin },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حذف 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Add New Admin Form (Collapsible / Toggleable)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAddFormOpen = !isAddFormOpen },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "إضافة مشرف جديد وتحديد صلاحياته ➕",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    )
                                    Text(
                                        text = if (isAddFormOpen) "انقر للطي والإخفاء" else "انقر لفتح نموذج إضافة مشرف جديد وتحديد أقسامه",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            IconButton(onClick = { isAddFormOpen = !isAddFormOpen }) {
                                Icon(
                                    imageVector = if (isAddFormOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        }

                        AnimatedVisibility(visible = isAddFormOpen) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedTextField(
                                    value = newEmail,
                                    onValueChange = { newEmail = it },
                                    label = { Text("البريد الإلكتروني لحساب Google للمشرف *") },
                                    placeholder = { Text("engineer@gmail.com") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text("صلاحيات المشرف في أقسام التطبيق:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AVAILABLE_ADMIN_PERMISSIONS.forEach { opt ->
                                        val isChecked = newPermissions.contains(opt.key) || (opt.key != "all" && newPermissions.contains("all"))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (opt.key == "all") {
                                                        newPermissions.clear()
                                                        newPermissions.add("all")
                                                    } else {
                                                        newPermissions.remove("all")
                                                        if (newPermissions.contains(opt.key)) {
                                                            newPermissions.remove(opt.key)
                                                            if (newPermissions.isEmpty()) newPermissions.add("motors")
                                                        } else {
                                                            newPermissions.add(opt.key)
                                                        }
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        if (opt.key == "all") {
                                                            newPermissions.clear()
                                                            newPermissions.add("all")
                                                        } else {
                                                            newPermissions.remove("all")
                                                            if (it) newPermissions.add(opt.key) else newPermissions.remove(opt.key)
                                                        }
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Column {
                                                    Text(opt.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    Text(opt.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        val emailClean = newEmail.trim().lowercase()
                                        if (emailClean.isBlank() || !emailClean.contains("@") || !emailClean.contains(".")) {
                                            coroutineScope.launch { snackbarHostState.showSnackbar("يرجى إدخال بريد إلكتروني صالح") }
                                            return@Button
                                        }
                                        isSubmitting = true
                                        onAddAdmin(emailClean, newPermissions.toList()) { success, msg ->
                                            isSubmitting = false
                                            if (success) {
                                                newEmail = ""
                                                newPermissions.clear()
                                                newPermissions.add("all")
                                                isAddFormOpen = false
                                            }
                                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.align(Alignment.End),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSubmitting) "جاري الإضافة..." else "إضافة المشرف بالصلاحيات المحددة")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Edit Admin Permissions
    editingAdmin?.let { admin ->
        var editPermissions = remember(admin) {
            mutableStateListOf<String>().apply {
                if (admin.permissions.isEmpty()) add("all") else addAll(admin.permissions)
            }
        }
        var isSavingEdit by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) editingAdmin = null },
            title = {
                Text(
                    text = "تعديل صلاحيات المشرف 🛡️",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "المشرف: ${admin.email}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                    Text("حدد الأقسام المسموح له بتعديلها:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AVAILABLE_ADMIN_PERMISSIONS.forEach { opt ->
                            val isChecked = editPermissions.contains(opt.key) || (opt.key != "all" && editPermissions.contains("all"))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (opt.key == "all") {
                                            editPermissions.clear()
                                            editPermissions.add("all")
                                        } else {
                                            editPermissions.remove("all")
                                            if (editPermissions.contains(opt.key)) {
                                                editPermissions.remove(opt.key)
                                                if (editPermissions.isEmpty()) editPermissions.add("motors")
                                            } else {
                                                editPermissions.add(opt.key)
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = {
                                            if (opt.key == "all") {
                                                editPermissions.clear()
                                                editPermissions.add("all")
                                            } else {
                                                editPermissions.remove("all")
                                                if (it) editPermissions.add(opt.key) else editPermissions.remove(opt.key)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(opt.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(opt.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
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
                        isSavingEdit = true
                        onUpdatePermissions(admin.email, editPermissions.toList()) { success, msg ->
                            isSavingEdit = false
                            if (success) {
                                editingAdmin = null
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    },
                    enabled = !isSavingEdit
                ) {
                    Text(if (isSavingEdit) "جاري الحفظ..." else "حفظ الصلاحيات ✓")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAdmin = null }, enabled = !isSavingEdit) {
                    Text("إلغاء")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal: Delete Admin Confirmation with ConfirmDeleteDialog
    if (deletingAdmin != null) {
        ConfirmDeleteDialog(
            title = "تأكيد حذف المشرف ⚠️",
            message = "هل تود بالتأكيد إزالة صلاحيات الإشراف عن الحساب (${deletingAdmin!!.email})؟ لن يتمكن من تعديل أو إضافة أي بيانات بعد الآن.",
            onConfirm = {
                val email = deletingAdmin!!.email
                deletingAdmin = null
                onRemoveAdmin(email) { _, msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                }
            },
            onDismiss = { deletingAdmin = null }
        )
    }
}
