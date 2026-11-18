package com.nutra.app

import android.app.Application
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.repository.NutritionRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NutraApplication : Application() {

    // Eagerly injected so DataStore & Room warm up on IO threads in parallel with Activity launch.
    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var nutritionRepository: NutritionRepository
}
