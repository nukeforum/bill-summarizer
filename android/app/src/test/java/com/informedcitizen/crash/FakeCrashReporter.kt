package com.informedcitizen.crash

class FakeCrashReporter : CrashReporter {

    data class Recorded(val throwable: Throwable, val message: String?)

    private val _recorded = mutableListOf<Recorded>()
    val recorded: List<Recorded> get() = _recorded.toList()

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        _recorded.add(Recorded(throwable, message))
    }
}
