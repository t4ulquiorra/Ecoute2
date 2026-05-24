package com.ecoute.music.preferences

import com.ecoute.music.GlobalPreferencesHolder

object AccountPreferences : GlobalPreferencesHolder() {
    var innerTubeCookie by string("")
    var visitorData by string("")
    var dataSyncId by string("")
    var accountName by string("")
    var accountEmail by string("")
    var accountChannelHandle by string("")
}
