package com.nicktj.autoledger.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.ui.theme.LocalExtra

/**
 * 统一设计系统（Kit）。
 *
 * 全应用只用这一套组件，取代之前分散的 SoftCard / Group / Pill / IconTile 等写法。
 * 规则：
 *  - 页面背景 = surface；内容卡 = 白色（深色为 surfaceContainer）、22dp 圆角、无描边
 *  - 间距只用 8 的倍数：8 / 12 / 16 / 20 / 24
 *  - 字号只用 Material 3 的 typography 角色，不手写 sp
 *  - 金额一律 TabularNums
 */
object Kit {
    val PageHorizontal: Dp = 20.dp
    val CardRadius: Dp = 22.dp
    val CardPadding: Dp = 16.dp
    val Gap: Dp = 12.dp
    val SectionGap: Dp = 24.dp
}

/** 内容卡：统一圆角、底色与阴影。 */
@Composable
fun KitCard(
    modifier: Modifier = Modifier,
    padding: Dp = Kit.CardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val extra = LocalExtra.current
    val shape = RoundedCornerShape(Kit.CardRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (extra.dark) Modifier else Modifier.shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = extra.cardShadow,
                    spotColor = extra.cardShadow,
                ),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) {
        content()
    }
}

/** 页头：大标题 + 可选的右侧操作。 */
@Composable
fun KitHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (action != null) action()
    }
}

/** 分组标题：小字 + 可选计数。全应用统一样式。 */
@Composable
fun KitSection(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

/** 圆形图标按钮（统一的操作入口）。 */
@Composable
fun KitIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/** 小胶囊标签（状态、类型）。 */
@Composable
fun KitTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** 列表行：左图标 + 中间标题/副标题 + 右侧数值。 */
@Composable
fun KitRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 分割线，用于卡片内列表。 */
@Composable
fun KitDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 圆形彩色图标底座（统一替代 IconTile）。 */
@Composable
fun KitIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * 预算状态配色（全应用统一）：正常=成功绿，接近超支=警告黄，超支=危险红。
 */
@Composable
fun budgetLevelColor(level: com.nicktj.autoledger.core.domain.BudgetLevel): Color {
    val extra = LocalExtra.current
    return when (level) {
        com.nicktj.autoledger.core.domain.BudgetLevel.NORMAL -> extra.success
        com.nicktj.autoledger.core.domain.BudgetLevel.WARNING -> extra.warning
        com.nicktj.autoledger.core.domain.BudgetLevel.OVER -> extra.danger
    }
}
