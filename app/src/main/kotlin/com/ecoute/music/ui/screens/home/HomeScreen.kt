package com.ecoute.music.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.ecoute.music.R
import com.ecoute.music.models.toUiMood
import com.ecoute.music.preferences.UIStatePreferences
import com.ecoute.music.ui.components.themed.Scaffold
import com.ecoute.music.ui.screens.GlobalRoutes
import com.ecoute.music.ui.screens.Route
import com.ecoute.music.ui.screens.albumRoute
import com.ecoute.music.ui.screens.artistRoute
import com.ecoute.music.ui.screens.builtInPlaylistRoute
import com.ecoute.music.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import com.ecoute.music.ui.screens.localPlaylistRoute
import com.ecoute.music.ui.screens.localplaylist.LocalPlaylistScreen
import com.ecoute.music.ui.screens.mood.MoodScreen
import com.ecoute.music.ui.screens.mood.MoreAlbumsScreen
import com.ecoute.music.ui.screens.mood.MoreMoodsScreen
import com.ecoute.music.ui.screens.moodRoute
import com.ecoute.music.ui.screens.pipedPlaylistRoute
import com.ecoute.music.ui.screens.playlistRoute
import com.ecoute.music.ui.screens.searchRoute
import com.ecoute.music.ui.screens.settingsRoute
import com.ecoute.compose.persist.PersistMapCleanup
import com.ecoute.compose.routing.Route0
import com.ecoute.compose.routing.RouteHandler

private val moreMoodsRoute = Route0("moreMoodsRoute")
private val moreAlbumsRoute = Route0("moreAlbumsRoute")

@Route
@Composable
fun HomeScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    RouteHandler {
        GlobalRoutes()

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(playlistId = playlistId)
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(builtInPlaylist = builtInPlaylist)
        }

        moodRoute { mood ->
            MoodScreen(mood = mood)
        }

        moreMoodsRoute {
            MoreMoodsScreen()
        }

        moreAlbumsRoute {
            MoreAlbumsScreen()
        }

        Content {
            Scaffold(
                key = "home",
                topIconButtonId = R.drawable.settings,
                onTopIconButtonClick = { settingsRoute() },
                tabIndex = UIStatePreferences.homeScreenTabIndex,
                onTabChange = { UIStatePreferences.homeScreenTabIndex = it },
                tabColumnContent = {
                    tab(0, R.string.quick_picks, R.drawable.sparkles)
                    tab(1, R.string.discover, R.drawable.globe)
                    tab(2, R.string.songs, R.drawable.musical_notes)
                    tab(3, R.string.playlists, R.drawable.playlist)
                    tab(4, R.string.artists, R.drawable.person)
                    tab(5, R.string.albums, R.drawable.disc)
                    tab(6, R.string.local, R.drawable.download)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    val onSearchClick = { searchRoute("") }
                    when (currentTabIndex) {
                        0 -> QuickPicks(
                            onAlbumClick = { albumRoute(it.key) },
                            onArtistClick = { artistRoute(it.key) },
                            onPlaylistClick = {
                                playlistRoute(
                                    p0 = it.key,
                                    p1 = null,
                                    p2 = null,
                                    p3 = it.channel?.name == "YouTube Music"
                                )
                            },
                            onSearchClick = onSearchClick
                        )

                        1 -> HomeDiscovery(
                            onMoodClick = { mood -> moodRoute(mood.toUiMood()) },
                            onNewReleaseAlbumClick = { albumRoute(it) },
                            onSearchClick = onSearchClick,
                            onMoreMoodsClick = { moreMoodsRoute() },
                            onMoreAlbumsClick = { moreAlbumsRoute() },
                            onPlaylistClick = { playlistRoute(it, null, null, true) }
                        )

                        2 -> HomeSongs(
                            onSearchClick = onSearchClick
                        )

                        3 -> HomePlaylists(
                            onBuiltInPlaylist = { builtInPlaylistRoute(it) },
                            onPlaylistClick = { localPlaylistRoute(it.id) },
                            onPipedPlaylistClick = { session, playlist ->
                                pipedPlaylistRoute(
                                    p0 = session.apiBaseUrl.toString(),
                                    p1 = session.token,
                                    p2 = playlist.id.toString()
                                )
                            },
                            onSearchClick = onSearchClick
                        )

                        4 -> HomeArtistList(
                            onArtistClick = { artistRoute(it.id) },
                            onSearchClick = onSearchClick
                        )

                        5 -> HomeAlbums(
                            onAlbumClick = { albumRoute(it.id) },
                            onSearchClick = onSearchClick
                        )

                        6 -> HomeLocalSongs(
                            onSearchClick = onSearchClick
                        )
                    }
                }
            }
        }
    }
}
