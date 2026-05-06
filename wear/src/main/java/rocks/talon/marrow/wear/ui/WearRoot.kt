package rocks.talon.marrow.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import rocks.talon.marrow.shared.Section
import rocks.talon.marrow.wear.WearViewModel

@Composable
fun WearRoot() {
    MaterialTheme(motionScheme = MotionScheme.expressive()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppScaffold(timeText = { TimeText() }) {
                val nav = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(
                    navController = nav,
                    startDestination = "list",
                ) {
                    composable("list") { SectionListScreen(nav) }
                    composable("detail/{idx}") { entry ->
                        val idx = entry.arguments?.getString("idx")?.toIntOrNull() ?: 0
                        SectionDetailScreen(idx)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionListScreen(nav: NavHostController) {
    val vm: WearViewModel = viewModel()
    val snapshot by vm.snapshot.collectAsState()
    val pingState by vm.pingState.collectAsState()
    val state = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = state,
        edgeButton = {
            EdgeButton(
                onClick = { vm.ping() },
                buttonSize = EdgeButtonSize.Medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    when (pingState) {
                        WearViewModel.PingState.IDLE -> "Ping phone"
                        WearViewModel.PingState.SENDING -> "Sending…"
                        WearViewModel.PingState.SENT -> "Sent"
                        WearViewModel.PingState.FAILED -> "No phone"
                    },
                )
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, transformSpec),
                ) { Text("Marrow", color = MaterialTheme.colorScheme.primary) }
            }
            val sections = snapshot?.sections.orEmpty()
            if (sections.isEmpty()) {
                item {
                    Box(modifier = Modifier.transformedHeight(this, transformSpec)) {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Collecting…", modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            } else {
                itemsIndexed(sections, key = { _, s -> s.id }) { idx, section ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this@itemsIndexed, transformSpec),
                    ) {
                        SectionCard(section) { nav.navigate("detail/$idx") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(section: Section, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (section.preview.isNotEmpty()) {
                Text(
                    section.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun SectionDetailScreen(index: Int) {
    val vm: WearViewModel = viewModel()
    val snapshot by vm.snapshot.collectAsState()
    val state = rememberTransformingLazyColumnState()
    val transformSpec = rememberTransformationSpec()
    val section = snapshot?.sections?.getOrNull(index)

    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, transformSpec)) {
                    Text(
                        section?.title ?: "Detail",
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (section == null) {
                item {
                    Box(modifier = Modifier.transformedHeight(this, transformSpec)) {
                        Text("Not available", modifier = Modifier.padding(8.dp))
                    }
                }
            } else {
                itemsIndexed(section.rows, key = { i, _ -> "row-$i" }) { _, row ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this@itemsIndexed, transformSpec),
                    ) {
                        Card(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    row.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    row.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
