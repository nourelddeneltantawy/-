package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

    val newPermissions = remember { mutableStateListOf("all") }
    var editingAdmin by remember { mutableStateOf<AdminRecord?>(null) }

    Scaffold(
        modifier = modifier.testTag("manage_admins_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "إدارة المشرفين وتحديد الصلاحيات 🛡️",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "إضافة مشرفين معتمدين وتحديد الأقسام المسموح لهم بتعديلها",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Add new admin card with Granular Permissions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                        Text(
                            text = "إضافة مشرف جديد وتحديد صلاحياته",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                                        Text(opt.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(opt.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val emailClean = newEmail.trim().lowercase()
                            if (emailClean.isBlank() || !emailClean.contains("@") || !emailClean.contains(".")) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("يرجى إدخال بريد إلكتروني صالح")
                                }
                                return@Button
                            }
                            isSubmitting = true
                            onAddAdmin(emailClean, newPermissions.toList()) { success, msg ->
                                isSubmitting = false
                                if (success) {
                                    newEmail = ""
                                    newPermissions.clear()
                                    newPermissions.add("all")
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
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

            // Section 2: Registered Admins List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المشرفون المعتمدون (${admins.size}) 🛡️",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }

            if (admins.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("جاري مزامنة قائمة المشرفين من السحاب...", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(admins, key = { it.email }) { admin ->
                        val isProtected = AdminRepository.isSuperAdminEmail(admin.email)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isProtected) Color(0xFFEAB308).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isProtected) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (isProtected) Color(0xFFCA8A04) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

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

                                    if (!isProtected) {
                                        IconButton(
                                            onClick = { editingAdmin = admin },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل الصلاحيات", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                onRemoveAdmin(admin.email) { _, msg ->
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(msg)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف المشرف",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Permissions Pills
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

        AlertDialog(
            onDismissRequest = { editingAdmin = null },
            title = {
                Text(
                    text = "تعديل صلاحيات المشرف (${admin.email.substringBefore("@")})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("اختر الأقسام المسموح له بتعديلها:", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))

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
                                    Text(opt.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(opt.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val adminTarget = admin.email
                        val perms = editPermissions.toList()
                        editingAdmin = null
                        onUpdatePermissions(adminTarget, perms) { success, msg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    }
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAdmin = null }) {
                    Text("إلغاء")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }
}
