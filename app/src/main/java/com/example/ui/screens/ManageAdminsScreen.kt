package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAdminsScreen(
    admins: List<AdminRecord>,
    onAddAdmin: (String, (Result<Unit>) -> Unit) -> Unit,
    onRemoveAdmin: (String, (Result<Unit>) -> Unit) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newAdminEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var adminToDelete by remember { mutableStateOf<AdminRecord?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Ensure super admins are shown even before snapshot returns
    val allAdmins = remember(admins) {
        val emailMap = admins.associateBy { it.email.lowercase() }.toMutableMap()
        for (superEmail in AdminRepository.SUPER_ADMIN_EMAILS) {
            if (!emailMap.containsKey(superEmail)) {
                emailMap[superEmail] = AdminRecord(
                    email = superEmail,
                    addedBy = "النظام (Hardcoded)",
                    role = "super_admin"
                )
            }
        }
        emailMap.values.sortedByDescending { it.role == "super_admin" }
    }

    Scaffold(
        modifier = modifier.testTag("manage_admins_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إدارة المشرفين وصلاحيات التطبيق (RBAC)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("manage_admins_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // Super Admin Notice & Security Policy Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "نظام التحكم بالأدوار والصلاحيات (RBAC)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "يمكن للمشرفين فقط إضافة مواتير جديدة وتعديل الأطراف. الزوار والمستخدمون العاديون يتمتعون بصلاحية القراءة فقط.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 1: Add New Admin Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "إضافة مشرف جديد للقائمة البيضاء:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = newAdminEmail,
                            onValueChange = { newAdminEmail = it },
                            label = { Text("البريد الإلكتروني لحساب Google الخاص بالمشرف") },
                            placeholder = { Text("example@gmail.com") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_admin_email_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val email = newAdminEmail.trim()
                                if (email.isBlank() || !email.contains("@")) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("يرجى إدخال بريد إلكتروني صالح")
                                    }
                                    return@Button
                                }

                                isSubmitting = true
                                onAddAdmin(email) { result ->
                                    isSubmitting = false
                                    coroutineScope.launch {
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("تم منح صلاحيات المشرف بنجاح للحساب: $email")
                                            newAdminEmail = ""
                                        } else {
                                            snackbarHostState.showSnackbar("فشلت الإضافة: ${result.exceptionOrNull()?.message}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_add_admin_button"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSubmitting && newAdminEmail.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إضافة إلى قائمة المشرفين المعتمدين", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2: Active Admins Whitelist Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المشرفون النشطون (${allAdmins.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Admin Whitelist Items
            items(allAdmins, key = { it.email }) { admin ->
                AdminUserCard(
                    admin = admin,
                    onDeleteClick = { adminToDelete = admin }
                )
            }
        }
    }

    // Confirmation Dialog for Revoking Admin Rights
    adminToDelete?.let { targetAdmin ->
        AlertDialog(
            onDismissRequest = { adminToDelete = null },
            title = { Text("تأكيد سحب الصلاحيات") },
            text = {
                Text("هل أنت متأكد من رغبتك في إلغاء صلاحيات المشرف للمستخدم: ${targetAdmin.email}؟ سيتحول حسابه فوراً إلى وضع القراءة فقط.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = targetAdmin.email
                        adminToDelete = null
                        onRemoveAdmin(email) { result ->
                            coroutineScope.launch {
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("تم سحب صلاحيات الإشراف من $email")
                                } else {
                                    snackbarHostState.showSnackbar("فشل الإلغاء: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_revoke_admin_button")
                ) {
                    Text("نعم، سحب الصلاحيات")
                }
            },
            dismissButton = {
                TextButton(onClick = { adminToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun AdminUserCard(
    admin: AdminRecord,
    onDeleteClick: () -> Unit
) {
    val isSuper = admin.isProtectedSuperAdmin || admin.role == "super_admin"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_card_${admin.email}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSuper) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSuper) Icons.Default.VerifiedUser else Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = if (isSuper) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = admin.email,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSuper) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isSuper) "مشرف رئيسي (Super Admin)" else "مشرف معتمد (Admin)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuper) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (admin.addedBy.isNotBlank() && !isSuper) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "أضيف بواسطة: ${admin.addedBy}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Action button: Locked badge for protected Super Admins, Delete button for dynamic admins
            if (admin.isProtectedSuperAdmin) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "محمي من الحذف",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "محمي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_admin_button_${admin.email}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "سحب الصلاحية",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
