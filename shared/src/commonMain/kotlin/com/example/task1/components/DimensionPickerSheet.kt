package com.example.task1.components

import androidx.compose.runtime.Composable
import com.example.task1.components.core.CheckMark
import com.example.task1.data.GroupDimension
import com.example.task1.theme.AppColors
import com.example.task1.theme.AppShapes
import com.example.task1.theme.AppTypography
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.border
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.text.font.FontWeight
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp

/** 维度 → 图标（zip 图标集）。新增维度时在这里补一行，`when` 是穷尽的，漏了编译不过。 */
private fun dimensionIcon(dim: GroupDimension): String = when (dim) {
    GroupDimension.ACTION -> "ic-group-focus"
    GroupDimension.SIGNAL -> "ic-trend"
    GroupDimension.SCORE -> "ic-valuation"
    GroupDimension.SCENARIO -> "ic-shouyilv"
}

/**
 * 分组维度选择弹层：由「分析智窗」长按呼出，用于切换自选列表的分组维度，选中即回调关闭。
 *
 * 版式：拖拽柄 → 标题 + 一句副标题 → 2×2 磁贴。每格是「图标 + 维度名 + 它到底怎么分」
 * （[GroupDimension.desc]），选中态用靛蓝浅底 + 靛蓝描边 + 角标勾。
 *
 * 顶角圆角由自己画：`ModalBottomSheet` 没有 shape 参数，调用方传透明容器，圆角落在这里
 * （与 AI 抽屉同一套写法）。
 *
 * @param current 当前选中维度
 * @param onSelect 选中某项的回调（传出维度）
 */
@Composable
fun DimensionPickerSheet(current: GroupDimension, onSelect: (GroupDimension) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(AppColors.PageBg, AppShapes.SheetTop)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 22.dp),
    ) {
        Box(
            modifier = Modifier.size(width = 36.dp, height = 4.dp)
                .background(AppColors.HandleGray, AppShapes.Handle)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "选择分组维度", color = AppColors.MainText, fontSize = AppTypography.H3, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "自选列表与智窗建议都按这个维度聚合",
            color = AppColors.SubGray,
            fontSize = AppTypography.Caption,
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 2×2 磁贴：维度不到 4 个也保持两列对齐，尾部用等宽 Spacer 占位（不拉伸最后一格）
        GroupDimension.values().toList().chunked(2).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { dim ->
                    DimensionTile(
                        dim = dim,
                        selected = dim == current,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(dim) },
                    )
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** 单个维度磁贴：选中态换靛蓝浅底 + 靛蓝描边 + 角标勾，未选中是白底发丝描边。 */
@Composable
private fun DimensionTile(
    dim: GroupDimension,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(if (selected) AppColors.AiBg else AppColors.PageBg, AppShapes.Card)
            .border(1.dp, if (selected) AppColors.AiLight else AppColors.Border, AppShapes.Card)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(name = dimensionIcon(dim), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = dim.label,
                color = if (selected) AppColors.RiskText else AppColors.MainText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selected) {
                CheckMark(modifier = Modifier.size(12.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = dim.desc,
            color = AppColors.SubGray,
            fontSize = AppTypography.Caption,
            maxLines = 2,
        )
    }
}
