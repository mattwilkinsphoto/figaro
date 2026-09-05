"""Pure parser tests; no generated output or temporary directories."""
import unittest
from build_reference import DocParser, clean, invocation, signature_parts, split_top


class SignatureTests(unittest.TestCase):
    def test_generic_varargs_and_context(self):
        types, groups, result = signature_parts(
            "def apply[T](targets: Element[T]*)(implicit universe: Universe): Algorithm[T]", "apply")
        self.assertEqual(result, "Algorithm[T]")
        self.assertEqual(invocation("Factory", True, "apply", types, groups), "Factory.apply[T](targets*)(using universe)")

    def test_nested_function_and_tuple(self):
        types, groups, result = signature_parts(
            "def map[T, U](f: (T, List[(Int, U)]) => Map[T, U], n: Int = 2): List[U]", "map")
        self.assertEqual(len(groups[0]["parameters"]), 2)
        self.assertEqual(invocation("Owner", False, "map", types, groups), "receiver.map[T, U](f, n)")

    def test_empty_and_parameterless(self):
        for signature, expected in [("def kill(): Unit", "receiver.kill()"), ("def isActive: Boolean", "receiver.isActive")]:
            name = signature.split()[1].split("(")[0].split(":")[0]
            types, groups, result = signature_parts(signature, name)
            self.assertEqual(invocation("Owner", False, name, types, groups), expected)

    def test_default_string_delimiters(self):
        _, groups, result = signature_parts('def f(s: String = "(a,b)", n: Int): Unit', "f")
        self.assertEqual(len(groups[0]["parameters"]), 2)
        self.assertEqual(result, "Unit")

    def test_operator(self):
        _, groups, result = signature_parts("def ++(that: List[Int]): List[Int]", "++")
        self.assertEqual(invocation("Owner", False, "++", [], groups), "receiver.++(that)")

    def test_auxiliary_constructor(self):
        types, groups, result = signature_parts("def this(n: Int)", "this")
        self.assertEqual(invocation("Owner", False, "this", types, groups), "new Owner(n)")

    def test_html_content_only(self):
        parser = DocParser()
        parser.feed('<nav>ignore</nav><div id="content"><div class="signature">def f(x: Int): Unit</div></div><footer>ignore</footer>')
        self.assertEqual(clean(parser.root.first("signature").text()), "def f(x: Int): Unit")
        self.assertNotIn("ignore", parser.root.text())

    def test_malformed_signature_fails(self):
        with self.assertRaises(ValueError):
            signature_parts("def f(x: List[Int]", "f")


if __name__ == "__main__":
    unittest.main()
