package com.example.task1.components

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Image
import com.tencent.kuikly.compose.resources.DrawableResource
import com.tencent.kuikly.compose.resources.InternalResourceApi
import com.tencent.kuikly.compose.resources.painterResource
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.core.base.attr.ImageUri

@OptIn(InternalResourceApi::class)
fun commonIcon(name: String): DrawableResource =
    // 资源名必须带扩展名（assets 按文件名精确解析）。图标统一用透明 PNG：
    // 如 "sparkles" -> assets://common/sparkles.png。调用方仍传裸名即可。
    DrawableResource(ImageUri.commonAssets("$name.png").toUrl(""))

/**
 * Renders a transparent PNG icon glyph from assets/common/<name>.png.
 * 图标资产须为干净、透明底、颜色正确的 PNG 字形（由 Figma 导出字形 layer 得到，格式选 PNG/2x/透明）。
 */
@Composable
fun AppIcon(name: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(commonIcon(name)),
        contentDescription = null,
        modifier = modifier,
    )
}
