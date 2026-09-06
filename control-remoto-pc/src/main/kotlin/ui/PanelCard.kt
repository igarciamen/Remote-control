package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        backgroundColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
        elevation = 0.dp
    ) {
        val scrollState = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 20.dp)
                    .verticalScroll(scrollState),
                content = content
            )

            CompositionLocalProvider(
                LocalScrollbarStyle provides ScrollbarStyle(
                    minimalHeight = 32.dp,
                    thickness = 7.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 200,
                    unhoverColor = AccentSecondary.copy(alpha = 0.55f),
                    hoverColor = AccentSecondary
                )
            ) {
                VerticalScrollbar(
                    adapter = androidx.compose.foundation.rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                )
            }
        }
    }
}