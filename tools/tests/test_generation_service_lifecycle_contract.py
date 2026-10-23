from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "app/src/main"
JAVA = APP / "java/com/lucent/app"


class GenerationServiceLifecycleContractTest(unittest.TestCase):
    def setUp(self):
        self.service = (JAVA / "chat/ChatGenerationService.kt").read_text(encoding="utf-8")

    def test_activity_and_task_removal_do_not_cancel_generation(self):
        activity = (JAVA / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertNotIn("ChatSessions.stopAll()", activity)
        self.assertNotIn("override fun onTaskRemoved", self.service)
        android = "{http://schemas.android.com/apk/res/android}"
        services = ET.parse(APP / "AndroidManifest.xml").findall("application/service")
        service = next(s for s in services if s.get(android + "name") == ".chat.ChatGenerationService")
        self.assertEqual("false", service.get(android + "stopWithTask"))
        self.assertEqual("false", service.get(android + "exported"))
        self.assertEqual("dataSync", service.get(android + "foregroundServiceType"))

    def test_idle_shutdown_rechecks_live_work_and_latest_start_before_removing_protection(self):
        idle = self.service.split("private fun stopProtectionIfIdle()", 1)[1].split("private fun stopProtection()", 1)[0]
        checks = ["if (ChatSessions.activeCount.value != 0) return",
                  "if (!stopSelfResult(latestStartId)) return",
                  "shutdownHandled = true", "observation?.cancel()",
                  "stopForeground(STOP_FOREGROUND_REMOVE)"]
        positions = [idle.index(check) for check in checks]
        self.assertEqual(sorted(positions), positions)
        self.assertNotIn("stopSelf()", idle)
        start = self.service.split("override fun onStartCommand", 1)[1].split("override fun onTimeout", 1)[0]
        self.assertIn("latestStartId = startId", start)
        self.assertIn("shutdownHandled = false", start)
        self.assertLess(start.index("startForeground("), start.index("activeCount.collect"))
        self.assertIn("stopProtectionIfIdle()", start)

    def test_explicit_stop_waits_for_settlement_but_timeout_stops_service_promptly(self):
        stop = self.service.split("if (intent?.action == ACTION_STOP)", 1)[1].split("observation?.cancel()", 1)[0]
        self.assertIn("ChatSessions.stopAll()", stop)
        self.assertNotIn("stopProtection()", stop)
        timeout = self.service.split("override fun onTimeout", 1)[1].split("override fun onDestroy", 1)[0]
        self.assertIn("ChatSessions.stopAll()", timeout)
        self.assertIn("stopProtection()", timeout)
        failure = self.service.split("catch (error: RuntimeException)", 1)[1].split("if (intent?.action", 1)[0]
        self.assertIn("ChatSessions.stopAll()", failure)
        self.assertIn("stopProtection()", failure)
        shutdown = self.service.split("private fun stopProtection()", 1)[1].split("private fun createNotificationChannel", 1)[0]
        self.assertIn("shutdownHandled = true", shutdown)
        self.assertIn("stopSelf(latestStartId)", shutdown)
        self.assertNotIn("stopSelf()", shutdown)

    def test_destroy_cancels_observer_without_cancelling_a_new_request_after_idle_stop(self):
        destroy = self.service.split("override fun onDestroy()", 1)[1].split("private fun stopProtectionIfIdle", 1)[0]
        self.assertIn("serviceScope.cancel()", destroy)
        self.assertIn("if (!shutdownHandled) ChatSessions.stopAll()", destroy)

    def test_foreground_protection_is_only_for_real_work_without_resurrection(self):
        ensure = self.service.split("fun ensureRunning", 1)[1]
        self.assertIn("Looper.myLooper() == Looper.getMainLooper()", ensure)
        self.assertLess(ensure.index("check(ChatSessions.activeCount.value > 0)"), ensure.index("ContextCompat.startForegroundService"))
        self.assertIn("return START_NOT_STICKY", self.service)
        for forbidden in ("return START_STICKY", "START_REDELIVER_INTENT", "WakeLock", "AlarmManager", "WorkManager"):
            self.assertNotIn(forbidden, self.service)
        sessions = (JAVA / "chat/ChatSessions.kt").read_text(encoding="utf-8")
        self.assertLess(sessions.index("running.value += 1"), sessions.index("ChatGenerationService.ensureRunning(app)"))


if __name__ == "__main__":
    unittest.main()
