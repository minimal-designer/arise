package com.minimaldesigner.arise

import android.app.Application
import com.minimaldesigner.arise.data.AriseDb
import com.minimaldesigner.arise.data.Backups
import com.minimaldesigner.arise.data.ChallengeRepository
import com.minimaldesigner.arise.data.FoodRepository
import com.minimaldesigner.arise.data.HealthRepository
import com.minimaldesigner.arise.data.PhotoStore
import com.minimaldesigner.arise.data.Prefs

class AriseApp : Application() {
    val db by lazy { AriseDb.open(this) }
    val photoStore by lazy { PhotoStore(this) }
    val challenges by lazy { ChallengeRepository(db, photoStore) }
    val prefs by lazy { Prefs(this) }
    val food by lazy { FoodRepository(this) }
    val health by lazy { HealthRepository(this) }
    val backups by lazy { Backups(db, photoStore, cacheDir) }
}
