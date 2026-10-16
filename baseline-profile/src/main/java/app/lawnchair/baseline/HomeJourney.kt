package app.lawnchair.baseline

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** The launcher must be the device's default Home when collecting this journey. */
internal fun MacrobenchmarkScope.exerciseDrawerAndSearch() {
    device.wait(Until.hasObject(By.res(Constants.PACKAGE_NAME, "workspace")), 5_000)
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(width / 2, height * 4 / 5, width / 2, height / 4, 16)
    device.waitForIdle()
    device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 12)
    device.waitForIdle()
    val input = device.findObject(By.res(Constants.PACKAGE_NAME, "input"))
    input?.text = "omni"
    device.waitForIdle()
    input?.text = "أومني"
    device.waitForIdle()
    device.pressBack()
    pressHome()
}
