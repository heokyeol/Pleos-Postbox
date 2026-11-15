package com.pleospostbox.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pleospostbox.car.R
import com.pleospostbox.letter.Letter
import java.time.ZoneId

/**
 * 받은 편지함: 날짜 묶음은 최신 날짜가 위, 같은 날짜 안에서는 오른쪽으로 갈수록 늦게 온 편지.
 * 썸네일 아래 보낸 사람(크게)과 시각(작게).
 */
@Composable
fun ArchiveScreen(
    letters: List<Letter>,
    isUnread: (String) -> Boolean,
    nowMs: Long,
    zone: ZoneId,
    onOpen: (Letter) -> Unit,
    onInvite: () -> Unit,
) {
    val groups = remember(letters, zone) { letters.groupBy { TimeLabels.localDate(it.createdAt, zone) }
        .mapValues { (_, day) -> day.sortedBy { it.createdAt } } }
    val today = TimeLabels.localDate(nowMs, zone)
    Column(Modifier.fillMaxSize().background(Ink.Black)) {
        Row(
            Modifier.padding(start = 64.u, end = 64.u, top = 48.u).fillMaxWidth().height(104.u),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.u),
        ) {
            Brand(height = 28, textSize = 34)
            Box(Modifier.width(2.u).height(40.u).background(Ink.Line))
            Text(stringResource(R.string.archive_title), color = Ink.White, fontSize = 48.su, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(88.u).clip(RoundedCornerShape(50)).background(Ink.Surface)
                    .clickable(onClick = onInvite).padding(horizontal = 40.u),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.invite), color = Ink.White, fontSize = 32.su, fontWeight = FontWeight.SemiBold) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 64.u, end = 64.u, top = 48.u, bottom = 64.u),
            horizontalArrangement = Arrangement.spacedBy(32.u),
            verticalArrangement = Arrangement.spacedBy(40.u),
        ) {
            groups.entries.forEachIndexed { i, (day, items) ->
                item(key = "day-$day", span = { GridItemSpan(maxLineSpan) }) {
                    val (strong, soft) = TimeLabels.dayHeader(day, today)
                    Row(
                        Modifier.padding(top = if (i == 0) 0.u else 16.u),
                        horizontalArrangement = Arrangement.spacedBy(16.u),
                    ) {
                        Text(strong, color = Ink.White, fontSize = 34.su, fontWeight = FontWeight.SemiBold)
                        Text(soft, color = Ink.Dim, fontSize = 34.su, fontWeight = FontWeight.Medium)
                    }
                }
                items(items, key = { it.id }) { l -> ArchiveCard(l, isUnread(l.id), zone) { onOpen(l) } }
            }
        }
    }
}

@Composable
private fun ArchiveCard(letter: Letter, unread: Boolean, zone: ZoneId, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        val thumb = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.u))
        // 안 읽은 편지는 그림을 봉투로 가린다
        if (unread) Envelope(thumb) else LetterCanvas(letter, null, thumb)
        Row(
            Modifier.padding(top = 20.u),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.u),
        ) {
            Text(letter.senderName, color = Ink.White, fontSize = 40.su, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                TimeLabels.clock(letter.createdAt, zone), color = Ink.Dim, fontSize = 32.su, maxLines = 1,
                modifier = Modifier.padding(bottom = 3.u),
            )
            if (unread) UnreadDot(Modifier.align(Alignment.CenterVertically).padding(top = 6.u))
        }
    }
}
