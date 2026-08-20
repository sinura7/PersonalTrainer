package com.sinura.personaltrainer

import android.app.Application
import androidx.lifecycle.AndroidViewModel

fun Application.appContainer(): AppContainer = (this as PersonalTrainerApp).container

abstract class AppViewModel(application: Application) : AndroidViewModel(application) {
    protected val container: AppContainer = application.appContainer()
}
