package ui.views

import LocalApplicationLocalization
import LocalProfileState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextFieldScrollState
import androidx.compose.foundation.text.rememberTextFieldVerticalScrollState
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import logic.*
import tool.clearAllHtmlTags
import tool.isImageName
import tool.unescapeHtml4
import ui.SvgIcons
import ui.components.ButtonWithIcon
import ui.icons.*

@OptIn(
    FlowPreview::class,
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun RawTextView(
    note: Note,
    optionVisible: Boolean = false,
    changeTip: (String, Long) -> Unit,
) {
    val textFieldFocusRequester = remember { FocusRequester() }
    val textFieldState = remember {
        val rawText = DataStore.loadBlocksAsRawText(note.id)
        mutableStateOf(TextFieldValue(rawText, TextRange(rawText.length)))
    }
    val localApplicationLocalization = LocalApplicationLocalization.current
    val version = remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        snapshotFlow { version.value }
            .distinctUntilChanged { oldValue, newValue ->
                oldValue == newValue
            }
            .filter {
                it > 0L
            }
            .onEach {
                changeTip(localApplicationLocalization.inputting, 0L)
            }
            .debounce(500)
            .onEach {
                DataStore.saveRawTextAsBlocks(note.id, textFieldState.value.text)
                changeTip(localApplicationLocalization.saved, 500L)
            }.launchIn(coroutineScope)
    }
    val localProfileState = LocalProfileState.current
    val noteColor = remember(localProfileState.colorTheme, note.style) {
        getNoteColor(localProfileState.colorTheme, note.style)
    }
    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                when (val dragData = event.dragData()) {
                    is DragData.Text -> {
                        val oldText = textFieldState.value.text
                        val prefix = if (oldText.isNotEmpty() && !oldText.endsWith("\n")) {
                            oldText + "\n"
                        } else {
                            oldText
                        }
                        val newText = prefix + dragData.readText().clearAllHtmlTags().unescapeHtml4()
                        textFieldState.value = textFieldState.value.copy(
                            text = newText,
                            selection = TextRange(newText.length)
                        )
                        if (newText != oldText) {
                            version.value += 1
                        }
                        textFieldFocusRequester.requestFocus()
                    }

                    is DragData.Image -> {
                        textFieldFocusRequester.requestFocus()
                    }

                    is DragData.FilesList -> {
                        val oldText = textFieldState.value.text
                        val prefix = if (oldText.isNotEmpty() && !oldText.endsWith("\n")) {
                            oldText + "\n"
                        } else {
                            oldText
                        }
                        val newText = prefix + dragData.readFiles().map { filePath ->
                            val rawFilePath = if (filePath.startsWith("file:/")) {
                                filePath.substring(6)
                            } else {
                                filePath
                            }
                            if (rawFilePath.isImageName()) {
                                "# image\n${rawFilePath}"
                            } else {
                                rawFilePath
                            }
                        }.fastJoinToString("\n")
                        textFieldState.value = textFieldState.value.copy(
                            text = newText,
                            selection = TextRange(newText.length)
                        )
                        if (newText != oldText) {
                            version.value += 1
                        }
                        textFieldFocusRequester.requestFocus()
                    }
                }
                return true
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dragAndDropTarget)
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val textFieldVerticalScrollState = rememberTextFieldVerticalScrollState(0)
            BasicTextField(
                value = textFieldState.value,
                onValueChange = {
                    val oldText = textFieldState.value.text
                    textFieldState.value = it.copy(text = it.text.replace("\t", " "))
                    val newText = textFieldState.value.text
                    if (newText != oldText) {
                        version.value += 1
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .focusRequester(textFieldFocusRequester),
                scrollState = textFieldVerticalScrollState,
                singleLine = false,
                cursorBrush = SolidColor(noteColor.font),
                textStyle = LocalTextStyle.current.copy(
                    fontWeight = FontWeight(localProfileState.fontWeight),
                    fontSize = localProfileState.fontSize.sp,
                    lineHeight = (localProfileState.fontSize + 8).sp,
                    fontFamily = when (localProfileState.fontFamily) {
                        FontFamily.Serif.name -> FontFamily.Serif
                        FontFamily.SansSerif.name -> FontFamily.SansSerif
                        FontFamily.Monospace.name -> FontFamily.Monospace
                        FontFamily.Cursive.name -> FontFamily.Cursive
                        else -> FontFamily.Default
                    },
                    color = noteColor.font,
                ),
                decorationBox = { innerTextField ->
                    if (textFieldState.value.text.isEmpty()) {
                        Text(
                            text = "${localApplicationLocalization.takeNote}...",
                            color = Color(0xFFA0A0A0),
                            fontWeight = FontWeight(localProfileState.fontWeight),
                            fontSize = localProfileState.fontSize.sp,
                            lineHeight = (localProfileState.fontSize + 8).sp,
                            fontFamily = when (localProfileState.fontFamily) {
                                FontFamily.Serif.name -> FontFamily.Serif
                                FontFamily.SansSerif.name -> FontFamily.SansSerif
                                FontFamily.Monospace.name -> FontFamily.Monospace
                                FontFamily.Cursive.name -> FontFamily.Cursive
                                else -> FontFamily.Default
                            },
                        )
                    }
                    innerTextField()
                }
            )
            VerticalScrollbar(textFieldVerticalScrollState)
        }
        val tipsDialogVisible = remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = tipsDialogVisible.value,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
        ) {
            Dialog(
                onDismissRequest = { tipsDialogVisible.value = false },
            ) {
                Text(
                    localApplicationLocalization.notSupported,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        if (optionVisible) {
            Column(modifier = Modifier.fillMaxWidth().height((39.5).dp)) {
                Divider(thickness = (1.5).dp, color = noteColor.border)
                Row(
                    modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ButtonWithIcon(
                        text = localApplicationLocalization.bold,
                        iconSize = 18.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditB(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ButtonWithIcon(
                        text = localApplicationLocalization.italic,
                        iconSize = 15.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditI(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ButtonWithIcon(
                        text = localApplicationLocalization.underline,
                        iconSize = 16.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditU(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ButtonWithIcon(
                        text = localApplicationLocalization.strikethrough,
                        iconSize = 18.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditS(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ButtonWithIcon(
                        text = localApplicationLocalization.toggleBullets,
                        iconSize = 19.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditList(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    ButtonWithIcon(
                        text = localApplicationLocalization.addImage,
                        iconSize = 17.dp,
                        width = 30.dp,
                        height = 30.dp,
                        icon = SvgIcons.winEditImage(),
                        showTooltip = localProfileState.tooltip,
                        onClick = { tipsDialogVisible.value = true }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.VerticalScrollbar(textFieldVerticalScrollState: TextFieldScrollState) {
    val hoverInteractionSource = remember { MutableInteractionSource() }
    val hovered = hoverInteractionSource.collectIsHoveredAsState()
    val scrollbarVisible = remember { mutableStateOf(false) }
    LaunchedEffect(hovered.value) {
        if (hovered.value) {
            scrollbarVisible.value = true
        } else {
            delay(2000)
            scrollbarVisible.value = false
        }
    }
    Box(
        modifier = Modifier.Companion.align(Alignment.CenterEnd)
            .width(12.dp)
            .fillMaxHeight()
            .background(color = Color.Transparent)
            .hoverable(interactionSource = hoverInteractionSource, enabled = true),
    ) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(textFieldVerticalScrollState),
            modifier = Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .background(color = Color.Transparent),
            style = ScrollbarStyle(
                minimalHeight = 10.dp,
                thickness = if (scrollbarVisible.value) 12.dp else 2.dp,
                shape = RoundedCornerShape(0.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color(0xff8b8b8b),
                hoverColor = Color(0xff636363)
            )
        )
    }
}