package com.sms.textmessages.messenger.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.textmessages.messenger.R
import com.sms.textmessages.messenger.ui.home.generateColorFromName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GeneralSansSemiBold = FontFamily(
    Font(R.font.general_sans_bold, FontWeight.SemiBold)
)

private val GeneralSansMedium = FontFamily(
    Font(R.font.general_sans_medium, FontWeight.Medium)
)

private val AccentBlue = Color(0xFF3E6AE1)

////////////////////////////////////////////////////////
// 🔵 GLOBAL SEARCH SCREEN
////////////////////////////////////////////////////////

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onResultClick: (phoneNumber: String) -> Unit
) {

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        results = if (query.isBlank()) emptyList() else searchAllMessages(context, query)
    }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F1F1), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = Color.Black,
                                fontFamily = GeneralSansMedium
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                if (query.isEmpty()) {
                                    Text(
                                        text = "Search messages",
                                        color = Color.Gray,
                                        fontFamily = GeneralSansMedium
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
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

            when {

                query.isEmpty() -> {
                    EmptySearchState(text = "Search across all your conversations")
                }

                results.isEmpty() -> {
                    EmptySearchState(text = "No messages found for \"$query\"")
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results) { result ->
                            GlobalSearchResultRow(
                                result = result,
                                query = query,
                                onClick = { onResultClick(result.phoneNumber) }
                            )
                        }
                    }
                }
            }
        }
    }
}

////////////////////////////////////////////////////////
// 🔵 EMPTY STATE
////////////////////////////////////////////////////////

@Composable
private fun EmptySearchState(text: String) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = GeneralSansMedium,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

////////////////////////////////////////////////////////
// 🔵 SEARCH RESULT ROW (mirrors HomeScreen's MessageItem layout)
////////////////////////////////////////////////////////

@Composable
private fun GlobalSearchResultRow(
    result: SearchResult,
    query: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        val circleColor = remember(result.contactName) {
            generateColorFromName(result.contactName)
        }

        val initial = result.contactName.trim().firstOrNull()

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial?.uppercase()?.toString() ?: "#",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {

            Text(
                text = result.contactName,
                fontSize = 17.sp,
                fontFamily = GeneralSansSemiBold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = highlightedSnippet(result.snippet, query),
                fontSize = 15.sp,
                fontFamily = GeneralSansMedium,
                color = Color(0xFF5F6368),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatSearchResultTime(result.date),
            fontSize = 13.sp,
            fontFamily = GeneralSansMedium,
            color = Color(0xFF757575)
        )
    }
}

private fun highlightedSnippet(text: String, query: String): AnnotatedString {

    if (query.isBlank()) return AnnotatedString(text)

    return buildAnnotatedString {

        var startIndex = 0

        while (startIndex <= text.length) {

            val matchIndex = text.indexOf(query, startIndex, ignoreCase = true)

            if (matchIndex < 0) {
                append(text.substring(startIndex))
                break
            }

            append(text.substring(startIndex, matchIndex))

            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentBlue)) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }

            startIndex = matchIndex + query.length
        }
    }
}

private fun formatSearchResultTime(timestamp: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
