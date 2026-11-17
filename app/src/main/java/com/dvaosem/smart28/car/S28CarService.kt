package com.dvaosem.smart28.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Android Auto entry (IOT category): favourite switches (gate, lights) as one-tap toggles. */
class S28CarService : CarAppService() {
    // Allow any host: the app is sideloaded, so Google's allow-list of signed hosts does not apply.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = SwitchesScreen(carContext)
    }
}
