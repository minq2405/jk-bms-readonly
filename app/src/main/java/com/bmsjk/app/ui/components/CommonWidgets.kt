package com.bmsjk.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.theme.OledTokens

@Composable
fun OledCard(
    modifier: Modifier = Modifier,
    borderColor: Color = OledTokens.CardBorder,
    containerColor: Color = OledTokens.Card,
    contentPadding: PaddingValues = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(OledTokens.CardRadius),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun CardLabelRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OledTokens.Muted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = OledTokens.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}

@Composable
fun LiveDot(
    active: Boolean,
    size: Dp = 8.dp
) {
    val color = if (active) OledTokens.ChargeGreen else OledTokens.Muted
    Box(
        modifier = Modifier
            .size(size)
            .shadow(if (active) 6.dp else 0.dp, CircleShape, ambientColor = color, spotColor = color)
            .background(color, CircleShape)
    )
}

@Composable
fun ReadOnlyPill(
    label: String,
    compact: Boolean = false
) {
    Surface(
        color = OledTokens.CardElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, OledTokens.CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 7.dp else 8.dp,
                vertical = 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = AppIcons.Lock,
                contentDescription = null,
                tint = OledTokens.Muted,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                color = OledTokens.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    textColor: Color = OledTokens.Text,
    containerColor: Color = OledTokens.CardElevated,
    borderColor: Color = OledTokens.CardBorder,
    fontSize: TextUnit = 10.5.sp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 4.dp
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

@Composable
fun EmptyTelemetryCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    OledCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.Bluetooth,
                contentDescription = null,
                tint = OledTokens.Accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = OledTokens.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            color = OledTokens.Muted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = OledTokens.Accent,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text(text = actionLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
