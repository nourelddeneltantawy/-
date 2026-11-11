package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CommunityMessage
import com.example.data.model.UserProfile
import com.example.data.repository.CommunityGroupRepository
import com.example.data.repository.MarketplaceRepository
import com.example.ui.screens.AuthorProfileDetailDialog
import com.example.util.ImageHelper
import kotlinx.coroutines.launch

/**
 * Professional Telegram / WhatsApp-styled Community Group Chat for Technicians.
 * Features speech bubbles (sender/receiver aligned), quote replies, photo previews,
 * emoji reactions, and smooth real-time streaming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianGroupChatView(
    currentUserId: String,
    currentUserEmail: String?,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    isGuest: Boolean,
    onAuthClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onZoomImage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        CommunityGroupRepository.init(context)
    }

    val messages by CommunityGroupRepository.messages.collectAsState()
    val listState = rememberLazyListState()

    var messageInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var replyingToMessage by remember { mutableStateOf<CommunityMessage?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var profileToView by remember { mutableStateOf<UserProfile?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? -> selectedImageUri = uri }
    )

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Gated for unauthenticated / guest users
    if (isGuest || currentUserEmail.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "جروب الصنايعية والفنيين 💬",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "للمشاركة في الجروب، طرح الاستفسارات، مشاركة صور البوردات والأعطال والتفاعل مع زملائك، يجب تسجيل الدخول بالبريد الإلكتروني وكتابة بياناتك 🔒",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Button(
                        onClick = onAuthClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("تسجيل الدخول بالبريد الإلكتروني 🚀", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val senderName = userProfile?.displayName?.ifBlank { currentUserEmail.substringBefore("@") } ?: currentUserEmail.substringBefore("@")
    val senderAvatar = userProfile?.avatarUrl ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "جروب الصنايعية والفنيين 💬",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "محادثة فنية فورية ومشاركة أعطال",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0B131E)) // WhatsApp/Telegram dark wallpaper background
        ) {
            // Chat Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("👋 مرحباً بك في جروب الفنيين!", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("كن أول من يطرح استفساراً أو يشارك صورة كارتة أو عطل مع زملائك.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderEmail.equals(currentUserEmail, ignoreCase = true)
                        TelegramChatBubble(
                            message = msg,
                            isMe = isMe,
                            currentUserEmail = currentUserEmail,
                            isAdmin = isAdmin,
                            onReply = { replyingToMessage = msg },
                            onDelete = {
                                CommunityGroupRepository.deleteMessage(context, msg.id) {
                                    Toast.makeText(context, "تم حذف الرسالة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleReaction = { emoji ->
                                CommunityGroupRepository.toggleReaction(context, msg.id, emoji, currentUserEmail)
                            },
                            onZoomImage = onZoomImage,
                            onAuthorClick = { key, name, avatar ->
                                coroutineScope.launch {
                                    val fetched = MarketplaceRepository.fetchAuthorProfile(context, key)
                                    profileToView = fetched ?: UserProfile(
                                        userId = key,
                                        email = if (key.contains("@")) key else "",
                                        displayName = name,
                                        avatarUrl = avatar
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Pinned Telegram-style Bottom Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    // Reply quote banner with slide-in animation
                    AnimatedVisibility(
                        visible = replyingToMessage != null,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        replyingToMessage?.let { target ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(28.dp)
                                                .background(Color(0xFF0284C7), RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("الرد على ${target.senderName}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                            Text(target.messageText.ifBlank { "صورة مرفقة 📷" }, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "إلغاء الرد", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Selected Image Preview
                    AnimatedVisibility(visible = selectedImageUri != null) {
                        selectedImageUri?.let { uri ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("صورة مرفقة جاهزة للإرسال 📷", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("اضغط إرسال لمشاركتها في الجروب", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف الصورة", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Main Input Row (WhatsApp / Telegram Styled)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Image attachment button
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "إرفاق صورة",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Text Field (Telegram curved capsule)
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("اكتب استفسارك أو رسالتك للجروب...", fontSize = 12.sp) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        )

                        // Send Button (Telegram Vibrant Blue Circle)
                        val canSend = (messageInput.isNotBlank() || selectedImageUri != null) && !isSending
                        Surface(
                            shape = CircleShape,
                            color = if (canSend) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canSend) {
                                    if (!canSend) return@clickable
                                    isSending = true
                                    val textToSend = messageInput.trim()
                                    val repId = replyingToMessage?.id
                                    val repName = replyingToMessage?.senderName
                                    val repText = replyingToMessage?.messageText

                                    coroutineScope.launch {
                                        var uploadedImageUrl = ""
                                        if (selectedImageUri != null) {
                                            uploadedImageUrl = ImageHelper.saveAndPreparePermanentImage(context, selectedImageUri!!)
                                        }

                                        CommunityGroupRepository.sendMessage(
                                            context = context,
                                            senderId = currentUserId,
                                            senderEmail = currentUserEmail,
                                            senderName = senderName,
                                            senderAvatar = senderAvatar,
                                            messageText = textToSend,
                                            imageUrl = uploadedImageUrl,
                                            replyToId = repId,
                                            replyToName = repName,
                                            replyToText = repText
                                        ) { success ->
                                            isSending = false
                                            if (success) {
                                                messageInput = ""
                                                selectedImageUri = null
                                                replyingToMessage = null
                                            } else {
                                                Toast.makeText(context, "تعذر إرسال الرسالة، تحقق من الاتصال", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: View author profile details with WhatsApp and Call buttons
    if (profileToView != null) {
        AuthorProfileDetailDialog(
            profile = profileToView!!,
            onDismiss = { profileToView = null },
            onWhatsAppClick = { phone ->
                openWhatsApp(context, phone, "السلام عليكم أخي الفني، تواصلت معك عبر جروب الفنيين في تطبيق البراء إلكترونيك")
            },
            onCallClick = { phone ->
                openDialer(context, phone)
            }
        )
    }
}

/**
 * Authentic Telegram / WhatsApp Chat Bubble:
 * - Yours on one side (aligned End / Right in RTL), theirs on the other (aligned Start / Left).
 * - Distinct speech bubble tail corners.
 * - Inline timestamp and double checkmark (✓✓).
 * - Quoted reply preview.
 * - Tap/long-press actions & emoji reactions hanging neatly below the bubble.
 */
@Composable
fun TelegramChatBubble(
    message: CommunityMessage,
    isMe: Boolean,
    currentUserEmail: String,
    isAdmin: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onZoomImage: (String) -> Unit,
    onAuthorClick: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val canDelete = isMe || isAdmin

    // Telegram-inspired bubble colors
    val bubbleColor = if (isMe) Color(0xFF0369A1) else Color(0xFF1E293B)
    val textColor = Color.White
    val bubbleBorder = if (isMe) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF334155).copy(alpha = 0.6f)

    // Speech bubble shape with tail on bottom corner (as demonstrated in namethatui reference)
    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 3.dp // Tail notch
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 3.dp, // Tail notch
            bottomEnd = 16.dp
        )
    }

    var showActionMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.widthIn(max = 330.dp),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                border = BorderStroke(1.dp, bubbleBorder),
                modifier = Modifier
                    .clip(bubbleShape)
                    .clickable { showActionMenu = !showActionMenu }
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    // Sender Profile Header (Clickable Avatar + Name, Facebook comment / Telegram style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onAuthorClick(
                                    message.senderEmail.ifBlank { message.senderId },
                                    message.senderName,
                                    message.senderAvatar
                                )
                            }
                            .padding(bottom = 4.dp, end = 4.dp)
                    ) {
                        if (message.senderAvatar.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ImageHelper.getCoilData(message.senderAvatar))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = message.senderName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, if (isMe) Color.White.copy(alpha = 0.6f) else Color(0xFF38BDF8), CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = if (isMe) Color(0xFF0284C7) else Color(0xFF334155),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = message.senderName.take(1).ifBlank { "ف" },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMe) "${message.senderName} (أنت)" else message.senderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isMe) Color(0xFFBAE6FD) else Color(0xFF38BDF8)
                        )
                    }

                    // Quoted reply snippet
                    if (!message.replyToName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(if (isMe) Color.White else Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMe) Color(0xFFBAE6FD) else Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = message.replyToText ?: "صورة مرفقة",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Attached Photo Preview
                    if (message.imageUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onZoomImage(message.imageUrl) }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ImageHelper.getCoilData(message.imageUrl))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "صورة مرفقة",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Message text
                    if (message.messageText.isNotBlank()) {
                        Text(
                            text = message.messageText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = textColor
                        )
                    }

                    // Inline Timestamp and Delivery Indicators (bottom-end aligned)
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.formattedTime,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        if (isMe) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "تم التسليم",
                                tint = Color(0xFF38BDF8), // Cyan checkmarks like WhatsApp
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reactions cluster directly below the bubble
        if (message.reactions.isNotEmpty() || showActionMenu) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, start = if (isMe) 0.dp else 4.dp, end = if (isMe) 4.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attached reaction chips
                message.reactions.forEach { (emoji, users) ->
                    if (users.isNotEmpty()) {
                        val didIReact = users.contains(currentUserEmail.trim().lowercase())
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (didIReact) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (didIReact) Color(0xFF38BDF8) else Color(0xFF334155)),
                            modifier = Modifier.clickable { onToggleReaction(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${users.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Quick Reaction Picker / Actions when clicked
                if (showActionMenu) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("👍", "❤️", "🔥", "💡", "👏").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            onToggleReaction(emoji)
                                            showActionMenu = false
                                        }
                                        .padding(2.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    onReply()
                                    showActionMenu = false
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Reply, contentDescription = "رد", tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                            }

                            if (canDelete) {
                                IconButton(
                                    onClick = {
                                        onDelete()
                                        showActionMenu = false
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openWhatsApp(context: Context, phone: String, message: String) {
    try {
        var clean = phone.replace(Regex("[^0-9+]"), "")
        if (clean.startsWith("01")) {
            clean = "+2$clean"
        } else if (!clean.startsWith("+") && clean.isNotBlank()) {
            clean = "+2$clean"
        }
        val encodedMsg = java.net.URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=$encodedMsg")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح تطبيق واتساب", Toast.LENGTH_SHORT).show()
    }
}

private fun openDialer(context: Context, phone: String) {
    try {
        val clean = phone.replace(Regex("[^0-9+]"), "")
        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        context.startActivity(intent)
    } catch (_: Throwable) {
        Toast.makeText(context, "تعذر فتح تطبيق الهاتف", Toast.LENGTH_SHORT).show()
    }
}

