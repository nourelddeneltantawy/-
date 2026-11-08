package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BottomNavManager
import com.example.data.model.BottomNavCustomization

@Composable
fun AdminBottomNavDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tabsMap by BottomNavManager.tabs.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إدارة الشريط السفلي والتبويبات ⚙️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "يمكنك كمسؤول إخفاء أو إظهار أي تبويب في الشريط السفلي عن المستخدمين العاديين وتشغيله في أي وقت:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                // Tab 1: Marketplace (سوق الكروت والبيع والشراء)
                val marketTab = tabsMap[BottomNavManager.TAB_MARKETPLACE]
                    ?: BottomNavCustomization(id = BottomNavManager.TAB_MARKETPLACE, title = "سوق الكروت والصيانة")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (marketTab.isHidden) Color(0xFFEF4444).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (marketTab.isHidden) Color(0xFFEF4444).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = if (marketTab.isHidden) Color(0xFFEF4444) else Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("سوق الكروت (البيع والشراء)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = if (marketTab.isHidden) "مغلق ومخفي عن المستخدمين 🔒" else "متاح ونشط لجميع المستخدمين ✓",
                                        fontSize = 11.sp,
                                        color = if (marketTab.isHidden) Color(0xFFEF4444) else Color(0xFF10B981)
                                    )
                                }
                            }

                            Switch(
                                checked = !marketTab.isHidden,
                                onCheckedChange = { isVisible ->
                                    BottomNavManager.setTabVisibility(context, BottomNavManager.TAB_MARKETPLACE, !isVisible) { success ->
                                        if (success) {
                                            val state = if (isVisible) "تفعيل وإظهار" else "إخفاء وقفل"
                                            Toast.makeText(context, "تم $state سوق الكروت بنجاح", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Tab 2: Home / Welcome (شاشة البدء)
                val homeTab = tabsMap[BottomNavManager.TAB_HOME]
                    ?: BottomNavCustomization(id = BottomNavManager.TAB_HOME, title = "شاشة البدء")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (homeTab.isHidden) Color(0xFFEF4444).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (homeTab.isHidden) Color(0xFFEF4444).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (homeTab.isHidden) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("شاشة البدء والترحيب", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = if (homeTab.isHidden) "مخفية عن المستخدمين" else "ظاهرة للجميع",
                                        fontSize = 11.sp,
                                        color = if (homeTab.isHidden) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Switch(
                                checked = !homeTab.isHidden,
                                onCheckedChange = { isVisible ->
                                    BottomNavManager.setTabVisibility(context, BottomNavManager.TAB_HOME, !isVisible) { success ->
                                        if (success) {
                                            val state = if (isVisible) "إظهار" else "إخفاء"
                                            Toast.makeText(context, "تم $state شاشة البدء", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}
