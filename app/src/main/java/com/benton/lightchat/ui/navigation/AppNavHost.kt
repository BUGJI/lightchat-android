package com.benton.lightchat.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.benton.lightchat.AppContainer
import com.benton.lightchat.data.local.PrefsState
import com.benton.lightchat.ui.channel.ChannelInfoScreen
import com.benton.lightchat.ui.chats.ChatScreen
import com.benton.lightchat.ui.chats.ChatTarget
import com.benton.lightchat.ui.contacts.AddContactsScreen
import com.benton.lightchat.ui.contacts.FriendsScreen
import com.benton.lightchat.ui.main.MainScreen
import com.benton.lightchat.ui.profile.ChangePasswordScreen
import com.benton.lightchat.ui.profile.EditProfileScreen
import com.benton.lightchat.ui.profile.EmailVerificationScreen
import com.benton.lightchat.ui.profile.NotificationSettingsScreen
import com.benton.lightchat.ui.profile.SessionsScreen
import com.benton.lightchat.ui.profile.SettingsScreen
import com.benton.lightchat.ui.profile.StorageScreen
import com.benton.lightchat.ui.profile.TwoFactorScreen
import com.benton.lightchat.ui.profile.UpdateViewModel
import com.benton.lightchat.ui.profile.UserProfileScreen
import com.benton.lightchat.ui.server.ServerConfigScreen

@Composable
fun MainNavHost(
    container: AppContainer,
    prefs: PrefsState,
    updateViewModel: UpdateViewModel,
) {
    val navController = rememberNavController()
    val baseUrl = prefs.baseUrl

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                baseUrl = baseUrl,
                onOpenChannel = { id, title ->
                    navController.navigate("channel/$id?title=${Uri.encode(title)}")
                },
                onOpenPrivate = { chatId, userId, title ->
                    navController.navigate("private/$chatId/$userId?title=${Uri.encode(title)}")
                },
                onOpenAdd = { navController.navigate("contacts/add") },
                onOpenFriends = { navController.navigate("friends") },
                onOpenSettings = { navController.navigate("settings") },
                onEditProfile = { navController.navigate("profile-edit") },
            )
        }

        composable("contacts/add") {
            AddContactsScreen(
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onOpenPrivate = { chatId, userId, title ->
                    navController.navigate("private/$chatId/$userId?title=${Uri.encode(title)}") {
                        popUpTo("main") { inclusive = false }
                    }
                },
            )
        }

        composable("friends") {
            FriendsScreen(
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onOpenAdd = { navController.navigate("contacts/add") },
                onOpenUserProfile = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenPrivate = { chatId, userId, title ->
                    navController.navigate("private/$chatId/$userId?title=${Uri.encode(title)}") {
                        popUpTo("main") { inclusive = false }
                    }
                },
            )
        }

        composable(
            route = "channel/{id}?title={title}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "频道"
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0
            val title = entry.arguments?.getString("title") ?: "频道"
            ChatScreen(
                target = ChatTarget.Channel(id, title),
                baseUrl = baseUrl,
                currentUserId = prefs.userId,
                onBack = { navController.popBackStack() },
                onOpenChannelInfo = { channelId, channelTitle ->
                    navController.navigate(
                        "channel-info/$channelId?title=${Uri.encode(channelTitle)}",
                    )
                },
            )
        }

        composable(
            route = "channel-info/{id}?title={title}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "群聊"
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0
            val title = entry.arguments?.getString("title") ?: "群聊"
            ChannelInfoScreen(
                channelId = id,
                initialTitle = title,
                baseUrl = baseUrl,
                currentUserId = prefs.userId,
                onBack = { navController.popBackStack() },
                onOpenUserProfile = { userId ->
                    navController.navigate("user/$userId")
                },
                onClosed = {
                    navController.popBackStack("main", inclusive = false)
                },
            )
        }

        composable(
            route = "user/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: 0
            UserProfileScreen(
                userId = userId,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onOpenPrivate = { chatId, otherUserId, title ->
                    navController.navigate(
                        "private/$chatId/$otherUserId?title=${Uri.encode(title)}",
                    )
                },
            )
        }

        composable(
            route = "private/{chatId}/{userId}?title={title}",
            arguments = listOf(
                navArgument("chatId") { type = NavType.LongType },
                navArgument("userId") { type = NavType.LongType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = "私聊"
                },
            ),
        ) { entry ->
            val chatId = entry.arguments?.getLong("chatId") ?: 0
            val userId = entry.arguments?.getLong("userId") ?: 0
            val title = entry.arguments?.getString("title") ?: "私聊"
            ChatScreen(
                target = ChatTarget.Private(chatId, userId, title),
                baseUrl = baseUrl,
                currentUserId = prefs.userId,
                onBack = { navController.popBackStack() },
                onOpenUserProfile = { targetUserId ->
                    navController.navigate("user/$targetUserId")
                },
            )
        }

        composable("settings") {
            SettingsScreen(
                serverUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onChangeServer = { navController.navigate("server") },
                onOpenStorage = { navController.navigate("settings/storage") },
                onOpenNotifications = { navController.navigate("settings/notifications") },
                onOpenPassword = { navController.navigate("settings/password") },
                onOpenSessions = { navController.navigate("settings/sessions") },
                onOpenTwoFactor = { navController.navigate("settings/two-factor") },
                onOpenEmailVerification = { navController.navigate("settings/email") },
                updateViewModel = updateViewModel,
            )
        }

        composable("settings/storage") {
            StorageScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/notifications") {
            NotificationSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/password") {
            ChangePasswordScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/sessions") {
            SessionsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/two-factor") {
            TwoFactorScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings/email") {
            EmailVerificationScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable("server") {
            ServerConfigScreen(
                initialUrl = baseUrl,
                allowBack = true,
                clearSessionOnSave = true,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable("profile-edit") {
            EditProfileScreen(
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
