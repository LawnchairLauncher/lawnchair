#!/usr/bin/env python3
"""Run real JVM rule/policy tests independently of the known Android NO-SOURCE test wiring.

The ktlint CLI bundles Kotlin's 2.2.21 compiler. Android/Compose and serializer generation
are validated by the existing three-variant APK CI, not by this small pure-logic suite.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("OMNI_TEST_CACHE", str(Path(tempfile.gettempdir()) / "omni-experience-jvm")))
CACHE.mkdir(parents=True, exist_ok=True)
ARTIFACTS = {
    # Match the APK runtime instead of the older coroutines bundled inside ktlint.
    "coroutines.jar": "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.jar",
    "ktlint.jar": "com/pinterest/ktlint/ktlint-cli/1.8.0/ktlint-cli-1.8.0-all.jar",
    "junit.jar": "junit/junit/4.13.2/junit-4.13.2.jar",
    "hamcrest.jar": "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
    "icu.jar": "com/ibm/icu/icu4j/68.2/icu4j-68.2.jar",
    "fuzzy.jar": "me/xdrop/fuzzywuzzy/1.4.0/fuzzywuzzy-1.4.0.jar",
}
for filename, artifact in ARTIFACTS.items():
    target = CACHE / filename
    if not target.exists():
        with urllib.request.urlopen("https://repo.maven.apache.org/maven2/" + artifact) as response:
            temporary = target.with_suffix(".download")
            with temporary.open("wb") as output:
                import shutil
                shutil.copyfileobj(response, output)
            temporary.replace(target)
classpath = os.pathsep.join(str(CACHE / name) for name in ARTIFACTS)
java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
base = "lawnchair/src/app/lawnchair/"
sources = [
    base + "search/algorithms/engine/provider/apps/SearchTextNormalizer.kt",
    base + "search/algorithms/ProgressiveSearch.kt",
    base + "search/algorithms/engine/provider/apps/AppMatcher.kt",
    base + "omni/experience/ExperienceModels.kt",
    base + "omni/experience/AppInventoryLabel.kt",
    base + "omni/experience/HomeLayoutPolicy.kt",
    "lawnchair/src/test/kotlin/app/lawnchair/omni/experience/HomeLayoutPolicyTest.kt",
    base + "omni/experience/ExperienceCapabilityNames.kt",
    base + "omni/OmniLauncherAccessPolicy.kt",
    base + "omni/OmniSearchPolicy.kt",
    base + "omni/experience/widgets/BoundedWidgetCalls.kt",
    base + "ui/preferences/components/layout/DividerLayoutMetrics.kt",
    "lawnchair/src/test/kotlin/app/lawnchair/omni/experience/WidgetLoadingTest.kt",
    "lawnchair/src/test/kotlin/app/lawnchair/omni/experience/ExperienceRulesTest.kt",
    "lawnchair/src/test/kotlin/app/lawnchair/omni/OmniLauncherAccessPolicyTest.kt",
    "lawnchair/src/test/kotlin/app/lawnchair/omni/OmniSearchPolicyTest.kt",
]
sources.append("lawnchair/src/test/kotlin/app/lawnchair/omni/experience/CalendarDateFormatterTest.kt")
with tempfile.TemporaryDirectory(prefix="omni-test-classes-") as output:
    # Android ICU is platform-provided. Run the identical formatter against the Android 11-era
    # ICU4J API on the JVM; APK CI independently checks actual android.icu compilation.
    formatter = Path(output) / "CalendarDateFormatter.kt"
    formatter.write_text((ROOT / (base + "smartspace/CalendarDateFormatter.kt")).read_text().replace("import android.icu.", "import com.ibm.icu."))
    sources.append(str(formatter))
    subprocess.run([java, "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-classpath", classpath, "-d", output] + sources, cwd=ROOT, check=True)
    subprocess.run([java, "-cp", output + os.pathsep + classpath, "org.junit.runner.JUnitCore", "app.lawnchair.omni.experience.ExperienceRulesTest", "app.lawnchair.omni.OmniLauncherAccessPolicyTest", "app.lawnchair.omni.OmniSearchPolicyTest", "app.lawnchair.omni.experience.CalendarDateFormatterTest", "app.lawnchair.omni.experience.WidgetLoadingTest", "app.lawnchair.omni.experience.HomeLayoutPolicyTest"], cwd=ROOT, check=True)
