package com.platinum.ott.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

// Раньше ВСЕ 49 вызовов navigate() в проекте (нижняя панель на телефоне,
// верхняя на TV) были простым navController.navigate("home") без каких-либо
// опций. Для табов (Главная/Избранное/История/Настройки/Поиск/Сериалы) это
// баг: каждый тап создавал НОВЫЙ экземпляр экрана поверх старого в back
// stack вместо того, чтобы вернуться к уже существующему — старый экземпляр
// (со своим ViewModel, со своими загруженными данными) никуда не девался,
// стек только рос. Подтверждено дампом памяти: 11 живых HomeViewModel у
// пользователя, который просто попереключался между вкладками.
//
// launchSingleTop — не плодить дубликат текущего экрана, если он уже сверху.
// popUpTo(startDestination) { saveState = true } — при уходе на другую вкладку
// схлопывать historyстек до начального экрана, а не копить бесконечно.
// restoreState = true — при возврате на вкладку восстанавливать её прежнее
// состояние (скролл и т.п.), а не пересоздавать с нуля.
//
// НЕ применять к "детальной" навигации (detail/{id}, player/{id},
// series/{id}, plugin/{id}) — там каждый переход должен быть отдельной
// записью в стеке, чтобы кнопка "назад" вела на предыдущий фильм/эпизод,
// а не терялась.
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
