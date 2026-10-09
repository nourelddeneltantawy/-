package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.example.ui.components.MotorDiagramCanvas
import com.example.ui.components.WireColorBadge
import com.example.ui.components.parseWireColorToComposeColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorDetailScreen(
    motor: Motor,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onAiGenerateForMotor: (Motor) -> Unit,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedPinIndex by remember { mutableStateOf<Int?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Schematic Canvas, 1: High-Res Photo/AI
    var showFullImageDialog by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.testTag("motor_detail_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = motor.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع للكتالوج"
                        )
                    }
                },
                actions = {
                    // Copy Pinout Text Action
                    IconButton(
                        onClick = {
                            val text = buildString {
                                appendLine("مخطط أطراف: ${motor.name} (${motor.brand} - ${motor.model})")
                                appendLine("النوع: ${motor.motorType}")
                                appendLine("--- الأطراف ---")
                                motor.pinouts.forEach { p ->
                                    appendLine("#${p.pinNumber}: ${p.name} | لون السلك: ${p.wireColor} | ${p.functionDesc}")
                                }
                            }
                            clipboardManager.setText(AnnotatedString(text))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("تم نسخ جدول الأطراف إلى الحافظة")
                            }
                        },
                        modifier = Modifier.testTag("copy_pinout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ الأطراف"
                        )
                    }

                    // Edit in Admin (Admins Only)
                    if (isAdmin) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.testTag("edit_motor_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "تعديل الماتور"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header: Model, Brand & Specifications summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = motor.brand,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "الموديل: ${motor.model}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${motor.pinouts.size} أطراف",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (motor.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = motor.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Top Section: Interactive Diagram View Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("مخطط التوصيل التفاعلي") },
                    icon = { Icon(Icons.Default.ElectricBolt, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("صورة الماتور / AI") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Display selected diagram view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (selectedTab == 0) {
                    MotorDiagramCanvas(
                        pinouts = motor.pinouts,
                        selectedPinIndex = selectedPinIndex,
                        onSelectPin = { selectedPinIndex = it }
                    )
                } else {
                    // High-resolution image/AI diagram view with zoom capability
                    HighResImageViewer(
                        imageUrl = motor.imageUrl,
                        isAdmin = isAdmin,
                        onExpandClick = { showFullImageDialog = true },
                        onGenerateAiClick = { onAiGenerateForMotor(motor) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Section: The Ordered Pinout Data Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جدول أطراف الماتور (ترتيب تسلسلي)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (isAdmin) {
                    TextButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إعادة الترتيب والتعديل", fontSize = 12.sp)
                    }
                }
            }

            // Sequential Pinout Data Sheet List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(motor.pinouts) { index, pin ->
                    val isSelected = selectedPinIndex == index
                    PinoutDataSheetRow(
                        pin = pin,
                        isSelected = isSelected,
                        onClick = {
                            selectedPinIndex = if (isSelected) null else index
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Modal Expand Dialog for Image Pinch to Zoom
    if (showFullImageDialog) {
        Dialog(
            onDismissRequest = { showFullImageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                ZoomableImageContent(imageUrl = motor.imageUrl)
                Button(
                    onClick = { showFullImageDialog = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Text("إغلاق")
                }
            }
        }
    }
}

@Composable
fun PinoutDataSheetRow(
    pin: Pinout,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val wireColor = parseWireColorToComposeColor(pin.wireColor, pin.colorHex)
    val technicalIcon = getTechnicalPinIcon(pin.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pinout_row_${pin.pinNumber}")
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pin Number Badge
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#${pin.pinNumber}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Pin Technical Icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = wireColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = technicalIcon,
                        contentDescription = null,
                        tint = wireColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Pin Name and Technical Function
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pin.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                if (pin.functionDesc.isNotBlank()) {
                    Text(
                        text = pin.functionDesc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stylized Wire Color Badge
            WireColorBadge(
                wireColor = pin.wireColor,
                fallbackHex = pin.colorHex
            )
        }
    }
}

fun getTechnicalPinIcon(pinName: String): ImageVector {
    val name = pinName.lowercase()
    return when {
        name.contains("تاكو") || name.contains("tacho") || name.contains("fg") || name.contains("hall") -> Icons.Default.Sensors
        name.contains("شربون") || name.contains("brush") -> Icons.Default.FlashOn
        name.contains("ملف") || name.contains("stator") || name.contains("rotor") -> Icons.Default.Loop
        name.contains("أوفرلود") || name.contains("thermal") || name.contains("حماية") -> Icons.Default.Shield
        name.contains("فاز") || name.contains("phase") || name.contains("u") || name.contains("v") || name.contains("w") -> Icons.Default.Power
        else -> Icons.Default.ElectricBolt
    }
}

@Composable
fun HighResImageViewer(
    imageUrl: String,
    isAdmin: Boolean = false,
    onExpandClick: () -> Unit,
    onGenerateAiClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageUrl.startsWith("data:image") || (imageUrl.length > 200 && !imageUrl.startsWith("http"))) {
                // Base64 AI generated image
                val base64Clean = if (imageUrl.contains(",")) imageUrl.substringAfter(",") else imageUrl
                val bitmap = remember(base64Clean) {
                    try {
                        val bytes = Base64.decode(base64Clean, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "مخطط الماتور الفائق الدقة",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DefaultMotorPlaceholder(isAdmin, onGenerateAiClick)
                }
            } else if (imageUrl.startsWith("http")) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "صورة الماتور",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DefaultMotorPlaceholder(isAdmin, onGenerateAiClick)
            }

            // Expand Button
            IconButton(
                onClick = onExpandClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "تكبير الصورة للشاشة كاملة",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun DefaultMotorPlaceholder(isAdmin: Boolean, onGenerateAiClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isAdmin) Icons.Default.AutoAwesome else Icons.Default.Info,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAdmin) "يمكنك توليد مخطط احترافي لهذا الماتور بالذكاء الاصطناعي" else "المخطط الصوري التوضيحي قيد المراجعة الفنية",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (isAdmin) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGenerateAiClick,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("توليد مخطط بالذكاء الاصطناعي (Gemini)")
            }
        }
    }
}

@Composable
fun ZoomableImageContent(imageUrl: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 5f)
        offset += panChange
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(transformState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.startsWith("data:image") || (imageUrl.length > 200 && !imageUrl.startsWith("http"))) {
            val base64Clean = if (imageUrl.contains(",")) imageUrl.substringAfter(",") else imageUrl
            val bitmap = remember(base64Clean) {
                try {
                    val bytes = Base64.decode(base64Clean, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (imageUrl.startsWith("http")) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
