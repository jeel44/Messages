package com.sms.textmessages.messenger.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

////////////////////////////////////////////////////////
// 🔵 SHARED MEDIA SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMediaScreen(
    phoneNumber: String,
    onBack: () -> Unit,
    onMediaClick: (index: Int) -> Unit
) {

    val context = LocalContext.current

    var attachments by remember { mutableStateOf<List<MediaAttachment>>(emptyList()) }

    LaunchedEffect(phoneNumber) {
        attachments = withContext(Dispatchers.IO) {
            loadMmsAttachments(context, phoneNumber)
        }
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Shared media",
                        fontSize = 17.sp,
                        fontFamily = GeneralSansSemiBold,
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {

            if (attachments.isEmpty()) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No shared media",
                        fontSize = 15.sp,
                        fontFamily = GeneralSansMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Photos and videos you send or receive will show up here.",
                        fontSize = 13.sp,
                        fontFamily = GeneralSansMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

            } else {

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    itemsIndexed(attachments, key = { _, item -> item.uri.toString() }) { index, attachment ->

                        MmsThumbnail(
                            attachment = attachment,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onMediaClick(index) }
                        )
                    }
                }
            }
        }
    }
}
