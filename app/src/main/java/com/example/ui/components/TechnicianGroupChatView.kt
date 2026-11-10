package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CommunityMessage
import com.example.data.model.UserProfile
import com.example.data.repository.CommunityGroupRepository
import com.example.util.ImageHelper
import kotlinx.coroutines.launch

@Composable
fun TechnicianGroupChatView(
    currentUserId: String,
    currentUserEmail: String?,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    isGuest: Boolean,
    onAuthClick: () -> Unit,
    onZoomImage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize CommunityGroupRepository
    LaunchedEffect(Unit) {
        CommunityGroupRepository.init(context)
    }

    val messages by CommunityGroupRepository.messages.collectAsState()
    val listState = rememberLazyListState()

    var messageInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var replyingToMessage by remember { mutableStateOf<CommunityMessage?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            selectedImageUri = uri
        }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Messages Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderEmail.equals(currentUserEmail, ignoreCase = true)
                ChatMessageItem(
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
                    onZoomImage = onZoomImage
                )
            }
        }

        // Pinned Bottom Input Row
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Reply quote banner
                AnimatedVisibility(visible = replyingToMessage != null) {
                    replyingToMessage?.let { target ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
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
                                    Icon(Icons.Default.Reply, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text("الرد على ${target.senderName}:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                        Text(target.messageText.ifBlank { "صورة مرفقة" }, fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Row(
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("صورة مرفقة مع الرسالة", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedImageUri = null }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "حذف الصورة", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Main Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Image picker button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "إرفاق صورة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Text Field
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اكتب استفسارك أو رسالتك للجروب...", fontSize = 12.sp) },
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // Send Button
                    val canSend = (messageInput.isNotBlank() || selectedImageUri != null) && !isSending
                    Button(
                        onClick = {
                            if (!canSend) return@Button
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
                                ) {
                                    isSending = false
                                    messageInput = ""
                                    selectedImageUri = null
                                    replyingToMessage = null
                                }
                            }
                        },
                        enabled = canSend,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(44.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: CommunityMessage,
    isMe: Boolean,
    currentUserEmail: String,
    isAdmin: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onZoomImage: (String) -> Unit
) {
    val canDelete = isMe || isAdmin
    val bubbleColor = if (isMe) Color(0xFF0369A1).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borderColor = if (isMe) Color(0xFF0284C7).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bubbleColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Sender name, email badge, time, and delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isMe) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isMe) Color(0xFF0284C7) else Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = message.senderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isMe) Color(0xFF0284C7) else MaterialTheme.colorScheme.primary
                            )
                            if (isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f)
                                ) {
                                    Text("أنت", fontSize = 8.sp, color = Color(0xFF0284C7), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                        Text(message.formattedTime, fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onReply, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Reply, contentDescription = "رد", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(15.dp))
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Quoted reply if exists
            if (!message.replyToName.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(Color(0xFF0284C7))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("↩️ رداً على ${message.replyToName}:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                            Text(message.replyToText ?: "", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }

            // Message text
            if (message.messageText.isNotBlank()) {
                Text(
                    text = message.messageText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Image attachment
            if (message.imageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onZoomImage(message.imageUrl) }
                ) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "صورة مرفقة",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Quick Reactions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Existing reaction badges
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.forEach { (emoji, users) ->
                        if (users.isNotEmpty()) {
                            val didIReact = users.contains(currentUserEmail.trim().lowercase())
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (didIReact) Color(0xFF0284C7).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (didIReact) Color(0xFF0284C7) else Color.Transparent),
                                modifier = Modifier
                                    .clickable { onToggleReaction(emoji) }
                                    .padding(vertical = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(emoji, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("${users.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Reaction Picker Emojis
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("👍", "❤️", "💡", "🔥").forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onToggleReaction(emoji) }
                        ) {
                            Text(emoji, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}
