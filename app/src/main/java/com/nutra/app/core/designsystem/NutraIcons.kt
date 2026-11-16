package com.nutra.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.nutra.app.R

/**
 * Local vector icons for symbols outside material-icons-core so the 35MB
 * material-icons-extended library does not bloat debug dexing or startup.
 */
object NutraIcons {
    val InsightsOutlined: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_insights_outlined)

    val InsightsRounded: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_insights_rounded)

    val Remove: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_remove_rounded)

    val MonitorWeight: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_monitor_weight_rounded)

    val WaterDrop: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_water_drop_rounded)

    val CalendarMonth: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_calendar_month_rounded)

    val WbSunny: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_wb_sunny_rounded)

    val Restaurant: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_restaurant_rounded)

    val DinnerDining: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_dinner_dining_rounded)

    val LocalCafe: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_local_cafe_rounded)

    val ExpandLess: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_expand_less_rounded)

    val ExpandMore: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_expand_more_rounded)

    val DeleteOutline: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete_outline_rounded)

    val SearchOff: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_search_off_rounded)

    val AutoAwesome: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome_rounded)

    val PhotoCamera: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_photo_camera_rounded)

    val QrCodeScanner: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_qr_code_scanner_rounded)

    val FileDownload: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_file_download_rounded)

    val FileUpload: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_file_upload_rounded)
}
