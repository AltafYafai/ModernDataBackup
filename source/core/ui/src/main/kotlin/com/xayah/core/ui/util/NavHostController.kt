package com.xayah.core.ui.util
import androidx.navigation.NavHostController
fun NavHostController.navigateTo(route: String) = navigate(route)
fun NavHostController.navigateBack() { popBackStack() }
