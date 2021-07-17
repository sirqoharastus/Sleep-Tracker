/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.content.res.Resources
import android.view.animation.Transformation
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

            private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val uiScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    private val tonight = MutableLiveData<SleepNight?>()
    private val nights = database.getAllNights()
    private val _showSnackbarEvent = MutableLiveData<Boolean>()
    val showSnackbarEvent: LiveData<Boolean> get() = _showSnackbarEvent

    fun doneShowingSnackbar(){
        _showSnackbarEvent.value = null
    }

    val nightString = Transformations.map(nights){nights ->
        formatNights(nights, application.resources)
    }

    private val _navigationToSleepQuality = MutableLiveData<SleepNight>()

    val navigationToSleepQuality: LiveData<SleepNight> get() = _navigationToSleepQuality

    fun doneNavigating(){
        _navigationToSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch(Dispatchers.Main) {
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO){
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli){
                night = null
            }
            night
        }
    }

    fun onStartTracking(){
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            withContext(Dispatchers.Main){
                tonight.value = getTonightFromDatabase()
            }
        }
    }

    private suspend fun insert(Night: SleepNight) {
        withContext(Dispatchers.IO){
            database.insert(Night)
        }
    }

     fun onStopTracking(){
        uiScope.launch {
            val oldNight = tonight.value ?:return@launch

            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            withContext(Dispatchers.Main){
                _navigationToSleepQuality.value = oldNight
            }
        }
    }

    private suspend fun update(Night: SleepNight) {
        withContext(Dispatchers.IO){
            database.update(Night)
        }
    }
    fun onClear(){
        uiScope.launch {
            clear()
            withContext(Dispatchers.Main){
                tonight.value = null
                _showSnackbarEvent.value = true
            }
        }
    }

    val startButtonVisible = Transformations.map(tonight){
        null == it
    }

    val stopButtonVisible = Transformations.map(tonight){
        null != it
    }

    val clearButtonVisible = Transformations.map(nights){
        it.isNotEmpty()
    }


    suspend fun clear(){
        withContext(Dispatchers.IO){
            database.clear()
        }
    }

}

