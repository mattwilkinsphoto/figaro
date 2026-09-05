import unittest

from check_links import local_targets


class LinkTests(unittest.TestCase):
    def test_relative_paths_and_fragments(self):
        self.assertEqual(list(local_targets("[one](../README.md#intro) [two](<a%20b.md>)")),
                         ["../README.md", "a b.md"])

    def test_external_links_and_code_are_ignored(self):
        self.assertEqual(list(local_targets(
            '[web](https://example.com) [mail](mailto:a@example.com) [anchor](#here)\n'
            '```scala\n[not a link](missing.md)\n```\n[real](good.md)')), ["good.md"])

    def test_inline_scala_generics_are_not_links(self):
        self.assertEqual(list(local_targets('`Constant[T](value)` ``Name[T]("x")`` [real](good.md)')),
                         ["good.md"])


if __name__ == "__main__":
    unittest.main()
