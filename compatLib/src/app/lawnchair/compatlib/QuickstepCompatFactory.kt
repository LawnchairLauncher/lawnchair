package app.lawnchair.compatlib;

abstract class QuickstepCompatFactory {

    abstract fun getActivityManagerCompat(): ActivityManagerCompat

    abstract fun getActivityOptionsCompat(): ActivityOptionsCompat

    abstract fun getRemoteTransitionCompat(): RemoteTransitionCompat
}

