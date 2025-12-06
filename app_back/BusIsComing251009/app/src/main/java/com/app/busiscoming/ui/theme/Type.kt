package com.app.busiscoming.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    // Display - 약시자분들을 위해 크게 증가
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 80.sp,  // 57 -> 80
        lineHeight = 90.sp, // 64 -> 90
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 65.sp,  // 45 -> 65
        lineHeight = 75.sp, // 52 -> 75
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 50.sp,  // 36 -> 50
        lineHeight = 60.sp, // 44 -> 60
        letterSpacing = 0.sp
    ),
    
    // Headline - 약시자분들을 위해 크게 증가
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,  // 32 -> 45
        lineHeight = 55.sp, // 40 -> 55
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,  // 28 -> 40
        lineHeight = 50.sp, // 36 -> 50
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 35.sp,  // 24 -> 35
        lineHeight = 45.sp, // 32 -> 45
        letterSpacing = 0.sp
    ),
    
    // Title - 약시자분들을 위해 크게 증가
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,  // 22 -> 32
        lineHeight = 40.sp, // 28 -> 40
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,  // 18 -> 28
        lineHeight = 35.sp, // 24 -> 35
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,  // 16 -> 24
        lineHeight = 30.sp, // 22 -> 30
        letterSpacing = 0.1.sp
    ),
    
    // Body - 약시자분들을 위해 크게 증가
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,  // 16 -> 24
        lineHeight = 35.sp, // 24 -> 35
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,  // 14 -> 22
        lineHeight = 30.sp, // 20 -> 30
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,  // 12 -> 20
        lineHeight = 25.sp, // 16 -> 25
        letterSpacing = 0.4.sp
    ),
    
    // Label - 약시자분들을 위해 크게 증가
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,  // 14 -> 22
        lineHeight = 30.sp, // 20 -> 30
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,  // 12 -> 20
        lineHeight = 25.sp, // 16 -> 25
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,  // 11 -> 18
        lineHeight = 25.sp, // 16 -> 25
        letterSpacing = 0.5.sp
    )
)
