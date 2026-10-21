package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commonmark.node.*
import org.commonmark.parser.Parser

private val markdownParser = Parser.builder().build()

@Composable
fun MarkdownText(text: String) {
    val document = remember(text) { runCatching { markdownParser.parse(text) }.getOrNull() }
    if (document == null) Text(text, style = MaterialTheme.typography.bodyMedium)
    else MarkdownBlocks(document)
}

private fun children(node: Node): List<Node> = buildList {
    var child = node.firstChild
    while (child != null) { add(child); child = child.next }
}

@Composable
private fun MarkdownBlocks(parent: Node, depth: Int = 0) {
    // Deeply nested model output falls back to plain inline text.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        children(parent).forEach { node ->
            when {
                depth > 12 -> Text(inlineText(node), style = MaterialTheme.typography.bodyMedium)
                node is Paragraph -> MarkdownInline(node)
                node is Heading -> Text(inlineText(node), style = if (node.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                node is FencedCodeBlock -> CodeBlock(node.literal.trimEnd(), node.info)
                node is IndentedCodeBlock -> CodeBlock(node.literal.trimEnd(), "code")
                node is BulletList || node is OrderedList -> {
                    children(node).forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (node is OrderedList) "${node.markerStartNumber + index}." else "•", style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(1f)) { MarkdownBlocks(item, depth + 1) }
                        }
                    }
                }
                node is BlockQuote -> Box(Modifier.background(Color(0xFF1E293B), RoundedCornerShape(8.dp)).padding(10.dp)) { MarkdownBlocks(node, depth + 1) }
                node is ThematicBreak -> HorizontalDivider()
                node is HtmlBlock -> Text(node.literal, style = MaterialTheme.typography.bodyMedium)
                else -> MarkdownBlocks(node, depth + 1)
            }
        }
    }
}

private fun inlineText(parent: Node): AnnotatedString = buildAnnotatedString {
    fun visit(node: Node, depth: Int = 0) {
        if (depth > 24) return
        when (node) {
            is org.commonmark.node.Text -> append(node.literal)
            is Code -> { pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF263348))); append(node.literal); pop() }
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is HtmlInline -> append(node.literal)
            is StrongEmphasis -> { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); children(node).forEach { visit(it, depth + 1) }; pop() }
            is Emphasis -> { pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); children(node).forEach { visit(it, depth + 1) }; pop() }
            is Link -> {
                pushStringAnnotation("url", node.destination)
                pushStyle(SpanStyle(color = Color(0xFF67E8F9)))
                children(node).forEach { visit(it, depth + 1) }
                pop(); pop()
            }
            else -> children(node).forEach { visit(it, depth + 1) }
        }
    }
    children(parent).forEach { visit(it) }
}

@Composable
private fun MarkdownInline(node: Node) {
    val uriHandler = LocalUriHandler.current
    val content = remember(node) { inlineText(node) }
    ClickableText(content, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), onClick = { offset ->
        content.getStringAnnotations("url", offset, offset).firstOrNull()?.item?.let { url ->
            if (url.startsWith("https://") || url.startsWith("http://")) runCatching { uriHandler.openUri(url) }
        }
    })
}

@Composable
private fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    Surface(color = Color(0xFF070D18), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(language.ifBlank { "code" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { copyText(context, code) }) { Icon(Icons.Default.ContentCopy, "Copy code", modifier = Modifier.size(16.dp)) }
            }
            Text(code, modifier = Modifier.horizontalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
