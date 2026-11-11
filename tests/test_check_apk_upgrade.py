"""Offline tests: no private keystore, APK, Android SDK or network required."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "scripts" / "check_apk_upgrade.py"
SPEC = importlib.util.spec_from_file_location("check_apk_upgrade", MODULE)
gate = importlib.util.module_from_spec(SPEC)
import sys
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)

CERT_A = "a" * 64
CERT_B = "b" * 64


class UpgradeGateTests(unittest.TestCase):
    def fixture(self, package="org.openarchive.telegram.qa.workqa20261028", code=51, cert=CERT_A):
        return gate.ApkIdentity(package, code, cert)

    def test_positive_same_package_signer_increased_version(self):
        gate.compare(self.fixture(), self.fixture(code=52))

    def test_negative_different_certificate(self):
        with self.assertRaisesRegex(gate.PreflightError, "сертификат"):
            gate.compare(self.fixture(), self.fixture(code=52, cert=CERT_B))

    def test_negative_different_package_even_same_cert(self):
        with self.assertRaisesRegex(gate.PreflightError, "package ID"):
            gate.compare(self.fixture(), self.fixture(package="org.openarchive.telegram.qa.previewstable", code=52))

    def test_negative_version_code_equal(self):
        with self.assertRaisesRegex(gate.PreflightError, "versionCode"):
            gate.compare(self.fixture(), self.fixture())

    def test_negative_version_code_downgrade(self):
        with self.assertRaisesRegex(gate.PreflightError, "versionCode"):
            gate.compare(self.fixture(code=52), self.fixture(code=51))

    def test_parse_realistic_aapt_badging(self):
        p, v = gate.parse_badging("package: name='org.openarchive.telegram.qa.workqa20261028' versionCode='51' versionName='0.51'\nsdkVersion:'26'\n")
        self.assertEqual(p, "org.openarchive.telegram.qa.workqa20261028")
        self.assertEqual(v, 51)

    def test_badging_missing_or_duplicated_is_blocked(self):
        for data in ("", "sdkVersion:'35'", "package: name='a.b' versionCode='1'\npackage: name='a.b' versionCode='2'"):
            with self.subTest(data=data), self.assertRaises(gate.PreflightError):
                gate.parse_badging(data)

    def test_badging_invalid_code_blocked(self):
        with self.assertRaises(gate.PreflightError):
            gate.parse_badging("package: name='a.b' versionCode='dev'")

    def test_parse_one_signer(self):
        self.assertEqual(gate.parse_signers("Signer #1 certificate SHA-256 digest: " + CERT_A + "\n"), CERT_A)
        self.assertEqual(gate.parse_signers("Signer #1 certificate SHA-256 digest: " + ("AA:" * 31 + "AA") + "\n"), CERT_A)

    def test_zero_or_multiple_signers_blocked(self):
        for data in ("", f"Signer #1 certificate SHA-256 digest: {CERT_A}\nSigner #2 certificate SHA-256 digest: {CERT_B}\n"):
            with self.subTest(data=data), self.assertRaises(gate.PreflightError):
                gate.parse_signers(data)

    def test_invalid_signer_length_blocked(self):
        with self.assertRaises(gate.PreflightError):
            gate.parse_signers("Signer #1 certificate SHA-256 digest: 123456\n")

    def test_tool_nonzero_fails_closed(self):
        fake = type("R", (), {"returncode": 1, "stdout": "", "stderr": "invalid"})()
        with patch.object(gate.subprocess, "run", return_value=fake):
            with self.assertRaises(gate.PreflightError):
                gate.run_checked(["apksigner", "verify", "test.apk"])


if __name__ == "__main__":
    unittest.main()
