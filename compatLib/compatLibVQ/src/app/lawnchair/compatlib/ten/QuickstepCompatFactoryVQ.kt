package app.lawnchair.compatlib.ten;

import app.lawnchair.compatlib.ActivityManagerCompat
import app.lawnchair.compatlib.ActivityOptionsCompat
import app.lawnchair.compatlib.eleven.QuickstepCompatFactoryVR

class QuickstepCompatFactoryVQ : QuickstepCompatFactoryVR() {

    override fun getActivityManagerCompat(): ActivityManagerCompat {
        return ActivityManagerCompatVQ()
    }

    override fun getActivityOptionsCompat(): ActivityOptionsCompat {
        return ActivityOptionsCompatVQ()
    }
}
