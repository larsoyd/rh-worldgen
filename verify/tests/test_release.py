import unittest

from verify.release import readme_contracts


class Readme(unittest.TestCase):
    def test_section_and_subsections_present(self):
        text = "# x\n\n## Contracts\n\n### RHW-C1 v1\nbody\n\n### RHW-C3 v1\nbody\n\n## Other\n"
        self.assertEqual(readme_contracts(text), "ok")

    def test_missing_section(self):
        self.assertEqual(readme_contracts("# x\n\n## Usage\n"), "refused: README has no Contracts section")

    def test_missing_subsection(self):
        text = "## Contracts\n\n### RHW-C1 v1\nbody\n\n## Other\n\n### RHW-C3 v1\n"
        self.assertEqual(readme_contracts(text), "refused: Contracts section lacks RHW-C3 v1")

    def test_empty_subsection(self):
        text = "## Contracts\n\n### RHW-C1 v1\n\n### RHW-C3 v1\nbody\n"
        self.assertEqual(readme_contracts(text), "refused: Contracts section lacks RHW-C1 v1")


if __name__ == "__main__":
    unittest.main()
