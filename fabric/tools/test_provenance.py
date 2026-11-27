import os
import shutil
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))


def run(repo):
    return subprocess.run(["bash", os.path.join(HERE, "provenance.sh"), repo], capture_output=True, text=True, timeout=60)


class ProvenanceTest(unittest.TestCase):
    def clone(self, root):
        dest = os.path.join(root, "clone")
        subprocess.run(["git", "clone", "-q", "--no-hardlinks", REPO, dest], check=True, timeout=120)
        return dest

    def test_clean_clone_with_untracked_build_dirs_is_clean(self):
        with tempfile.TemporaryDirectory() as root:
            repo = self.clone(root)
            os.makedirs(os.path.join(repo, "fabric", "build", "libs"))
            open(os.path.join(repo, "engine", "stray.txt"), "w").close()
            r = run(repo)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertRegex(r.stdout, r"^rev=[0-9a-f]{7,40} dirty=no$")

    def test_tracked_edit_is_dirty(self):
        with tempfile.TemporaryDirectory() as root:
            repo = self.clone(root)
            with open(os.path.join(repo, "fabric", "build.gradle.kts"), "a") as f:
                f.write("\n")
            r = run(repo)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertTrue(r.stdout.strip().endswith("dirty=yes"), r.stdout)

    def test_export_without_git_is_refused(self):
        with tempfile.TemporaryDirectory() as root:
            export = os.path.join(root, "export")
            shutil.copytree(os.path.join(REPO, "fabric", "tools"), os.path.join(export, "fabric", "tools"))
            r = run(export)
            self.assertEqual(r.returncode, 2, r.stdout)
            self.assertIn("refused:", r.stdout)
            self.assertNotIn("dirty=no", r.stdout)


if __name__ == "__main__":
    unittest.main()
